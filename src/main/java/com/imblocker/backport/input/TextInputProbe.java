package com.imblocker.backport.input;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiTextField;

import com.imblocker.backport.Config;

/**
 * Thin, name-independent wrapper around "something that looks like a text input".
 *
 * Vanilla {@link GuiTextField} needs no reflection at runtime (FML remaps the direct calls for us), everything else is
 * duck typed - 1.7.10 addons ship dozens of custom text widgets and almost all of them expose an {@code isFocused()}
 * or a {@code focused} flag.
 */
public abstract class TextInputProbe {

    public abstract boolean isFocused();

    private static final String[] FOCUS_METHODS = { "isFocused", "hasFocus", "getFocused", "isFocus" };
    private static final String[] FOCUS_FIELDS = { "focused", "isFocused", "hasFocus", "focus" };
    private static final String[] TEXT_METHODS = { "getText", "getTextString", "getValue", "getContent" };
    private static final String[] TEXT_FIELDS = { "text", "value", "content" };

    public static TextInputProbe of(Object candidate) {
        if (candidate instanceof GuiTextField) {
            return new Vanilla(candidate);
        }
        return Reflective.create(candidate);
    }

    /** Fast path: real vanilla text fields, which is the vast majority of 1.7.10 GUIs. */
    private static final class Vanilla extends TextInputProbe {

        private final GuiTextField field;

        Vanilla(Object candidate) {
            this.field = (GuiTextField) candidate;
        }

        @Override
        public boolean isFocused() {
            try {
                return field.isFocused();
            } catch (Throwable t) {
                return false;
            }
        }
    }

    /** Duck typed fallback for modded text widgets. */
    private static final class Reflective extends TextInputProbe {

        private final Object target;
        private final Method focusMethod;
        private final Field focusField;

        private Reflective(Object target, Method focusMethod, Field focusField) {
            this.target = target;
            this.focusMethod = focusMethod;
            this.focusField = focusField;
        }

        static TextInputProbe create(Object candidate) {
            Class<?> type = candidate.getClass();
            Method focusMethod = findBooleanMethod(type, FOCUS_METHODS);
            Field focusField = focusMethod == null ? findBooleanField(type, FOCUS_FIELDS) : null;
            if (focusMethod == null && focusField == null) {
                return null;
            }
            if (!looksLikeText(type)) {
                return null;
            }
            return new Reflective(candidate, focusMethod, focusField);
        }

        private static boolean looksLikeText(Class<?> type) {
            for (String name : Config.extraTextInputClasses) {
                if (type.getName()
                    .contains(name)) {
                    return true;
                }
            }
            return findStringMethod(type, TEXT_METHODS) != null || findStringField(type, TEXT_FIELDS) != null;
        }

        @Override
        public boolean isFocused() {
            try {
                if (focusMethod != null) {
                    Object result = focusMethod.invoke(target);
                    return result instanceof Boolean && ((Boolean) result).booleanValue();
                }
                return focusField.getBoolean(target);
            } catch (Throwable t) {
                return false;
            }
        }
    }

    // ------------------------------------------------------------------ reflection helpers

    private static Method findBooleanMethod(Class<?> type, String[] names) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method method : safeMethods(c)) {
                if (method.getReturnType() != boolean.class || method.getParameterTypes().length != 0) {
                    continue;
                }
                if (Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                if (matches(method.getName(), names)) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return null;
    }

    private static Field findBooleanField(Class<?> type, String[] names) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : safeFields(c)) {
                if (field.getType() != boolean.class || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (matches(field.getName(), names)) {
                    field.setAccessible(true);
                    return field;
                }
            }
        }
        return null;
    }

    private static Method findStringMethod(Class<?> type, String[] names) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method method : safeMethods(c)) {
                if (method.getReturnType() != String.class || method.getParameterTypes().length != 0) {
                    continue;
                }
                if (matches(method.getName(), names)) {
                    return method;
                }
            }
        }
        return null;
    }

    private static Field findStringField(Class<?> type, String[] names) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : safeFields(c)) {
                if (field.getType() == String.class && !Modifier.isStatic(field.getModifiers())
                    && matches(field.getName(), names)) {
                    return field;
                }
            }
        }
        return null;
    }

    private static boolean matches(String name, String[] candidates) {
        for (String candidate : candidates) {
            if (candidate.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static List<Method> safeMethods(Class<?> type) {
        try {
            return java.util.Arrays.asList(type.getDeclaredMethods());
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }

    private static List<Field> safeFields(Class<?> type) {
        try {
            return java.util.Arrays.asList(type.getDeclaredFields());
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }
}
