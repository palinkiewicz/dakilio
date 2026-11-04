package pl.dakil.dakilio.feature.keywords.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.dakil.dakilio.core.designsystem.component.SectionHeader
import pl.dakil.dakilio.core.designsystem.component.rememberHaptics
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.icon.symbolForKey
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.model.KeywordDraft
import pl.dakil.dakilio.core.model.Keywords
import pl.dakil.dakilio.core.model.Tag
import pl.dakil.dakilio.core.model.TagGroup
import pl.dakil.dakilio.core.ui.Hint
import pl.dakil.dakilio.core.ui.component.TagPill

private val DraftsSaver = listSaver<List<KeywordDraft>, Any>(
    save = { drafts -> drafts.flatMap { listOf(it.word, it.approximate) } },
    restore = { flat -> flat.chunked(2).map { (word, approximate) -> KeywordDraft(word as String, approximate as Boolean) } },
)

/**
 * Sets a tag's keywords. New ([existing] `null`): pick a tag without keywords first. Words are added
 * with Enter or a comma; each can match approximately (the start of longer words). An existing tag's
 * sheet also offers to add the tag to older entries that mention the words (only on request).
 */
@Composable
internal fun KeywordSheet(
    existing: TagKeywords?,
    groups: List<TagGroup>,
    taggedIds: Set<Long>,
    onSave: (Tag, List<KeywordDraft>) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    past: PastPreview?,
    onQueryPast: (List<KeywordDraft>) -> Unit,
    onReviewPast: (List<KeywordDraft>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberHaptics()
    var tagId by rememberSaveable { mutableStateOf(existing?.tag?.id) }
    var drafts by rememberSaveable(stateSaver = DraftsSaver) {
        mutableStateOf(existing?.keywords.orEmpty().map { KeywordDraft(it.word, it.approximate) })
    }
    val input = rememberTextFieldState()
    val pickable = groups
        .map { group -> group to group.tags.filter { it.id !in taggedIds } }
        .filter { (_, tags) -> tags.isNotEmpty() }
    val tag = existing?.tag ?: pickable.flatMap { it.second }.firstOrNull { it.id == tagId }

    fun add(raw: String) {
        val word = raw.trim()
        if (!Keywords.isUsable(word)) return
        val key = Keywords.key(word)
        if (drafts.none { Keywords.key(it.word) == key }) {
            haptics.tick()
            drafts = drafts + KeywordDraft(word, approximate = false)
        }
    }

    // A comma or a new line finishes a word, so a list can be typed or pasted in one go.
    LaunchedEffect(input) {
        snapshotFlow { input.text.toString() }.collect { text ->
            if (text.any { it == ',' || it == '\n' }) {
                val parts = text.split(',', '\n')
                parts.dropLast(1).forEach(::add)
                input.setTextAndPlaceCursorAtEnd(parts.last().trimStart())
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl)
                .padding(bottom = Spacing.l),
        ) {
            Text(
                stringResource(if (existing == null) R.string.keywords_sheet_new else R.string.keywords_sheet_edit),
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )

            if (existing != null) {
                TagPill(existing.tag, modifier = Modifier.padding(top = Spacing.m))
            } else {
                SectionHeader(stringResource(R.string.keywords_sheet_tag), Modifier.padding(top = Spacing.l))
                if (pickable.isEmpty()) {
                    Text(
                        stringResource(R.string.keywords_sheet_no_tags),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.s),
                    )
                } else {
                    Column(
                        Modifier
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(top = Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        pickable.forEach { (group, tags) ->
                            Text(
                                group.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                tags.forEach { candidate ->
                                    val selected = candidate.id == tagId
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            haptics.toggle(!selected)
                                            tagId = if (selected) null else candidate.id
                                        },
                                        label = { Text(candidate.label) },
                                        leadingIcon = {
                                            DakilioIcon(
                                                symbolForKey(candidate.iconKey),
                                                contentDescription = null,
                                                filled = selected,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        },
                                        shape = CircleShape,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            SectionHeader(stringResource(R.string.keywords_sheet_words), Modifier.padding(top = Spacing.l))
            OutlinedTextField(
                state = input,
                placeholder = { Text(stringResource(R.string.keywords_sheet_input)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
                onKeyboardAction = {
                    add(input.text.toString())
                    input.clearText()
                },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            add(input.text.toString())
                            input.clearText()
                        },
                        enabled = Keywords.isUsable(input.text.toString()),
                    ) {
                        DakilioIcon(DakilioSymbols.Add, contentDescription = stringResource(R.string.keywords_sheet_add_word))
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
            )

            drafts.forEachIndexed { index, draft ->
                Row(
                    Modifier.fillMaxWidth().padding(top = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    Text(
                        keywordLabel(draft.word, draft.approximate),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = draft.approximate,
                        onClick = {
                            haptics.toggle(!draft.approximate)
                            drafts = drafts.toMutableList().also { it[index] = draft.copy(approximate = !draft.approximate) }
                        },
                        label = { Text(stringResource(R.string.keywords_sheet_approximate)) },
                        leadingIcon = if (draft.approximate) {
                            { DakilioIcon(DakilioSymbols.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else {
                            null
                        },
                        shape = CircleShape,
                    )
                    IconButton(onClick = {
                        haptics.tick()
                        drafts = drafts.toMutableList().also { it.removeAt(index) }
                    }) {
                        DakilioIcon(DakilioSymbols.Close, contentDescription = stringResource(R.string.keywords_sheet_remove_word, draft.word))
                    }
                }
            }
            Hint {
                Text(
                    stringResource(R.string.keywords_sheet_approximate_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.s),
                )
            }

            if (existing != null) {
                LaunchedEffect(drafts) { onQueryPast(drafts) }
                OlderEntries(tag = existing.tag, drafts = drafts, past = past, onReview = { onReviewPast(drafts) })
            }

            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.l),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (existing != null) {
                    TextButton(
                        onClick = onClear,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.keywords_sheet_clear)) }
                    Spacer(Modifier.weight(1f))
                }
                // A word still in the field counts too, so nothing typed gets lost.
                val pending = input.text.toString().trim()
                val all = if (Keywords.isUsable(pending) && drafts.none { Keywords.key(it.word) == Keywords.key(pending) }) {
                    drafts + KeywordDraft(pending, approximate = false)
                } else {
                    drafts
                }
                Button(
                    onClick = {
                        val picked = tag ?: return@Button
                        haptics.confirm()
                        if (existing != null && all.isEmpty()) onClear() else onSave(picked, all)
                    },
                    enabled = tag != null && (all.isNotEmpty() || existing != null),
                ) {
                    Text(stringResource(R.string.keywords_sheet_save))
                }
            }
        }
    }
}

/**
 * Older entries that mention [drafts] but don't carry [tag].
 * Counts the words as edited, not just the saved ones; "Review" opens the list of those entries to
 * pick which get the tag.
 */
@Composable
private fun OlderEntries(
    tag: Tag,
    drafts: List<KeywordDraft>,
    past: PastPreview?,
    onReview: () -> Unit,
) {
    if (drafts.isEmpty()) return
    val haptics = rememberHaptics()
    val ready = past?.takeIf { it.query == PastQuery(tag.id, drafts) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    SectionHeader(stringResource(R.string.keywords_sheet_past), Modifier.padding(top = Spacing.l))
    when {
        ready == null -> Text(
            stringResource(R.string.keywords_sheet_past_checking),
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
            modifier = Modifier.padding(top = Spacing.s),
        )
        ready.postIds.isNotEmpty() -> Column(Modifier.padding(top = Spacing.s)) {
            Text(
                pluralStringResource(R.plurals.keywords_sheet_past_found, ready.postIds.size, ready.postIds.size, tag.label),
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
            )
            FilledTonalButton(
                onClick = {
                    haptics.tick()
                    onReview()
                },
                modifier = Modifier.padding(top = Spacing.s),
            ) {
                DakilioIcon(DakilioSymbols.History, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.keywords_sheet_past_review), modifier = Modifier.padding(start = Spacing.s))
            }
        }
        else -> Text(
            stringResource(R.string.keywords_sheet_past_none, tag.label),
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
            modifier = Modifier.padding(top = Spacing.s),
        )
    }
}
