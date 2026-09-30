package com.focusguard.app.utils

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * ServiceLifecycleOwner
 * =====================
 * Custom [LifecycleOwner] + [ViewModelStoreOwner] + [SavedStateRegistryOwner]
 * for hosting Jetpack Compose inside a Service context (where no Activity is available).
 *
 * Transitions:
 *  - Init: State.INITIALIZED / State.CREATED
 *  - start(): CREATED -> STARTED -> RESUMED
 *  - stop(): RESUMED -> STARTED -> CREATED -> DESTROYED (plus clears ViewModelStore)
 *
 * This ensures ComposeView properly attaches, composes, survives recompositions,
 * and releases resources cleanly when detached.
 */
class ServiceLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private var store = ViewModelStore()

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    init {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun start() {
        if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) {
            // Re-create if previously destroyed
            store = ViewModelStore()
        }
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
