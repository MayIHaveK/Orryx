package org.gitee.orryx.compat.maydmzanimation;

import org.junit.jupiter.api.Test;

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
    }

    interface LegacyPlayback {
        boolean stop(Object player, float transition);
    }

    interface CurrentPlayback extends LegacyPlayback {
        boolean stop(long instanceId, float transition);
    }
}
