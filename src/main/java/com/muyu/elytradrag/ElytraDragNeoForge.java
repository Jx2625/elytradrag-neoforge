package com.muyu.elytradrag;

import com.muyu.elytradrag.config.ElytraDragConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import org.slf4j.Logger; // 加上这个导入
import org.slf4j.LoggerFactory; // 加上这个导入

@Mod(ElytraDragNeoForge.MODID)
public class ElytraDragNeoForge {
    public static final String MODID = "elytradrag";

    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public ElytraDragNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, ElytraDragConfig.SPEC);
        modEventBus.addListener(this::registerKeys);
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.BRAKE_KEY);
    }
}