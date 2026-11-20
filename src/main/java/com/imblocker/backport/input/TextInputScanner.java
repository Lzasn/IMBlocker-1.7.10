package com.imblocker.backport.input;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.gui.GuiScreen;

/**
 * Collects text inputs reachable from a screen instance.
 *
 * The mixin on {@code GuiTextField#setFocused} already covers everything that derives from the vanilla widget, this scan
 * is the safety net for GUIs that ship their own input widget (NEI, GT, AE2, questing mods, ...). It runs once whenever
 * a new screen is opened and the result is polled afterwards, so the per-tick cost is a couple of virtual calls.
 */
public final class TextInputScanner {

    private static final int MAX_DEPTH = 3;
    private static final int MAX_NODES = 3000;
    private static final int MAX_COLLECTION_ENTRIES = 300;
    private static final int MAX_FIELDS_PER_CLASS = 96;

    private static final String[] BLOCKED_PREFIXES = { "java.", "javax.", "jdk.", "sun.", "com.sun.", "org.lwjgl",
        "io.netty", "com.google", "org.apache", "cpw.mods", "com.mojang", "net.minecraft.world", "net.minecraft.entity",
        "net.minecraft.network", "net.minecraft.nbt", "net.minecraft.server", "net.minecraft.client.multiplayer",
        "net.minecraft.client.network", "net.minecraft.client.renderer", "net.minecraft.client.resources",
        "net.minecraft.client.audio" };

    private static final java.util.Set<String> EXACT_BLOCKED = new java.util.HashSet<>(
        java.util.Arrays.asList("net.minecraft.client.Minecraft", "net.minecraft.client.gui.FontRenderer"));

    private static final String[] GUI_TOKENS = { "gui", "widget", "screen", "panel", "container", "list", "button",
        "field", "tab", "page" };

    private static final Map<Class<?>, Field[]> FIELD_CACHE = new ConcurrentHashMap<>();

    private TextInputScanner() {}

    public static List<TextInputProbe> scan(GuiScreen screen) {
        if (screen == null) {
            return Collections.emptyList();
        }
        List<TextInputProbe> probes = new ArrayList<>();
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        Deque<Entry> queue = new ArrayDeque<>();
        queue.add(new Entry(screen, 0));
        int visited = 0;

        while (!queue.isEmpty() && visited < MAX_NODES) {
            Entry entry = queue.poll();
            Object value = entry.value;
            if (value == null || seen.put(value, Boolean.TRUE) != null) {
                continue;
            }
            visited++;

            if (value instanceof Iterable) {
                if (entry.depth < MAX_DEPTH) {
                    enqueueAll((Iterable<?>) value, entry.depth + 1, queue);
                }
                continue;
            }
            if (value.getClass()
                .isArray() && !value.getClass()
                    .getComponentType()
                    .isPrimitive()) {
                if (entry.depth < MAX_DEPTH) {
                    enqueueArray(value, entry.depth + 1, queue);
                }
                continue;
            }
            if (value instanceof Map) {
                if (entry.depth < MAX_DEPTH) {
                    enqueueAll(((Map<?, ?>) value).values(), entry.depth + 1, queue);
                }
                continue;
            }

            TextInputProbe probe = TextInputProbe.of(value);
            if (probe != null) {
                probes.add(probe);
                continue;
            }

            if (entry.depth >= MAX_DEPTH || !traversable(value.getClass(), entry.depth)) {
                continue;
            }
            for (Field field : fieldsOf(value.getClass())) {
                Object child;
                try {
                    child = field.get(value);
                } catch (Throwable t) {
                    continue;
                }
                if (child != null) {
                    queue.add(new Entry(child, entry.depth + 1));
                }
            }
        }

        return probes;
    }

    private static void enqueueAll(Iterable<?> iterable, int depth, Deque<Entry> queue) {
        int count = 0;
        for (Object element : iterable) {
            if (element != null) {
                queue.add(new Entry(element, depth));
            }
            if (++count >= MAX_COLLECTION_ENTRIES) {
                return;
            }
        }
    }

    private static void enqueueArray(Object array, int depth, Deque<Entry> queue) {
        int length = Math.min(java.lang.reflect.Array.getLength(array), MAX_COLLECTION_ENTRIES);
        for (int i = 0; i < length; i++) {
            Object element = java.lang.reflect.Array.get(array, i);
            if (element != null) {
                queue.add(new Entry(element, depth));
            }
        }
    }

    private static boolean traversable(Class<?> type, int depth) {
        String name = type.getName();
        if (EXACT_BLOCKED.contains(name)) {
            return false;
        }
        for (String prefix : BLOCKED_PREFIXES) {
            if (name.startsWith(prefix)) {
                return false;
            }
        }
        if (depth <= 1) {
            return true;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String token : GUI_TOKENS) {
            if (lower.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static Field[] fieldsOf(Class<?> type) {
        Field[] cached = FIELD_CACHE.get(type);
        if (cached != null) {
            return cached;
        }
        List<Field> fields = new ArrayList<>();
        int levels = 0;
        for (Class<?> c = type; c != null && c != Object.class && levels < 8; c = c.getSuperclass(), levels++) {
            Field[] declared;
            try {
                declared = c.getDeclaredFields();
            } catch (Throwable t) {
                continue;
            }
            for (Field field : declared) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType()
                    .isPrimitive()) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                } catch (Throwable t) {
                    continue;
                }
                fields.add(field);
                if (fields.size() >= MAX_FIELDS_PER_CLASS) {
                    break;
                }
            }
        }
        Field[] result = fields.toArray(new Field[0]);
        FIELD_CACHE.put(type, result);
        return result;
    }

    private static final class Entry {

        final Object value;
        final int depth;

        Entry(Object value, int depth) {
            this.value = value;
            this.depth = depth;
        }
    }
}
