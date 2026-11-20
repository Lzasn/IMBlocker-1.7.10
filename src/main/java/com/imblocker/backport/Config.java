package com.imblocker.backport;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraftforge.common.config.Configuration;

/**
 * Configuration, stored in config/imblocker.cfg.
 */
public final class Config {

    public static final String CATEGORY = "general";

    /** Master switch. */
    public static boolean enabled = true;
    /** "detach" (default, IMBlocker behaviour) or "openstatus" (only close/open the IME, keener on some IMEs). */
    public static String mode = "detach";
    /** How many client ticks between two focus checks. 1 = every tick (50 ms). */
    public static int checkIntervalTicks = 1;
    /** Print every state transition to the log. */
    public static boolean debug = false;
    /** Screens that always need the IME, even though they own no GuiTextField (chat, sign, book, ...). */
    public static List<String> alwaysImeScreens = new ArrayList<>(
        Arrays.asList(
            "net.minecraft.client.gui.GuiChat",
            "net.minecraft.client.gui.GuiSleepMP",
            "net.minecraft.client.gui.inventory.GuiEditSign",
            "net.minecraft.client.gui.GuiScreenBook"));
    /** Extra classes (or class name fragments) that should be treated as text inputs. */
    public static List<String> extraTextInputClasses = new ArrayList<>();

    private Config() {}

    public static void load(File file) {
        Configuration cfg = new Configuration(file);
        try {
            cfg.load();
            enabled = cfg.getBoolean("enabled", CATEGORY, enabled, "Enable the IME blocker at all.");

            mode = cfg.getString(
                "mode",
                CATEGORY,
                mode,
                "detach: detach the IME context while not typing (IMBlocker behaviour, works with most IMEs).\n"
                    + "openstatus: keep the context attached but close/open the IME instead (fallback for IMEs that ignore detaching).");

            checkIntervalTicks = cfg.getInt(
                "checkIntervalTicks",
                CATEGORY,
                checkIntervalTicks,
                1,
                20,
                "Ticks between two focus checks (20 ticks = 1 second).");

            debug = cfg.getBoolean("debug", CATEGORY, debug, "Log every IME state change.");

            alwaysImeScreens = new ArrayList<>(
                Arrays.asList(
                    cfg.getStringList(
                        "alwaysImeScreens",
                        CATEGORY,
                        alwaysImeScreens.toArray(new String[0]),
                        "Fully qualified screen class names that always get the IME (no text field detection).")));

            extraTextInputClasses = new ArrayList<>(
                Arrays.asList(
                    cfg.getStringList(
                        "extraTextInputClasses",
                        CATEGORY,
                        extraTextInputClasses.toArray(new String[0]),
                        "Extra class names (or name fragments) that should count as a text input, e.g. mymod.gui.MySearchBox")));
        } catch (Exception e) {
            IMBlocker.LOG.error("Failed to read config, falling back to defaults", e);
        } finally {
            if (cfg.hasChanged()) {
                cfg.save();
            }
        }
    }

    public static boolean useDetachMode() {
        return !"openstatus".equalsIgnoreCase(mode);
    }
}
