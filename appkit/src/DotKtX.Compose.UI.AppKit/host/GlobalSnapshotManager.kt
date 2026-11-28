package dotktx.compose.ui.appkit

import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable

/**
 * Sends apply notifications for writes to the global snapshot.
 *
 * State written outside a composition (for example `count++` in a click handler) goes to the global
 * snapshot, and the `Recomposer` only sees it once `Snapshot.sendApplyNotifications()` runs. Writes
 * from any thread are coalesced into one notification per turn of the UI queue.
 */
internal class GlobalSnapshotManager(private val dispatcher: CoroutineDispatcher) {
    private val scheduled = atomic(false)
    private var handle: ObserverHandle? = null

    fun start() {
        handle = Snapshot.registerGlobalWriteObserver {
            if (scheduled.compareAndSet(false, true)) {
                dispatcher.dispatch(EmptyCoroutineContext, Runnable {
                    scheduled.value = false
                    Snapshot.sendApplyNotifications()
                })
            }
        }
    }

    fun stop() {
        handle?.dispose()
        handle = null
    }
}
