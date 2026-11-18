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
        // "1" 是 payload 协议版本号：只要 BrakeStatePacket 的字段或编码方式有改动，就必须 bump 到 "2"。
        // 否则新旧客户端混连时会按不同结构解析同一条包（NeoForge 只在版本一致时才允许握手通过）。
        var registrar = event.registrar("1");
        registrar.playToServer(
                BrakeStatePacket.TYPE,
                BrakeStatePacket.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    // 跑在服务端主线程：这里再校验一次状态合法性（见 ServerEvents.handleBrakeState）
                    var player = context.player();
                    ServerEvents.handleBrakeState(player, payload.isBraking());
                })
        );
    }
}