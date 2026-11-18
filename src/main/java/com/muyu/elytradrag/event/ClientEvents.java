package com.muyu.elytradrag.event;

import com.muyu.elytradrag.ElytraDragNeoForge;
import com.muyu.elytradrag.KeyBindings;
import com.muyu.elytradrag.compat.DaBRRollFreeze;
import com.muyu.elytradrag.config.ElytraDragConfig;
import com.muyu.elytradrag.network.BrakeStatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = ElytraDragNeoForge.MODID, value = Dist.CLIENT)
public class ClientEvents {

    /**
     * 落地后继续维持刹车状态的 tick 数。
     * 摔落伤害是服务端在玩家落地那一个 tick 里算的，客户端的 false 包如果和落地同 tick 发出，
     * 会被服务端先处理掉，摔落距离截断就失效了。多维持几 tick 保证 LivingFallEvent 还能看到标记。
     */
    private static final int LAND_GRACE_TICKS = 3;

    /** 当前是否处于刹车状态（同时代表已上报给服务端的状态）。 */
    private static boolean braking = false;
    /** 本次腾空期间是否真的滑翔过 —— 站着按 Shift 绝不能触发刹车。 */
    private static boolean glideLatch = false;
    /** 连续贴地 tick 数，用于滑翔结束后保留一小段刹车保护。 */
    private static int groundedTicks = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        // 退世界 / 暂停 / 开界面（背包、聊天等）：必须补发 false，否则服务端 BRAKING_PLAYERS 会残留。
        // 界面里 Shift 仍会被 isDown() 读成按下（例如背包里 Shift 连点），所以这里直接复位。
        if (player == null || mc.level == null || mc.isPaused() || mc.screen != null) {
            stopBraking(mc, player);
            return;
        }

        updateGlideLatch(player);

        // 每 tick 把 DaBR 的飞行按键对齐物理输入：刹车会短暂关掉它的输入 context，
        // 若不补同步，绑在鼠标上的翻滚键会在松开后一直"按住"（视角自转）。
        DaBRRollFreeze.syncMovementKeys();

        // 只有"真的在滑翔（或刚结束滑翔还在空中/刚落地的几个 tick）"才允许刹车。
        boolean wantBrake = KeyBindings.BRAKE_KEY.isDown() && glideLatch;
        setBraking(mc, player, wantBrake);

        if (braking && player.isFallFlying() && !player.onGround()) {
            applyBrake(player);
            player.yRotO = player.getYRot();
            player.xRotO = player.getXRot();
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // 兜底：断开连接/退出世界时把状态全部归零，不给 DaBR 留任何残留
        braking = false;
        glideLatch = false;
        groundedTicks = 0;
        DaBRRollFreeze.shutdown();
    }

    /**
     * 当前是否处于刹车状态（含"刚结束滑翔、仍受落地保护"的窗口）。
     * 同端的其它事件（如烟花拦截）统一读这里，避免各自再写一份按键判断而慢慢分叉。
     */
    public static boolean isBraking() {
        return braking;
    }

    private static void updateGlideLatch(Player player) {
        if (player.isFallFlying()) {
            glideLatch = true;
            groundedTicks = 0;
        } else if (player.onGround()) {
            if (++groundedTicks > LAND_GRACE_TICKS) {
                glideLatch = false;
            }
        } else {
            // 空中但已停止滑翔（例如中途收翅）：保留 latch，让刹车+摔落保护继续生效
            groundedTicks = 0;
        }
    }

    private static void setBraking(Minecraft mc, Player player, boolean want) {
        if (want == braking) {
            return;
        }
        braking = want;
        sendBrakeState(mc, want);
        if (want) {
            DaBRRollFreeze.beginBrake(player);
        } else {
            DaBRRollFreeze.endBrake(player);
        }
    }

    /** 把本地状态和上报状态一起清掉；会补发 false 包，避免服务端残留。 */
    private static void stopBraking(Minecraft mc, Player player) {
        glideLatch = false;
        groundedTicks = 0;
        if (!braking) {
            return;
        }
        braking = false;
        sendBrakeState(mc, false);
        if (player != null) {
            DaBRRollFreeze.endBrake(player);
        } else {
            // 玩家对象短暂不存在（换维度/重生）：没有实体可清 roll，但必须解除翻滚抑制，
            // 否则 suppressRoll 会一直挂在那里，之后滑翔时 DaBR 永远不翻滚。
            DaBRRollFreeze.shutdown();
        }
    }

    private static void sendBrakeState(Minecraft mc, boolean isBraking) {
        // 还没进世界/正在重生的窗口里没有可用的连接上下文，不用发（服务端会按登出/重生兜底清理）
        if (mc.player == null || mc.getConnection() == null) {
            return;
        }
        PacketDistributor.sendToServer(new BrakeStatePacket(isBraking));
    }

    private static void applyBrake(Player player) {
        Vec3 velocity = player.getDeltaMovement();
        double speed = velocity.length() * 20.0;    // 格/秒，与 HUD 显示一致

        double minSpeed = ElytraDragConfig.INSTANCE.minimumSpeed.get();
        double multiplier = ElytraDragConfig.INSTANCE.dragMultiplier.get();

        if (speed > minSpeed) {
            double dragFactor = 1.0D - (0.05D * multiplier);
            dragFactor = Math.max(0.01D, dragFactor);
            player.setDeltaMovement(velocity.scale(dragFactor));
            player.hurtMarked = true;
        }
        // speed <= minSpeed 时什么都不做，保留最小速度，避免悬停抽搐
    }
}
