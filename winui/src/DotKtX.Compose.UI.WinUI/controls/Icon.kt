package dotktx.compose.ui.winui

import Microsoft.UI.Xaml.Controls.BitmapIcon
import Microsoft.UI.Xaml.Controls.BitmapIconSource
import Microsoft.UI.Xaml.Controls.FontIcon
import Microsoft.UI.Xaml.Controls.FontIconSource
import Microsoft.UI.Xaml.Controls.IconElement
import Microsoft.UI.Xaml.Controls.IconSource
import Microsoft.UI.Xaml.Controls.IconSourceElement
import Microsoft.UI.Xaml.Controls.ImageIcon
import Microsoft.UI.Xaml.Controls.ImageIconSource
import Microsoft.UI.Xaml.Controls.SymbolIcon
import Microsoft.UI.Xaml.Controls.SymbolIconSource
import Microsoft.UI.Xaml.Media.FontFamily
import Microsoft.UI.Xaml.Media.Imaging.BitmapImage
import System.Uri
import Microsoft.UI.Xaml.Controls.Symbol as XamlSymbol

/**
 * An icon for the controls that show one (`TitleBar`, menu and command bar entries, navigation items,
 * …). It is an immutable value: an equal icon is not applied again on recomposition, and one icon can
 * be used by any number of controls.
 *
 * ```
 * MenuEntry.Item("Save", Icon.Symbol(Symbol.Save)) { save() }
 * TitleBar("Notes", icon = Icon.Glyph(""))
 * CommandBarEntry.Button("Sync", Icon.Bitmap("ms-appx:///Assets/Sync.png")) { sync() }
 * ```
 */
sealed class Icon {
    /** A glyph of the system symbol font by name (`SymbolIcon`). */
    data class Symbol(val symbol: XamlSymbol) : Icon()

    /**
     * A glyph of a font (`FontIcon`), for example a Segoe Fluent Icons code point such as `""`.
     * [fontFamily] is a font family name or URI; `null` keeps the theme's symbol font.
     */
    data class Glyph(val glyph: String, val fontFamily: String? = null) : Icon()

    /**
     * A bitmap from [uri] (`BitmapIcon`). With [monochrome] the image is drawn as a mask in the
     * foreground color, like the other icons; otherwise in its own colors.
     */
    data class Bitmap(val uri: String, val monochrome: Boolean = true) : Icon()

    /** A bitmap image from [uri] (`ImageIcon`), drawn in its own colors. */
    data class Image(val uri: String) : Icon()

    /**
     * Any WinUI icon source, for icons the other cases do not cover (`PathIconSource`,
     * `AnimatedIconSource`, a `FontIconSource` with more settings, …). Icons are equal when they hold
     * the same source object, so create the source once (for example with `remember`).
     */
    data class Source(val source: IconSource) : Icon()
}

/** The icon as an `IconSource`, for properties such as `TitleBar.IconSource`. */
internal fun Icon.toIconSource(): IconSource = when (this) {
    is Icon.Symbol -> SymbolIconSource().also { it.Symbol = symbol }
    is Icon.Glyph -> FontIconSource().also { source ->
        source.Glyph = glyph
        if (fontFamily != null) source.FontFamily = FontFamily(fontFamily)
    }
    is Icon.Bitmap -> BitmapIconSource().also { source ->
        source.UriSource = Uri(uri)
        source.ShowAsMonochrome = monochrome
    }
    is Icon.Image -> ImageIconSource().also { it.ImageSource = BitmapImage(Uri(uri)) }
    is Icon.Source -> source
}

/**
 * The icon as a new `IconElement`, for properties such as `MenuFlyoutItem.Icon`. An element can be in
 * the visual tree only once, so every call creates a new one.
 */
internal fun Icon.toIconElement(): IconElement = when (this) {
    is Icon.Symbol -> SymbolIcon(symbol)
    is Icon.Glyph -> FontIcon().also { icon ->
        icon.Glyph = glyph
        if (fontFamily != null) icon.FontFamily = FontFamily(fontFamily)
    }
    is Icon.Bitmap -> BitmapIcon().also { icon ->
        icon.UriSource = Uri(uri)
        icon.ShowAsMonochrome = monochrome
    }
    is Icon.Image -> ImageIcon().also { it.Source = BitmapImage(Uri(uri)) }
    is Icon.Source -> IconSourceElement().also { it.IconSource = source }
}
