package com.muyu.elytradrag;

import com.muyu.elytradrag.config.ElytraDragConfig;
import com.muyu.elytradrag.event.ServerEvents;
import com.muyu.elytradrag.network.BrakeStatePacket;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ElytraDragNeoForge.MODID)
public class ElytraDragNeoForge {
    public static final String MODID = "elytradrag";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public ElytraDragNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, ElytraDragConfig.SPEC);
        modEventBus.addListener(this::registerKeys);
        modEventBus.addListener(this::registerPayloads);
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.BRAKE_KEY);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(
                BrakeStatePacket.TYPE,
                BrakeStatePacket.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    var player = context.player();
                    ServerEvents.handleBrakeState(player, payload.isBraking());
                })
        );
    }
}