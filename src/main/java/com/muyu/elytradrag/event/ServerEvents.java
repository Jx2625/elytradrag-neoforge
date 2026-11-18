package com.muyu.elytradrag.event;

import com.muyu.elytradrag.ElytraDragNeoForge;
import com.muyu.elytradrag.config.ElytraDragConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = ElytraDragNeoForge.MODID)
public class ServerEvents {

    /**
     * "刹车中"的玩家 → 最后一次确认处于空中的服务端 tick 号。
     *
     * <p>为什么不是干净的 Set：刹车状态由客户端驱动，客户端崩溃/卡死/强杀时不会补发 false。
     * 只靠登出清理，重连后同 UUID 会带着旧标记，结果是永久 15 格摔落伤害 + 永久免疫撞墙动能伤害
     * （联机服上是可被利用的漏洞）。所以服务端自己记时间戳：只要玩家还在空中就刷新，
     * 落地后 {@link #BRAKE_TTL_TICKS} 内没有再次确认就自动过期。
     */
    private static final Map<UUID, Long> BRAKING_PLAYERS = new HashMap<>();

    /**
     * 落地后的宽限 tick 数。要大于客户端 {@code ClientEvents.LAND_GRACE_TICKS}（3）+ 一个网络往返，
     * 覆盖"刹车中落地"那一 tick 的 LivingFallEvent 时序；又足够小，不会让掉线残留活太久。
     */
    private static final long BRAKE_TTL_TICKS = 40L;

    /** 服务端 tick 计数，用作 {@link #BRAKING_PLAYERS} 的时间戳。 */
    private static long serverTick = 0L;

    public static void handleBrakeState(Player player, boolean isBraking) {
        if (player == null) {
            return;
        }
        // 兜底校验：客户端异常/包乱序时，只认"在空中（正在滑翔）"的刹车上报。
        // 站着按 Shift 不再能把 UUID 塞进来，也不会顺带截断落地摔落伤害。
        boolean accepted = isBraking && (player.isFallFlying() || !player.onGround());
        if (accepted) {
            BRAKING_PLAYERS.put(player.getUUID(), serverTick);
        } else {
            BRAKING_PLAYERS.remove(player.getUUID());
        }
    }

    /**
     * 每 tick 维护刹车标记的有效性：空中的玩家刷新时间戳，落地太久或已下线的直接清掉。
     * 这样即使客户端从此不再发包，标记也不会永久残留。
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        serverTick++;
        if (BRAKING_PLAYERS.isEmpty()) {
            return;
        }

        MinecraftServer server = event.getServer();
        Iterator<Map.Entry<UUID, Long>> iterator = BRAKING_PLAYERS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            Player player = server.getPlayerList().getPlayer(entry.getKey());

            if (player == null) {
                // 玩家已经不在这台服务器上
                iterator.remove();
                continue;
            }
            if (player.isFallFlying() || !player.onGround()) {
                // 还在空中：刹车状态有效，顺带续期（覆盖"收翅后继续下坠"的保护）
                entry.setValue(serverTick);
                continue;
            }
            if (serverTick - entry.getValue() > BRAKE_TTL_TICKS) {
                iterator.remove();
            }
        }
    }

    // 玩家断开/重生时立刻清掉标记，不用等 TTL（也避免同 UUID 重连/重生继承旧状态）
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        BRAKING_PLAYERS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        BRAKING_PLAYERS.remove(event.getEntity().getUUID());
    }

    // 1. 拦截摔落伤害，并截断到配置的距离
    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && isBraking(player)) {
            float maxFall = (float) (double) ElytraDragConfig.INSTANCE.maxFallDistance.get();

            if (event.getDistance() > maxFall) {
                event.setDistance(maxFall);
            }
        }
    }

    // 2. 拦截高速撞击方块的动能伤害
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player player && isBraking(player)) {
            // 配置关闭 → 放行原版动能伤害
            if (!ElytraDragConfig.INSTANCE.removeKineticDamage.get()) {
                return;
            }
            if (event.getSource().is(DamageTypes.FLY_INTO_WALL)) {
                event.setCanceled(true);
            }
        }
    }

    /** 该玩家当前是否处于刹车状态（已过期/已下线的条目不会命中）。 */
    private static boolean isBraking(Player player) {
        return BRAKING_PLAYERS.containsKey(player.getUUID());
    }
}
