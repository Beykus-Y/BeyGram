package com.beykus.beygram.ui.screens.settings

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.ui.theme.BeyGramTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountSwitcherContentTest {
    @get:Rule val composeRule = createComposeRule()

    private fun row(
        id: String,
        label: Int,
        title: String,
        status: AccountSwitcherStatus,
        current: Boolean = false,
        unread: Int = 0,
        canSelect: Boolean = true,
        canSignIn: Boolean = false,
        canDelete: Boolean = false
    ) = AccountSwitcherRow(
        accountId = id,
        isPrimary = id == "legacy",
        labelNumber = label,
        isCurrent = current,
        title = title,
        username = "",
        phone = "",
        unread = unread,
        meta = AccountSwitcherMeta(
            identity = AccountSwitcherIdentity(
                title = title,
                unread = unread,
                connection = TelegramConnectionState.Connected
            )
        ),
        status = status,
        busy = false,
        error = null,
        canSelect = canSelect,
        canSignIn = canSignIn,
        canDelete = canDelete,
        canDisconnect = false,
        canLogout = !canDelete && id != "legacy",
        canConfigureRoute = id != "legacy"
    )

    @Test fun currentReadyAndUnfinishedAccountsHaveDistinctActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clicked = mutableListOf<String>()
        val continued = mutableListOf<String>()
        val added = mutableListOf<Unit>()
        val rows = listOf(
            row("legacy", 1, context.getString(R.string.secondary_accounts_primary),
                AccountSwitcherStatus.CURRENT, current = true, canSelect = false),
            row("a-1", 2, "Alice", AccountSwitcherStatus.READY, unread = 3),
            row("a-2", 3, "Bob", AccountSwitcherStatus.UNSIGNED,
                canSelect = false, canSignIn = true, canDelete = true)
        )
        composeRule.setContent {
            BeyGramTheme {
                AccountSwitcherContent(
                    rows = rows,
                    busy = false,
                    errorText = null,
                    onRowClick = { clicked += it.accountId },
                    onAdd = { added += Unit },
                    onContinue = { continued += it },
                    onRetry = {},
                    onConfigureRoute = {},
                    onDisconnect = {},
                    onLogout = {},
                    onDeleteIncomplete = {}
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.secondary_accounts_primary))
            .assertIsSelected()
        composeRule.onNodeWithText("Alice").performClick()
        composeRule.runOnIdle { assertEquals(listOf("a-1"), clicked) }

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.account_switcher_menu_description, "Bob")
        ).performClick()
        composeRule.onNodeWithText(context.getString(R.string.account_switcher_continue)).performClick()
        composeRule.runOnIdle { assertEquals(listOf("a-2"), continued) }

        composeRule.onNodeWithText(context.getString(R.string.account_switcher_add)).performClick()
        composeRule.runOnIdle { assertEquals(1, added.size) }
    }

    @Test fun unreadBadgeHasAccessibleDescription() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            BeyGramTheme {
                AccountSwitcherContent(
                    rows = listOf(
                        row("a-1", 2, "Alice", AccountSwitcherStatus.READY, unread = 7)
                    ),
                    busy = false,
                    errorText = null,
                    onRowClick = {},
                    onAdd = {},
                    onContinue = {},
                    onRetry = {},
                    onConfigureRoute = {},
                    onDisconnect = {},
                    onLogout = {},
                    onDeleteIncomplete = {}
                )
            }
        }
        composeRule.onNodeWithContentDescription(
            context.resources.getQuantityString(R.plurals.multi_account_unread, 7, 7)
        ).assertExists()
    }

    @Test fun routeErrorIsVisibleWithoutOpeningASecondManagementScreen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            BeyGramTheme {
                AccountSwitcherContent(
                    rows = listOf(
                        row("a-1", 2, "Alice", AccountSwitcherStatus.ERROR,
                            canSelect = true),
                    ),
                    busy = false,
                    errorText = context.getString(R.string.account_switcher_error_route),
                    onRowClick = {},
                    onAdd = {},
                    onContinue = {},
                    onRetry = {},
                    onConfigureRoute = {},
                    onDisconnect = {},
                    onLogout = {},
                    onDeleteIncomplete = {}
                )
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.account_switcher_error_route))
            .assertExists()
        composeRule.onNodeWithText(context.getString(R.string.account_switcher_title))
            .assertExists()
    }
}
