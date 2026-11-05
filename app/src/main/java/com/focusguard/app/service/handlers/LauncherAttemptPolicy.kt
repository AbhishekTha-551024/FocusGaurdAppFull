package com.focusguard.app.service.handlers

internal class LauncherAttemptPolicy {

    private enum class State {
        IDLE,
        ATTACHING,
        SHOWING
    }

    private var state = State.IDLE
    private var lastAcceptedEventTime = Long.MIN_VALUE

    @Synchronized
    fun begin(eventTime: Long, overlayShowing: Boolean): Boolean {
        if (state == State.SHOWING && !overlayShowing) {
            state = State.IDLE
        }
        if (overlayShowing || state != State.IDLE || eventTime <= lastAcceptedEventTime) {
            return false
        }
        lastAcceptedEventTime = eventTime
        state = State.ATTACHING
        return true
    }

    @Synchronized
    fun onOverlayAttached() {
        if (state == State.ATTACHING) state = State.SHOWING
    }

    @Synchronized
    fun onOverlayDismissed() {
        if (state == State.SHOWING) state = State.IDLE
    }

    @Synchronized
    fun shouldRetry(overlayShowing: Boolean): Boolean =
        state == State.ATTACHING && !overlayShowing

    @Synchronized
    fun onAttachFailed() {
        if (state == State.ATTACHING) state = State.IDLE
    }
}
