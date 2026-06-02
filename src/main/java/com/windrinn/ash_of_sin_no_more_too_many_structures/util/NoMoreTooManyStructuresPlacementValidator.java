package com.windrinn.ash_of_sin_no_more_too_many_structures.util;

import com.windrinn.ash_of_sin_no_more_too_many_structures.config.NoMoreTooManyStructuresConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NoMoreTooManyStructuresPlacementValidator {
    private static final Logger LOGGER = LogManager.getLogger();

    public static boolean canPlaceStructure(LevelAccessor level, BoundingBox box, Structure structure, String structureName, BlockPos center) {
        NoMoreTooManyStructuresSavedData data = getData(level);
        if (data == null) return true;

        ResourceKey<Level> dimension = getDimension(level);
        AABB aabb = new AABB(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());

        ResourceLocation id = ResourceLocation.tryParse(structureName);

        boolean isWhitelisted = id != null && NoMoreTooManyStructuresConfig.isStructureWhitelisted(id, structure);
        boolean isIgnored = id != null && NoMoreTooManyStructuresConfig.isStructureIgnored(id, structure);
        boolean isBlacklisted = id != null && NoMoreTooManyStructuresConfig.isStructureBlacklisted(id, structure);

        if (isIgnored) {
            if (NoMoreTooManyStructuresConfig.DEBUG.get()) {
                LOGGER.debug("Structure {} is ignored, generating without checks", structureName);
            }
            return true;
        }
        if (isBlacklisted) {
            if (NoMoreTooManyStructuresConfig.DEBUG.get()) {
                LOGGER.debug("Structure {} is blacklisted, skipping generation.", structureName);
            }
            return false;
        }

        NoMoreTooManyStructuresSavedData.PlacementResult result = data.checkPlacement(
                center, aabb, structureName,
                NoMoreTooManyStructuresConfig.MAX_NEARBY.get(),
                NoMoreTooManyStructuresConfig.CHECK_RADIUS.get(),
                isWhitelisted
        );

        if (!result.isAllowed()) {
            if (NoMoreTooManyStructuresConfig.DEBUG.get()) {
                LOGGER.debug("Skipped structure {} at {} (center: {}) in dimension {} due to {}",
                        structureName, box.getCenter(), center, dimension.location(), result.getDenyReason());
            }
            return false;
        } else {
            if (NoMoreTooManyStructuresConfig.DEBUG.get()) {
                LOGGER.debug("Allowed structure {} at {} (center: {}) in dimension {}", structureName, box.getCenter(), center, dimension.location());
            }
        }

        if (!result.getToRemove().isEmpty()) {
            data.removeObjects(result.getToRemove());
            if (NoMoreTooManyStructuresConfig.DEBUG.get()) {
                LOGGER.info("Whitelisted structure {} overrides {} existing object(s)",
                        structureName, result.getToRemove().size());
            }
        }
        return true;
    }

    public static boolean canPlaceFeature(LevelAccessor level, BlockPos pos, String featureName) {
        NoMoreTooManyStructuresSavedData data = getData(level);
        if (data == null) return true;

        ResourceKey<Level> dimension = getDimension(level);
        double halfSize = 5.0;
        AABB aabb = new AABB(pos.getX() - halfSize, pos.getY() - halfSize, pos.getZ() - halfSize,
                pos.getX() + halfSize, pos.getY() + halfSize, pos.getZ() + halfSize);

        ResourceLocation id = ResourceLocation.tryParse(featureName);
        boolean isWhitelisted = id != null && NoMoreTooManyStructuresConfig.isFeatureWhitelisted(id);
        NoMoreTooManyStructuresSavedData.PlacementResult result = data.checkPlacement(
                pos, aabb, featureName,
                NoMoreTooManyStructuresConfig.MAX_NEARBY.get(),
                NoMoreTooManyStructuresConfig.CHECK_RADIUS.get(),
                isWhitelisted
        );

        if (!result.isAllowed()) {
            if (NoMoreTooManyStructuresConfig.DEBUG.get()) {
                LOGGER.info("Skipped feature {} at {} in dimension {} due to {}",
                        featureName, pos.toShortString(), dimension.location(), result.getDenyReason());
            }
            return false;
        } else {
            if (NoMoreTooManyStructuresConfig.DEBUG.get()) {
                LOGGER.debug("Allowed feature {} at {} in dimension {}", featureName, pos.toShortString(), dimension.location());
            }
        }

        if (!result.getToRemove().isEmpty()) {
            data.removeObjects(result.getToRemove());
            if (NoMoreTooManyStructuresConfig.DEBUG.get()) {
                LOGGER.info("Whitelisted feature {} overrides {} existing object(s)",
                        featureName, result.getToRemove().size());
            }
        }

        return true;
    }

    private static NoMoreTooManyStructuresSavedData getData(LevelAccessor level) {
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.getDataStorage().computeIfAbsent(
                    NoMoreTooManyStructuresSavedData::load,
                    NoMoreTooManyStructuresSavedData::create,
                    "no_more_too_many_structures_data"
            );
        }
        return null;
    }

    private static ResourceKey<Level> getDimension(LevelAccessor level) {
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.dimension();
        } else if (level instanceof WorldGenLevel worldGenLevel) {
            ServerLevel underlyingLevel = worldGenLevel.getLevel();
            if (underlyingLevel != null) {
                return underlyingLevel.dimension();
            }
        }
        return Level.OVERWORLD;
    }
}