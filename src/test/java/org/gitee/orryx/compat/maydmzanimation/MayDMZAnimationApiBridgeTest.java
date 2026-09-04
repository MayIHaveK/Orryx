package org.gitee.orryx.compat.maydmzanimation;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class MayDMZAnimationApiBridgeTest {
    @Test
    void newlyAddedMethodIsAnOptionalCapabilityForLegacyProviders() {
        assertNull(MayDMZAnimationApiBridge.findOptionalPublicMethod(
                LegacyPlayback.class, "stop", new Class<?>[]{long.class, float.class}
        ));
        assertNotNull(MayDMZAnimationApiBridge.findOptionalPublicMethod(
                CurrentPlayback.class, "stop", new Class<?>[]{long.class, float.class}
        ));
        assertNull(MayDMZAnimationApiBridge.findOptionalPublicMethod(
                LegacyCombo.class, "compareAndSet",
                new Class<?>[]{Player.class, Optional.class, Optional.class}
        ));
        assertNotNull(MayDMZAnimationApiBridge.findOptionalPublicMethod(
                CurrentCombo.class, "compareAndSet",
                new Class<?>[]{Player.class, Optional.class, Optional.class}
        ));
    }

    interface LegacyPlayback {
        boolean stop(Object player, float transition);
    }

    interface CurrentPlayback extends LegacyPlayback {
        boolean stop(long instanceId, float transition);
    }

    interface LegacyCombo {
    }

    interface CurrentCombo extends LegacyCombo {
        Object compareAndSet(Player player, Optional<String> expected, Optional<String> replacement);
    }
}
