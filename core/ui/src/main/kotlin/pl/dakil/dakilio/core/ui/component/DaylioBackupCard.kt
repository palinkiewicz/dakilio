package pl.dakil.dakilio.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.ui.R

/**
 * A card that sends the user to Daylio's backup screen, from where the backup is shared back to
 * Dakilio. Only show it while Daylio is installed (`rememberDaylioInstalled`).
 */
@Composable
fun DaylioBackupCard(
    title: String,
    body: String,
    onOpenDaylio: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    Surface(shape = RoundedCornerShape(28.dp), color = containerColor, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(Spacing.xl)) {
            DakilioIcon(DakilioSymbols.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = Spacing.l)) {
                Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                FilledTonalButton(onClick = onOpenDaylio, modifier = Modifier.padding(top = Spacing.m)) {
                    Text(stringResource(R.string.ui_daylio_open))
                    DakilioIcon(DakilioSymbols.OpenInNew, contentDescription = null, modifier = Modifier.padding(start = Spacing.s))
                }
            }
        }
    }
}
