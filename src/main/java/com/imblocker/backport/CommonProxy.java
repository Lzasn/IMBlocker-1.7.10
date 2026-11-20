package com.imblocker.backport;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        // Server side: no IME, nothing to do.
    }

    public void init(FMLInitializationEvent event) {
        // Server side: no IME, nothing to do.
    }
}
