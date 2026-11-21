package es.msaezo.iptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/**
 * A text setting for the TV: it shows the value and only opens the keyboard when selected,
 * and then edits in a box at the top of the screen so the keyboard never covers it.
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
) {
    var editing by remember { mutableStateOf(false) }
    Column(
        modifier
            .tvClickable(Square, 1.01f) { editing = true }
            .border(1.dp, Color(0x55FFFFFF), Square)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        if (label != null) {
            ProvideTextStyle(MaterialTheme.typography.labelSmall.copy(color = Color(0xFF9A9A9A))) { label() }
        }
        if (value.isNotEmpty()) {
            Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
        } else {
            ProvideTextStyle(LocalTextStyle.current.copy(color = Color(0xFF6A6A6A))) {
                if (placeholder != null) placeholder() else Text(" ")
            }
        }
    }
    if (editing) {
        var text by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
        val focus = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(Unit) { delay(80); runCatching { focus.requestFocus() }; keyboard?.show() }
        val done = { keyboard?.hide(); onValueChange(text.text); editing = false }
        Dialog(onDismissRequest = done, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.padding(top = 24.dp).widthIn(max = 900.dp).fillMaxWidth(0.9f)
                        .background(Color(0xFF161616), Square).padding(16.dp),
                ) {
                    OutlinedTextField(
                        value = text, onValueChange = { text = it },
                        label = label, placeholder = placeholder, singleLine = singleLine,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { done() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                    Row(Modifier.padding(top = 10.dp)) {
                        Text(tr("Press OK on the keyboard (or Back) to finish."), style = MaterialTheme.typography.labelSmall, color = Color(0xFF9A9A9A))
                    }
                }
            }
        }
    }
}
