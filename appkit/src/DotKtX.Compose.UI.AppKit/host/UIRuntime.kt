package dotktx.compose.ui.appkit

import androidx.compose.runtime.Recomposer
import kotlinx.coroutines.MainCoroutineDispatcher

/**
 * The Compose machinery of the main thread: a coroutine dispatcher on the main queue, a frame clock,
 * the `Recomposer` and apply notifications for global snapshot writes.
 *
 * [application] and every [ComposeHost] share it, so state written anywhere on the main thread
 * recomposes all of them. The runtime is created on first use and lives until [shutdown].
 */
internal class UIRuntime private constructor() {
    val dispatcher: MainCoroutineDispatcher get() = TODO("U1")
    val recomposer: Recomposer get() = TODO("U1")

    fun shutdown() {
        TODO("U1")
    }

    companion object {
        /** The main thread's runtime, created on first use. Throws when called off the main thread. */
        fun forMainThread(): UIRuntime = TODO("U1")
    }
}
