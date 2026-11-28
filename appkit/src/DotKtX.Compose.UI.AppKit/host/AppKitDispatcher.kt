package dotktx.compose.ui.appkit

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.Runnable
import CoreFoundation.DispatchQueue
import Foundation.NSThread

/** Coroutine dispatcher that runs on the given queue; use the main queue for UI work. */
fun DispatchQueue.asCoroutineDispatcher(): MainCoroutineDispatcher = AppKitDispatcher(this, inlineOnMainThread = false)

private class AppKitDispatcher(
    private val queue: DispatchQueue,
    private val inlineOnMainThread: Boolean,
) : MainCoroutineDispatcher() {
    private var immediateDispatcher: MainCoroutineDispatcher? = null

    override val immediate: MainCoroutineDispatcher
        get() {
            if (inlineOnMainThread) return this
            return immediateDispatcher ?: AppKitDispatcher(queue, inlineOnMainThread = true).also { immediateDispatcher = it }
        }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = !inlineOnMainThread || !NSThread.IsMain

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        queue.DispatchAsync { block.run() }
    }

    override fun toString(): String = if (inlineOnMainThread) "Dispatchers.AppKit.immediate" else "Dispatchers.AppKit"
}
