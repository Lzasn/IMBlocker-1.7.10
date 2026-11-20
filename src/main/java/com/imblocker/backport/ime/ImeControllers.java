package com.imblocker.backport.ime;

import java.util.Locale;

import com.imblocker.backport.IMBlocker;

public final class ImeControllers {

    private ImeControllers() {}

    public static ImeController create() {
        String os = System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT);
        if (!os.contains("win")) {
            IMBlocker.LOG.info("IME blocking is only implemented for Windows, found {}", os);
            return new NoopImeController(os);
        }
        try {
            ImeController controller = new WindowsImeController();
            if (controller.isAvailable()) {
                return controller;
            }
        } catch (Throwable t) {
            IMBlocker.LOG.error("Could not initialise the Windows IME backend", t);
        }
        return new NoopImeController("windows backend failed");
    }
}
