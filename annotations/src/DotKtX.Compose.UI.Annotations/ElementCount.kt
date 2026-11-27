package dotktx.compose.ui.annotations

/**
 * The annotated composable always emits exactly one element into the content it is called from.
 * Composables that emit nothing there (effects, or windows and popups that compose into their own
 * tree), that emit conditionally, or that emit several elements are left unmarked.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
annotation class EmitsOneElement

/**
 * The annotated content parameter fills a slot that holds at most one element, such as the content of
 * a window or a button. The analyzers report content that is certain to emit a second element, which
 * fails at run time; several elements belong in a layout container.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
annotation class AtMostOneElementContent
