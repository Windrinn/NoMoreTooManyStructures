package com.windrinn.ash_of_sin_no_more_too_many_structures.mixin;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.windrinn.ash_of_sin_no_more_too_many_structures.config.NoMoreTooManyStructuresConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.commands.LocateCommand;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocateCommand.class)
public class LocateCommandMixin {

    @Inject(method = "locateStructure", at = @At("HEAD"), cancellable = true)
    private static void filterBlacklistedStructures(CommandSourceStack source,
                                                    ResourceOrTagKeyArgument.Result<Structure> result,
                                                    CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
        Registry<Structure> registry = source.getLevel().registryAccess().registry(Registries.STRUCTURE).orElse(null);
        if (registry == null) return;

        result.unwrap().ifLeft(resourceKey -> {
            ResourceLocation id = resourceKey.location();
            Structure structure = registry.get(resourceKey);
            if (structure != null && NoMoreTooManyStructuresConfig.isStructureBlacklisted(id, structure)) {
                source.sendFailure(Component.translatable("commands.locate.structure.not_found", resourceKey.location().toString()));
                cir.setReturnValue(0);
            }
        }).ifRight(tagKey -> {
            if (NoMoreTooManyStructuresConfig.isStructureTagBlacklisted(tagKey)) {
                source.sendFailure(Component.translatable("commands.locate.structure.not_found", "#" + tagKey.location()));
                cir.setReturnValue(0);
            }
        });
    }
}