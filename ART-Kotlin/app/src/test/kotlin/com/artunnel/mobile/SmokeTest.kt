package com.artunnel.mobile

import kotlin.test.Test
import kotlin.test.assertEquals

class SmokeTest {
    @Test
    fun packageNameIsStable() {
        assertEquals("com.artunnel.mobile", BuildConfig.APPLICATION_ID)
    }
}
