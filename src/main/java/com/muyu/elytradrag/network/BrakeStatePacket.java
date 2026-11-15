package com.muyu.elytradrag.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.muyu.elytradrag.ElytraDragNeoForge;

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