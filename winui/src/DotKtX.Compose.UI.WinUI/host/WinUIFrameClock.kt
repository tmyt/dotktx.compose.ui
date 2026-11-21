package dotktx.compose.ui.winui

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.MonotonicFrameClock
import Microsoft.UI.Dispatching.DispatcherQueue
import Microsoft.UI.Xaml.Media.CompositionTarget
import System.Diagnostics.Stopwatch

/**
 * Frame clock driven by `CompositionTarget.Rendering`.
 *
 * `Rendering` is subscribed only while a frame is awaited and released after each frame: while any
 * handler is attached, WinUI renders on every vsync, which would keep an idle window busy.
 */
internal class WinUIFrameClock(private val queue: DispatcherQueue) : MonotonicFrameClock {
    private var rendering: AutoCloseable? = null
    private val clock = BroadcastFrameClock { requestFrame() }

    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R = clock.withFrameNanos(onFrame)

    /** Called from whichever thread awaits a frame; `Rendering` may only be touched on the UI thread. */
    private fun requestFrame() {
        if (queue.HasThreadAccess) {
            subscribe()
        } else {
            queue.TryEnqueue { subscribe() }
        }
    }

    private fun subscribe() {
        if (rendering == null) {
            rendering = CompositionTarget.Rendering.subscribe { _, _ -> onRendering() }
        }
    }

    private fun onRendering() {
        rendering?.close()
        rendering = null
        clock.sendFrame(nanoTime())
    }

    fun dispose() {
        rendering?.close()
        rendering = null
        clock.cancel()
    }
}

private fun nanoTime(): Long {
    val ticks = Stopwatch.GetTimestamp()
    val frequency = Stopwatch.Frequency
    // Split the conversion so ticks * 1e9 cannot overflow.
    return (ticks / frequency) * 1_000_000_000L + (ticks % frequency) * 1_000_000_000L / frequency
}
