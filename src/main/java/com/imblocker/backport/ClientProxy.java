package com.imblocker.backport;

import net.minecraft.client.Minecraft;

import com.imblocker.backport.event.ClientEventHandler;
import com.imblocker.backport.ime.ImeController;
import com.imblocker.backport.ime.ImeControllers;
import com.imblocker.backport.input.ImeStateManager;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.relauncher.Side;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        Config.load(event.getSuggestedConfigurationFile());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        if (FMLCommonHandler.instance()
            .getSide() != Side.CLIENT) {
            return;
        }

        ImeController controller = ImeControllers.create();
        if (!controller.isAvailable()) {
            IMBlocker.LOG.warn("No IME backend available on this platform, the mod will do nothing.");
            return;
        }
        IMBlocker.LOG.info("Using IME backend: {}", controller.describe());

        ImeStateManager.init(controller, Minecraft.getMinecraft());

        ClientEventHandler handler = new ClientEventHandler(controller);
        FMLCommonHandler.instance()
            .bus()
            .register(handler);
        handler.registerKeyBinding();

        // Make sure a detached IME never outlives the game window.
        Runtime.getRuntime()
            .addShutdownHook(new Thread(controller::restore, "IMBlocker-IME-restore"));
    }
}
