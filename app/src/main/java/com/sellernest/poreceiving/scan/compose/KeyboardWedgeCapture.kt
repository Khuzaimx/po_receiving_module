package com.sellernest.poreceiving.scan.compose

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.sellernest.poreceiving.scan.ScanSource
import com.sellernest.poreceiving.scan.wedge.WedgeBurstDetector

/** Exposed so tests can target this field directly; not otherwise part of
 *  this composable's public API. */
const val WEDGE_CAPTURE_TEST_TAG = "keyboard_wedge_capture"

/**
 * §4.1: "Keyboard wedge: some devices emulate a keyboard. A hidden
 * always-focused input captures these." Drop this into any screen that needs
 * hardware-trigger operability, alongside
 * [com.sellernest.poreceiving.scan.compose.ScanFocusEffect] -- it dispatches
 * directly into the shared [com.sellernest.poreceiving.scan.ScanDispatcher]
 * itself (the same way [com.sellernest.poreceiving.scan.datawedge.DataWedgeScanReceiver]
 * does), so a screen only ever needs to place it, never wire its output by hand.
 *
 * Requests focus once, on entering composition, and immediately hides the
 * software keyboard if it tries to appear -- there is no visual presence
 * (1dp) and nothing here is meant for a person to type into directly.
 * A screen that also has real, visibly-focusable fields (manual SKU entry,
 * a quantity keypad) is responsible for moving focus back here once the user
 * is done with those; Compose's own focus system already guarantees this
 * field cannot "swallow" keystrokes meant for whatever the user actually
 * tapped, since only one focusable element holds focus at a time.
 */
@Composable
fun KeyboardWedgeCapture() {
    val dispatcher = rememberScanDispatcher()
    val detector = remember { WedgeBurstDetector() }
    var value by remember { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    BasicTextField(
        value = value,
        onValueChange = { newValue ->
            val now = System.currentTimeMillis()
            var completedScan: String? = null
            for (char in newValue.text) {
                detector.onCharacter(char, now)?.let { completedScan = it }
            }
            value = TextFieldValue("")
            completedScan?.let { dispatcher.dispatch(it, ScanSource.KEYBOARD_WEDGE) }
        },
        modifier = Modifier
            .testTag(WEDGE_CAPTURE_TEST_TAG)
            .size(1.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) keyboardController?.hide()
            },
    )
}
