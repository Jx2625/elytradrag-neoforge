package com.muyu.elytradrag.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class ElytraDragConfig {
    public static final ModConfigSpec SPEC;
    public static final ElytraDragConfig INSTANCE;

    public final ModConfigSpec.DoubleValue dragMultiplier;
    public final ModConfigSpec.DoubleValue minimumSpeed;
    public final ModConfigSpec.DoubleValue maxFallDistance;
    public final ModConfigSpec.BooleanValue removeKineticDamage;

    /**
     * true（默认）：刹车只施加阻力，DaBR 保持原样运行 —— 不压制翻滚组、不强制回正、不锁视角。
     * false：回到旧行为，刹车期间压制 DaBR 的翻滚组并把视角回正。
     */
    public final ModConfigSpec.BooleanValue keepDaBREnabledWhileBraking;

    static {
        Pair<ElytraDragConfig, ModConfigSpec> pair = new net.neoforged.neoforge.common.ModConfigSpec.Builder().configure(ElytraDragConfig::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }

    private ElytraDragConfig(ModConfigSpec.Builder builder) {
        dragMultiplier = builder.defineInRange("dragMultiplier", 2.0D, 0.1D, 20.0D);
        minimumSpeed = builder.defineInRange("minimumSpeed", 2.0D, 0.0D, 100.0D);
        maxFallDistance = builder.defineInRange("maxFallDistance", 15.0D, 0.0D, 100.0D);
        removeKineticDamage = builder.define("removeKineticDamage", true);
        keepDaBREnabledWhileBraking = builder
                .comment("true: braking only applies drag, DoABarrelRoll keeps running and the view is left alone.",
                        "false: braking temporarily suppresses DoABarrelRoll's rolling group and levels the view (older behaviour).")
                .define("keepDaBREnabledWhileBraking", true);
    }
}
