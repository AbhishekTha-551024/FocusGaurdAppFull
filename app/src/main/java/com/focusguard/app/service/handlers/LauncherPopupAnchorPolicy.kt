package com.focusguard.app.service.handlers

import kotlin.math.abs

private const val POPUP_EDGE_INSET_PX = 51
private const val POPUP_EDGE_TOLERANCE_PX = 24
private const val POPUP_VERTICAL_GAP_PX = 16
private const val POPUP_VERTICAL_TOLERANCE_PX = 40

internal object LauncherPopupAnchorPolicy {

    fun matches(
        popupLeft: Int,
        popupTop: Int,
        popupRight: Int,
        popupBottom: Int,
        iconLeft: Int,
        iconTop: Int,
        iconRight: Int,
        iconBottom: Int
    ): Boolean {
        val rightAligned = abs(popupRight - (iconRight - POPUP_EDGE_INSET_PX)) <= POPUP_EDGE_TOLERANCE_PX
        val leftAligned = abs(popupLeft - (iconLeft + POPUP_EDGE_INSET_PX)) <= POPUP_EDGE_TOLERANCE_PX
        val opensAbove = abs(popupBottom - (iconTop - POPUP_VERTICAL_GAP_PX)) <= POPUP_VERTICAL_TOLERANCE_PX
        val opensBelow = abs(popupTop - (iconBottom + POPUP_VERTICAL_GAP_PX)) <= POPUP_VERTICAL_TOLERANCE_PX

        return (rightAligned || leftAligned) && (opensAbove || opensBelow)
    }
}
