package com.windrinn.ash_of_sin_no_more_too_many_structures.mixin;

import com.windrinn.ash_of_sin_no_more_too_many_structures.config.NoMoreTooManyStructuresConfig;
import com.windrinn.ash_of_sin_no_more_too_many_structures.util.NoMoreTooManyStructuresPlacementValidator;
import com.windrinn.ash_of_sin_no_more_too_many_structures.util.NoMoreTooManyStructuresSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Feature.class)
public class FeatureMixin<FC extends FeatureConfiguration> {
    private static final Logger LOGGER = LogManager.getLogger();

    @Inject(
            method = "place(Lnet/minecraft/world/level/levelgen/feature/configurations/FeatureConfiguration;Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void checkOverlap(FC config, WorldGenLevel level, ChunkGenerator generator,
                              RandomSource random, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Feature<?> feature = (Feature<?>) (Object) this;
        ResourceLocation id = ForgeRegistries.FEATURES.getKey(feature);
        if (id == null) return;

        if (NoMoreTooManyStructuresConfig.isFeatureIgnored(id)) {
            if (NoMoreTooManyStructuresConfig.DEBUG.get()) {
                LOGGER.debug("Feature {} is ignored, generating without checks", id);
            }
            return;
        }

        if (NoMoreTooManyStructuresConfig.isFeatureBlacklisted(id)) {
            if (NoMoreTooManyStructuresConfig.DEBUG.get()) {
                LOGGER.debug("Feature {} is blacklisted, skipping generation.", id);
            }
            cir.setReturnValue(false);
            return;
        }

        boolean isWhitelisted = NoMoreTooManyStructuresConfig.isFeatureWhitelisted(id);
        boolean canPlace = NoMoreTooManyStructuresPlacementValidator.canPlaceFeature(
                level, pos, id.toString());

        if (!canPlace) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "place(Lnet/minecraft/world/level/levelgen/feature/configurations/FeatureConfiguration;Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("RETURN")
    )
    private void onPlaceReturn(FC config, WorldGenLevel level, ChunkGenerator generator,
                               RandomSource random, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;

        Feature<?> feature = (Feature<?>) (Object) this;
        ResourceLocation id = ForgeRegistries.FEATURES.getKey(feature);
        if (id == null) return;

        if (NoMoreTooManyStructuresConfig.isFeatureIgnored(id)) {
            return;
        }

        boolean isWhitelisted = NoMoreTooManyStructuresConfig.isFeatureWhitelisted(id);

        NoMoreTooManyStructuresSavedData data = getData(level);
        if (data == null) return;

        double halfSize = 5.0;
        AABB aabb = new AABB(pos.getX() - halfSize, pos.getY() - halfSize, pos.getZ() - halfSize,
                pos.getX() + halfSize, pos.getY() + halfSize, pos.getZ() + halfSize);
        data.addPlacedObject(aabb, id.toString(), isWhitelisted);
    }

    private NoMoreTooManyStructuresSavedData getData(WorldGenLevel level) {
        ServerLevel serverLevel = level.getLevel();
        if (serverLevel == null) return null;
        return serverLevel.getDataStorage().computeIfAbsent(
                NoMoreTooManyStructuresSavedData::load,
                NoMoreTooManyStructuresSavedData::create,
                "no_more_too_many_structures_data"
        );
    }
}