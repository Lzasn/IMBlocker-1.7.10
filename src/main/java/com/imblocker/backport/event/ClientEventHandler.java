package com.imblocker.backport.event;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import com.imblocker.backport.Config;
import com.imblocker.backport.ime.ImeController;
import com.imblocker.backport.input.ImeStateManager;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class ClientEventHandler {

    private final ImeController controller;
    private KeyBinding toggleKey;
    private boolean hotkeyWasDown;

    public ClientEventHandler(ImeController controller) {
        this.controller = controller;
    }

    public void registerKeyBinding() {
        if (Config.toggleKeyCode <= 0) {
            return;
        }
        toggleKey = new KeyBinding("key.imblocker.toggle", Config.toggleKeyCode, "key.categories.imblocker");
        ClientRegistry.registerKeyBinding(toggleKey);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ImeStateManager.clientTick();
        handleHotkey();
    }

    /**
     * Edge detection on the raw LWJGL keyboard state instead of {@code KeyBinding.isPressed()}: the latter is not
     * delivered while a GUI is open, and that is exactly where people want to hit the toggle.
     */
    private void handleHotkey() {
        if (toggleKey == null || Config.toggleKeyCode <= 0) {
            return;
        }
        int code = toggleKey.getKeyCode();
        if (code <= 0) {
            return;
        }
        boolean down;
        try {
            down = Keyboard.isKeyDown(code);
        } catch (Throwable t) {
            return;
        }
        if (down && !hotkeyWasDown) {
            ImeStateManager.toggle();
        }
        hotkeyWasDown = down;
    }
}
