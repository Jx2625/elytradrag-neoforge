package com.muyu.elytradrag.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class ElytraDragConfig {
    public static final ModConfigSpec SPEC;
    public static final ElytraDragConfig INSTANCE;

    // 全部配置项合并
    public final ModConfigSpec.DoubleValue dragMultiplier;
    public final ModConfigSpec.DoubleValue minimumSpeed;
    public final ModConfigSpec.DoubleValue maxFallDistance;

    static {
        Pair<ElytraDragConfig, ModConfigSpec> pair = new net.neoforged.neoforge.common.ModConfigSpec.Builder().configure(ElytraDragConfig::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }

    private ElytraDragConfig(ModConfigSpec.Builder builder) {

        dragMultiplier = builder
                .defineInRange("dragMultiplier", 2.0D, 0.1D, 20.0D);
        minimumSpeed = builder
                .defineInRange("minimumSpeed", 0.1D, 0.0D, 5.0D);
        maxFallDistance = builder
                .defineInRange("maxFallDistance", 15.0D, 0.0D, 100.0D);

    }
}