package com.imblocker.backport.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import com.imblocker.backport.Config;
import com.imblocker.backport.IMBlocker;
import com.imblocker.backport.ime.ImeController;

/**
 * Decides whether the IME should currently be usable and forwards the result to the platform controller.
 *
 * Focus information comes from two places:
 *
 * <ul>
 * <li>the mixin on {@link GuiTextField#setFocused(boolean)} - reliable for vanilla and every addon that reuses the
 * vanilla widget;</li>
 * <li>a one-off object graph scan per screen, for GUIs with hand-rolled input widgets.</li>
 * </ul>
 */
public final class ImeStateManager {

    private static final Map<GuiTextField, Boolean> FOCUSED = Collections
        .synchronizedMap(new WeakHashMap<GuiTextField, Boolean>());

    private static ImeController controller;
    private static Minecraft minecraft;
    private static GuiScreen lastScreen;
    private static List<TextInputProbe> probes = Collections.emptyList();
    private static Boolean manualOverride;
    private static int tickCounter;

    private ImeStateManager() {}

    public static void init(ImeController imeController, Minecraft mc) {
        controller = imeController;
        minecraft = mc;
    }

    // ------------------------------------------------------------------ mixin callbacks

    public static void onTextFieldFocused(GuiTextField field) {
        if (field == null) {
            return;
        }
        FOCUSED.put(field, Boolean.TRUE);
        if (manualOverride == null) {
            apply(true);
        }
    }

    public static void onTextFieldBlurred(GuiTextField field) {
        if (field == null) {
            return;
        }
        FOCUSED.remove(field);
        if (manualOverride == null) {
            refresh();
        }
    }

    // ------------------------------------------------------------------ tick

    public static void clientTick() {
        if (controller == null || minecraft == null) {
            return;
        }
        GuiScreen screen = minecraft.currentScreen;
        if (screen != lastScreen) {
            lastScreen = screen;
            probes = TextInputScanner.scan(screen);
            FOCUSED.clear();
            manualOverride = null;
            IMBlocker.debug(
                "screen -> {} (probes: {})",
                screen == null ? "none" : screen.getClass()
                    .getName(),
                probes.size());
        }
        if (tickCounter++ % Math.max(1, Config.checkIntervalTicks) != 0) {
            return;
        }
        refresh();
    }

    public static void toggle() {
        boolean current = computeWanted();
        manualOverride = Boolean.valueOf(!current);
        apply(manualOverride.booleanValue());
    }

    // ------------------------------------------------------------------ internals

    private static void refresh() {
        apply(computeWanted());
    }

    private static void apply(boolean wanted) {
        if (controller == null) {
            return;
        }
        controller.setImeActive(wanted);
    }

    private static boolean computeWanted() {
        if (!Config.enabled) {
            return true;
        }
        if (manualOverride != null) {
            return manualOverride.booleanValue();
        }
        GuiScreen screen = minecraft == null ? null : minecraft.currentScreen;
        if (screen == null) {
            return false;
        }
        if (isAlwaysImeScreen(screen)) {
            return true;
        }
        if (anyTrackedFieldFocused()) {
            return true;
        }
        for (int i = 0; i < probes.size(); i++) {
            if (probes.get(i)
                .isFocused()) {
                return true;
            }
        }
        return false;
    }

    private static boolean anyTrackedFieldFocused() {
        List<GuiTextField> stale = null;
        boolean found = false;
        synchronized (FOCUSED) {
            for (GuiTextField field : new ArrayList<>(FOCUSED.keySet())) {
                if (isFocused(field)) {
                    found = true;
                } else {
                    if (stale == null) {
                        stale = new ArrayList<>();
                    }
                    stale.add(field);
                }
            }
            if (stale != null) {
                for (GuiTextField field : stale) {
                    FOCUSED.remove(field);
                }
            }
        }
        return found;
    }

    private static boolean isFocused(GuiTextField field) {
        try {
            return field.isFocused();
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isAlwaysImeScreen(GuiScreen screen) {
        if (screen == null || Config.alwaysImeScreens.isEmpty()) {
            return false;
        }
        for (Class<?> type = screen.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            String name = type.getName();
            for (String configured : Config.alwaysImeScreens) {
                if (configured != null && !configured.isEmpty() && name.equals(configured)) {
                    return true;
                }
            }
        }
        return false;
    }
}
