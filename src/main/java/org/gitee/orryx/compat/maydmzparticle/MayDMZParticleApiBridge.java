package org.gitee.orryx.compat.maydmzparticle;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Java 8-compatible linkage island around MayDMZParticle's Java 17 public API. */
public final class MayDMZParticleApiBridge {
    private static final String API_PACKAGE = "com.mayihavek.dmzparticle.api.";
    private static volatile ReflectionBindings cachedBindings;

    private MayDMZParticleApiBridge() {
    }

    public static boolean available() {
        return service() != null;
    }

    public static boolean effectExists(String effectId) {
        Object service = service();
        return service != null && (Boolean) invoke(
                service, apiType("ParticlePlaybackService"), "effectExists",
                new Class<?>[]{String.class}, effectId);
    }

    public static Set<?> effectIds() {
        Object service = service();
        return service == null ? java.util.Collections.emptySet() : (Set<?>) invoke(
                service, apiType("ParticlePlaybackService"), "effectIds", new Class<?>[0]);
    }

    public static int activeCount() {
        Object service = service();
        return service == null ? 0 : ((Number) invoke(
                service, apiType("ParticlePlaybackService"), "activeCount", new Class<?>[0]
        )).intValue();
    }

    public static String play(
            Player player, String effectId, String bone, int durationTicks,
            float x, float y, float z,
            float pitch, float yaw, float roll,
            float scaleX, float scaleY, float scaleZ
    ) {
        Object service = service();
        if (service == null) return "";
        Object transform = transform(x, y, z, pitch, yaw, roll, scaleX, scaleY, scaleZ);
        Object handle = invoke(service, apiType("ParticlePlaybackService"), "play", new Class<?>[]{
                String.class, Entity.class, String.class, apiType("ParticleTransform"), int.class
        }, effectId, player, bone == null ? "" : bone, transform, durationTicks);
        return handleValue(handle);
    }

    public static String playAt(
            Location location, String effectId, int durationTicks,
            float x, float y, float z,
            float pitch, float yaw, float roll,
            float scaleX, float scaleY, float scaleZ
    ) {
        Object service = service();
        if (service == null) return "";
        Object transform = transform(x, y, z, pitch, yaw, roll, scaleX, scaleY, scaleZ);
        Object handle = invoke(service, apiType("ParticlePlaybackService"), "playAt", new Class<?>[]{
                String.class, Location.class, apiType("ParticleTransform"), int.class
        }, effectId, location, transform, durationTicks);
        return handleValue(handle);
    }

    public static boolean stop(String handle) {
        Object service = service();
        if (service == null) return false;
        Object playbackHandle = construct(apiType("PlaybackHandle"),
                new Class<?>[]{UUID.class}, UUID.fromString(handle));
        return (Boolean) invoke(service, apiType("ParticlePlaybackService"), "stop",
                new Class<?>[]{apiType("PlaybackHandle")}, playbackHandle);
    }

    public static int stopEntity(Entity entity) {
        Object service = service();
        return service == null ? 0 : ((Number) invoke(
                service, apiType("ParticlePlaybackService"), "stopAll",
                new Class<?>[]{Entity.class}, entity)).intValue();
    }

    public static int stopEverywhere() {
        Object service = service();
        return service == null ? 0 : ((Number) invoke(
                service, apiType("ParticlePlaybackService"), "stopEverywhere", new Class<?>[0]
        )).intValue();
    }

    private static Object transform(
            float x, float y, float z,
            float pitch, float yaw, float roll,
            float scaleX, float scaleY, float scaleZ
    ) {
        Class<?> type = apiType("ParticleTransform");
        return invokeStatic(type, "fromEulerDegrees", new Class<?>[]{
                float.class, float.class, float.class,
                float.class, float.class, float.class,
                float.class, float.class, float.class
        }, x, y, z, pitch, yaw, roll, scaleX, scaleY, scaleZ);
    }

    private static String handleValue(Object handle) {
        return String.valueOf(invoke(
                handle, apiType("PlaybackHandle"), "value", new Class<?>[0]
        ));
    }

    private static Object service() {
        ReflectionBindings bindings = bindingsOrNull();
        if (bindings == null) return null;
        Optional<?> service = (Optional<?>) invokeStatic(bindings.apiType("MayDMZParticleApi"), "playback");
        return service.orElse(null);
    }

    private static Class<?> apiType(String simpleName) {
        ReflectionBindings bindings = bindingsOrNull();
        if (bindings == null) throw linkage("MayDMZParticle plugin class loader is unavailable", null);
        return bindings.apiType(simpleName);
    }

    private static Object construct(Class<?> type, Class<?>[] parameterTypes, Object... arguments) {
        try {
            return bindings().constructor(type, parameterTypes).newInstance(arguments);
        } catch (InvocationTargetException error) {
            throw propagate("MayDMZParticle API constructor failed: " + type.getName(), error.getCause());
        } catch (ReflectiveOperationException error) {
            throw linkage("Incompatible MayDMZParticle API constructor: " + type.getName(), error);
        }
    }

    private static Object invokeStatic(Class<?> owner, String name, Object... arguments) {
        return invoke(null, owner, name, new Class<?>[0], arguments);
    }

    private static Object invokeStatic(Class<?> owner, String name, Class<?>[] parameterTypes, Object... arguments) {
        return invoke(null, owner, name, parameterTypes, arguments);
    }

    private static Object invoke(Object target, Class<?> owner, String name,
                                 Class<?>[] parameterTypes, Object... arguments) {
        try {
            return bindings().method(owner, name, parameterTypes).invoke(target, arguments);
        } catch (InvocationTargetException error) {
            throw propagate("MayDMZParticle API call failed: " + owner.getName() + '#' + name, error.getCause());
        } catch (ReflectiveOperationException error) {
            throw linkage("Incompatible MayDMZParticle API: " + owner.getName() + '#' + name, error);
        }
    }

    private static RuntimeException propagate(String message, Throwable cause) {
        if (cause instanceof RuntimeException) return (RuntimeException) cause;
        if (cause instanceof Error) throw (Error) cause;
        return new IllegalStateException(message, cause);
    }

    private static LinkageError linkage(String message, Throwable cause) {
        LinkageError error = new LinkageError(message);
        if (cause != null) error.initCause(cause);
        return error;
    }

    private static ReflectionBindings bindings() {
        ReflectionBindings bindings = bindingsOrNull();
        if (bindings == null) throw linkage("MayDMZParticle plugin class loader is unavailable", null);
        return bindings;
    }

    private static ReflectionBindings bindingsOrNull() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("MayDMZParticle");
        if (plugin == null || !plugin.isEnabled()) return null;
        ReflectionBindings current = cachedBindings;
        if (current != null && current.plugin == plugin) return current;
        synchronized (MayDMZParticleApiBridge.class) {
            current = cachedBindings;
            if (current == null || current.plugin != plugin) {
                current = new ReflectionBindings(plugin);
                cachedBindings = current;
            }
            return current;
        }
    }

    private static final class ReflectionBindings {
        private final Plugin plugin;
        private final ClassLoader classLoader;
        private final Map<String, Class<?>> apiTypes = new ConcurrentHashMap<>();
        private final Map<MethodKey, Method> methods = new ConcurrentHashMap<>();
        private final Map<MethodKey, Constructor<?>> constructors = new ConcurrentHashMap<>();

        private ReflectionBindings(Plugin plugin) {
            this.plugin = plugin;
            this.classLoader = plugin.getClass().getClassLoader();
        }

        private Class<?> apiType(String simpleName) {
            Class<?> current = apiTypes.get(simpleName);
            if (current != null) return current;
            try {
                Class<?> loaded = Class.forName(API_PACKAGE + simpleName, false, classLoader);
                apiTypes.put(simpleName, loaded);
                return loaded;
            } catch (ClassNotFoundException error) {
                throw linkage("Missing MayDMZParticle API type " + simpleName, error);
            }
        }

        private Method method(Class<?> owner, String name, Class<?>[] parameterTypes)
                throws NoSuchMethodException {
            MethodKey key = new MethodKey(owner, name, parameterTypes);
            Method current = methods.get(key);
            if (current != null) return current;
            Method resolved = owner.getMethod(name, parameterTypes);
            methods.put(key, resolved);
            return resolved;
        }

        private Constructor<?> constructor(Class<?> owner, Class<?>[] parameterTypes)
                throws NoSuchMethodException {
            MethodKey key = new MethodKey(owner, "<init>", parameterTypes);
            Constructor<?> current = constructors.get(key);
            if (current != null) return current;
            Constructor<?> resolved = owner.getConstructor(parameterTypes);
            constructors.put(key, resolved);
            return resolved;
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

        @Override public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof MethodKey)) return false;
            MethodKey other = (MethodKey) value;
            return owner.equals(other.owner) && name.equals(other.name)
                    && parameterTypes.equals(other.parameterTypes);
        }

        @Override public int hashCode() {
            int result = owner.hashCode();
            result = 31 * result + name.hashCode();
            return 31 * result + parameterTypes.hashCode();
        }
    }
}
