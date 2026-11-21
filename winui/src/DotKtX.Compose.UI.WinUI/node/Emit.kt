package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.Updater
import Microsoft.UI.Xaml.FrameworkElement

/**
 * Places any WinUI element in the composition, for controls that have no wrapper composable.
 *
 * [factory] runs once when the element enters the composition. [update] runs on the UI thread after
 * every recomposition of the caller; state read inside it is not observed, so read state in the
 * calling composable and capture the values.
 *
 * ```
 * WinUIElement(factory = { RatingControl() }, modifier = Modifier.margin(8.0)) { it.Value = rating }
 * ```
 */
@Composable
fun <E : FrameworkElement> WinUIElement(
    factory: () -> E,
    modifier: Modifier = Modifier,
    update: (E) -> Unit = {},
) {
    ComposeNode<ElementNode<E>, WinUIApplier>(
        factory = { ElementNode(factory(), ChildSlot.None("WinUIElement")) },
        update = {
            set(modifier) { applyModifier(it) }
            reconcile { update(element) }
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
    ComposeNode<N, WinUIApplier>(
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
    ComposeNode<N, WinUIApplier>(
        factory = factory,
        update = {
            set(modifier) { applyModifier(it) }
            update()
        },
        content = content,
    )
}
