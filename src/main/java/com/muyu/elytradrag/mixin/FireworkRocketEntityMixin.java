package com.muyu.elytradrag.mixin;

import com.muyu.elytradrag.KeyBindings;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireworkRocketEntity.class)
public class FireworkRocketEntityMixin {

    @Shadow private LivingEntity attachedToEntity;

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isFallFlying()Z"), cancellable = true)
    private void elytradrag$stopBoost(CallbackInfo ci) {
        if (attachedToEntity instanceof Player player) {
            if (player.level().isClientSide() && player.isFallFlying() && KeyBindings.BRAKE_KEY.isDown()) {
                ((FireworkRocketEntity) (Object) this).discard();
                ci.cancel();
            }
        }
    }
}