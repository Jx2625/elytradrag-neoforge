package com.muyu.elytradrag.event;

import com.muyu.elytradrag.ElytraDragNeoForge;
import com.muyu.elytradrag.KeyBindings;
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

        // 如果玩家在滑翔，且手里拿的是烟花火箭
        if (player.isFallFlying() && event.getItemStack().is(Items.FIREWORK_ROCKET)) {
            // 如果按下了刹车键
            if (KeyBindings.BRAKE_KEY.isDown()) {
                event.setCanceled(true);
            }
        }
    }
}