package dotktx.compose.ui.appkit

import androidx.compose.runtime.Recomposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.setMain
import CoreFoundation.DispatchQueue
import Foundation.NSThread

/**
 * The Compose machinery of the main thread: a coroutine dispatcher on the main queue, a frame
 * clock, the `Recomposer` and apply notifications for global snapshot writes.
 *
 * [application] and every [ComposeHost] share it, so state written anywhere on the main thread
 * recomposes all of them. The runtime is created on first use and lives until [shutdown].
 */
internal class UIRuntime private constructor() {
    val dispatcher: MainCoroutineDispatcher = DispatchQueue.MainQueue.asCoroutineDispatcher()
    private val frameClock = AppKitFrameClock()
    private val job = Job()
    private val scope = CoroutineScope(dispatcher + frameClock + job)
    val recomposer = Recomposer(scope.coroutineContext)
    private val snapshots = GlobalSnapshotManager(dispatcher)

    private fun start() {
        // The main thread's dispatcher becomes Dispatchers.Main, so LaunchedEffect and plain Main code land on it.
        Dispatchers.setMain(dispatcher)
        snapshots.start()
        scope.launch(start = CoroutineStart.UNDISPATCHED) { recomposer.runRecomposeAndApplyChanges() }
    }

    fun shutdown() {
        if (instance === this) instance = null
        recomposer.cancel()
        frameClock.dispose()
        snapshots.stop()
        job.cancel()
    }

    companion object {
        private var instance: UIRuntime? = null

        /** The main thread's runtime, created on first use. Throws when called off the main thread. */
        fun forMainThread(): UIRuntime {
            val existing = instance
            if (existing != null) return existing
            if (!NSThread.IsMain) {
                throw IllegalStateException("Compose for AppKit runs on the main thread")
            }
            val runtime = UIRuntime()
            runtime.start()
            instance = runtime
            return runtime
        }
    }
}
