package com.beykus.beygram.ui.screens.profile

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.MonetizationUnavailableException
import com.beykus.beygram.domain.model.PremiumFeatureKind
import com.beykus.beygram.domain.model.PremiumGiftCodeApplyOutcome
import com.beykus.beygram.domain.model.PremiumGiftCodeEvent
import com.beykus.beygram.domain.model.PremiumGiftCodeInfo
import com.beykus.beygram.domain.model.PremiumGiftCodeStep
import com.beykus.beygram.domain.model.PremiumGiftCodeValidation
import com.beykus.beygram.domain.model.PremiumInfo
import com.beykus.beygram.domain.model.PremiumPlan
import com.beykus.beygram.domain.model.formatPriceMinorUnits
import com.beykus.beygram.domain.model.next
import com.beykus.beygram.domain.model.normalizePremiumGiftCode
import com.beykus.beygram.domain.model.premiumGiftDuration
import com.beykus.beygram.domain.model.validatePremiumGiftCode
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.beyGramTextFieldColors
import com.beykus.beygram.ui.text.resolve
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun PremiumScreen(
    load: suspend () -> Result<PremiumInfo>,
    checkGiftCode: suspend (String) -> Result<PremiumGiftCodeInfo>,
    applyGiftCode: suspend (String) -> PremiumGiftCodeApplyOutcome,
    onBack: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    var info by remember { mutableStateOf<PremiumInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var unsupported by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    BackHandler(onBack = onBack)
    LaunchedEffect(reload) {
        loading = true
        error = null
        unsupported = false
        try {
            load()
                .onSuccess { info = it }
                .onFailure { failure ->
                    if (failure is MonetizationUnavailableException) unsupported = true
                    else error = failure.userMessage(context, R.string.premium_load_error)
                }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(stringResource(R.string.premium_title), onBack) {
            IconButton(onClick = { if (!loading) reload++ }, enabled = !loading) {
                Icon(Icons.Rounded.Refresh, stringResource(R.string.common_retry), tint = palette.textPrimary)
            }
        }
        when {
            info == null && unsupported -> MonetizationCenteredState(
                title = stringResource(R.string.monetization_unsupported_title),
                body = stringResource(R.string.monetization_unsupported_body)
            )
            info == null && error != null -> MonetizationCenteredState(
                title = stringResource(R.string.premium_load_error),
                body = error.orEmpty(),
                isError = true,
                onRetry = { reload++ }
            )
            info == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = palette.accent)
            }
            else -> PremiumContent(
                info = info!!,
                refreshError = error,
                unsupported = unsupported,
                loading = loading,
                checkGiftCode = checkGiftCode,
                applyGiftCode = applyGiftCode,
                onRetry = { reload++ }
            )
        }
    }
}

@Composable
private fun PremiumContent(
    info: PremiumInfo,
    refreshError: String?,
    unsupported: Boolean,
    loading: Boolean,
    checkGiftCode: suspend (String) -> Result<PremiumGiftCodeInfo>,
    applyGiftCode: suspend (String) -> PremiumGiftCodeApplyOutcome,
    onRetry: () -> Unit
) {
    val palette = beyGramPalette()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        if (refreshError != null) item("refresh_error") {
            Text(
                refreshError,
                color = palette.danger,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).errorAnnouncement()
            )
        }
        if (unsupported) item("unsupported") {
            MonetizationStateCard(
                title = stringResource(R.string.monetization_unsupported_title),
                body = stringResource(R.string.monetization_unsupported_body)
            )
        }
        item("status") {
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
        item("purchase_fallback") {
            MonetizationStateCard(
                title = stringResource(R.string.premium_purchase_unavailable_title),
                body = stringResource(R.string.premium_purchase_unavailable_body),
                tone = MonetizationTone.Warning
            )
        }
        item("gift_code") {
            PremiumGiftCodeSection(
                checkCode = checkGiftCode,
                applyCode = applyGiftCode,
                onActivated = onRetry
            )
        }
        item("features_header") {
            MonetizationSectionHeader(stringResource(R.string.premium_features_title))
        }
        if (info.features.isEmpty()) {
            item("features_empty") {
                Text(
                    stringResource(R.string.premium_features_empty),
                    color = palette.textSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        } else {
            item("features") {
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
        }
        item("plans_header") {
            MonetizationSectionHeader(stringResource(R.string.premium_plans_title))
        }
        if (info.plans.isEmpty()) {
            item("plans_empty") {
                Text(
                    stringResource(R.string.premium_plans_empty),
                    color = palette.textSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        } else {
            item("plans") {
                MonetizationCard {
                    info.plans.forEachIndexed { index, plan ->
                        if (index > 0) HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 18.dp))
                        PremiumPlanRow(plan)
                    }
                }
            }
        }
        if (loading) item("loading") {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), color = palette.accent)
            }
        }
        if (refreshError != null) item("retry") {
            TextButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.common_retry))
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: TimeoutCancellationException) {
                PremiumGiftCodeApplyOutcome.Uncertain
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
                if (info.isForAnotherUser) {
                    Text(
                        stringResource(R.string.premium_gift_code_other_user),
                        color = palette.danger,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
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
private fun PremiumPlanRow(plan: PremiumPlan) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.premium_plan_months, plan.monthCount),
                color = palette.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                formatPriceMinorUnits(plan.amountMinor, plan.currency),
                color = palette.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
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
    }
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
        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(palette.surface)
            .padding(vertical = 4.dp),
        content = content
    )
}

internal enum class MonetizationTone { Neutral, Warning, Error }

@Composable
internal fun MonetizationStateCard(
    title: String,
    body: String,
    tone: MonetizationTone = MonetizationTone.Neutral,
    onRetry: (() -> Unit)? = null
) {
    val palette = beyGramPalette()
    val background = when (tone) {
        MonetizationTone.Warning -> palette.warningSurface
        MonetizationTone.Error -> palette.warningSurface
        MonetizationTone.Neutral -> palette.surface
    }
    val titleColor = when (tone) {
        MonetizationTone.Error -> palette.danger
        MonetizationTone.Warning -> palette.warningText
        MonetizationTone.Neutral -> palette.textPrimary
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .semantics(mergeDescendants = true) {}
    ) {
        Text(title, color = titleColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(body, color = palette.textPrimary, fontSize = 14.sp, lineHeight = 20.sp)
        if (onRetry != null) {
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.common_retry))
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
