package pl.dakil.dakilio.feature.insights.impl

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import java.time.DayOfWeek
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.Mood
import pl.dakil.dakilio.core.ui.rememberFormats

/**
 * The frame of every screen behind an overview card: a collapsing app bar with Back, then
 * Week · Month · Year · All and the period stepper (which sticks under the bar), then the screen's
 * own [content] as list items.
 *
 * @param header `null` while the screen is loading.
 * @param subtitle under the title: what the screen is narrowed to (a diary, a tag's group).
 * @param itemSpacing the gap between the list's items; a list that groups its own items passes zero.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun InsightsDetailScaffold(
    title: String,
    header: PeriodHeaderState?,
    moods: List<Mood>,
    firstDay: DayOfWeek,
    viewModel: InsightsScopedViewModel,
    onBack: () -> Unit,
    subtitle: String? = null,
    itemSpacing: Dp = Spacing.m,
    content: LazyListScope.() -> Unit,
) {
    val formats = rememberFormats()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var picking by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(title) },
                subtitle = { subtitle?.let { Text(it) } },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        DakilioIcon(DakilioSymbols.ArrowBack, contentDescription = stringResource(R.string.insights_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (header == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Scaffold
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.padding(top = padding.calculateTopPadding()).widthIn(max = Spacing.maxContentWidth).fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = padding.calculateBottomPadding() + Spacing.xl,
                    start = Spacing.gutter,
                    end = Spacing.gutter,
                ),
                verticalArrangement = Arrangement.spacedBy(itemSpacing),
            ) {
                item(key = "kind") { PeriodKindToggle(header.period.kind, viewModel::setKind) }
                stickyHeader(key = "period") {
                    PeriodStepper(header, formats, viewModel::step, onPick = if (header.period.hasPicker) ({ picking = true }) else null)
                }
                content()
            }
        }
        if (picking) {
            PeriodPicker(
                state = header,
                moods = moods,
                firstDay = firstDay,
                formats = formats,
                onPick = {
                    picking = false
                    viewModel.setPeriod(it)
                },
                onDismiss = { picking = false },
            )
        }
    }
}
