package com.windrinn.ash_of_sin_no_more_too_many_structures.mixin;

import com.windrinn.ash_of_sin_no_more_too_many_structures.config.NoMoreTooManyStructuresConfig;
import com.windrinn.ash_of_sin_no_more_too_many_structures.util.NoMoreTooManyStructuresPlacementValidator;
import com.windrinn.ash_of_sin_no_more_too_many_structures.util.NoMoreTooManyStructuresSavedData;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureFeatureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.FeatureAccess;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {

    private static final Logger LOGGER = LogManager.getLogger();

    @WrapOperation(
            method = "tryGenerateStructure",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/StructureFeatureManager;setStartForFeature(Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/levelgen/feature/ConfiguredStructureFeature;Lnet/minecraft/world/level/levelgen/structure/StructureStart;Lnet/minecraft/world/level/chunk/FeatureAccess;)V"
            )
    )
    private void filterStructure(StructureFeatureManager structureManager,
                                 SectionPos sectionPos,
                                 ConfiguredStructureFeature<?, ?> structure,
                                 StructureStart pendingStructure,
                                 FeatureAccess featureAccess,
                                 Operation<Void> original) {
        BoundingBox box = pendingStructure.getBoundingBox();
        if (box == null) {
            original.call(structureManager, sectionPos, structure, pendingStructure, featureAccess);
            return;
        }

        ResourceLocation id = getStructureKey(structure);
        if (id == null) {
            if (NoMoreTooManyStructuresConfig.DEBUG.get()) {
                LOGGER.warn("Structure key is null, skipping detection for {}", structure);
            }
            original.call(structureManager, sectionPos, structure, pendingStructure, featureAccess);
            return;
        } else if (NoMoreTooManyStructuresConfig.DEBUG.get()) {
            LOGGER.debug("Structure id: {}", id);
        }

        if (NoMoreTooManyStructuresConfig.isStructureIgnored(id)) {
            if (NoMoreTooManyStructuresConfig.DEBUG.get()) {
                LOGGER.debug("Structure {} is ignored, generating without checks and not recording", id);
            }
            original.call(structureManager, sectionPos, structure, pendingStructure, featureAccess);
            return;
        }

        if (NoMoreTooManyStructuresConfig.isStructureBlacklisted(id)) {
            if (NoMoreTooManyStructuresConfig.DEBUG.get()) {
                LOGGER.debug("Structure {} is blacklisted, skipping generation.", id);
            }
            return;
        }

        ServerLevel serverLevel = getServerLevel(structureManager);
        if (serverLevel == null) {
            original.call(structureManager, sectionPos, structure, pendingStructure, featureAccess);
            return;
        }

        ChunkPos chunkPos = pendingStructure.getChunkPos();
        int yCenter = (box.minY() + box.maxY()) / 2;
        BlockPos center = new BlockPos(chunkPos.getMinBlockX() + 8, yCenter, chunkPos.getMinBlockZ() + 8);

        boolean isWhitelisted = NoMoreTooManyStructuresConfig.isStructureWhitelisted(id);
        boolean canPlace = NoMoreTooManyStructuresPlacementValidator.canPlaceStructure(serverLevel, box, id.toString(), center);
        if (!canPlace) {
            if (NoMoreTooManyStructuresConfig.DEBUG.get()) {
                LOGGER.debug("Skipping generation of {} due to placement check", id);
            }
            return;
        }

        original.call(structureManager, sectionPos, structure, pendingStructure, featureAccess);

        if (pendingStructure.isValid()) {
            NoMoreTooManyStructuresSavedData data = serverLevel.getDataStorage()
                    .computeIfAbsent(NoMoreTooManyStructuresSavedData::load,
                            NoMoreTooManyStructuresSavedData::create,
                            "no_more_too_many_structures_data");
            AABB aabb = new AABB(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
            data.addPlacedObject(aabb, id.toString(), isWhitelisted);
        } else {
            LOGGER.warn("Structure {} at {} was marked as generated but is invalid after generation", id, center);
        }
    }

    private ResourceLocation getStructureKey(ConfiguredStructureFeature<?, ?> structure) {
        return Optional.ofNullable(BuiltinRegistries.CONFIGURED_STRUCTURE_FEATURE.getKey(structure))
                .orElse(new ResourceLocation("ash_of_sin_no_more_too_many_structures:none"));
    }

    private ServerLevel getServerLevel(StructureFeatureManager manager) {
        LevelAccessor levelAccessor = ((StructureFeatureManagerAccessor) manager).getLevel();
        if (levelAccessor instanceof ServerLevel) {
            return (ServerLevel) levelAccessor;
        } else if (levelAccessor instanceof WorldGenLevel) {
            return ((WorldGenLevel) levelAccessor).getLevel();
        }
        return null;
    }
}