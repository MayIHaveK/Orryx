package org.gitee.orryx.compat.dmz;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import java.lang.reflect.Method;

/** Optional native DMZ combat adapter. No Forge types enter Orryx's Java 8 signatures. */
public final class StudioDamageBridge {
    private static volatile Binding binding;
    private static volatile Method attributes;
    private StudioDamageBridge() {}
    public static boolean available() { try { binding(); return true; } catch (RuntimeException e) { return false; } }
    @SuppressWarnings("unchecked")
    public static java.util.Map<String, Double> attributes(Player caster) {
        try {
            Method method = attributes;
            if (method == null) {
                Binding b = binding();
                Class<?> snapshotClass = loadClass(b.basePackage + ".internal.studio.StudioAttributeSnapshot");
                attributes = method = snapshotClass.getMethod("capture", b.hurt.getParameterTypes()[0]);
            }
            return (java.util.Map<String, Double>) method.invoke(null, handle(caster));
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IllegalStateException("DMZ 属性占位符不可用，请更新服务端并等待玩家数据加载", e);
        }
    }
    public static boolean hostile(Player caster, LivingEntity target) {
        try { return (Boolean) binding().hostile.invoke(null, handle(caster), handle(target)); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("DMZ target validation failed", e); }
    }
    public static boolean hurt(Player caster, LivingEntity target, double amount, String kind) {
        if (!Double.isFinite(amount) || amount < 0) throw new IllegalArgumentException("Invalid damage");
        try {
            Binding b = binding(); Object selected = null;
            for (Object constant : b.kind.getEnumConstants()) if (constant.toString().equals(kind)) selected = constant;
            if (selected == null) throw new IllegalArgumentException("Unknown DMZ damage kind");
            return (Boolean) b.hurt.invoke(null, handle(caster), handle(target), amount, false, selected);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("DMZ damage failed", e); }
    }
    public static double health(LivingEntity target) {
        try { return ((Number) binding().health.invoke(null, handle(target))).doubleValue(); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("DMZ exact health unavailable", e); }
    }
    private static Object handle(LivingEntity target) throws ReflectiveOperationException { return target.getClass().getMethod("getHandle").invoke(target); }
    private static Class<?> loadClass(String name) throws ClassNotFoundException {
        return Class.forName(name);
    }
    private static Binding binding() {
        Binding value = binding;
        if (value != null) return value;
        ReflectiveOperationException last = null;
        for (String base : new String[]{"com.dragonversez", "com.dragonminez"}) {
            try {
                Class<?> api = loadClass(base + ".api.ExternalSkillRuntimeAPI");
                Method hurt = null;
                for (Method m : api.getMethods()) if (m.getName().equals("hurtAtContact") && m.getParameterCount() == 5) hurt = m;
                boolean contact = hurt != null;
                if (!contact) for (Method m : api.getMethods()) if (m.getName().equals("hurt") && m.getParameterCount() == 5) hurt = m;
                if (hurt == null) throw new NoSuchMethodException("DMZ precise hurt");
                Class<?> attacker = hurt.getParameterTypes()[0], target = hurt.getParameterTypes()[1];
                value = new Binding(base, hurt, loadClass(base + ".api.CombatTargetingAPI").getMethod(contact ? "canHitAtContact" : "isHostileTarget", attacker, target),
                        loadClass(base + ".api.attributes.ExternalCombatantAPI").getMethod("getHealth", target), hurt.getParameterTypes()[4]);
                binding = value; return value;
            } catch (ClassNotFoundException e) {
                last = e;
            } catch (ReflectiveOperationException | LinkageError e) {
                throw new IllegalStateException("当前服务端缺少兼容的 DMZ 战斗 API", e);
            }
        }
        throw new IllegalStateException("当前服务端缺少兼容的 DMZ 战斗 API", last);
    }
    private static final class Binding {
        final String basePackage; final Method hurt, hostile, health; final Class<?> kind;
        Binding(String basePackage, Method hurt, Method hostile, Method health, Class<?> kind) {
            this.basePackage=basePackage;this.hurt=hurt;this.hostile=hostile;this.health=health;this.kind=kind;
        }
    }
}
