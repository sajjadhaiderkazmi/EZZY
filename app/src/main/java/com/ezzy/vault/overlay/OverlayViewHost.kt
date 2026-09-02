package com.ezzy.vault.overlay

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * A window added through WindowManager has no Activity behind it, so Compose has nowhere to
 * find its lifecycle, view-model store or saved-state registry — and crashes on first
 * composition. This supplies all three for views that live in the overlay.
 */
class OverlayViewHost(private val context: Context) : LifecycleOwner, ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private var started = false

    fun start() {
        if (started) return
        started = true
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        if (!started) return
        started = false
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }

    /** Builds a ComposeView already wired to this host. */
    fun composeView(content: @Composable () -> Unit): ComposeView =
        ComposeView(context).also { view ->
            view.attachTo(this)
            view.setContent(content)
        }
}

/**
 * A focusable overlay window receives the hardware/gesture Back key, but there is no Activity
 * behind it and therefore no back dispatcher for Compose's BackHandler to hook into. This root
 * catches the key itself so Back closes the bar instead of doing nothing.
 */
class OverlayRootView(
    context: Context,
    private val onBack: () -> Unit,
) : FrameLayout(context) {

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            onBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

/** Attaches the three view-tree owners Compose looks for when it composes inside a window. */
fun View.attachTo(host: OverlayViewHost) {
    setViewTreeLifecycleOwner(host)
    setViewTreeViewModelStoreOwner(host)
    setViewTreeSavedStateRegistryOwner(host)
}
