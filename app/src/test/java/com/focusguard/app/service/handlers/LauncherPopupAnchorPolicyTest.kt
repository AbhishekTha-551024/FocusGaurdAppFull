package com.focusguard.app.service.handlers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherPopupAnchorPolicyTest {

    @Test
    fun `matches measured Vivo popup aligned to icon left edge`() {
        assertTrue(
            LauncherPopupAnchorPolicy.matches(
                popupLeft = 85,
                popupTop = 649,
                popupRight = 696,
                popupBottom = 978,
                iconLeft = 33,
                iconTop = 995,
                iconRight = 286,
                iconBottom = 1276
            )
        )
    }

    @Test
    fun `preserves the previously measured right-edge alignment`() {
        assertTrue(
            LauncherPopupAnchorPolicy.matches(
                popupLeft = 0,
                popupTop = 649,
                popupRight = 235,
                popupBottom = 978,
                iconLeft = 33,
                iconTop = 995,
                iconRight = 286,
                iconBottom = 1276
            )
        )
    }

    @Test
    fun `rejects popup without measured horizontal alignment`() {
        assertFalse(
            LauncherPopupAnchorPolicy.matches(
                popupLeft = 500,
                popupTop = 649,
                popupRight = 900,
                popupBottom = 978,
                iconLeft = 33,
                iconTop = 995,
                iconRight = 286,
                iconBottom = 1276
            )
        )
    }

    @Test
    fun `rejects popup without measured vertical alignment`() {
        assertFalse(
            LauncherPopupAnchorPolicy.matches(
                popupLeft = 85,
                popupTop = 500,
                popupRight = 696,
                popupBottom = 800,
                iconLeft = 33,
                iconTop = 995,
                iconRight = 286,
                iconBottom = 1276
            )
        )
    }
}
