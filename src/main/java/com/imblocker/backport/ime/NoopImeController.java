package com.imblocker.backport.ime;

/**
 * Used on non-Windows platforms and whenever the native backend could not be initialised. Keeps the rest of the mod
 * free of null checks.
 */
public final class NoopImeController implements ImeController {

    private final String reason;

    public NoopImeController(String reason) {
        this.reason = reason;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public boolean isImeActive() {
        return true;
    }

    @Override
    public void setImeActive(boolean active) {}

    @Override
    public void restore() {}

    @Override
    public String describe() {
        return "noop (" + reason + ")";
    }
}
