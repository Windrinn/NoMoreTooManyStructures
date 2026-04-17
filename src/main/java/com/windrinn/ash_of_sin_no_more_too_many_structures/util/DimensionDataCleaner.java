package com.windrinn.ash_of_sin_no_more_too_many_structures.util;

import com.windrinn.ash_of_sin_no_more_too_many_structures.config.NoMoreTooManyStructuresConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber
public class DimensionDataCleaner {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<ResourceKey<Level>, Integer> PLAYER_COUNTS = new ConcurrentHashMap<>();
    private static final Set<ResourceKey<Level>> AUTO_RESET_DIMENSIONS = ConcurrentHashMap.newKeySet();
    private static MinecraftServer server;

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        loadAutoResetDimensions();

        for (ResourceKey<Level> dimKey : AUTO_RESET_DIMENSIONS) {
            resetDimensionData(dimKey, "server start");
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            incrementCount(player.serverLevel().dimension());
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ResourceKey<Level> fromDim = event.getFrom();
        ResourceKey<Level> toDim = event.getTo();

        decrementCount(fromDim);
        incrementCount(toDim);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        decrementCount(player.serverLevel().dimension());
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        incrementCount(player.serverLevel().dimension());
    }

    private static void incrementCount(ResourceKey<Level> dim) {
        PLAYER_COUNTS.merge(dim, 1, Integer::sum);
    }

    private static void decrementCount(ResourceKey<Level> dim) {
        int newCount = PLAYER_COUNTS.compute(dim, (k, v) -> v == null ? 0 : v - 1);
        if (newCount <= 0) {
            PLAYER_COUNTS.remove(dim);
            if (AUTO_RESET_DIMENSIONS.contains(dim) && server != null) {
                resetDimensionData(dim, "no players present");
            }
        }
    }

    private static void loadAutoResetDimensions() {
        AUTO_RESET_DIMENSIONS.clear();
        for (String dimStr : NoMoreTooManyStructuresConfig.AUTO_RESET_DIMENSIONS.get()) {
            ResourceLocation rl = ResourceLocation.tryParse(dimStr);
            if (rl != null) {
                AUTO_RESET_DIMENSIONS.add(ResourceKey.create(Registries.DIMENSION, rl));
            } else {
                LOGGER.warn("Invalid dimension ID in autoResetDimensions: {}", dimStr);
            }
        }
    }

    private static void resetDimensionData(ResourceKey<Level> dimKey, String reason) {
        ServerLevel level = server.getLevel(dimKey);
        if (level == null) {
            if (NoMoreTooManyStructuresConfig.DEBUG.get()) {
                LOGGER.debug("Dimension {} not loaded, skipping clear on {}", dimKey.location(), reason);
            }
            return;
        }

        NoMoreTooManyStructuresSavedData data = level.getDataStorage()
                .get(NoMoreTooManyStructuresSavedData::load, "no_more_too_many_structures_data");
        if (data != null) {
            data.clearAll();
            if (NoMoreTooManyStructuresConfig.DEBUG.get()) {
                LOGGER.debug("Clean data for dimension {} (no players present)", dimKey.location());
            }
        }
    }
}