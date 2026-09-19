package com.pedroleite.opencomanda.ui

import androidx.compose.ui.test.filter
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText

// Waits for UI that appears or disappears as a consequence of work the Compose test rule cannot
// see. Built on the rule's stable `waitUntil(conditionDescription, …)`, so a timeout says which
// node it was waiting for (the matcher-based `waitUntil…Exists` helpers are still experimental).
//
// The rule's automatic synchronization only covers recomposition, layout and animations on the main
// thread. It does not cover Room's query executor: a ViewModel state built with
// `combine(roomFlow, …)` only emits after Room's first result, so anything derived from it (a dialog
// opened by tapping a FAB, for instance) can show up after the tap has already returned. Windows
// created or removed by a dialog or menu are registered with the rule after the fact as well. Wait
// for the specific thing the next step needs instead of asserting on it straight away.
private const val UI_SYNC_TIMEOUT_MILLIS = 5_000L

/** Waits until exactly one node carries [tag] — e.g. a dialog's confirm button being ready to tap. */
fun ComposeTestRule.waitForTag(tag: String) =
    waitUntil("exactly one node with test tag '$tag'", UI_SYNC_TIMEOUT_MILLIS) {
        onAllNodesWithTag(tag).fetchSemanticsNodes().size == 1
    }

/** Waits until no node shows [text] — e.g. a dialog that was just dismissed. */
fun ComposeTestRule.waitForTextGone(text: String) =
    waitUntil("no node with text '$text'", UI_SYNC_TIMEOUT_MILLIS) {
        onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
    }

/** Waits until the node carrying [tag] exists and is enabled — for a button whose availability
 *  depends on state that loads after the screen first appears. */
fun ComposeTestRule.waitForTagEnabled(tag: String) =
    waitUntil("an enabled node with test tag '$tag'", UI_SYNC_TIMEOUT_MILLIS) {
        onAllNodesWithTag(tag).filter(isEnabled()).fetchSemanticsNodes().size == 1
    }

/** Waits until a node has [description] as its content description — e.g. a top-bar icon on a
 *  screen that renders nothing until its first data has loaded. */
fun ComposeTestRule.waitForContentDescription(description: String) =
    waitUntil("a node with content description '$description'", UI_SYNC_TIMEOUT_MILLIS) {
        onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
    }
