package com.imblocker.backport.ime;

/**
 * Platform abstraction for switching the system input method on and off.
 *
 * Implementations must be idempotent: calling {@link #setImeActive(boolean)} with the value that is already in effect
 * must not produce any native call.
 */
public interface ImeController {

    /** Whether this backend can actually do something on the current platform. */
    boolean isAvailable();

    /** @return true while the IME is usable (attached / open). */
    boolean isImeActive();

    /**
     * @param active true when the player is typing and the IME must work, false when keys must reach the game
     *               untouched.
     */
    void setImeActive(boolean active);

    /** Best-effort restore of the original state, called from a shutdown hook. */
    void restore();

    /** Human readable description for the log. */
    String describe();
}
