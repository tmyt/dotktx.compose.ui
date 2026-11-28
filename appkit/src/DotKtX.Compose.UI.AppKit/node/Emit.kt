package dotktx.compose.ui.appkit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.Updater
import AppKit.NSView

/**
 * Places any AppKit view in the composition, for controls that have no wrapper composable.
 *
 * [factory] runs once when the view enters the composition. [update] runs on the main thread after
 * every recomposition of the caller; state read inside it is not observed, so read state in the
 * calling composable and capture the values.
 *
 * ```
 * AppKitView(factory = { NSLevelIndicator() }, modifier = Modifier.margin(8.0)) { it.DoubleValue = level }
 * ```
 */
@Composable
fun <V : NSView> AppKitView(
    factory: () -> V,
    modifier: Modifier = Modifier,
    update: (V) -> Unit = {},
) {
    ComposeNode<ElementNode<V>, AppKitApplier>(
        factory = { ElementNode(factory(), ChildSlot.None("AppKitView")) },
        update = {
            set(modifier) { applyModifier(it) }
            reconcile { update(view) }
        },
    )
}

/** Emits a node without children. */
@Composable
internal inline fun <N : ElementNode<*>> Leaf(
    noinline factory: () -> N,
    modifier: Modifier,
    update: @DisallowComposableCalls Updater<N>.() -> Unit,
) {
    ComposeNode<N, AppKitApplier>(
        factory = factory,
        update = {
            set(modifier) { applyModifier(it) }
            update()
        },
    )
}

/** Emits a node whose children come from [content]. */
@Composable
internal inline fun <N : ElementNode<*>> Container(
    noinline factory: () -> N,
    modifier: Modifier,
    update: @DisallowComposableCalls Updater<N>.() -> Unit,
    content: @Composable () -> Unit,
) {
    ComposeNode<N, AppKitApplier>(
        factory = factory,
        update = {
            set(modifier) { applyModifier(it) }
            update()
        },
        content = content,
    )
}
