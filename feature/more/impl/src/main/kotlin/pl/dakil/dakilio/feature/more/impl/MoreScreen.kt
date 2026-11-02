package pl.dakil.dakilio.feature.more.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.dakil.dakilio.core.designsystem.component.SectionHeader
import pl.dakil.dakilio.core.designsystem.component.ShapedIcon
import pl.dakil.dakilio.core.designsystem.component.segmentedItemColors
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbol
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.ui.bottomBarPadding

private data class MoreItem(
    val symbol: DakilioSymbol,
    val title: String,
    val body: String,
    val onClick: (() -> Unit)? = null,
)

@Composable
internal fun MoreScreen(
    viewModel: MoreViewModel,
    onOpenMoods: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenDiaries: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenExtras: () -> Unit,
) {
    val showSampleHint by viewModel.showSampleHint.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = Spacing.maxContentWidth)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = Spacing.gutter)
                .padding(bottom = bottomBarPadding(extra = Spacing.xl)),
        ) {
            Header()
            AnimatedVisibility(showSampleHint, exit = shrinkVertically() + fadeOut()) {
                SampleHint(onManage = onOpenSettings, onDismiss = viewModel::dismissSampleHint)
            }
            Group(
                stringResource(R.string.more_section_customize),
                listOf(
                    MoreItem(DakilioSymbols.Mood, stringResource(R.string.more_moods), stringResource(R.string.more_moods_body), onOpenMoods),
                    MoreItem(DakilioSymbols.Sell, stringResource(R.string.more_tags), stringResource(R.string.more_tags_body), onOpenTags),
                    MoreItem(DakilioSymbols.Book2, stringResource(R.string.more_diaries), stringResource(R.string.more_diaries_body), onOpenDiaries),
                    MoreItem(DakilioSymbols.AutoAwesome, stringResource(R.string.more_extras), stringResource(R.string.more_extras_body), onOpenExtras),
                    MoreItem(DakilioSymbols.Inventory2, stringResource(R.string.more_archive), stringResource(R.string.more_archive_body), onOpenArchive),
                ),
            )
            Group(
                stringResource(R.string.more_section_data),
                listOf(MoreItem(DakilioSymbols.DriveFileMove, stringResource(R.string.more_import_daylio), stringResource(R.string.more_import_daylio_body), onOpenImport)),
            )
            Group(
                stringResource(R.string.more_section_app),
                listOf(MoreItem(DakilioSymbols.Settings, stringResource(R.string.more_settings), stringResource(R.string.more_settings_body), onOpenSettings)),
            )
            Group(
                stringResource(R.string.more_section_about),
                listOf(
                    MoreItem(DakilioSymbols.Shield, stringResource(R.string.more_privacy), stringResource(R.string.more_privacy_body)),
                    MoreItem(DakilioSymbols.Code, stringResource(R.string.more_open_source), stringResource(R.string.more_open_source_body)),
                    MoreItem(DakilioSymbols.Info, stringResource(R.string.more_version), version),
                ),
            )
        }
    }
}

@Composable
private fun Header() {
    Column(
        Modifier.fillMaxWidth().padding(top = Spacing.xxl, bottom = Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShapedIcon(
            symbol = DakilioSymbols.Route,
            polygon = MaterialShapes.Cookie12Sided,
            size = 96.dp,
            iconSize = 48.dp,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            stringResource(R.string.more_app_name),
            style = MaterialTheme.typography.displaySmallEmphasized,
            // This screen has no Surface/Scaffold to provide a content color, so set it explicitly.
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.l),
        )
        Text(
            stringResource(R.string.more_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.s, start = Spacing.l, end = Spacing.l),
        )
    }
}

@Composable
private fun SampleHint(onManage: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s),
    ) {
        Column(Modifier.padding(start = Spacing.xl, end = Spacing.s, top = Spacing.s, bottom = Spacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DakilioIcon(DakilioSymbols.AutoAwesome, contentDescription = null, filled = true)
                Text(
                    stringResource(R.string.more_sample_title),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.weight(1f).padding(start = Spacing.m),
                )
                IconButton(onClick = onDismiss) {
                    DakilioIcon(DakilioSymbols.Close, contentDescription = stringResource(R.string.more_sample_dismiss))
                }
            }
            Text(
                stringResource(R.string.more_sample_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = Spacing.l),
            )
            Button(
                onClick = onManage,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ),
                modifier = Modifier.padding(top = Spacing.m),
            ) { Text(stringResource(R.string.more_sample_manage)) }
        }
    }
}

@Composable
private fun Group(title: String, items: List<MoreItem>) {
    SectionHeader(title)
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        items.forEachIndexed { index, item ->
            val shapes = ListItemDefaults.segmentedShapes(index, items.size)
            val leading: @Composable () -> Unit = {
                ShapedIcon(item.symbol, polygon = MaterialShapes.Cookie9Sided, size = 40.dp)
            }
            val supporting: @Composable () -> Unit = { Text(item.body) }
            if (item.onClick != null) {
                SegmentedListItem(
                    onClick = item.onClick,
                    shapes = shapes,
                    colors = segmentedItemColors(),
                    leadingContent = leading,
                    supportingContent = supporting,
                    trailingContent = { DakilioIcon(DakilioSymbols.ChevronRight, contentDescription = null) },
                ) { Text(item.title) }
            } else {
                SegmentedListItem(shapes = shapes, colors = segmentedItemColors(), leadingContent = leading, supportingContent = supporting) { Text(item.title) }
            }
        }
    }
}
