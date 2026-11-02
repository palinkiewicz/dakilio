package pl.dakil.dakilio.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.symbolForKey
import pl.dakil.dakilio.core.model.Tag
import pl.dakil.dakilio.core.ui.color.tagColors

/** Compact, read-only tag label used on entry cards, in the tag's color when it has one. */
@Composable
fun TagPill(tag: Tag, modifier: Modifier = Modifier, compact: Boolean = false) {
    val colors = tagColors(tag.color)
    if (compact) {
        val contentColor = colors?.accent ?: MaterialTheme.colorScheme.onSurfaceVariant
        Box(
            modifier = modifier
                .background(colors?.soft ?: MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            DakilioIcon(symbolForKey(tag.iconKey), contentDescription = tag.label, tint = contentColor, modifier = Modifier.size(16.dp))
        }
        return
    }
    TagPill(
        label = tag.label,
        iconKey = tag.iconKey,
        modifier = modifier,
        containerColor = colors?.soft ?: MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = colors?.accent ?: MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** [TagPill] for a tag that doesn't exist yet (a suggestion). */
@Composable
fun TagPill(
    label: String,
    iconKey: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = modifier
            .background(containerColor, CircleShape)
            .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DakilioIcon(symbolForKey(iconKey), contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun CountPill(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
