package dotktx.compose.ui.appkit



/**
 * An ordered, immutable chain of decorations shared by every element: alignment, margin, size,
 * attached properties. Properties specific to one control are parameters of its composable instead.
 *
 * `Modifier` is the empty chain; elements are appended with [then], usually through extension
 * functions (`Modifier.align(Alignment.Center).margin(8.0)`).
 */
interface Modifier {
    fun <R> foldIn(initial: R, operation: (R, Element) -> R): R

    infix fun then(other: Modifier): Modifier =
        if (other === Modifier) this else CombinedModifier(this, other)

    interface Element : Modifier {
        override fun <R> foldIn(initial: R, operation: (R, Element) -> R): R = operation(initial, this)
    }

    companion object : Modifier {
        override fun <R> foldIn(initial: R, operation: (R, Element) -> R): R = initial
        override infix fun then(other: Modifier): Modifier = other
        override fun toString(): String = "Modifier"
    }
}

private class CombinedModifier(private val outer: Modifier, private val inner: Modifier) : Modifier {
    override fun <R> foldIn(initial: R, operation: (R, Modifier.Element) -> R): R =
        inner.foldIn(outer.foldIn(initial, operation), operation)

    override fun equals(other: Any?): Boolean =
        other is CombinedModifier && outer == other.outer && inner == other.inner

    override fun hashCode(): Int = outer.hashCode() + 31 * inner.hashCode()

    override fun toString(): String = "$outer.then($inner)"
}

/**
 * A modifier element that sets something on a node's view or its layout properties and can undo it.
 *
 * Elements with the same [key] replace each other; the last one in the chain wins. Elements must
 * implement `equals` (data classes) so unchanged elements are not reapplied.
 */
interface ElementModifier : Modifier.Element {
    val key: Any
    fun apply(node: ElementNode<*>)

    /** Restores the view's default value (or clears the layout property). */
    fun reset(node: ElementNode<*>)
}

/**
 * Applies [modifier] to the element, resetting what the previously applied chain set and the new
 * chain no longer sets, and skipping elements that did not change.
 */
internal fun ElementNode<*>.applyModifier(modifier: Modifier) {
    val previous = collect(appliedModifier)
    val next = collect(modifier)
    val before = layout
    for ((key, old) in previous) {
        if (!next.containsKey(key)) old.reset(this)
    }
    for ((key, new) in next) {
        if (previous[key] != new) new.apply(this)
    }
    appliedModifier = modifier
    if (layout != before) invalidateLayout()
}

private fun collect(modifier: Modifier): Map<Any, ElementModifier> {
    val result = LinkedHashMap<Any, ElementModifier>()
    modifier.foldIn(Unit) { _, element ->
        if (element is ElementModifier) result[element.key] = element
    }
    return result
}
