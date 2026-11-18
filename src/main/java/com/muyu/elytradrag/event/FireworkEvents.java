package com.muyu.elytradrag.event;

import com.muyu.elytradrag.ElytraDragNeoForge;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = ElytraDragNeoForge.MODID, value = Dist.CLIENT)
public class FireworkEvents {

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();

        // 滑翔中拿着烟花、且正处在刹车状态 → 禁止右键消耗烟花（避免方向不对白扔一枚）
        // 状态统一从 ClientEvents 读，不再单独判断按键，防止两套判断慢慢分叉
        if (player.isFallFlying()
                && event.getItemStack().is(Items.FIREWORK_ROCKET)
                && ClientEvents.isBraking()) {
            event.setCanceled(true);
        }
    }
}
