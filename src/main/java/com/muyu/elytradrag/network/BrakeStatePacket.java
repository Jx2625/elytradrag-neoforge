package com.muyu.elytradrag.network;

import com.muyu.elytradrag.ElytraDragNeoForge;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 → 服务端：玩家是否进入/退出"刹车中"。
 *
 * <p>边沿触发（状态变化时才发），不是每 tick 心跳；服务端侧自己维护有效期，
 * 所以这里只传一个 boolean 就够。协议版本见 {@code ElytraDragNeoForge#registerPayloads}。
 */
public record BrakeStatePacket(boolean isBraking) implements CustomPacketPayload {
    public static final Type<BrakeStatePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ElytraDragNeoForge.MODID, "brake_state"));
    public static final StreamCodec<ByteBuf, BrakeStatePacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, BrakeStatePacket::isBraking,
            BrakeStatePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
