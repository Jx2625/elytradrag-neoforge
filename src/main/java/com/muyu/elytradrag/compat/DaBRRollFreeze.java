package com.muyu.elytradrag.compat;

import com.mojang.blaze3d.platform.InputConstants;
import com.muyu.elytradrag.ElytraDragNeoForge;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import nl.enjarai.doabarrelroll.DoABarrelRollClient;
import nl.enjarai.doabarrelroll.ModEventsClient;
import nl.enjarai.doabarrelroll.ModKeybindings;
import nl.enjarai.doabarrelroll.api.RollEntity;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.List;

/**
 * 刹车期间"临时不翻滚"的 DaBR 兼容层。
 *
 * <p>实现方式：向 DaBR 的 {@code fall_flying} 滚动条件组注入一条 {@code falseUnless} 条件，
 * 刹车时让 {@code RollEvents.shouldRoll()} 返回 false。效果等价于玩家自己把 DaBR 关掉
 * （{@code LocalPlayer} 每 tick 用 shouldRoll() 刷新 rolling 状态），但<b>完全不动
 * ModConfig 的持久化配置</b> —— 旧实现 {@code setModEnabled(false)} 会被 DaBR 的
 * {@code save()} 写进 {@code config/do_a_barrel_roll-client.json}，刹车期间崩溃/强杀
 * 就可能把 mod_enabled=false 留下来。
 *
 * <p><b>翻滚键卡住的问题</b>：让滚动条件组变 false 等于把 DaBR 的输入 context 关掉，
 * 而 NeoForge 在 context 不活跃时会丢弃这个按键事件流（连"松开"都丢，见
 * {@code KeyMappingLookup#getAll} 的 {@code isActiveAndMatches} 过滤）。DaBR 自己在
 * context 重新激活时会补同步（{@code InputContextImpl#syncHeldKeys}），但<b>只补键盘，
 * 刻意跳过鼠标键</b>。所以"按住绑在鼠标上的翻滚键 → 刹车 → 松开鼠标"会让该键永远停在
 * 按下状态，刹车结束后视角就会绕视轴自转不停。{@link #syncMovementKeys()} 每 tick 用
 * GLFW 的物理状态覆盖 DaBR 的飞行按键（键盘 + 鼠标），把这个洞补上。
 *
 * <p>依赖策略：这是<b>硬依赖</b>（见 neoforge.mods.toml，{@code do_a_barrel_roll >= 3.7.5}）。
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

    /**
     * 要跟物理输入对齐的 DaBR 飞行按键。
     * 翻滚键默认未绑定，玩家常见做法是绑到鼠标左右键 —— 正是会卡住的那类。
     */
    private static final List<KeyMapping> MOVEMENT_KEYS = List.of(
            ModKeybindings.PITCH_UP,
            ModKeybindings.PITCH_DOWN,
            ModKeybindings.YAW_LEFT,
            ModKeybindings.YAW_RIGHT,
            ModKeybindings.ROLL_LEFT,
            ModKeybindings.ROLL_RIGHT,
            ModKeybindings.THRUST_FORWARD,
            ModKeybindings.THRUST_BACKWARD
    );

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
     * 把 DaBR 的飞行按键 raw 状态强制对齐到物理输入（键盘 + 鼠标）。
     *
     * <p>为什么需要：我们的刹车会让 DaBR 的 {@code fall_flying} 输入 context 失效，而 NeoForge
     * 在 context 失效期间丢弃该按键事件流（含释放）。DaBR 只在 context 重新激活时补同步一次，
     * 且跳过鼠标键，于是鼠标绑定的翻滚键会永久停在按下，刹车结束后视角自转。这里每 tick 覆盖
     * 一遍，顺带也修掉 DaBR 自己的"落地时按着翻滚鼠标键"同款残留。
     *
     * <p>只写 {@link KeyMapping#setDown(boolean)}，不碰 click 计数，也不影响其它任何按键。
     */
    public static void syncMovementKeys() {
        Minecraft mc = Minecraft.getInstance();
        // 界面里鼠标是在操作 GUI，不代表飞行输入，跳过；回到游戏后的第一个 tick 会补上
        if (mc.player == null || mc.screen != null) {
            return;
        }

        long window = mc.getWindow().getWindow();
        for (KeyMapping mapping : MOVEMENT_KEYS) {
            InputConstants.Key key = mapping.getKey();
            if (key.getType() == InputConstants.Type.MOUSE) {
                // 鼠标键不能用 InputConstants.isKeyDown（那是 glfwGetKey，只对键盘有效）
                mapping.setDown(GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS);
            } else if (key.getType() == InputConstants.Type.KEYSYM
                    && key.getValue() != InputConstants.UNKNOWN.getValue()) {
                mapping.setDown(InputConstants.isKeyDown(window, key.getValue()));
            }
            // 未绑定的键保持原样（没人会读它）
        }
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
