package org.gitee.orryx.compat.maydmzanimation;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Java 8-compatible linkage island around MayDMZAnimation's Java 17 public API.
 *
 * <p>Orryx supports servers whose JVM cannot load Java 17 records. Reflection is intentionally
 * confined to this class and only addresses public {@code api} types; no implementation class,
 * protocol object or plugin singleton crosses this boundary.</p>
 */
public final class MayDMZAnimationApiBridge {
    private static final String API_PACKAGE = "com.mayihavek.dmzanimation.api.";
    private static volatile ReflectionBindings cachedBindings;

    private MayDMZAnimationApiBridge() {
    }

    public static boolean available() {
        return service("actions") != null
                || service("playback") != null
                || service("combos") != null;
    }

    public static String assignCombo(Player player, String comboId) {
        Object service = service("combos");
        if (service == null) return "unavailable";
        Object result = invoke(
                service,
                "assign",
                new Class<?>[]{Player.class, String.class},
                player,
                comboId
        );
        return ((Enum<?>) invoke(result, "status")).name().toLowerCase(Locale.ROOT);
    }

    public static String clearCombo(Player player) {
        Object service = service("combos");
        if (service == null) return "unavailable";
        Object result = invoke(
                service,
                "clear",
                new Class<?>[]{Player.class},
                player
        );
        return ((Enum<?>) invoke(result, "status")).name().toLowerCase(Locale.ROOT);
    }

    public static String assignedCombo(Player player) {
        Object service = service("combos");
        if (service == null) return null;
        Optional<?> assigned = (Optional<?>) invoke(
                service,
                "assigned",
                new Class<?>[]{Player.class},
                player
        );
        return assigned.map(String::valueOf).orElse(null);
    }

    public static boolean comboExists(String comboId) {
        Object service = service("combos");
        return service != null && ((Set<?>) invoke(service, "comboIds")).contains(comboId);
    }

    public static String start(Player player, String actionId, Integer band, String policy) {
        Object service = service("actions");
        if (service == null) return "unavailable";

        Class<?> optionsType = apiType("ActionStartOptions");
        Object options = band == null ? invokeStatic(optionsType, "defaults") : options(band, policy);
        Object result = invoke(
                service,
                "start",
                new Class<?>[]{Player.class, String.class, optionsType},
                player,
                actionId,
                options
        );
        Optional<?> rejection = (Optional<?>) invoke(result, "rejection");
        Object outcome = rejection.isPresent() ? rejection.get() : invoke(result, "status");
        return ((Enum<?>) outcome).name().toLowerCase(Locale.ROOT);
    }

    public static boolean signal(Player player, String value, String channel) {
        Object service = service("actions");
        if (service == null) return false;
        boolean accepted = false;
        for (Object handle : active(service, player)) {
            if (matches(handle, channel)) {
                accepted |= (Boolean) invoke(handle, "signal", new Class<?>[]{String.class}, value);
            }
        }
        return accepted;
    }

    public static int stop(Player player, String channel) {
        Object service = service("actions");
        if (service == null) return 0;
        int count = 0;
        for (Object handle : active(service, player)) {
            if (matches(handle, channel) && (Boolean) invoke(handle, "stop")) count++;
        }
        return count;
    }

    public static int cancel(Player player, String reason, String channel) {
        Object service = service("actions");
        if (service == null) return 0;
        int count = 0;
        for (Object handle : active(service, player)) {
            if (matches(handle, channel)
                    && (Boolean) invoke(handle, "cancel", new Class<?>[]{String.class}, reason)) {
                count++;
            }
        }
        return count;
    }

    public static int running(Player player, String channel) {
        Object service = service("actions");
        if (service == null) return 0;
        int count = 0;
        for (Object handle : active(service, player)) {
            if (matches(handle, channel)) count++;
        }
        return count;
    }

    public static boolean actionExists(String actionId) {
        Object service = service("actions");
        if (service == null) return false;
        return ((Set<?>) invoke(service, "actionIds")).contains(actionId);
    }

    public static boolean play(
            Player player,
            String animation,
            String mode,
            float speed,
            int duration,
            float transition
    ) {
        Object service = service("playback");
        if (service == null) return false;
        Class<?> modeType = apiType("PlaybackMode");
        Object playbackMode = invokeStatic(
                modeType,
                "parse",
                new Class<?>[]{String.class},
                mode
        );
        invoke(
                service,
                "play",
                new Class<?>[]{
                        Player.class, String.class, modeType,
                        float.class, int.class, float.class
                },
                player,
                animation,
                playbackMode,
                speed,
                duration,
                transition
        );
        return true;
    }

    public static boolean stopPlayback(Player player, float transition) {
        Object service = service("playback");
        return service != null && (Boolean) invoke(
                service,
                "stop",
                new Class<?>[]{Player.class, float.class},
                player,
                transition
        );
    }

    private static Object service(String accessor) {
        ReflectionBindings bindings = bindingsOrNull();
        if (bindings == null) return null;
        Optional<?> service = (Optional<?>) invokeStatic(
                bindings.apiType("MayDMZAnimationApi"), accessor
        );
        return service.orElse(null);
    }

    private static Object options(int band, String policy) {
        Class<?> priorityType = apiType("ActionPriority");
        String normalized = policy.toLowerCase(Locale.ROOT);
        String factory;
        if (normalized.equals("drop") || normalized.equals("drop_if_busy")) {
            factory = "dropIfBusy";
        } else if (normalized.equals("replace") || normalized.equals("replace_lower")) {
            factory = "replaceLower";
        } else if (normalized.equals("force")) {
            factory = "force";
        } else {
            throw new IllegalArgumentException("MayDMZAnimation policy 必须是 drop、replace 或 force");
        }

        Object priority = invokeStatic(priorityType, factory, new Class<?>[]{int.class}, band);
        Class<?> optionsType = apiType("ActionStartOptions");
        Object builder = invokeStatic(optionsType, "builder");
        invoke(builder, "priority", new Class<?>[]{priorityType}, priority);
        invoke(
                builder,
                "owner",
                new Class<?>[]{Plugin.class},
                Bukkit.getPluginManager().getPlugin("Orryx")
        );
        return invoke(builder, "build");
    }

    private static List<?> active(Object service, Player player) {
        return (List<?>) invoke(service, "active", new Class<?>[]{Player.class}, player);
    }

    private static boolean matches(Object handle, String channel) {
        return channel == null || channel.equals(invoke(handle, "channel"));
    }

    private static Class<?> apiType(String simpleName) {
        ReflectionBindings bindings = bindingsOrNull();
        if (bindings == null) {
            throw linkage("MayDMZAnimation plugin class loader is unavailable", null);
        }
        return bindings.apiType(simpleName);
    }

    private static Object invokeStatic(Class<?> owner, String name, Object... arguments) {
        return invoke(null, owner, name, new Class<?>[0], arguments);
    }

    private static Object invokeStatic(
            Class<?> owner,
            String name,
            Class<?>[] parameterTypes,
            Object... arguments
    ) {
        return invoke(null, owner, name, parameterTypes, arguments);
    }

    private static Object invoke(Object target, String name, Object... arguments) {
        return invoke(target, target.getClass(), name, new Class<?>[0], arguments);
    }

    private static Object invoke(
            Object target,
            String name,
            Class<?>[] parameterTypes,
            Object... arguments
    ) {
        return invoke(target, target.getClass(), name, parameterTypes, arguments);
    }

    private static Object invoke(
            Object target,
            Class<?> owner,
            String name,
            Class<?>[] parameterTypes,
            Object... arguments
    ) {
        try {
            ReflectionBindings bindings = bindingsOrNull();
            if (bindings == null) {
                throw linkage("MayDMZAnimation plugin class loader is unavailable", null);
            }
            Method method = bindings.method(owner, name, parameterTypes);
            return method.invoke(target, arguments);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw linkage("MayDMZAnimation API call failed: " + owner.getName() + '#' + name, cause);
        } catch (ReflectiveOperationException error) {
            throw linkage("Incompatible MayDMZAnimation API: " + owner.getName() + '#' + name, error);
        }
    }

    private static LinkageError linkage(String message, Throwable cause) {
        LinkageError error = new LinkageError(message);
        if (cause != null) error.initCause(cause);
        return error;
    }

    private static ReflectionBindings bindingsOrNull() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("MayDMZAnimation");
        if (plugin == null || !plugin.isEnabled()) return null;
        ReflectionBindings current = cachedBindings;
        if (current != null && current.plugin == plugin) return current;
        synchronized (MayDMZAnimationApiBridge.class) {
            current = cachedBindings;
            if (current == null || current.plugin != plugin) {
                current = new ReflectionBindings(plugin);
                cachedBindings = current;
            }
            return current;
        }
    }

    /** Cached metadata only. Service providers are deliberately resolved for every API call. */
    private static final class ReflectionBindings {
        private final Plugin plugin;
        private final ClassLoader classLoader;
        private final Map<String, Class<?>> apiTypes = new ConcurrentHashMap<>();
        private final Map<MethodKey, Method> methods = new ConcurrentHashMap<>();

        private ReflectionBindings(Plugin plugin) {
            this.plugin = plugin;
            this.classLoader = plugin.getClass().getClassLoader();
        }

        private Class<?> apiType(String simpleName) {
            Class<?> cached = apiTypes.get(simpleName);
            if (cached != null) return cached;
            try {
                Class<?> loaded = Class.forName(API_PACKAGE + simpleName, true, classLoader);
                Class<?> raced = apiTypes.putIfAbsent(simpleName, loaded);
                return raced == null ? loaded : raced;
            } catch (ClassNotFoundException error) {
                throw linkage("Missing MayDMZAnimation API type " + simpleName, error);
            }
        }

        private Method method(Class<?> owner, String name, Class<?>[] parameterTypes) {
            MethodKey key = new MethodKey(owner, name, parameterTypes);
            Method cached = methods.get(key);
            if (cached != null) return cached;
            try {
                Method resolved = owner.getMethod(name, parameterTypes);
                Method raced = methods.putIfAbsent(key, resolved);
                return raced == null ? resolved : raced;
            } catch (NoSuchMethodException error) {
                throw linkage("Incompatible MayDMZAnimation API: "
                        + owner.getName() + '#' + name, error);
            }
        }
    }

    private static final class MethodKey {
        private final Class<?> owner;
        private final String name;
        private final List<Class<?>> parameterTypes;

        private MethodKey(Class<?> owner, String name, Class<?>[] parameterTypes) {
            this.owner = owner;
            this.name = name;
            this.parameterTypes = Arrays.asList(parameterTypes.clone());
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof MethodKey)) return false;
            MethodKey that = (MethodKey) other;
            return owner == that.owner
                    && name.equals(that.name)
                    && parameterTypes.equals(that.parameterTypes);
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(owner);
            result = 31 * result + name.hashCode();
            result = 31 * result + parameterTypes.hashCode();
            return result;
        }
    }
}
