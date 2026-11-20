package com.imblocker.backport.ime;

import java.lang.reflect.Method;

import com.imblocker.backport.Config;
import com.imblocker.backport.IMBlocker;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

/**
 * Windows IME backend.
 *
 * Two strategies are available:
 *
 * <ul>
 * <li><b>detach</b> (default): {@code ImmAssociateContext(hwnd, NULL)} removes the IME from the game window, so the
 * keyboard behaves like a plain US keyboard. The original context is kept in {@link #savedContext} and re-associated
 * when a text field is focused. This is what IMBlocker does on modern versions and is what most IMEs honour.</li>
 * <li><b>openstatus</b>: keeps the context attached and only calls {@code ImmSetOpenStatus}. Gentler, but a few IMEs
 * keep eating keys in "closed" state.</li>
 * </ul>
 */
public final class WindowsImeController implements ImeController {

    private Pointer hwnd;
    private Pointer savedContext;
    private Boolean lastActive;

    // Cached GLFW handles, resolved once.
    private Method glfwGetCurrentContext;
    private Method glfwGetWin32Window;
    private boolean glfwLookupDone;

    @Override
    public boolean isAvailable() {
        try {
            return Win32.Imm32.INSTANCE != null;
        } catch (Throwable t) {
            IMBlocker.LOG.warn("imm32 is not reachable, IME control disabled", t);
            return false;
        }
    }

    @Override
    public boolean isImeActive() {
        Pointer window = resolveWindow();
        return window != null && hasContext(window);
    }

    @Override
    public synchronized void setImeActive(boolean active) {
        if (lastActive != null && lastActive.booleanValue() == active) {
            return;
        }
        Pointer window = resolveWindow();
        if (window == null) {
            // No game window yet (or it is not in the foreground): try again next tick, do not touch other apps' IME.
            return;
        }
        if (!isForeground(window)) {
            IMBlocker.debug("window is not in the foreground, leaving the IME alone");
            return;
        }

        if (Config.useDetachMode()) {
            applyDetach(window, active);
        } else {
            applyOpenStatus(window, active);
        }
        if (lastActive == null || lastActive.booleanValue() != active) {
            IMBlocker.debug("IME -> {}", active ? "on (typing)" : "off (playing)");
        }
        lastActive = Boolean.valueOf(active);    }

    @Override
    public synchronized void restore() {
        try {
            Pointer window = hwnd;
            if (window == null || !Win32.User32.INSTANCE.IsWindow(window)) {
                return;
            }
            if (savedContext != null) {
                Win32.Imm32.INSTANCE.ImmAssociateContext(window, savedContext);
                savedContext = null;
            }
        } catch (Throwable t) {
            // shutting down, nothing useful to do
        }
    }

    @Override
    public String describe() {
        return "windows/imm32 (" + (Config.useDetachMode() ? "detach" : "openstatus") + ")";
    }

    // ------------------------------------------------------------------ state machine

    private void applyDetach(Pointer window, boolean active) {
        boolean attached = hasContext(window);
        if (attached == active) {
            return;
        }
        if (active) {
            Pointer context = savedContext;
            if (context != null) {
                Win32.Imm32.INSTANCE.ImmAssociateContext(window, context);
                savedContext = null;
            }
            // A context that went stale while it was detached (some IMEs drop it) would leave the player unable to type
            // at all, so verify and recreate if needed.
            if (!hasContext(window)) {
                Pointer fresh = Win32.Imm32.INSTANCE.ImmCreateContext();
                if (fresh != null) {
                    Win32.Imm32.INSTANCE.ImmAssociateContext(window, fresh);
                }
            }
        } else {
            Pointer context = Win32.Imm32.INSTANCE.ImmAssociateContext(window, null);
            if (context != null) {
                savedContext = context;
            }
        }
    }

    private void applyOpenStatus(Pointer window, boolean active) {
        Pointer context = Win32.Imm32.INSTANCE.ImmGetContext(window);
        if (context == null) {
            context = Win32.Imm32.INSTANCE.ImmCreateContext();
            if (context != null) {
                Win32.Imm32.INSTANCE.ImmAssociateContext(window, context);
            }
        }
        if (context == null) {
            return;
        }
        try {
            Win32.Imm32.INSTANCE.ImmSetOpenStatus(context, active);
        } finally {
            Win32.Imm32.INSTANCE.ImmReleaseContext(window, context);
        }
    }

    private boolean hasContext(Pointer window) {
        Pointer context = Win32.Imm32.INSTANCE.ImmGetContext(window);
        if (context == null) {
            return false;
        }
        Win32.Imm32.INSTANCE.ImmReleaseContext(window, context);
        return true;
    }

    // ------------------------------------------------------------------ window lookup

    private synchronized Pointer resolveWindow() {
        if (hwnd != null) {
            if (Win32.User32.INSTANCE.IsWindow(hwnd)) {
                return hwnd;
            }
            hwnd = null;
            savedContext = null;
            lastActive = null;
        }

        Pointer window = windowFromGlfw();
        if (window == null) {
            window = foregroundWindowOfThisProcess();
        }
        hwnd = window;
        return window;
    }

    /**
     * GTNH 2.7+ runs on lwjgl3ify, i.e. a real LWJGL 3 / GLFW window. Asking GLFW for the native handle is exact; on
     * plain LWJGL 2 installs these classes do not exist and we fall back to the foreground window below.
     */
    private Pointer windowFromGlfw() {
        if (glfwLookupDone && glfwGetCurrentContext == null) {
            return null;
        }
        try {
            if (glfwGetCurrentContext == null) {
                Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW");
                glfwGetCurrentContext = glfw.getMethod("glfwGetCurrentContext");
                Class<?> nativeWin32 = Class.forName("org.lwjgl.glfw.GLFWNativeWin32");
                glfwGetWin32Window = nativeWin32.getMethod("glfwGetWin32Window", long.class);
                glfwLookupDone = true;
            }
            Object context = glfwGetCurrentContext.invoke(null);
            if (!(context instanceof Long)) {
                return null;
            }
            long handle = ((Long) context).longValue();
            if (handle == 0L) {
                return null;
            }
            Object nativeHandle = glfwGetWin32Window.invoke(null, Long.valueOf(handle));
            if (nativeHandle instanceof Long) {
                long value = ((Long) nativeHandle).longValue();
                if (value != 0L) {
                    return Pointer.createConstant(value);
                }
            }
        } catch (Throwable t) {
            glfwLookupDone = true;
            glfwGetCurrentContext = null;
        }
        return null;
    }

    private Pointer foregroundWindowOfThisProcess() {
        try {
            Pointer foreground = Win32.User32.INSTANCE.GetForegroundWindow();
            if (foreground == null) {
                return null;
            }
            IntByReference pid = new IntByReference();
            Win32.User32.INSTANCE.GetWindowThreadProcessId(foreground, pid);
            if (pid.getValue() == Win32.Kernel32.INSTANCE.GetCurrentProcessId()) {
                return foreground;
            }
        } catch (Throwable t) {
            IMBlocker.debug("foreground window lookup failed: {}", t.toString());
        }
        return null;
    }

    private boolean isForeground(Pointer window) {
        try {
            Pointer foreground = Win32.User32.INSTANCE.GetForegroundWindow();
            return foreground != null && Pointer.nativeValue(foreground) == Pointer.nativeValue(window);
        } catch (Throwable t) {
            return false;
        }
    }
}
