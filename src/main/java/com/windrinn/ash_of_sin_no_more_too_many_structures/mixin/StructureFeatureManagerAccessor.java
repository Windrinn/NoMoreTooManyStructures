package com.windrinn.ash_of_sin_no_more_too_many_structures.mixin;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureFeatureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StructureFeatureManager.class)
public interface StructureFeatureManagerAccessor {
    @Accessor("level")
    LevelAccessor getLevel();
}