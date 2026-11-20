package com.imblocker.backport.ime;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

/**
 * Minimal Win32 bindings. Deliberately hand written instead of pulling in jna-platform, the mod only needs a handful of
 * functions and staying small keeps the jar friendly for a 1.7.10 pack.
 *
 * HWND / HIMC / HKL are all opaque pointers, {@link Pointer} is good enough for them.
 */
final class Win32 {

    interface Imm32 extends Library {

        Imm32 INSTANCE = Native.load("imm32", Imm32.class);

        Pointer ImmGetContext(Pointer hwnd);

        Pointer ImmAssociateContext(Pointer hwnd, Pointer himc);

        Pointer ImmCreateContext();

        boolean ImmDestroyContext(Pointer himc);

        boolean ImmReleaseContext(Pointer hwnd, Pointer himc);

        boolean ImmGetOpenStatus(Pointer himc);

        boolean ImmSetOpenStatus(Pointer himc, boolean open);
    }

    interface User32 extends Library {

        User32 INSTANCE = Native.load("user32", User32.class);

        Pointer GetForegroundWindow();

        boolean IsWindow(Pointer hwnd);

        int GetWindowThreadProcessId(Pointer hwnd, IntByReference processId);
    }

    interface Kernel32 extends Library {

        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);

        int GetCurrentProcessId();
    }

    private Win32() {}
}
