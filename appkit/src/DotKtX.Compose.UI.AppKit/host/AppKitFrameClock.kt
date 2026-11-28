package dotktx.compose.ui.appkit

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.MonotonicFrameClock
import CoreFoundation.DispatchQueue
import Foundation.NSRunLoop
import Foundation.NSRunLoopMode
import Foundation.NSThread
import Foundation.NSTimer
import System.Diagnostics.Stopwatch

/**
 * Frame clock driven by a one-shot `NSTimer` at 60 Hz.
 *
 * The timer exists only while a frame is awaited, so an idle application does not wake up.
 */
internal class AppKitFrameClock : MonotonicFrameClock {
    private var timer: NSTimer? = null
    private val clock = BroadcastFrameClock { requestFrame() }

    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R = clock.withFrameNanos(onFrame)

    /** Called from whichever thread awaits a frame; the timer may only be scheduled on the main thread. */
    private fun requestFrame() {
        if (NSThread.IsMain) {
            schedule()
        } else {
            DispatchQueue.MainQueue.DispatchAsync { schedule() }
        }
    }

    private fun schedule() {
        if (timer != null) return
        val next = NSTimer.CreateTimer(1.0 / 60, false) { _ -> onTimer() }
        timer = next
        NSRunLoop.Main.AddTimer(next, NSRunLoopMode.Common)
    }

    private fun onTimer() {
        timer = null
        clock.sendFrame(nanoTime())
    }

    fun dispose() {
        timer?.Invalidate()
        timer = null
        clock.cancel()
    }
}

private fun nanoTime(): Long {
    val ticks = Stopwatch.GetTimestamp()
    val frequency = Stopwatch.Frequency
    // Split the conversion so ticks * 1e9 cannot overflow.
    return (ticks / frequency) * 1_000_000_000L + (ticks % frequency) * 1_000_000_000L / frequency
}
