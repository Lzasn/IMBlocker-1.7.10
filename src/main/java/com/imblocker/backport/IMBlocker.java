package com.imblocker.backport;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/**
 * IMBlocker for Minecraft 1.7.10 / Forge (GTNH).
 *
 * The mod keeps the Windows IME (input method editor) detached while the player is *not* typing, so that keys such as
 * W/A/S/D, E or T reach the game instead of being swallowed by a Chinese/Japanese/Korean IME. As soon as a text field
 * gains focus the IME is attached again and CJK input works as usual.
 *
 * Client side only: nothing is registered on a dedicated server.
 */
@Mod(
    modid = IMBlocker.MODID,
    name = IMBlocker.NAME,
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]")
public class IMBlocker {

    public static final String MODID = "imblocker";
    public static final String NAME = "IMBlocker (1.7.10 Backport)";
    public static final Logger LOG = LogManager.getLogger("IMBlocker");

    @SidedProxy(
        clientSide = "com.imblocker.backport.ClientProxy",
        serverSide = "com.imblocker.backport.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    public static void debug(String message, Object... args) {
        if (Config.debug) {
            LOG.info("[debug] " + message, args);
        }
    }
}
