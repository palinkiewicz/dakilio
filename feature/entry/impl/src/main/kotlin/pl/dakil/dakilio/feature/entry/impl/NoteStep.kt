package pl.dakil.dakilio.feature.entry.impl

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File
import pl.dakil.dakilio.core.designsystem.icon.DakilioIcon
import pl.dakil.dakilio.core.designsystem.icon.DakilioSymbols
import pl.dakil.dakilio.core.designsystem.theme.Spacing
import pl.dakil.dakilio.core.ui.hintOrNull

private const val MAX_PICK = 10
private val ThumbSize = 104.dp
private val ThumbShape = RoundedCornerShape(20.dp)

/** Journal-style body: borderless title and note, plus a strip of photos. */
@Composable
internal fun NoteStep(
    title: TextFieldState,
    note: TextFieldState,
    photos: List<EditorPhoto>,
    importing: Int,
    bodyRequired: Boolean,
    onAddPhotos: (List<android.net.Uri>) -> Unit,
    onRemovePhoto: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(vertical = Spacing.s)) {
        StepHeader(
            stringResource(R.string.entry_note_title),
            hintOrNull(stringResource(R.string.entry_note_body)),
            Modifier.padding(horizontal = Spacing.gutter),
        )
        NoteFields(title, note, photos, importing, bodyRequired, onAddPhotos, onRemovePhoto)
    }
}

/** Title, note and photo strip, without a header or scrolling of its own. */
@Composable
internal fun NoteFields(
    title: TextFieldState,
    note: TextFieldState,
    photos: List<EditorPhoto>,
    importing: Int,
    bodyRequired: Boolean,
    onAddPhotos: (List<android.net.Uri>) -> Unit,
    onRemovePhoto: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK), onAddPhotos)
    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
    )
    Column(modifier) {
        AnimatedVisibility(
            visible = bodyRequired,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Row(
                Modifier
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.xs)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(20.dp))
                    .padding(Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DakilioIcon(DakilioSymbols.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(
                    stringResource(R.string.entry_note_required),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(start = Spacing.m),
                )
            }
        }
        TextField(
            state = title,
            placeholder = { Text(stringResource(R.string.entry_title_hint), style = MaterialTheme.typography.headlineSmall) },
            textStyle = MaterialTheme.typography.headlineSmall,
            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 3),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
        )
        TextField(
            state = note,
            placeholder = { Text(stringResource(R.string.entry_note_hint)) },
            textStyle = MaterialTheme.typography.bodyLarge,
            lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 4),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.gutter),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            modifier = Modifier.padding(top = Spacing.l),
        ) {
            items(photos, key = { it.fileName }) { photo ->
                Box(Modifier.animateItem().size(ThumbSize)) {
                    AsyncImage(
                        model = File(photo.path),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(ThumbShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                    FilledTonalIconButton(
                        onClick = { onRemovePhoto(photo.fileName) },
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
                        ),
                    ) {
                        DakilioIcon(DakilioSymbols.Close, contentDescription = stringResource(R.string.entry_remove_photo), modifier = Modifier.size(18.dp))
                    }
                }
            }
            items(importing, key = { "importing-$it" }) {
                Box(
                    Modifier.animateItem().size(ThumbSize).clip(ThumbShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) { LoadingIndicator() }
            }
            item(key = "add") {
                Surface(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    shape = ThumbShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.animateItem().size(ThumbSize),
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        DakilioIcon(DakilioSymbols.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(28.dp))
                        Text(
                            stringResource(R.string.entry_add_photos),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
            }
        }
    }
}
