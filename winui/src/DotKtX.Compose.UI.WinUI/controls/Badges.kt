package dotktx.compose.ui.winui

import androidx.compose.runtime.Composable
import Microsoft.UI.Xaml.Controls.Symbol
import Microsoft.UI.Xaml.Controls.SymbolIconSource
import Microsoft.UI.Xaml.Controls.InfoBadge as XamlInfoBadge
import Microsoft.UI.Xaml.Controls.PersonPicture as XamlPersonPicture

/**
 * A small status marker (`InfoBadge`), usually placed over the corner of another element.
 *
 * With [value] it shows the number. Without it, it shows [icon], or a plain dot when there is no icon.
 *
 * ```
 * InfoBadge(value = unread.takeIf { it > 0 })
 * InfoBadge(icon = Symbol.Important)
 * ```
 */
@Composable
fun InfoBadge(
    modifier: Modifier = Modifier,
    value: Int? = null,
    icon: Symbol? = null,
) {
    Leaf({ ElementNode(XamlInfoBadge(), ChildSlot.None("InfoBadge")) }, modifier) {
        set(value) { if (it == null) element.ClearValue(XamlInfoBadge.ValueProperty) else element.Value = it }
        set(icon) {
            if (it == null) {
                element.ClearValue(XamlInfoBadge.IconSourceProperty)
            } else {
                val source = SymbolIconSource()
                source.Symbol = it
                element.IconSource = source
            }
        }
    }
}

/**
 * A round avatar for a person (`PersonPicture`). It shows [initials], or initials derived from
 * [displayName] when [initials] is empty. A [badgeNumber] above zero is shown in a badge.
 *
 * ```
 * PersonPicture(displayName = "Ann Lee", badgeNumber = missedCalls)
 * ```
 */
@Composable
fun PersonPicture(
    displayName: String,
    modifier: Modifier = Modifier,
    initials: String = "",
    badgeNumber: Int = 0,
) {
    Leaf({ ElementNode(XamlPersonPicture(), ChildSlot.None("PersonPicture")) }, modifier) {
        set(displayName) { element.DisplayName = it }
        set(initials) { if (it.isEmpty()) element.ClearValue(XamlPersonPicture.InitialsProperty) else element.Initials = it }
        set(badgeNumber) { element.BadgeNumber = it }
    }
}
