package org.gitee.orryx.compat.maydmzanimation;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.lang.reflect.Method;
import java.util.Optional;

/** Java 8 boundary. The provider owns instance timing; Studio never guesses from a player-global layer. */
public final class StudioAnimationCursor {
    private static volatile Binding cached;
    private StudioAnimationCursor() {}
    public static long[] sample(Player player, long instanceId) {
        try {
            Binding b = binding();
            Object service = ((Optional<?>) b.provider.invoke(null)).orElse(null);
            if (service == null) throw new IllegalStateException("MayDMZAnimation unavailable");
            Object result = b.sample.invoke(service, player.getUniqueId(), instanceId);
            boolean active = "ACTIVE".equals(String.valueOf(b.state.invoke(result)));
            return new long[]{active ? 1 : 0, ((Number) b.start.invoke(result)).longValue(),
                    ((Number) b.duration.invoke(result)).longValue(), ((Number) b.elapsed.invoke(result)).longValue()};
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("动作实例反馈需要新版 MayDMZAnimation", error);
        }
    }
    private static Binding binding() throws ReflectiveOperationException {
        org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("MayDMZAnimation");
        if (plugin == null || !plugin.isEnabled()) throw new IllegalStateException("MayDMZAnimation unavailable");
        Binding b = cached;
        if (b == null || b.plugin != plugin) { b = new Binding(plugin); cached = b; }
        return b;
    }
    private static final class Binding {
        final org.bukkit.plugin.Plugin plugin; final Method provider, sample, state, start, duration, elapsed;
        Binding(org.bukkit.plugin.Plugin plugin) throws ReflectiveOperationException {
            this.plugin=plugin; ClassLoader loader=plugin.getClass().getClassLoader();
            provider=Class.forName("com.mayihavek.dmzanimation.api.MayDMZAnimationApi",false,loader).getMethod("playback");
            sample=Class.forName("com.mayihavek.dmzanimation.api.AnimationPlaybackService",false,loader).getMethod("sample",java.util.UUID.class,long.class);
            Class<?> type=Class.forName("com.mayihavek.dmzanimation.api.PlaybackSample",false,loader);
            state=type.getMethod("state");start=type.getMethod("startTick");duration=type.getMethod("durationTicks");elapsed=type.getMethod("elapsedTicks");
        }
    }
}
