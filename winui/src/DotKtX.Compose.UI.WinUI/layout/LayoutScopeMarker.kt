package dotktx.compose.ui.winui

/**
 * Marks the content scopes of layouts whose children take layout-specific modifiers
 * ([GridScope], [CanvasScope], [RelativePanelScope]). Inside another marked scope, the outer
 * layout's modifiers are not available without an explicit receiver, so a child of a nested scoped
 * layout cannot take a modifier meant for its grandparent.
 */
@DslMarker
annotation class LayoutScopeMarker
