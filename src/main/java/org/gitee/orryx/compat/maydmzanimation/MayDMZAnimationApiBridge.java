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
import java.util.Objects;
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
                apiType("ComboAssignmentService"),
                "assign",
                new Class<?>[]{Player.class, String.class},
                player,
                comboId
        );
        return assignmentStatus(result);
    }

    public static String clearCombo(Player player) {
        Object service = service("combos");
        if (service == null) return "unavailable";
        Object result = invoke(
                service,
                apiType("ComboAssignmentService"),
                "clear",
                new Class<?>[]{Player.class},
                player
        );
        return assignmentStatus(result);
    }

    /** Atomically replaces a temporary assignment without overwriting a newer owner. */
    public static String compareAndSetCombo(
            Player player, String expectedCurrent, String replacement
    ) {
        Objects.requireNonNull(expectedCurrent, "expectedCurrent");
        Objects.requireNonNull(replacement, "replacement");
        Object service = service("combos");
        if (service == null) return "unavailable";
        Class<?> serviceType = apiType("ComboAssignmentService");
        Optional<Object> atomic = invokeOptional(
                service,
                serviceType,
                "compareAndSet",
                new Class<?>[]{Player.class, Optional.class, Optional.class},
                player,
                optionalCombo(expectedCurrent),
                optionalCombo(replacement)
        );
        if (atomic.isPresent()) return assignmentStatus(atomic.get());

        // Legacy providers have no CAS method. Kether invokes this whole bridge call in one
        // Bukkit-main-thread task, so the read and mutation still cannot interleave there.
        Optional<?> current = (Optional<?>) invoke(
                service, serviceType, "assigned", new Class<?>[]{Player.class}, player
        );
        String currentId = current.map(String::valueOf).orElse("");
        if (!currentId.equals(expectedCurrent)) return "conflict";
        if (currentId.equals(replacement)) return "unchanged";
        Object result = replacement.isEmpty()
                ? invoke(service, serviceType, "clear", new Class<?>[]{Player.class}, player)
                : invoke(service, serviceType, "assign",
                        new Class<?>[]{Player.class, String.class}, player, replacement);
        return assignmentStatus(result);
    }

    public static String assignedCombo(Player player) {
        Object service = service("combos");
        if (service == null) return null;
        Optional<?> assigned = (Optional<?>) invoke(
                service,
                apiType("ComboAssignmentService"),
                "assigned",
                new Class<?>[]{Player.class},
                player
        );
        return assigned.map(String::valueOf).orElse(null);
    }

    public static boolean comboExists(String comboId) {
        Object service = service("combos");
        return service != null && ((Set<?>) invoke(
                service, apiType("ComboAssignmentService"), "comboIds", new Class<?>[0]
        )).contains(comboId);
    }

    public static String start(Player player, String actionId, Integer band, String policy) {
        Object service = service("actions");
        if (service == null) return "unavailable";

        Class<?> optionsType = apiType("ActionStartOptions");
        Object options = band == null ? invokeStatic(optionsType, "defaults") : options(band, policy);
        Object result = invoke(
                service,
                apiType("ActionFlowService"),
                "start",
                new Class<?>[]{Player.class, String.class, optionsType},
                player,
                actionId,
                options
        );
        Class<?> resultType = apiType("ActionStartResult");
        Optional<?> rejection = (Optional<?>) invoke(
                result, resultType, "rejection", new Class<?>[0]
        );
        Object outcome = rejection.isPresent() ? rejection.get()
                : invoke(result, resultType, "status", new Class<?>[0]);
        return ((Enum<?>) outcome).name().toLowerCase(Locale.ROOT);
    }

    public static boolean signal(Player player, String value, String channel) {
        Object service = service("actions");
        if (service == null) return false;
        boolean accepted = false;
        for (Object handle : active(service, player)) {
            if (matches(handle, channel)) {
                accepted |= (Boolean) invoke(
                        handle, apiType("ActionHandle"), "signal",
                        new Class<?>[]{String.class}, value
                );
            }
        }
        return accepted;
    }

    public static int stop(Player player, String channel) {
        Object service = service("actions");
        if (service == null) return 0;
        int count = 0;
        for (Object handle : active(service, player)) {
            if (matches(handle, channel) && (Boolean) invoke(
                    handle, apiType("ActionHandle"), "stop", new Class<?>[0]
            )) count++;
        }
        return count;
    }

    public static int cancel(Player player, String reason, String channel) {
        Object service = service("actions");
        if (service == null) return 0;
        int count = 0;
        for (Object handle : active(service, player)) {
            if (matches(handle, channel)
                    && (Boolean) invoke(
                    handle, apiType("ActionHandle"), "cancel",
                    new Class<?>[]{String.class}, reason)) {
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
        return ((Set<?>) invoke(
                service, apiType("ActionFlowService"), "actionIds", new Class<?>[0]
        )).contains(actionId);
    }

    public static long play(
            Player player,
            String animation,
            String mode,
            float speed,
            int duration,
            float transition
    ) {
        Object service = service("playback");
        if (service == null) return 0L;
        Class<?> modeType = apiType("PlaybackMode");
        Object playbackMode = invokeStatic(
                modeType,
                "parse",
                new Class<?>[]{String.class},
                mode
        );
        Object instanceId = invoke(
                service,
                apiType("AnimationPlaybackService"),
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
        return ((Number) instanceId).longValue();
    }

    public static boolean stopPlayback(Player player, float transition) {
        Object service = service("playback");
        return service != null && (Boolean) invoke(
                service,
                apiType("AnimationPlaybackService"),
                "stop",
                new Class<?>[]{Player.class, float.class},
                player,
                transition
        );
    }

    public static boolean stopPlaybackInstance(long instanceId, float transition) {
        Object service = service("playback");
        if (service == null || instanceId <= 0L) return false;
        return invokeOptional(
                service, apiType("AnimationPlaybackService"), "stop",
                new Class<?>[]{long.class, float.class}, instanceId, transition
        ).filter(Boolean.class::isInstance).map(Boolean.class::cast).orElse(false);
    }

    private static Object service(String accessor) {
        ReflectionBindings bindings = bindingsOrNull();
        if (bindings == null) return null;
        Optional<?> service = (Optional<?>) invokeStatic(
                bindings.apiType("MayDMZAnimationApi"), accessor
        );
        return service.orElse(null);
    }

    private static Optional<String> optionalCombo(String comboId) {
        return comboId.isEmpty() ? Optional.empty() : Optional.of(comboId);
    }

    private static String assignmentStatus(Object result) {
        return ((Enum<?>) invoke(
                result, apiType("ComboAssignmentResult"), "status", new Class<?>[0]
        )).name().toLowerCase(Locale.ROOT);
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
        Class<?> builderType = apiType("ActionStartOptions$Builder");
        invoke(builder, builderType, "priority", new Class<?>[]{priorityType}, priority);
        invoke(
                builder,
                builderType,
                "owner",
                new Class<?>[]{Plugin.class},
                Bukkit.getPluginManager().getPlugin("Orryx")
        );
        return invoke(builder, builderType, "build", new Class<?>[0]);
    }

    private static List<?> active(Object service, Player player) {
        return (List<?>) invoke(
                service, apiType("ActionFlowService"), "active",
                new Class<?>[]{Player.class}, player
        );
    }

    private static boolean matches(Object handle, String channel) {
        return channel == null || channel.equals(invoke(
                handle, apiType("ActionHandle"), "channel", new Class<?>[0]
        ));
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

    /** A missing newly-added API method is an unsupported capability, not a broken provider. */
    private static Optional<Object> invokeOptional(
            Object target,
            Class<?> owner,
            String name,
            Class<?>[] parameterTypes,
            Object... arguments
    ) {
        try {
            ReflectionBindings bindings = bindingsOrNull();
            if (bindings == null) return Optional.empty();
            Method method = bindings.optionalMethod(owner, name, parameterTypes);
            if (method == null) return Optional.empty();
            return Optional.ofNullable(method.invoke(target, arguments));
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw linkage("MayDMZAnimation API call failed: " + owner.getName() + '#' + name, cause);
        } catch (IllegalAccessException error) {
            throw linkage("Incompatible MayDMZAnimation API: " + owner.getName() + '#' + name, error);
        }
    }

    private static LinkageError linkage(String message, Throwable cause) {
        LinkageError error = new LinkageError(message);
        if (cause != null) error.initCause(cause);
        return error;
    }

    static Method findOptionalPublicMethod(
            Class<?> owner, String name, Class<?>[] parameterTypes
    ) {
        try {
            return owner.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException error) {
            return null;
        }
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
        private final Set<MethodKey> missingOptionalMethods = ConcurrentHashMap.newKeySet();

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

        private Method optionalMethod(Class<?> owner, String name, Class<?>[] parameterTypes) {
            MethodKey key = new MethodKey(owner, name, parameterTypes);
            Method cached = methods.get(key);
            if (cached != null) return cached;
            if (missingOptionalMethods.contains(key)) return null;
            Method resolved = findOptionalPublicMethod(owner, name, parameterTypes);
            if (resolved == null) {
                missingOptionalMethods.add(key);
                return null;
            }
            Method raced = methods.putIfAbsent(key, resolved);
            return raced == null ? resolved : raced;
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
