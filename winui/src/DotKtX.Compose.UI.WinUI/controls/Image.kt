package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import dotktx.compose.ui.annotations.EmitsOneElement
import Microsoft.UI.Xaml.Media.Stretch
import Microsoft.UI.Xaml.Media.Imaging.BitmapImage
import System.Uri
import Microsoft.UI.Xaml.Controls.Image as XamlImage

/**
 * A bitmap (`Image`) loaded from [source], an absolute URI or file path (`https://…`,
 * `C:\\images\\logo.png`). [stretch] decides how it fills the element's size. Set
 * [Modifier.semantics] to describe the image for screen readers.
 */
@EmitsOneElement
@Composable
fun Image(
    source: String,
    modifier: Modifier = Modifier,
    stretch: Stretch = Stretch.Uniform,
) {
    Leaf({ ElementNode(XamlImage(), ChildSlot.None("Image")) }, modifier) {
        set(source) { element.Source = BitmapImage(Uri(it)) }
        set(stretch) { element.Stretch = it }
    }
}
