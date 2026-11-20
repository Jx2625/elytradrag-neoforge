package com.muyu.elytradrag.compat;

import com.muyu.elytradrag.ElytraDragNeoForge;
import net.minecraft.world.entity.player.Player;
import nl.enjarai.doabarrelroll.DoABarrelRollClient;
import nl.enjarai.doabarrelroll.ModEventsClient;
import nl.enjarai.doabarrelroll.ModKeybindings;
import nl.enjarai.doabarrelroll.api.RollEntity;

import java.lang.reflect.Field;

/**
 * 刹车期间与 DoABarrelRoll 交互的兼容层。
 *
 * <p>两种模式：
 * <ul>
 *   <li>旧模式（{@code keepDaBREnabledWhileBraking=false}）：向 DaBR 的 {@code fall_flying}
 *       滚动条件组注入一条 {@code falseUnless} 条件，刹车时让 {@code RollEvents.shouldRoll()}
 *       返回 false。效果等价于玩家自己把 DaBR 关掉，但<b>完全不动 ModConfig 的持久化配置</b>
 *       —— 旧实现 {@code setModEnabled(false)} 会被 DaBR 的 save() 写进客户端配置，
 *       刹车期间崩溃就可能把 mod_enabled=false 留下来。</li>
 *   <li>新模式（默认）：DaBR 照常运行，这里只负责在刹车开始/结束、以及姿态键松开的那一帧
 *       清掉镜头平滑残留，避免"松手还转一段"。</li>
 * </ul>
 *
 * <p><b>不再处理按键状态。</b>早期版本需要在客户端每 tick 把 DaBR 的飞行键对齐物理输入，
 * 并在刹车期间临时替换它们的 conflict context，用来绕过 NeoForge 的两条"丢事件"路径
 * （输入 context 失效时丢鼠标释放；按住修饰键时丢按下与释放）。这两条已在 DoABarrelRoll
 * 3.8.2 从根上修掉（重激活时同步鼠标键；飞行 context 与 {@code IN_GAME} 视为重叠），
 * 因此本类不再触碰 KeyMapping，最低依赖也提到 3.8.2。
 *
 * <p>依赖策略：硬依赖（见 neoforge.mods.toml，{@code do_a_barrel_roll >= 3.8.2}）。
 * 公开 API 一律直接引用；只剩两处只能反射，且都做了"失败只告警、不影响刹车本身"的降级：
 * <ul>
 *   <li>{@code Player.prevRoll} —— DaBR 的 mixin 注入到 Player 的字段，没有访问器；</li>
 *   <li>{@code ModEventsClient.rollBack / lastRollBack} —— private static 字段，没有访问器。</li>
 * </ul>
 * 本类只在客户端被加载（引用方都标了 {@code Dist.CLIENT}），专服不会触碰。
 */
public final class DaBRRollFreeze {

    /** 条件优先级必须低于 DaBR 自己的 trueIf(fall_flying, 0)，先求值才能短路成 false。 */
    private static final int CONDITION_PRIORITY = -1000;

    /** 姿态键分组：松开哪一组，就清哪一轴的镜头平滑残留。 */
    private static final int MASK_PITCH = 1;
    private static final int MASK_YAW = 2;
    private static final int MASK_ROLL = 4;
    /** 上一 tick 的姿态键按下状态（仅新模式刹车期间使用）。 */
    private static int prevAttitudeMask = 0;

    private static boolean suppressRoll = false;
    private static boolean conditionRegistered = false;

    private static Field prevRollField;
    private static boolean prevRollResolved = false;

    private static Field rollBackField;
    private static Field lastRollBackField;
    private static boolean rollBackResolved = false;

    private DaBRRollFreeze() {}

    /** 进入刹车：立刻停掉翻滚，并把己方 roll 归零。 */
    public static void beginBrake(Player player) {
        registerCondition();
        suppressRoll = true;
        resetRoll(player);
    }

    /** 退出刹车：恢复 DaBR 的正常行为，并清掉平滑器/回滚残留，避免松手时视角被甩一下。 */
    public static void endBrake(Player player) {
        suppressRoll = false;
        resetRoll(player);
        clearSmoothers();
        clearRollBack();
    }

    /** 退出世界/客户端停止/无玩家可用时：复位内存状态（没有任何持久化副作用）。 */
    public static void shutdown() {
        suppressRoll = false;
        clearSmoothers();
        clearRollBack();
    }

    /**
     * 新模式：刹车开始。丢掉刹车前累积的镜头平滑残留，并记录当前姿态键状态。
     * 只动平滑器的累积量，不改变当前姿态。
     */
    public static void beginDragOnlyBrake() {
        clearSmoothers();
        prevAttitudeMask = attitudeMask();
    }

    /**
     * 新模式：刹车每 tick。任一姿态键松开时，清掉该轴剩余的平滑量 —— 松手即刻停住，
     * 不会因为 DaBR 的镜头平滑继续转一段。
     *
     * <p>只在"松开"这一帧清，不在按住期间清：{@code SmoothDouble} 的输出是按累积缺口逐帧逼近的，
     * 每 tick 清零等于每 tick 重新起算，反而会把按住时的输入吞掉。
     */
    public static void tickDragOnlyBrake() {
        int mask = attitudeMask();
        int released = prevAttitudeMask & ~mask;
        if (released != 0) {
            if ((released & MASK_PITCH) != 0) {
                DoABarrelRollClient.PITCH_SMOOTHER.reset();
            }
            if ((released & MASK_YAW) != 0) {
                DoABarrelRollClient.YAW_SMOOTHER.reset();
            }
            if ((released & MASK_ROLL) != 0) {
                DoABarrelRollClient.ROLL_SMOOTHER.reset();
            }
        }
        prevAttitudeMask = mask;
    }

    /** 新模式：刹车结束。清掉刹车期间残留的平滑量。 */
    public static void endDragOnlyBrake() {
        clearSmoothers();
        prevAttitudeMask = 0;
    }

    /**
     * 往 DaBR 的滚动条件组里挂一条"刹车时禁止翻滚"的条件。
     * 只注册一次；条件本身通过静态字段实时读取，所以不需要反复注册/反注册。
     */
    private static void registerCondition() {
        if (conditionRegistered) {
            return;
        }
        conditionRegistered = true;
        try {
            DoABarrelRollClient.FALL_FLYING_GROUP.falseUnless(() -> !suppressRoll, CONDITION_PRIORITY);
        } catch (Throwable t) {
            ElytraDragNeoForge.LOGGER.warn(
                    "[ElytraDrag] 注册 DaBR 翻滚抑制条件失败，刹车期间将无法暂时禁用翻滚", t);
        }
    }

    private static void resetRoll(Player player) {
        if (player instanceof RollEntity rollEntity) {
            try {
                rollEntity.doABarrelRoll$setRolling(false);
                rollEntity.doABarrelRoll$setRoll(0.0f);
            } catch (Throwable t) {
                ElytraDragNeoForge.LOGGER.debug("[ElytraDrag] 调用 DaBR 翻滚接口失败: {}", t.toString());
            }
        }
        // 关键：清 PlayerMixin 的 prevRoll（上次转动残留），否则松手瞬间视线会跳一下
        clearPrevRoll(player);
    }

    private static void clearPrevRoll(Player player) {
        if (!prevRollResolved) {
            prevRollResolved = true;
            try {
                Field field = Player.class.getDeclaredField("prevRoll");
                field.setAccessible(true);
                prevRollField = field;
            } catch (Throwable t) {
                ElytraDragNeoForge.LOGGER.warn(
                        "[ElytraDrag] 找不到 DaBR 注入的 Player.prevRoll 字段，刹车回正可能不完整: {}",
                        t.toString());
            }
        }
        if (prevRollField == null) {
            return;
        }
        try {
            prevRollField.setFloat(player, 0.0f);
        } catch (Throwable t) {
            ElytraDragNeoForge.LOGGER.debug("[ElytraDrag] 重置 prevRoll 失败: {}", t.toString());
        }
    }

    private static void clearSmoothers() {
        try {
            DoABarrelRollClient.PITCH_SMOOTHER.reset();
            DoABarrelRollClient.YAW_SMOOTHER.reset();
            DoABarrelRollClient.ROLL_SMOOTHER.reset();
        } catch (Throwable t) {
            ElytraDragNeoForge.LOGGER.debug("[ElytraDrag] 重置 DaBR 平滑器失败: {}", t.toString());
        }
    }

    /** 当前姿态键的按下情况，按轴聚合成一个位掩码。 */
    private static int attitudeMask() {
        int mask = 0;
        if (ModKeybindings.PITCH_UP.isDown() || ModKeybindings.PITCH_DOWN.isDown()) {
            mask |= MASK_PITCH;
        }
        if (ModKeybindings.YAW_LEFT.isDown() || ModKeybindings.YAW_RIGHT.isDown()) {
            mask |= MASK_YAW;
        }
        if (ModKeybindings.ROLL_LEFT.isDown() || ModKeybindings.ROLL_RIGHT.isDown()) {
            mask |= MASK_ROLL;
        }
        return mask;
    }

    private static void clearRollBack() {
        if (!rollBackResolved) {
            rollBackResolved = true;
            try {
                // ModEventsClient 是硬依赖，直接取类对象；两个字段仍是 private static，只能反射
                Field rollBack = ModEventsClient.class.getDeclaredField("rollBack");
                Field lastRollBack = ModEventsClient.class.getDeclaredField("lastRollBack");
                rollBack.setAccessible(true);
                lastRollBack.setAccessible(true);
                rollBackField = rollBack;
                lastRollBackField = lastRollBack;
            } catch (Throwable t) {
                ElytraDragNeoForge.LOGGER.warn(
                        "[ElytraDrag] 找不到 DaBR 的 rollBack 字段，刹车结束时视角可能残留一点滚转: {}",
                        t.toString());
            }
        }
        if (rollBackField == null || lastRollBackField == null) {
            return;
        }
        try {
            rollBackField.setFloat(null, 0.0f);
            lastRollBackField.setFloat(null, 0.0f);
        } catch (Throwable t) {
            ElytraDragNeoForge.LOGGER.debug("[ElytraDrag] 重置 DaBR rollBack 失败: {}", t.toString());
        }
    }
}
