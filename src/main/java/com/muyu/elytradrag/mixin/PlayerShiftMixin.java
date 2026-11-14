package com.muyu.elytradrag.mixin;

import com.muyu.elytradrag.KeyBindings;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public class PlayerShiftMixin {

    @Inject(method = "stopFallFlying", at = @At("HEAD"), cancellable = true)
    private void elytradrag$preventShiftCancel(CallbackInfo ci) {
        Player player = (Player) (Object) this;

        if (player.level().isClientSide() && KeyBindings.BRAKE_KEY.isDown()) {
            ci.cancel();
        }
    }
}