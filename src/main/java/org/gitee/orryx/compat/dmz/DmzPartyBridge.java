package org.gitee.orryx.compat.dmz;

import org.bukkit.entity.Player;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/** Optional DMZ roster; protected releases retain only this private bridge's named entry point. */
public final class DmzPartyBridge {
    private static Method members;
    private static boolean resolved;
    private DmzPartyBridge() {}
    @SuppressWarnings("unchecked")
    public static Set<UUID> members(Player player) {
        if (!resolved) {
            for (String className : new String[]{
                    "com.dragonversez.internal.studio.StudioPartySnapshot",
                    "com.dragonminez.internal.studio.StudioPartySnapshot"
            }) {
                try {
                    Class<?> bridge = Class.forName(className);
                    for (Method method : bridge.getMethods()) {
                        if (method.getName().equals("members") && method.getParameterCount() == 1) {
                            members = method;
                            break;
                        }
                    }
                    if (members != null) break;
                } catch (ClassNotFoundException ignored) { /* Older/non-DMZ servers retain their existing team providers. */ }
            }
            resolved = true;
        }
        if (members==null) return Collections.emptySet();
        try { return (Set<UUID>) members.invoke(null, player.getClass().getMethod("getHandle").invoke(player)); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("DMZ party roster unavailable",e); }
    }
}
