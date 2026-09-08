package com.example

import com.example.model.PlatformType
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonEngineRoutingTest {
    @Test
    fun supportedPlatformsAreDeclared() {
        assertTrue(PlatformType.values().toSet().containsAll(setOf(
            PlatformType.REDGIFS,
            PlatformType.XXXFOLLOW,
            PlatformType.FACEBOOK,
            PlatformType.TWITTER,
            PlatformType.YOUTUBE
        )))
    }
}
