package com.windrinn.ash_of_sin_no_more_too_many_structures.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;
import com.windrinn.ash_of_sin_no_more_too_many_structures.main.AshOfSin;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class NoMoreTooManyStructuresConfig {
    private static final Logger LOGGER = LogManager.getLogger();
    public static final ForgeConfigSpec NO_MORE_TOO_MANY_STRUCTURES_CONFIG;
    public static final ForgeConfigSpec.BooleanValue ONLY_OVERLAP;
    public static final ForgeConfigSpec.DoubleValue CHECK_RADIUS;
    public static final ForgeConfigSpec.IntValue MAX_NEARBY;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> WHITELIST_STRUCTURES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> WHITELIST_FEATURES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLACKLIST_STRUCTURES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLACKLIST_FEATURES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> IGNORE_STRUCTURES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> IGNORE_FEATURES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> AUTO_RESET_DIMENSIONS;
    public static final ForgeConfigSpec.BooleanValue DEBUG;

    private static final Set<ResourceLocation> WHITELIST_STRUCTURES_SET = new HashSet<>();
    private static final Set<ResourceLocation> WHITELIST_FEATURES_SET = new HashSet<>();
    private static final Set<ResourceLocation> BLACKLIST_STRUCTURES_SET = new HashSet<>();
    private static final Set<ResourceLocation> BLACKLIST_FEATURES_SET = new HashSet<>();
    private static final Set<ResourceLocation> IGNORE_STRUCTURES_SET = new HashSet<>();
    private static final Set<ResourceLocation> IGNORE_FEATURES_SET = new HashSet<>();
    public final Path configPath;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("No More Too Many Structures Settings");

        ONLY_OVERLAP = builder
                .define("only_overlap", false);

        CHECK_RADIUS = builder
                .comment("Radius (in blocks) to check for nearby structures/features. 0.0 ~ 1000.0")
                .defineInRange("checkRadius", 300.0, 0.0, 1000.0);

        MAX_NEARBY = builder
                .comment("Maximum number of structures/features allowed within the radius. 0 ~ 16")
                .defineInRange("maxNearby", 1, 0, 16);

        WHITELIST_STRUCTURES = builder
                .comment("Structures that will always generate regardless of overlap (registry names). Example: 'minecraft:ancient_city'")
                .defineList("whitelistStructures", Arrays.asList(
                        ),
                        it -> it instanceof String);

        WHITELIST_FEATURES = builder
                .comment("Features that will always generate regardless of overlap (registry names). Example: 'minecraft:tree'")
                .defineList("whitelistFeatures", Lists.newArrayList(), Predicates.alwaysTrue());

        BLACKLIST_STRUCTURES = builder
                .comment("Structures that will never generate (registry names). Example: 'minecraft:shipwreck'")
                .defineList("blacklistStructures", Arrays.asList(
                                "minecraft:shipwreck",
                                "minecraft:mineshaft"
                        ),
                        it -> it instanceof String);

        BLACKLIST_FEATURES = builder
                .comment("Features that will never generate (registry names). Example: 'minecraft:tree'")
                .defineList("blacklistFeatures", Lists.newArrayList(), Predicates.alwaysTrue());

        IGNORE_STRUCTURES = builder
                .comment("Structures that will be completely ignored (no checks, no recording). Example: 'minecraft:village'")
                .defineList("ignoreStructures", Lists.newArrayList(), Predicates.alwaysTrue());

        IGNORE_FEATURES = builder
                .comment("Features that will be completely ignored (no checks, no recording). Example: 'minecraft:seagrass'")
                .defineList("ignoreFeatures", Arrays.asList(
                        "minecraft:seagrass",
                        "minecraft:kelp",
                        "minecraft:random_selector",
                        "minecraft:random_patch",
                        "minecraft:ore",
                        "minecraft:simple_block",
                        "minecraft:flower",
                        "minecraft:vegetation_patch",
                        "minecraft:multiface_growth",
                        "minecraft:simple_random_selector",
                        "minecraft:random_boolean_selector",
                        "minecraft:block_column"
                ), Predicates.alwaysTrue());

        AUTO_RESET_DIMENSIONS = builder
                .comment("Dimensions whose No-More-Too-Many-Structures data will be cleared automatically when no players are present. " +
                        "Example: 'minecraft:the_end'")
                .defineList("autoResetDimensions", Arrays.asList(
                        "cataclysm_dimension:cataclysm_abyssal_depths",
                        "cataclysm_dimension:cataclysm_bastion_lost",
                        "cataclysm_dimension:cataclysm_eternal_frosthold",
                        "cataclysm_dimension:cataclysm_forge_of_aeons",
                        "cataclysm_dimension:cataclysm_infernos_maw",
                        "cataclysm_dimension:cataclysm_pharaohs_bane",
                        "cataclysm_dimension:cataclysm_sanctum_fallen",
                        "cataclysm_dimension:cataclysm_souls_anvil"
                ), obj -> obj instanceof String);

        DEBUG = builder
                .comment("Enable debug logging for skipped structures/features.")
                .define("debug", false);

        builder.pop();
        NO_MORE_TOO_MANY_STRUCTURES_CONFIG = builder.build();
    }

    public static void register(@NotNull ModLoadingContext context, @NotNull IEventBus modBus) {
        context.registerConfig(ModConfig.Type.COMMON, NO_MORE_TOO_MANY_STRUCTURES_CONFIG);
        modBus.addListener((ModConfigEvent.Reloading event) -> {
            if (event.getConfig().getModId().equals(AshOfSin.MODID)) {
                WHITELIST_STRUCTURES_SET.clear();
                WHITELIST_FEATURES_SET.clear();
                BLACKLIST_STRUCTURES_SET.clear();
                BLACKLIST_FEATURES_SET.clear();
                IGNORE_STRUCTURES_SET.clear();
                IGNORE_FEATURES_SET.clear();
            }
        });
    }

    public static boolean isStructureWhitelisted(ResourceLocation id) {
        if (WHITELIST_STRUCTURES_SET.isEmpty()) {
            List<? extends String> list = WHITELIST_STRUCTURES.get();
            for (String s : list) {
                ResourceLocation parsed = ResourceLocation.tryParse(s);
                if (parsed != null) {
                    WHITELIST_STRUCTURES_SET.add(parsed);
                } else {
                    LOGGER.warn("Invalid structure registry name in whitelistStructures: {}", s);
                }
            }
        }
        return WHITELIST_STRUCTURES_SET.contains(id);
    }

    public static boolean isFeatureWhitelisted(ResourceLocation id) {
        if (WHITELIST_FEATURES_SET.isEmpty()) {
            List<? extends String> list = WHITELIST_FEATURES.get();
            for (String s : list) {
                ResourceLocation parsed = ResourceLocation.tryParse(s);
                if (parsed != null) {
                    WHITELIST_FEATURES_SET.add(parsed);
                } else {
                    LOGGER.warn("Invalid feature registry name in whitelistFeatures: {}", s);
                }
            }
        }
        return WHITELIST_FEATURES_SET.contains(id);
    }

    // 判断是否在黑名单中（永不生成）
    public static boolean isStructureBlacklisted(ResourceLocation id) {
        if (BLACKLIST_STRUCTURES_SET.isEmpty()) {
            List<? extends String> list = BLACKLIST_STRUCTURES.get();
            for (String s : list) {
                ResourceLocation parsed = ResourceLocation.tryParse(s);
                if (parsed != null) {
                    BLACKLIST_STRUCTURES_SET.add(parsed);
                } else {
                    LOGGER.warn("Invalid structure registry name in blacklistStructures: {}", s);
                }
            }
        }
        return BLACKLIST_STRUCTURES_SET.contains(id);
    }

    public static boolean isFeatureBlacklisted(ResourceLocation id) {
        if (BLACKLIST_FEATURES_SET.isEmpty()) {
            List<? extends String> list = BLACKLIST_FEATURES.get();
            for (String s : list) {
                ResourceLocation parsed = ResourceLocation.tryParse(s);
                if (parsed != null) {
                    BLACKLIST_FEATURES_SET.add(parsed);
                } else {
                    LOGGER.warn("Invalid feature registry name in blacklistFeatures: {}", s);
                }
            }
        }
        return BLACKLIST_FEATURES_SET.contains(id);
    }

    public static boolean isStructureIgnored(ResourceLocation id) {
        if (IGNORE_STRUCTURES_SET.isEmpty()) {
            List<? extends String> list = IGNORE_STRUCTURES.get();
            for (String s : list) {
                ResourceLocation parsed = ResourceLocation.tryParse(s);
                if (parsed != null) {
                    IGNORE_STRUCTURES_SET.add(parsed);
                } else {
                    LOGGER.warn("Invalid structure registry name in ignoreStructures: {}", s);
                }
            }
        }
        return IGNORE_STRUCTURES_SET.contains(id);
    }

    public static boolean isFeatureIgnored(ResourceLocation id) {
        if (IGNORE_FEATURES_SET.isEmpty()) {
            List<? extends String> list = IGNORE_FEATURES.get();
            for (String s : list) {
                ResourceLocation parsed = ResourceLocation.tryParse(s);
                if (parsed != null) {
                    IGNORE_FEATURES_SET.add(parsed);
                } else {
                    LOGGER.warn("Invalid feature registry name in ignoreFeatures: {}", s);
                }
            }
        }
        return IGNORE_FEATURES_SET.contains(id);
    }

    public NoMoreTooManyStructuresConfig() {
        this.configPath = FMLPaths.CONFIGDIR.get().resolve("ash_of_sin/no_more_too_many_structures.toml");
        loadConfig();
    }

    public void loadConfig() {
        if (!Files.exists(configPath)) {
            try {
                Files.createDirectories(configPath.getParent());
            } catch (IOException e) {
                throw new RuntimeException("Failed to create default no more too many structures config.", e);
            }
        }
        final CommentedFileConfig fileConfig = CommentedFileConfig.builder(configPath)
                .sync()
                .autosave()
                .preserveInsertionOrder()
                .writingMode(WritingMode.REPLACE)
                .build();
        fileConfig.load();
        NO_MORE_TOO_MANY_STRUCTURES_CONFIG.setConfig(fileConfig);
    }
}