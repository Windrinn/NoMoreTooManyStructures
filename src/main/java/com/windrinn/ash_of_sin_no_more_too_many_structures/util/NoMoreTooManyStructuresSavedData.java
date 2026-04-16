package com.windrinn.ash_of_sin_no_more_too_many_structures.util;

import com.windrinn.ash_of_sin_no_more_too_many_structures.config.NoMoreTooManyStructuresConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NoMoreTooManyStructuresSavedData extends SavedData {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Map<UUID, PlacedObject> placedObjects = new ConcurrentHashMap<>();
    private final Map<ChunkPos, List<PlacedObject>> objectsByChunk = new ConcurrentHashMap<>();

    public static NoMoreTooManyStructuresSavedData create() {
        return new NoMoreTooManyStructuresSavedData();
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        ListTag list = new ListTag();
        for (PlacedObject obj : placedObjects.values()) {
            list.add(obj.save());
        }
        tag.put("placed_objects", list);
        return tag;
    }

    public static NoMoreTooManyStructuresSavedData load(CompoundTag tag) {
        NoMoreTooManyStructuresSavedData data = new NoMoreTooManyStructuresSavedData();
        ListTag list = tag.getList("placed_objects", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            PlacedObject obj = PlacedObject.load(list.getCompound(i));
            data.placedObjects.put(obj.uuid, obj);
            Set<ChunkPos> covered = data.getCoveredChunks(obj.aabb);
            for (ChunkPos cp : covered) {
                data.objectsByChunk.computeIfAbsent(cp, k -> new ArrayList<>()).add(obj);
            }
        }
        return data;
    }

    public void addPlacedObject(AABB aabb, String type, boolean whitelisted) {
        Set<ChunkPos> covered = getCoveredChunks(aabb);
        for (ChunkPos cp : covered) {
            List<PlacedObject> list = objectsByChunk.get(cp);
            if (list != null) {
                for (PlacedObject obj : list) {
                    if (obj.aabb.equals(aabb)) {
                        if (NoMoreTooManyStructuresConfig.DEBUG.get()) {
                            LOGGER.debug("Duplicate structure/feature {} at {}, skipping record", type, aabb.getCenter());
                        }
                        return;
                    }
                }
            }
        }

        PlacedObject obj = new PlacedObject(aabb, type, whitelisted);
        placedObjects.put(obj.uuid, obj);

        for (ChunkPos cp : covered) {
            objectsByChunk.computeIfAbsent(cp, k -> new ArrayList<>()).add(obj);
        }

        setDirty();
    }

    public void removeObjects(Collection<UUID> uuids) {
        if (uuids.isEmpty()) return;
        boolean changed = false;
        for (UUID uuid : uuids) {
            PlacedObject removed = placedObjects.remove(uuid);
            if (removed != null) {
                Set<ChunkPos> covered = getCoveredChunks(removed.aabb);
                for (ChunkPos cp : covered) {
                    List<PlacedObject> list = objectsByChunk.get(cp);
                    if (list != null) {
                        list.removeIf(obj -> obj.uuid.equals(uuid));
                    }
                }
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    private Set<ChunkPos> getCoveredChunks(AABB aabb) {
        int minX = (int) Math.floor(aabb.minX / 16.0);
        int maxX = (int) Math.floor(aabb.maxX / 16.0);
        int minZ = (int) Math.floor(aabb.minZ / 16.0);
        int maxZ = (int) Math.floor(aabb.maxZ / 16.0);

        Set<ChunkPos> chunks = new HashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                chunks.add(new ChunkPos(x, z));
            }
        }
        return chunks;
    }

    private static class PlacedObject {
        final UUID uuid;
        final AABB aabb;
        final String type;
        final boolean whitelisted;

        PlacedObject(AABB aabb, String type, boolean whitelisted) {
            this(UUID.randomUUID(), aabb, type, whitelisted);
        }

        private PlacedObject(UUID uuid, AABB aabb, String type, boolean whitelisted) {
            this.uuid = uuid;
            this.aabb = aabb;
            this.type = type;
            this.whitelisted = whitelisted;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("uuid", uuid);
            tag.putDouble("minX", aabb.minX);
            tag.putDouble("minY", aabb.minY);
            tag.putDouble("minZ", aabb.minZ);
            tag.putDouble("maxX", aabb.maxX);
            tag.putDouble("maxY", aabb.maxY);
            tag.putDouble("maxZ", aabb.maxZ);
            tag.putString("type", type);
            tag.putBoolean("whitelisted", whitelisted);
            return tag;
        }

        static PlacedObject load(CompoundTag tag) {
            UUID uuid = tag.getUUID("uuid");
            double minX = tag.getDouble("minX");
            double minY = tag.getDouble("minY");
            double minZ = tag.getDouble("minZ");
            double maxX = tag.getDouble("maxX");
            double maxY = tag.getDouble("maxY");
            double maxZ = tag.getDouble("maxZ");
            AABB aabb = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
            String type = tag.getString("type");
            boolean whitelisted = tag.getBoolean("whitelisted");
            return new PlacedObject(uuid, aabb, type, whitelisted);
        }
    }

    public PlacementResult checkPlacement(BlockPos center, AABB aabb, String type, int maxNearby, double radius, boolean isWhitelisted) {
        Set<ChunkPos> covered = getCoveredChunks(aabb);
        List<PlacedObject> overlapCandidates = new ArrayList<>();
        for (ChunkPos cp : covered) {
            List<PlacedObject> list = objectsByChunk.get(cp);
            if (list != null) {
                overlapCandidates.addAll(list);
            }
        }

        List<UUID> toRemove = new ArrayList<>();
        for (PlacedObject existing : overlapCandidates) {
            if (!existing.aabb.intersects(aabb)) continue;

            if (isWhitelisted && !existing.whitelisted) {
                toRemove.add(existing.uuid);
            } else {
                return PlacementResult.deny("overlap");
            }
        }

        if (!NoMoreTooManyStructuresConfig.ONLY_OVERLAP.get()) {
            Vec3 centerVec = new Vec3(center.getX(), center.getY(), center.getZ());
            long count = placedObjects.values().stream()
                    .filter(obj -> !obj.whitelisted)
                    .filter(obj -> obj.aabb.getCenter().distanceToSqr(centerVec) <= radius * radius)
                    .count();
            if (count >= maxNearby) {
                return PlacementResult.deny("density");
            }
        }

        return toRemove.isEmpty() ? PlacementResult.allow() : PlacementResult.allow(toRemove);
    }

    public static class PlacementResult {
        private final boolean allowed;
        private final String denyReason;
        private final List<UUID> toRemove;

        private PlacementResult(boolean allowed, String denyReason, List<UUID> toRemove) {
            this.allowed = allowed;
            this.denyReason = denyReason;
            this.toRemove = toRemove;
        }

        public static PlacementResult allow() {
            return new PlacementResult(true, null, Collections.emptyList());
        }

        public static PlacementResult allow(List<UUID> toRemove) {
            return new PlacementResult(true, null, new ArrayList<>(toRemove));
        }

        public static PlacementResult deny(String reason) {
            return new PlacementResult(false, reason, Collections.emptyList());
        }

        public boolean isAllowed() { return allowed; }
        public String getDenyReason() { return denyReason; }
        public List<UUID> getToRemove() { return Collections.unmodifiableList(toRemove); }
    }
}