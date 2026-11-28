package dotktx.compose.ui.appkit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import dotktx.compose.ui.annotations.AtMostOneElementContent
import AppKit.NSView
import AppKit.NSViewResizingMask
import CoreFoundation.DispatchQueue

/**
 * Hosts Compose content inside an existing AppKit view tree, the counterpart of Android's
 * `ComposeView`. Use it to adopt Compose screen by screen in an app built with Interface Builder or
 * code.
 *
 * The content is composed while the host is in a window and disposed when it is removed from it;
 * remembered state does not survive removing the host from its window. All hosts and [application]
 * windows share one `Recomposer`, so snapshot state written by ordinary AppKit code (an action
 * handler, for example) recomposes them.
 *
 * ```
 * val host = ComposeHost()
 * host.setContent { Counter() }
 * container.AddSubview(host)
 * ```
 */
open class ComposeHost : NSView() {
    private val contentHost = ContentHost()
    private var content: (@Composable () -> Unit)? = null
    private var composition: Composition? = null

    init {
        contentHost.AutoresizingMask = NSViewResizingMask.WidthSizable or NSViewResizingMask.HeightSizable
        contentHost.Frame = Bounds
        AddSubview(contentHost)
    }

    override val IsFlipped: Boolean get() = true

    /** Sets the content, replacing the previous one. It is composed now if the host is in a window. */
    fun setContent(@AtMostOneElementContent content: @Composable () -> Unit) {
        this.content = content
        if (Window != null) compose()
    }

    /** Disposes the composition now instead of when the host leaves its window. */
    fun disposeComposition() {
        composition?.dispose()
        composition = null
    }

    override fun ViewDidMoveToWindow() {
        super.ViewDidMoveToWindow()
        if (Window != null) compose() else disposeComposition()
    }

    override fun Layout() {
        super.Layout()
        contentHost.Frame = Bounds
    }

    private fun compose() {
        // Not `?: return`: DotKt 0.9.34 emits invalid IL for an elvis-return in a Release build.
        val body = content
        if (body == null) return
        val runtime = UIRuntime.forMainThread()
        val target = composition ?: Composition(AppKitApplier(HostRoot(contentHost)), runtime.recomposer).also { composition = it }
        target.setContent {
            CompositionLocalProvider(LocalDispatchQueue provides DispatchQueue.MainQueue) { body() }
        }
    }
}

private class HostRoot(host: ContentHost) : Node(ChildSlot.Single("ComposeHost", host))
