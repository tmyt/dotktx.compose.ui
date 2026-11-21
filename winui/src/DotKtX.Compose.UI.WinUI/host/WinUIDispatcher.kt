package dotktx.compose.ui.winui

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.Runnable
import Microsoft.UI.Dispatching.DispatcherQueue

/** Coroutine dispatcher that runs on the thread owning this `DispatcherQueue`. */
fun DispatcherQueue.asCoroutineDispatcher(): MainCoroutineDispatcher = WinUIDispatcher(this, inlineOnUIThread = false)

private class WinUIDispatcher(
    private val queue: DispatcherQueue,
    private val inlineOnUIThread: Boolean,
) : MainCoroutineDispatcher() {
    private var immediateDispatcher: MainCoroutineDispatcher? = null

    override val immediate: MainCoroutineDispatcher
        get() {
            if (inlineOnUIThread) return this
            return immediateDispatcher ?: WinUIDispatcher(queue, inlineOnUIThread = true).also { immediateDispatcher = it }
        }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = !inlineOnUIThread || !queue.HasThreadAccess

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (!queue.TryEnqueue { block.run() }) {
            // The queue is shutting down. Cancel the job and resume it elsewhere so it completes
            // with a cancellation instead of staying suspended forever.
            context[Job]?.cancel(CancellationException("The WinUI dispatcher queue has shut down", null))
            Dispatchers.Default.dispatch(context, block)
        }
    }

    override fun toString(): String = if (inlineOnUIThread) "Dispatchers.WinUI.immediate" else "Dispatchers.WinUI"
}
