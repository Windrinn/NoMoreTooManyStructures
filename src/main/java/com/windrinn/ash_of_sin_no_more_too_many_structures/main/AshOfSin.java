package com.windrinn.ash_of_sin_no_more_too_many_structures.main;

import com.windrinn.ash_of_sin_no_more_too_many_structures.config.NoMoreTooManyStructuresConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

// The value here should match an entry in the META-INF/mods.toml file
@Mod("ash_of_sin_no_more_too_many_structures")
public class AshOfSin {

    public static final String MODID = "ash_of_sin_no_more_too_many_structures";

    public AshOfSin() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(this);

        NoMoreTooManyStructuresConfig.register(ModLoadingContext.get(), FMLJavaModLoadingContext.get().getModEventBus());
    }

    public void setup(final FMLCommonSetupEvent event) {
        NoMoreTooManyStructuresConfig.reloadSets();
    }
}