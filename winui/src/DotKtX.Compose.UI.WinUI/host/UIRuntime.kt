package dotktx.compose.ui.winui

import androidx.compose.runtime.Recomposer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.setMain
import Microsoft.UI.Dispatching.DispatcherQueue
import System.Environment
import System.Threading.Thread

/**
 * The Compose machinery of one UI thread: a coroutine dispatcher on its `DispatcherQueue`, a frame
 * clock, the `Recomposer` and apply notifications for global snapshot writes.
 *
 * [application] and every [ComposeHost] on a thread share its runtime, so state written anywhere on
 * the thread recomposes all of them. The runtime is created on first use and lives until
 * [shutdown]. Only use it from UI threads.
 */
internal class UIRuntime private constructor(val queue: DispatcherQueue) {
    val dispatcher = queue.asCoroutineDispatcher()
    private val frameClock = WinUIFrameClock(queue)
    private val job = Job()
    private val scope = CoroutineScope(dispatcher + frameClock + job)
    val recomposer = Recomposer(scope.coroutineContext)
    private val snapshots = GlobalSnapshotManager(dispatcher)

    private fun start() {
        // The first UI thread becomes Dispatchers.Main, so LaunchedEffect and plain Main code land on it.
        if (runtimes.isEmpty()) Dispatchers.setMain(dispatcher)
        snapshots.start()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                recomposer.runRecomposeAndApplyChanges()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                compositionFailed(e)
            }
        }
    }

    /**
     * An exception from recomposition or from applying changes ends the recomposer, so no window of
     * this thread would update again. The process ends like one with an unhandled exception: the
     * message and stack trace go to the error output and the Windows event log.
     */
    private fun compositionFailed(e: Throwable): Nothing {
        Environment.FailFast("Compose for WinUI: the composition failed.\n" + e.stackTraceToString())
        throw e
    }

    fun shutdown() {
        runtimes.remove(threadId())
        recomposer.cancel()
        frameClock.dispose()
        snapshots.stop()
        job.cancel()
    }

    companion object {
        private val runtimes = HashMap<Int, UIRuntime>()

        private fun threadId(): Int = Thread.CurrentThread.ManagedThreadId

        /** The runtime of the calling UI thread, created on first use. */
        fun forCurrentThread(): UIRuntime {
            runtimes[threadId()]?.let { return it }
            val queue = DispatcherQueue.GetForCurrentThread()
                ?: throw IllegalStateException("Compose for WinUI runs on a UI thread (a thread with a DispatcherQueue)")
            val runtime = UIRuntime(queue)
            runtime.start()
            runtimes[threadId()] = runtime
            return runtime
        }
    }
}
