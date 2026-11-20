package com.imblocker.backport.event;

import com.imblocker.backport.input.ImeStateManager;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Only job left: drive the state machine once per client tick. Focus changes arrive through the mixin, so nothing else
 * needs to be hooked here.
 */
public class ClientEventHandler {

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ImeStateManager.clientTick();
    }
}
