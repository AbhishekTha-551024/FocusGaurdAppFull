package com.focusguard.app.core.engine

import com.focusguard.app.core.model.RulesSnapshot
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thread-safe holder for the unified [RulesSnapshot].
 * Provides sub-nanosecond, zero-lock reads via [current] and atomic atomic updates.
 */
@Singleton
class RulesSnapshotHolder @Inject constructor() {
    private val snapshotRef = AtomicReference(RulesSnapshot())

    val current: RulesSnapshot
        get() = snapshotRef.get()

    fun update(transform: (RulesSnapshot) -> RulesSnapshot): RulesSnapshot {
        return snapshotRef.updateAndGet(transform)
    }

    fun set(newSnapshot: RulesSnapshot) {
        snapshotRef.set(newSnapshot)
    }
}
