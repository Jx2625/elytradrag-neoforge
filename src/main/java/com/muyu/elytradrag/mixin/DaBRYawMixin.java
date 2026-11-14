package com.muyu.elytradrag.mixin;

import com.muyu.elytradrag.KeyBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.util.SmoothDouble;
import nl.enjarai.doabarrelroll.api.event.RollContext;
import nl.enjarai.doabarrelroll.config.Sensitivity;
import nl.enjarai.doabarrelroll.flight.RotationModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RotationModifiers.class)
public class DaBRYawMixin {

    // 1. 拦截 A/D 输入
    @Inject(method = "buttonControls", at = @At("HEAD"), cancellable = true)
    private static void elytradrag$disableButtonControls(double power, CallbackInfoReturnable<RollContext.ConfiguresRotation> cir) {
        if (Minecraft.getInstance().player != null && KeyBindings.BRAKE_KEY.isDown()) {
            cir.setReturnValue((rotationInstant, context) -> rotationInstant);
        }
    }

    // 2. 终极杀招：拦截平滑器！只要按刹车，就不让平滑器产生任何余留的角速度
    @Inject(method = "smoothing", at = @At("HEAD"), cancellable = true)
    private static void elytradrag$disableSmoothing(SmoothDouble pitchSmoother, SmoothDouble yawSmoother, SmoothDouble rollSmoother, Sensitivity smoothness, CallbackInfoReturnable<RollContext.ConfiguresRotation> cir) {
        if (Minecraft.getInstance().player != null && KeyBindings.BRAKE_KEY.isDown()) {
            // 如果按下了刹车，直接返回原值，不让平滑器累加数据
            cir.setReturnValue((rotationInstant, context) -> rotationInstant);
        }
    }
}