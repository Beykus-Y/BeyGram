package com.beykus.beygram.ui.screens.profile

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.MonetizationUnavailableException
import com.beykus.beygram.domain.model.PremiumCheckoutEvent
import com.beykus.beygram.domain.model.PremiumCheckoutOutcome
import com.beykus.beygram.domain.model.PremiumCheckoutStep
import com.beykus.beygram.domain.model.PremiumFeatureKind
import com.beykus.beygram.domain.model.PremiumGiftCodeApplyOutcome
import com.beykus.beygram.domain.model.PremiumGiftCodeEvent
import com.beykus.beygram.domain.model.PremiumGiftCodeInfo
import com.beykus.beygram.domain.model.PremiumGiftCodeStep
import com.beykus.beygram.domain.model.PremiumGiftCodeValidation
import com.beykus.beygram.domain.model.PremiumInfo
import com.beykus.beygram.domain.model.PremiumLoadState
import com.beykus.beygram.domain.model.PremiumPlan
import com.beykus.beygram.domain.model.PremiumPlanBlock
import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.domain.model.canCheckout
import com.beykus.beygram.domain.model.failed
import com.beykus.beygram.domain.model.formatPriceMinorUnits
import com.beykus.beygram.domain.model.needsRefresh
import com.beykus.beygram.domain.model.next
import com.beykus.beygram.domain.model.normalizePremiumGiftCode
import com.beykus.beygram.domain.model.premiumGiftDuration
import com.beykus.beygram.domain.model.premiumLoadSucceeded
import com.beykus.beygram.domain.model.premiumPlanBlock
import com.beykus.beygram.domain.model.showsPremiumDetails
import com.beykus.beygram.domain.model.startingRefresh
import com.beykus.beygram.domain.model.validatePremiumGiftCode
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.accessibility.statusAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.beyGramTextFieldColors
import com.beykus.beygram.ui.text.resolve
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun PremiumScreen(
    accountId: String,
    connectionState: TelegramConnectionState,
    load: suspend () -> Result<PremiumInfo>,
    checkGiftCode: suspend (String) -> Result<PremiumGiftCodeInfo>,
    applyGiftCode: suspend (String) -> PremiumGiftCodeApplyOutcome,
    purchaseLink: suspend (PremiumPlan) -> PremiumCheckoutOutcome,
    onBack: () -> Unit
) {
    // A switch between two live accounts keeps this route composed; all read and checkout state
    // must be rebuilt for the new account instead of reusing the previous account's snapshot.
    key(accountId) {
        PremiumScreenSession(
            accountId = accountId,
            connectionState = connectionState,
            load = load,
            checkGiftCode = checkGiftCode,
            applyGiftCode = applyGiftCode,
            purchaseLink = purchaseLink,
            onBack = onBack
        )
    }
}

@Composable
private fun PremiumScreenSession(
    accountId: String,
    connectionState: TelegramConnectionState,
    load: suspend () -> Result<PremiumInfo>,
    checkGiftCode: suspend (String) -> Result<PremiumGiftCodeInfo>,
    applyGiftCode: suspend (String) -> PremiumGiftCodeApplyOutcome,
    purchaseLink: suspend (PremiumPlan) -> PremiumCheckoutOutcome,
    onBack: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var loadState by remember { mutableStateOf<PremiumLoadState>(PremiumLoadState.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    // A cancelled read must not publish its late result over a newer one.
    var generation by remember { mutableIntStateOf(0) }
    var checkout by remember { mutableStateOf<PremiumCheckoutStep>(PremiumCheckoutStep.Idle) }
    var checkoutGeneration by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<PaymentNotice?>(null) }
    var previousPremium by remember { mutableStateOf<Boolean?>(null) }
    var checkAfterReturn by remember { mutableStateOf(false) }
    // The browser can outlive this composition; only flags survive rotation. The URL never does.
    var awaitingExternal by rememberSaveable(accountId) { mutableStateOf(false) }
    var leftApp by rememberSaveable(accountId) { mutableStateOf(false) }
    val checkoutBusy = awaitingExternal ||
        checkout is PremiumCheckoutStep.Confirming ||
        checkout is PremiumCheckoutStep.Preparing ||
        checkout is PremiumCheckoutStep.LinkReady ||
        checkout is PremiumCheckoutStep.AwaitingReturn
    // Re-evaluate freshness while the screen stays open; stale prices must not remain actionable.
    var nowMillis by remember(accountId) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(accountId) {
        while (true) {
            delay(PREMIUM_CLOCK_TICK_MS)
            nowMillis = System.currentTimeMillis()
        }
    }
    val readyState = loadState as? PremiumLoadState.Ready
    val loading = loadState is PremiumLoadState.Loading || readyState?.refreshing == true
    val stale = readyState != null && !loadState.showsPremiumDetails(accountId, nowMillis)

    fun refreshAfterPayment() {
        checkoutGeneration++
        awaitingExternal = false
        leftApp = false
        checkout = checkout.next(PremiumCheckoutEvent.Returned)
        checkAfterReturn = true
        reload++
    }

    // The previous connected flag is per account and never shared through rotation.
    var wasConnected by remember(accountId) {
        mutableStateOf(connectionState == TelegramConnectionState.Connected)
    }
    LaunchedEffect(connectionState) {
        val connected = connectionState == TelegramConnectionState.Connected
        val regained = connected && !wasConnected
        wasConnected = connected
        if (regained &&
            !checkoutBusy &&
            loadState.canRetryWithoutUser()
        ) {
            reload++
        }
    }

    val latestLoadState by rememberUpdatedState(loadState)
    val latestConnectionState by rememberUpdatedState(connectionState)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> if (awaitingExternal) leftApp = true
                Lifecycle.Event.ON_RESUME -> when {
                    awaitingExternal && leftApp -> refreshAfterPayment()
                    // A read that failed while the app was in the background gets one fresh attempt
                    // as soon as the connection is usable again.
                    latestConnectionState == TelegramConnectionState.Connected &&
                        latestLoadState.canRetryWithoutUser() -> reload++
                    else -> Unit
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // An expired snapshot is no longer shown and is re-read without user action.
    LaunchedEffect(loadState, nowMillis, checkoutBusy) {
        if (!checkoutBusy && loadState.needsRefresh(accountId, nowMillis)) reload++
    }

    BackHandler(onBack = onBack)
    LaunchedEffect(reload) {
        val afterPayment = checkAfterReturn
        checkAfterReturn = false
        val currentGeneration = ++generation
        val previous = loadState
        loadState = previous.startingRefresh()
        val result = try {
            load()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
        // A late response from a cancelled reload must not overwrite the newer state.
        if (generation != currentGeneration) return@LaunchedEffect
        result.fold(
            onSuccess = { loaded ->
                val loadedAt = System.currentTimeMillis()
                // Align the freshness clock with the read; otherwise a snapshot can look "future"
                // relative to a clock captured before the request started.
                nowMillis = loadedAt
                loadState = premiumLoadSucceeded(accountId, loaded, loadedAt)
                if (afterPayment) {
                    notice = if (previousPremium == false && loaded.isPremium) {
                        PaymentNotice(context.getString(R.string.premium_purchase_success), PaymentTone.Success)
                    } else {
                        PaymentNotice(context.getString(R.string.premium_purchase_refreshed), PaymentTone.Neutral)
                    }
                }
                previousPremium = loaded.isPremium
            },
            onFailure = { failure ->
                val unsupported = failure is MonetizationUnavailableException
                val reason = (failure as? UserMessageException)?.text
                    ?: UiText.Res(R.string.premium_load_error_body)
                loadState = previous.failed(
                    accountId = accountId,
                    reason = reason,
                    unsupported = unsupported
                )
                if (afterPayment) {
                    notice = PaymentNotice(
                        context.getString(R.string.premium_purchase_refreshed),
                        PaymentTone.Neutral
                    )
                }
            }
        )
    }

    fun selectPlan(plan: PremiumPlan) {
        if (checkoutBusy || premiumPlanBlock(plan, checkout) != null) return
        val ready = loadState as? PremiumLoadState.Ready ?: return
        if (!loadState.canCheckout(accountId, System.currentTimeMillis())) {
            if (!ready.refreshing) {
                notice = PaymentNotice(context.getString(R.string.premium_data_stale), PaymentTone.Neutral)
                reload++
            }
            return
        }
        notice = null
        checkout = checkout.next(PremiumCheckoutEvent.Select(plan))
    }

    fun dismissCheckout() {
        checkoutGeneration++
        checkout = checkout.next(PremiumCheckoutEvent.Dismiss)
    }

    fun confirmCheckout() {
        val confirming = checkout as? PremiumCheckoutStep.Confirming ?: return
        if (awaitingExternal) return
        if (!loadState.canCheckout(accountId, System.currentTimeMillis())) {
            checkout = checkout.next(PremiumCheckoutEvent.Dismiss)
            notice = PaymentNotice(context.getString(R.string.premium_data_stale), PaymentTone.Neutral)
            reload++
            return
        }
        checkout = checkout.next(PremiumCheckoutEvent.Confirm)
        val generation = ++checkoutGeneration
        scope.launch {
            val outcome = try {
                purchaseLink(confirming.plan)
            } catch (_: TimeoutCancellationException) {
                PremiumCheckoutOutcome.Failed(UiText.Res(R.string.err_monetization_timeout))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                PremiumCheckoutOutcome.Failed(UiText.Res(R.string.err_monetization_failed))
            }
            // The dialog may have been dismissed while this read-only request was in flight.
            if (!isActive || generation != checkoutGeneration) return@launch
            when (outcome) {
                is PremiumCheckoutOutcome.Ready -> {
                    val ready = checkout.next(PremiumCheckoutEvent.Ready(outcome.url))
                    if (ready !is PremiumCheckoutStep.LinkReady) return@launch
                    checkout = ready
                    previousPremium = (loadState as? PremiumLoadState.Ready)?.snapshot?.info?.isPremium
                        ?: previousPremium
                    if (openExternalPaymentPage(context, outcome.url.value)) {
                        checkout = checkout.next(PremiumCheckoutEvent.Opened)
                        awaitingExternal = true
                        leftApp = false
                        notice = PaymentNotice(
                            context.getString(R.string.premium_purchase_opened_title),
                            PaymentTone.Neutral
                        )
                    } else {
                        checkout = checkout.next(
                            PremiumCheckoutEvent.Failed(UiText.Res(R.string.premium_purchase_open_failed))
                        )
                        notice = PaymentNotice(
                            context.getString(R.string.premium_purchase_open_failed),
                            PaymentTone.Error
                        )
                    }
                }
                PremiumCheckoutOutcome.PlanChanged -> {
                    checkout = checkout.next(
                        PremiumCheckoutEvent.Failed(UiText.Res(R.string.premium_purchase_changed))
                    )
                    notice = PaymentNotice(context.getString(R.string.premium_purchase_changed), PaymentTone.Error)
                    reload++
                }
                PremiumCheckoutOutcome.Unavailable -> {
                    checkout = checkout.next(
                        PremiumCheckoutEvent.Failed(UiText.Res(R.string.premium_purchase_unavailable_body))
                    )
                    notice = PaymentNotice(
                        context.getString(R.string.premium_purchase_unavailable_body),
                        PaymentTone.Error
                    )
                    // The link or plan may have disappeared server-side; refresh the rows.
                    reload++
                }
                PremiumCheckoutOutcome.Busy -> {
                    checkout = checkout.next(
                        PremiumCheckoutEvent.Failed(UiText.Res(R.string.premium_purchase_busy))
                    )
                    notice = PaymentNotice(context.getString(R.string.premium_purchase_busy), PaymentTone.Error)
                }
                is PremiumCheckoutOutcome.Failed -> {
                    checkout = checkout.next(PremiumCheckoutEvent.Failed(outcome.reason))
                    notice = PaymentNotice(outcome.reason.resolve(context), PaymentTone.Error)
                }
            }
        }
    }

    val dialogPlan = when (val step = checkout) {
        is PremiumCheckoutStep.Confirming -> step.plan
        is PremiumCheckoutStep.Preparing -> step.plan
        else -> null
    }

    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(stringResource(R.string.premium_title), onBack) {
            IconButton(onClick = { if (!loading && !checkoutBusy) reload++ }, enabled = !loading && !checkoutBusy) {
                Icon(Icons.Rounded.Refresh, stringResource(R.string.common_retry), tint = palette.textPrimary)
            }
        }
        when (val current = loadState) {
            PremiumLoadState.Loading -> Box(Modifier.weight(1f).fillMaxWidth()) {
                PremiumLoadingSkeleton()
            }
            PremiumLoadState.Unsupported -> Box(Modifier.weight(1f).fillMaxWidth()) {
                MonetizationCenteredState(
                    title = stringResource(R.string.monetization_unsupported_title),
                    body = stringResource(R.string.monetization_unsupported_body),
                    onRetry = { reload++ }
                )
            }
            is PremiumLoadState.Failed -> Box(Modifier.weight(1f).fillMaxWidth()) {
                MonetizationCenteredState(
                    title = stringResource(R.string.premium_load_error),
                    body = current.reason.resolve(context),
                    isError = true,
                    onRetry = { reload++ }
                )
            }
            is PremiumLoadState.Ready -> PremiumContent(
                info = current.snapshot.info,
                refreshError = current.refreshError?.resolve(context),
                stale = stale,
                loading = current.refreshing,
                canPurchase = loadState.canCheckout(accountId, nowMillis),
                checkout = checkout,
                awaitingExternal = awaitingExternal,
                notice = notice,
                checkGiftCode = checkGiftCode,
                applyGiftCode = applyGiftCode,
                onSelectPlan = ::selectPlan,
                onRetry = { reload++ },
                onCheckStatus = ::refreshAfterPayment
            )
        }
    }
    if (dialogPlan != null) {
        PremiumCheckoutDialog(
            plan = dialogPlan,
            preparing = checkout is PremiumCheckoutStep.Preparing,
            onConfirm = ::confirmCheckout,
            onDismiss = ::dismissCheckout
        )
    }
}

@Composable
private fun PremiumContent(
    info: PremiumInfo,
    refreshError: String?,
    stale: Boolean,
    loading: Boolean,
    canPurchase: Boolean,
    checkout: PremiumCheckoutStep,
    awaitingExternal: Boolean,
    notice: PaymentNotice?,
    checkGiftCode: suspend (String) -> Result<PremiumGiftCodeInfo>,
    applyGiftCode: suspend (String) -> PremiumGiftCodeApplyOutcome,
    onSelectPlan: (PremiumPlan) -> Unit,
    onRetry: () -> Unit,
    onCheckStatus: () -> Unit
) {
    val palette = beyGramPalette()
    val scrollState = rememberScrollState()
    val loadingProgress = stringResource(R.string.premium_loading_progress)
    val staleBody = stringResource(R.string.premium_stale_body)
    val refreshBody = stringResource(R.string.premium_refresh_failed_body)
    val staleTitle = stringResource(R.string.premium_stale_title)
    val refreshFailedTitle = stringResource(R.string.premium_refresh_failed_title)
    val busy = loading || awaitingExternal ||
        checkout is PremiumCheckoutStep.Confirming ||
        checkout is PremiumCheckoutStep.Preparing ||
        checkout is PremiumCheckoutStep.LinkReady ||
        checkout is PremiumCheckoutStep.AwaitingReturn
    LaunchedEffect(notice) {
        if (notice != null) scrollState.animateScrollTo(0)
    }
    // A plain scroll container keeps the gift-code workflow and its coroutine composed while
    // the user inspects features and plans; a lazy item would discard both when scrolled away.
    Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(bottom = 24.dp)) {
        if (loading) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth()
                    .semantics { contentDescription = loadingProgress }
                    .statusAnnouncement(),
                color = palette.accent
            )
        }
        val warningBody = when {
            stale -> listOfNotNull(refreshError, staleBody).joinToString("\n")
            refreshError != null -> refreshError + "\n" + refreshBody
            else -> null
        }
        if (warningBody != null) {
            MonetizationStateCard(
                title = if (refreshError == null) staleTitle else refreshFailedTitle,
                body = warningBody,
                tone = MonetizationTone.Warning,
                onRetry = if (busy) null else onRetry,
                announce = true
            )
        }
        notice?.let { PaymentNoticeCard(it) }
        if (awaitingExternal || checkout is PremiumCheckoutStep.AwaitingReturn) {
            MonetizationStateCard(
                title = stringResource(R.string.premium_purchase_opened_title),
                body = stringResource(R.string.premium_purchase_opened_body),
                onRetry = if (loading) null else onCheckStatus,
                retryLabel = R.string.premium_purchase_check
            )
        }
        if (!stale) {
            MonetizationCard {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp).semantics(mergeDescendants = true) {}) {
                    Text(
                        stringResource(R.string.premium_status_heading),
                        color = palette.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(if (info.isPremium) R.string.premium_status_active else R.string.premium_status_inactive),
                        color = if (info.isPremium) palette.accentInk else palette.textPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (info.statusText.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(info.statusText, color = palette.textPrimary, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.premium_status_source_hint),
                        color = palette.textSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }
        // Keep this section composed even when stale details are hidden: it owns the entered
        // code and the coroutine of an in-flight check/apply, which must not be cancelled by a
        // status refresh or TTL expiry.
        PremiumGiftCodeSection(
            checkCode = checkGiftCode,
            applyCode = applyGiftCode,
            onActivated = onRetry
        )
        if (!stale) {
            MonetizationSectionHeader(stringResource(R.string.premium_features_title))
            if (info.features.isEmpty()) {
                Text(
                    stringResource(R.string.premium_features_empty),
                    color = palette.textSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            } else {
                MonetizationCard {
                    info.features.forEachIndexed { index, feature ->
                        if (index > 0) HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 18.dp))
                        Text(
                            stringResource(premiumFeatureLabel(feature)),
                            color = palette.textPrimary,
                            fontSize = 15.sp,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)
                        )
                    }
                }
            }
            MonetizationSectionHeader(stringResource(R.string.premium_plans_title))
            if (info.plans.isEmpty()) {
                Text(
                    stringResource(R.string.premium_plans_empty),
                    color = palette.textSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            } else {
                val hasAnyExternalLink = info.plans.any { it.hasExternalPaymentLink }
                if (!hasAnyExternalLink) {
                    MonetizationStateCard(
                        title = stringResource(R.string.premium_purchase_unavailable_title),
                        body = stringResource(R.string.premium_purchase_unavailable_body),
                        tone = MonetizationTone.Warning
                    )
                } else {
                    MonetizationStateCard(
                        title = stringResource(R.string.premium_purchase_browser_title),
                        body = stringResource(R.string.premium_purchase_browser_body)
                    )
                }
                MonetizationCard {
                    info.plans.forEachIndexed { index, plan ->
                        if (index > 0) HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 18.dp))
                        PremiumPlanRow(
                            plan = plan,
                            block = premiumPlanBlock(plan, checkout)
                                ?: if (loading || !canPurchase) PremiumPlanBlock.Busy else null,
                            onSelect = { onSelectPlan(plan) }
                        )
                    }
                }
            }
        }
    }
}

/** Useful progress and a content-shaped skeleton instead of an empty dark screen with a spinner. */
@Composable
private fun PremiumLoadingSkeleton() {
    val palette = beyGramPalette()
    val loadingProgress = stringResource(R.string.premium_loading_progress)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(palette.surface)
                .padding(horizontal = 18.dp, vertical = 16.dp)
                .semantics(mergeDescendants = true) {}
                .statusAnnouncement()
        ) {
            Text(
                stringResource(R.string.premium_loading_title),
                color = palette.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.premium_loading_body),
                color = palette.textSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                Modifier.fillMaxWidth().semantics { contentDescription = loadingProgress },
                color = palette.accent
            )
        }
        MonetizationSectionHeader(stringResource(R.string.premium_status_heading))
        PremiumSkeletonCard(rows = 2)
        MonetizationSectionHeader(stringResource(R.string.premium_features_title))
        PremiumSkeletonCard(rows = 4)
        MonetizationSectionHeader(stringResource(R.string.premium_plans_title))
        PremiumSkeletonCard(rows = 3)
    }
}

@Composable
private fun PremiumSkeletonCard(rows: Int) {
    val palette = beyGramPalette()
    MonetizationCard {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(rows) { index ->
                Box(
                    Modifier.fillMaxWidth(if (index % 2 == 1) 0.64f else 0.9f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(palette.surface2)
                )
            }
        }
    }
}

@Composable
private fun PremiumGiftCodeSection(
    checkCode: suspend (String) -> Result<PremiumGiftCodeInfo>,
    applyCode: suspend (String) -> PremiumGiftCodeApplyOutcome,
    onActivated: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Deliberately not rememberSaveable: a gift code must not survive leaving this screen.
    var code by remember { mutableStateOf("") }
    var checkedCode by remember { mutableStateOf("") }
    var step by remember { mutableStateOf<PremiumGiftCodeStep>(PremiumGiftCodeStep.Idle) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var validation by remember { mutableStateOf<PremiumGiftCodeValidation?>(null) }
    val checking = step is PremiumGiftCodeStep.Checking
    val applying = step is PremiumGiftCodeStep.Applying
    val busy = checking || applying
    val validationError = validation

    fun dismissConfirmation() {
        if (applying) return
        step = step.next(PremiumGiftCodeEvent.Reset)
        checkedCode = ""
    }

    fun check() {
        if (busy) return
        val result = validatePremiumGiftCode(code)
        if (result != PremiumGiftCodeValidation.Valid) {
            validation = result
            return
        }
        val normalized = normalizePremiumGiftCode(code)
        if (normalized == null) {
            validation = PremiumGiftCodeValidation.InvalidCharacters
            return
        }
        code = normalized
        validation = null
        message = null
        checkedCode = ""
        step = step.next(PremiumGiftCodeEvent.Check)
        scope.launch {
            val checked = try {
                checkCode(normalized)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
            checked.fold(
                onSuccess = { info ->
                    checkedCode = normalized
                    step = step.next(PremiumGiftCodeEvent.Checked(info))
                },
                onFailure = { failure ->
                    step = step.next(PremiumGiftCodeEvent.Failed)
                    message = failure.userMessage(context, R.string.premium_gift_code_check_failed)
                    messageIsError = true
                }
            )
        }
    }

    fun apply() {
        val confirming = step as? PremiumGiftCodeStep.Confirming ?: return
        if (!confirming.info.canActivate || checkedCode.isBlank()) return
        step = step.next(PremiumGiftCodeEvent.Apply)
        scope.launch {
            val outcome = try {
                applyCode(checkedCode)
            } catch (_: TimeoutCancellationException) {
                PremiumGiftCodeApplyOutcome.Uncertain
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                PremiumGiftCodeApplyOutcome.Uncertain
            }
            when (outcome) {
                PremiumGiftCodeApplyOutcome.Activated -> {
                    step = step.next(PremiumGiftCodeEvent.Applied)
                    code = ""
                    checkedCode = ""
                    message = context.getString(R.string.premium_gift_code_success)
                    messageIsError = false
                    onActivated()
                }
                is PremiumGiftCodeApplyOutcome.Blocked -> {
                    step = step.next(PremiumGiftCodeEvent.Failed)
                    checkedCode = ""
                    message = outcome.reason.resolve(context)
                    messageIsError = true
                }
                is PremiumGiftCodeApplyOutcome.Failed -> {
                    step = step.next(PremiumGiftCodeEvent.Failed)
                    checkedCode = ""
                    message = outcome.reason.resolve(context)
                    messageIsError = true
                }
                PremiumGiftCodeApplyOutcome.Uncertain -> {
                    step = step.next(PremiumGiftCodeEvent.Uncertain)
                    code = ""
                    checkedCode = ""
                    message = context.getString(R.string.premium_gift_code_uncertain)
                    messageIsError = true
                    onActivated()
                }
            }
        }
    }

    MonetizationCard {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                stringResource(R.string.premium_gift_code_title),
                color = palette.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().semantics { heading() }
            )
            Text(
                stringResource(R.string.premium_gift_code_hint),
                color = palette.textSecondary,
                fontSize = 13.sp
            )
            OutlinedTextField(
                value = code,
                onValueChange = { value ->
                    code = value.take(256)
                    validation = null
                    if (step !is PremiumGiftCodeStep.Checking && step !is PremiumGiftCodeStep.Applying) {
                        checkedCode = ""
                        message = null
                        messageIsError = false
                    }
                },
                label = { Text(stringResource(R.string.premium_gift_code_label)) },
                placeholder = { Text(stringResource(R.string.premium_gift_code_placeholder)) },
                singleLine = true,
                enabled = !busy,
                isError = validationError != null,
                supportingText = if (validationError == null || validationError == PremiumGiftCodeValidation.Valid) {
                    null
                } else {
                    { Text(stringResource(premiumGiftCodeValidationLabel(validationError))) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                shape = RoundedCornerShape(14.dp),
                colors = beyGramTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            FilledTonalButton(
                onClick = ::check,
                enabled = !busy,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = palette.accentSoft,
                    contentColor = palette.accentInk
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text(
                    if (checking) stringResource(R.string.premium_gift_code_checking)
                    else stringResource(R.string.premium_gift_code_check)
                )
            }
            message?.let { text ->
                Text(
                    text,
                    color = if (messageIsError) palette.danger else palette.successText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().errorAnnouncement()
                )
            }
        }
    }

    val dialogInfo = when (val current = step) {
        is PremiumGiftCodeStep.Confirming -> current.info
        is PremiumGiftCodeStep.Applying -> current.info
        else -> null
    }
    if (dialogInfo != null) {
        GiftCodeConfirmationDialog(
            info = dialogInfo,
            applying = applying,
            onConfirm = ::apply,
            onDismiss = ::dismissConfirmation
        )
    }
}

@Composable
private fun GiftCodeConfirmationDialog(
    info: PremiumGiftCodeInfo,
    applying: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = beyGramPalette()
    val duration = premiumGiftDuration(info.monthCount, info.dayCount)
    val durationText = if (!duration.isKnown) {
        stringResource(R.string.premium_gift_code_duration_unknown)
    } else {
        val parts = mutableListOf<String>()
        if (duration.months > 0) {
            parts += stringResource(R.string.premium_gift_code_months, duration.months)
        }
        if (duration.days > 0) {
            parts += stringResource(R.string.premium_gift_code_days, duration.days)
        }
        stringResource(R.string.premium_gift_code_duration, parts.joinToString(" "))
    }
    AlertDialog(
        onDismissRequest = { if (!applying) onDismiss() },
        title = { Text(stringResource(R.string.premium_gift_code_info_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(durationText, color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(
                        R.string.premium_gift_code_creator,
                        info.creatorName ?: stringResource(R.string.premium_gift_code_creator_unknown)
                    ),
                    color = palette.textSecondary,
                    fontSize = 14.sp
                )
                Text(
                    stringResource(
                        if (info.isFromGiveaway) R.string.premium_gift_code_type_giveaway
                        else R.string.premium_gift_code_type_regular
                    ),
                    color = palette.textSecondary,
                    fontSize = 14.sp
                )
                if (info.creationDateSeconds > 0) {
                    Text(
                        stringResource(
                            R.string.premium_gift_code_created,
                            giftCodeDate(info.creationDateSeconds)
                        ),
                        color = palette.textSecondary,
                        fontSize = 13.sp
                    )
                }
                if (info.isUsed) {
                    Text(
                        if (info.useDateSeconds > 0) {
                            stringResource(
                                R.string.premium_gift_code_activated_on,
                                giftCodeDate(info.useDateSeconds)
                            )
                        } else {
                            stringResource(R.string.premium_gift_code_used)
                        },
                        color = palette.danger,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (info.wasOriginallyForAnotherUser) {
                    Text(
                        stringResource(R.string.premium_gift_code_other_user),
                        color = palette.textSecondary,
                        fontSize = 14.sp
                    )
                }
                if (applying) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
                    Text(
                        stringResource(R.string.premium_gift_code_applying),
                        color = palette.textSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = info.canActivate && !applying,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(
                    if (applying) stringResource(R.string.premium_gift_code_applying)
                    else stringResource(R.string.premium_gift_code_apply)
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !applying,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@StringRes
private fun premiumGiftCodeValidationLabel(validation: PremiumGiftCodeValidation): Int = when (validation) {
    PremiumGiftCodeValidation.Valid -> R.string.premium_gift_code_label
    PremiumGiftCodeValidation.Empty -> R.string.premium_gift_code_validation_empty
    PremiumGiftCodeValidation.TooLong -> R.string.premium_gift_code_validation_too_long
    PremiumGiftCodeValidation.InvalidCharacters -> R.string.premium_gift_code_validation_invalid
}

private fun giftCodeDate(seconds: Int): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM)
        .format(Date(seconds.coerceAtLeast(0).toLong() * 1000L))

@Composable
private fun PremiumPlanRow(
    plan: PremiumPlan,
    block: PremiumPlanBlock?,
    onSelect: () -> Unit
) {
    val palette = beyGramPalette()
    val enabled = block == null
    Column(
        Modifier.fillMaxWidth()
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = stringResource(R.string.premium_plan_select, plan.monthCount),
                onClick = onSelect
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.premium_plan_months, plan.monthCount),
                color = if (enabled) palette.textPrimary else palette.textSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                formatPriceMinorUnits(plan.amountMinor, plan.currency),
                color = if (enabled) palette.textPrimary else palette.textSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (enabled) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    null,
                    tint = palette.textSecondary,
                    modifier = Modifier.padding(start = 6.dp).size(20.dp)
                )
            }
        }
        if (plan.isCurrent || plan.isUpgrade || plan.discountPercent > 0) {
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (plan.isCurrent) PremiumBadge(stringResource(R.string.premium_plan_current), palette.accentInk)
                if (plan.isUpgrade) PremiumBadge(stringResource(R.string.premium_plan_upgrade), palette.textSecondary)
                if (plan.discountPercent > 0) {
                    PremiumBadge(stringResource(R.string.premium_plan_discount, plan.discountPercent), palette.warningText)
                }
            }
        }
        if (block == PremiumPlanBlock.NoExternalCheckout) {
            Text(
                stringResource(R.string.premium_plan_no_payment),
                color = palette.warningText,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun PremiumCheckoutDialog(
    plan: PremiumPlan,
    preparing: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = beyGramPalette()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.premium_purchase_confirm_title),
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(
                        R.string.premium_purchase_confirm_body,
                        stringResource(R.string.premium_plan_months, plan.monthCount),
                        formatPriceMinorUnits(plan.amountMinor, plan.currency)
                    ),
                    color = palette.textPrimary,
                    fontSize = 15.sp,
                    lineHeight = 21.sp
                )
                Text(
                    stringResource(R.string.premium_purchase_external_hint),
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                if (preparing) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
                    Text(
                        stringResource(R.string.premium_purchase_preparing),
                        color = palette.textSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !preparing,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(
                    if (preparing) stringResource(R.string.premium_purchase_preparing)
                    else stringResource(R.string.premium_purchase_open)
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

internal enum class PaymentTone { Neutral, Success, Error }

internal data class PaymentNotice(val text: String, val tone: PaymentTone)

@Composable
internal fun PaymentNoticeCard(notice: PaymentNotice) {
    val palette = beyGramPalette()
    val background = when (notice.tone) {
        PaymentTone.Success -> palette.successSurface
        PaymentTone.Error -> palette.warningSurface
        PaymentTone.Neutral -> palette.surface
    }
    val contentColor = when (notice.tone) {
        PaymentTone.Success -> palette.successText
        PaymentTone.Error -> palette.danger
        PaymentTone.Neutral -> palette.textPrimary
    }
    Text(
        notice.text,
        color = contentColor,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .errorAnnouncement()
    )
}

@Composable
private fun PremiumBadge(text: String, color: Color) {
    Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
internal fun MonetizationSectionHeader(text: String) {
    val palette = beyGramPalette()
    Text(
        text,
        color = palette.textSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)
            .semantics { heading() }
    )
}

@Composable
internal fun MonetizationCard(content: @Composable ColumnScope.() -> Unit) {
    val palette = beyGramPalette()
    Column(
        Modifier.fillMaxWidth().profileCardSurface(palette.surface)
            .padding(vertical = 4.dp),
        content = content
    )
}

internal enum class MonetizationTone { Neutral, Warning, Error, Success }

@Composable
internal fun MonetizationStateCard(
    title: String,
    body: String,
    tone: MonetizationTone = MonetizationTone.Neutral,
    onRetry: (() -> Unit)? = null,
    @StringRes retryLabel: Int = R.string.common_retry,
    announce: Boolean = false
) {
    val palette = beyGramPalette()
    val background = when (tone) {
        MonetizationTone.Warning -> palette.warningSurface
        MonetizationTone.Error -> palette.warningSurface
        MonetizationTone.Success -> palette.successSurface
        MonetizationTone.Neutral -> palette.surface
    }
    val titleColor = when (tone) {
        MonetizationTone.Error -> palette.danger
        MonetizationTone.Warning -> palette.warningText
        MonetizationTone.Success -> palette.successText
        MonetizationTone.Neutral -> palette.textPrimary
    }
    val announcement = if (announce) Modifier.statusAnnouncement() else Modifier
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .semantics(mergeDescendants = true) {}
            .then(announcement)
    ) {
        Text(title, color = titleColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(body, color = palette.textPrimary, fontSize = 14.sp, lineHeight = 20.sp)
        if (onRetry != null) {
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(retryLabel))
            }
        }
    }
}

@Composable
internal fun MonetizationCenteredState(
    title: String,
    body: String,
    isError: Boolean = false,
    onRetry: (() -> Unit)? = null
) {
    val palette = beyGramPalette()
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.WorkspacePremium,
            null,
            tint = if (isError) palette.danger else palette.textSecondary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            color = if (isError) palette.danger else palette.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            color = palette.textSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.errorAnnouncement()
        )
        if (onRetry != null) {
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.common_retry))
            }
        }
    }
}

@StringRes
internal fun premiumFeatureLabel(feature: PremiumFeatureKind): Int = when (feature) {
    PremiumFeatureKind.AccentColor -> R.string.premium_feature_accent_color
    PremiumFeatureKind.AdvancedChatManagement -> R.string.premium_feature_advanced_chat_management
    PremiumFeatureKind.AnimatedProfilePhoto -> R.string.premium_feature_animated_profile_photo
    PremiumFeatureKind.AppIcons -> R.string.premium_feature_app_icons
    PremiumFeatureKind.BackgroundForBoth -> R.string.premium_feature_background_for_both
    PremiumFeatureKind.Business -> R.string.premium_feature_business
    PremiumFeatureKind.ChatBoost -> R.string.premium_feature_chat_boost
    PremiumFeatureKind.Checklists -> R.string.premium_feature_checklists
    PremiumFeatureKind.CustomEmoji -> R.string.premium_feature_custom_emoji
    PremiumFeatureKind.DisabledAds -> R.string.premium_feature_disabled_ads
    PremiumFeatureKind.EmojiStatus -> R.string.premium_feature_emoji_status
    PremiumFeatureKind.ForumTopicIcon -> R.string.premium_feature_forum_topic_icon
    PremiumFeatureKind.ImprovedDownloadSpeed -> R.string.premium_feature_improved_download_speed
    PremiumFeatureKind.IncreasedLimits -> R.string.premium_feature_increased_limits
    PremiumFeatureKind.IncreasedUploadFileSize -> R.string.premium_feature_increased_upload_file_size
    PremiumFeatureKind.LastSeenTimes -> R.string.premium_feature_last_seen_times
    PremiumFeatureKind.MessageEffects -> R.string.premium_feature_message_effects
    PremiumFeatureKind.MessagePrivacy -> R.string.premium_feature_message_privacy
    PremiumFeatureKind.PaidMessages -> R.string.premium_feature_paid_messages
    PremiumFeatureKind.ProfileBadge -> R.string.premium_feature_profile_badge
    PremiumFeatureKind.ProtectPrivateChatContent -> R.string.premium_feature_protect_private_chat_content
    PremiumFeatureKind.RealTimeChatTranslation -> R.string.premium_feature_real_time_chat_translation
    PremiumFeatureKind.SavedMessagesTags -> R.string.premium_feature_saved_messages_tags
    PremiumFeatureKind.TextComposition -> R.string.premium_feature_text_composition
    PremiumFeatureKind.UniqueReactions -> R.string.premium_feature_unique_reactions
    PremiumFeatureKind.UniqueStickers -> R.string.premium_feature_unique_stickers
    PremiumFeatureKind.UpgradedStories -> R.string.premium_feature_upgraded_stories
    PremiumFeatureKind.VoiceRecognition -> R.string.premium_feature_voice_recognition
    PremiumFeatureKind.Other -> R.string.premium_feature_other
}

/** A failed or expired read can be retried automatically when connectivity returns. */
private fun PremiumLoadState.canRetryWithoutUser(): Boolean = when (this) {
    is PremiumLoadState.Ready -> refreshError != null && !refreshing
    is PremiumLoadState.Failed, PremiumLoadState.Unsupported -> true
    PremiumLoadState.Loading -> false
}

/** Freshness is re-checked without re-reading on every frame. */
private const val PREMIUM_CLOCK_TICK_MS = 30_000L
