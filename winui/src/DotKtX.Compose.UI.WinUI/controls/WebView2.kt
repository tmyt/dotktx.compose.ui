package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.currentRecomposeScope
import dotktx.compose.ui.annotations.EmitsOneElement
import Microsoft.Web.WebView2.Core.CoreWebView2
import Microsoft.UI.Xaml.Controls.WebView2 as XamlWebView2

/** A finished navigation of a [WebView2]: the page's address and title, and whether it loaded. */
data class WebNavigation(val source: String, val title: String, val success: Boolean)

/**
 * A web browser control backed by Microsoft Edge (`WebView2`), controlled by [source], the URL of the
 * page it shows.
 *
 * Changing [source] navigates to it. A navigation that the page or the user starts (following a link,
 * a script setting `location`) calls [onSourceChange] with its URL; if the application does not
 * accept it, the web view navigates back to [source]. Redirects belong to the navigation they
 * redirect and are not reported. [onNavigated] is called when a navigation finishes, with the address
 * and title of the page as the browser reports them.
 *
 * The browser starts asynchronously; navigation to [source] begins once it is ready. The WebView2
 * Runtime must be installed (it is part of Windows 11).
 *
 * ```
 * var url by remember { mutableStateOf("https://example.com/") }
 * WebView2(url, onSourceChange = { url = it }, Modifier.height(400.0))
 * ```
 */
@EmitsOneElement
@Composable
fun WebView2(
    source: String,
    onSourceChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onNavigated: (WebNavigation) -> Unit = {},
) {
    val scope = currentRecomposeScope
    Leaf({ WebView2Node(XamlWebView2()) }, modifier) {
        set(scope) { this.scope = it }
        set(onSourceChange) { this.onSourceChange = it }
        set(onNavigated) { this.onNavigated = it }
        reconcile { sync(source) }
    }
}

/**
 * Keeps the web view on the composed source. The node compares the composed URL with the URL it last
 * navigated to or reported, not with `CoreWebView2.Source`: the browser normalizes URLs (escaping,
 * trailing slashes), and comparing with its form would navigate again on every recomposition.
 */
internal class WebView2Node(private val view: XamlWebView2) : ElementNode<XamlWebView2>(view, ChildSlot.None("WebView2")) {
    var scope: RecomposeScope? = null
    var onSourceChange: (String) -> Unit = {}
    var onNavigated: (WebNavigation) -> Unit = {}

    private var ready = false
    private var released = false
    private var composed: String? = null

    /** The URL the web view shows or is navigating to, in the form the node navigated to or reported. */
    private var current: String? = null

    /** The next NavigationStarting belongs to a navigation the node started. */
    private var ownNavigationPending = false

    /** Redirects keep the NavigationId of the navigation they redirect. */
    private var currentNavigationId: ULong? = null

    init {
        onRelease(view.CoreWebView2Initialized.subscribe { _, e ->
            if (e.Exception == null && !released) attach(view.CoreWebView2)
        })
        onRelease(AutoCloseable { released = true })
        // The returned operation is not awaited; CoreWebView2Initialized reports the result.
        view.EnsureCoreWebView2Async()
    }

    /**
     * Subscribes the browser's own events. The control's forwarded NavigationStarting and
     * NavigationCompleted events are not used: they are not raised for every navigation.
     */
    private fun attach(core: CoreWebView2) {
        onRelease(core.NavigationStarting.subscribe { _, e ->
            val id = e.NavigationId
            when {
                ownNavigationPending -> {
                    ownNavigationPending = false
                    currentNavigationId = id
                }
                id == currentNavigationId -> {}
                else -> {
                    currentNavigationId = id
                    val url = e.Uri
                    current = url
                    onSourceChange(url)
                    // If the application rejects the URL, nothing else recomposes; sync then navigates back.
                    scope?.invalidate()
                }
            }
        })
        onRelease(core.NavigationCompleted.subscribe { _, e ->
            onNavigated(WebNavigation(core.Source, core.DocumentTitle, e.IsSuccess))
        })
        ready = true
        val source = composed
        if (source != null && source != current) navigate(source)
    }

    fun sync(source: String) {
        composed = source
        if (ready && source != current) navigate(source)
    }

    private fun navigate(url: String) {
        current = url
        ownNavigationPending = true
        view.CoreWebView2.Navigate(url)
    }
}
