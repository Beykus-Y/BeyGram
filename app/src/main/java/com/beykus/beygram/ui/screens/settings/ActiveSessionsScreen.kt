package com.beykus.beygram.ui.screens.settings

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.TabletMac
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ActiveSessions
import com.beykus.beygram.domain.model.DeviceSession
import com.beykus.beygram.domain.model.SessionDeviceKind
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.BeyGramDialog
import com.beykus.beygram.ui.components.BeyGramSheet
import com.beykus.beygram.ui.components.Button
import com.beykus.beygram.ui.components.OutlinedButton
import com.beykus.beygram.ui.components.Switch
import com.beykus.beygram.ui.components.TextButton
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.settingsIconTint
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.glassScreenBackground
import com.beykus.beygram.ui.design.styledShape
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Inactivity periods offered by the picker, in days: Telegram's usual choices. */
private val inactiveTtlOptions = listOf(7, 30, 90, 180, 365)

/** Current device first, then sessions that need attention (unconfirmed, then waiting for 2FA), then the most recent. */
private val sessionOrder: Comparator<DeviceSession> = compareByDescending<DeviceSession> { it.current }
    .thenByDescending { it.unconfirmed }
    .thenByDescending { it.passwordPending }
    .thenByDescending { it.lastActive }

/** Where a divider between two rows starts: the row padding, the 40dp device icon and its 14dp gap. */
private val sessionRowDividerInset: Dp = 70.dp

/**
 * Telegram sessions of the selected account, read from TDLib. The current device is shown apart and is never offered
 * for termination. A row opens the details sheet, which holds the confirm, permission and termination actions. The
 * inactivity limit is the server's value and is changed on the server.
 *
 * Every write runs one at a time. A write that gets no answer is not reported as done or failed: the list is read
 * again, and a termination keeps its confirm button off until that fresh list has been read.
 */
@Composable
fun ActiveSessionsScreen(
    onBack: () -> Unit,
    load: suspend () -> Result<ActiveSessions>,
    terminate: suspend (Long) -> Result<Unit>,
    terminateAll: suspend () -> Result<Unit>,
    confirmSession: suspend (Long) -> Result<Unit>,
    setInactiveTtl: suspend (Int) -> Result<Unit>,
    setCanAcceptCalls: suspend (Long, Boolean) -> Result<Unit>,
    setCanAcceptSecretChats: suspend (Long, Boolean) -> Result<Unit>
) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val unknown = stringResource(R.string.sessions_unknown_device)
    val timeoutText = stringResource(R.string.sessions_action_timeout)
    var sessions by remember { mutableStateOf<List<DeviceSession>>(emptyList()) }
    var inactiveTtlDays by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    // The termination dialog targets one device ([target]) or every other device ([confirmAll]).
    var target by remember { mutableStateOf<Long?>(null) }
    var confirmAll by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    // Set when a termination got no answer. Its confirm button stays off until a fresh list has been read.
    var uncertain by remember { mutableStateOf(false) }
    var detailId by remember { mutableStateOf<Long?>(null) }
    var detailError by remember { mutableStateOf<String?>(null) }
    var ttlOpen by remember { mutableStateOf(false) }
    var ttlError by remember { mutableStateOf<String?>(null) }

    fun closeDialog() {
        target = null
        confirmAll = false
        actionError = null
        uncertain = false
    }

    /**
     * Runs one write. Its answer is the server's result or a timeout; a timeout is neither success nor failure, so
     * [onTimeout] decides what is shown and the caller re-reads the list.
     */
    fun runWrite(
        onError: (String) -> Unit,
        onDone: () -> Unit,
        onTimeout: () -> Unit,
        write: suspend () -> Result<Unit>
    ) {
        // A write during a reload could be overwritten by the late list.
        if (busy || loading) return
        busy = true
        scope.launch {
            try {
                when (val request = awaitSessionRequest(SESSION_ACTION_TIMEOUT_MS, write)) {
                    is SessionAwait.Completed -> request.result.fold(
                        { onDone() },
                        { onError(it.userMessage(context, R.string.feature_action_failed)) }
                    )
                    SessionAwait.TimedOut -> onTimeout()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                onError(failure.userMessage(context, R.string.feature_action_failed))
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(refresh) {
        loading = true
        error = null
        try {
            when (val request = awaitSessionRequest(SESSION_LOAD_TIMEOUT_MS, load)) {
                is SessionAwait.Completed -> request.result.fold(
                    { loaded ->
                        sessions = loaded.sessions.sortedWith(sessionOrder)
                        inactiveTtlDays = loaded.inactiveSessionTtlDays
                        // The list just read is the server's answer, so the confirm button may come back.
                        uncertain = false
                    },
                    { error = it.userMessage(context, R.string.feature_action_failed) }
                )
                SessionAwait.TimedOut -> error = context.getString(R.string.sessions_load_timeout)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.userMessage(context, R.string.feature_action_failed)
        } finally {
            loading = false
        }
    }

    LaunchedEffect(sessions) {
        // Neither the details sheet nor the termination dialog may stay on a device the server no longer lists.
        val open = detailId
        if (open != null && sessions.none { it.id == open }) detailId = null
        if (!busy) {
            val dialogTarget = target
            if (dialogTarget != null && sessions.none { it.id == dialogTarget && !it.current }) closeDialog()
            if (confirmAll && sessions.none { !it.current }) closeDialog()
        }
    }

    fun askTerminate(id: Long) {
        target = id
        confirmAll = false
        actionError = null
        uncertain = false
    }

    fun askTerminateAll() {
        target = null
        confirmAll = true
        actionError = null
        uncertain = false
    }

    fun terminateConfirmed() {
        if (busy) return
        actionError = null
        if (confirmAll) {
            runWrite(
                onError = { actionError = it },
                onDone = { closeDialog(); refresh++ },
                onTimeout = { actionError = timeoutText; uncertain = true; refresh++ }
            ) { terminateAll() }
        } else {
            val id = target ?: return
            runWrite(
                onError = { actionError = it },
                onDone = { closeDialog(); refresh++ },
                onTimeout = { actionError = timeoutText; uncertain = true; refresh++ }
            ) { terminate(id) }
        }
    }

    fun confirmThisDevice(id: Long) {
        detailError = null
        runWrite(
            onError = { detailError = it },
            onDone = { refresh++ },
            onTimeout = { detailError = timeoutText; refresh++ }
        ) { confirmSession(id) }
    }

    fun setCalls(id: Long, enabled: Boolean) {
        detailError = null
        runWrite(
            onError = { detailError = it },
            onDone = { refresh++ },
            onTimeout = { detailError = timeoutText; refresh++ }
        ) { setCanAcceptCalls(id, enabled) }
    }

    fun setSecretChats(id: Long, enabled: Boolean) {
        detailError = null
        runWrite(
            onError = { detailError = it },
            onDone = { refresh++ },
            onTimeout = { detailError = timeoutText; refresh++ }
        ) { setCanAcceptSecretChats(id, enabled) }
    }

    fun applyTtl(days: Int) {
        if (days == inactiveTtlDays) {
            ttlOpen = false
            return
        }
        ttlError = null
        runWrite(
            onError = { ttlError = it },
            onDone = { ttlOpen = false; refresh++ },
            onTimeout = { ttlError = timeoutText; refresh++ }
        ) { setInactiveTtl(days) }
    }

    val current = sessions.firstOrNull { it.current }
    val others = sessions.filterNot { it.current }
    val detail = sessions.firstOrNull { it.id == detailId }
    val chosen = sessions.firstOrNull { it.id == target }

    Column(
        Modifier.fillMaxSize()
            .glassScreenBackground(design, palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        ProfileToolbar(stringResource(R.string.feature_sessions), onBack) {
            IconButton(enabled = !loading && !busy, onClick = { refresh++ }) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.common_refresh), tint = palette.textPrimary)
            }
        }
        // The bar always has its height, so it appears and disappears without moving the list.
        Box(Modifier.fillMaxWidth().height(4.dp)) {
            if (loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.sessions_intro),
                    color = palette.textSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 14.dp)
                )
            }
            error?.let { message ->
                item {
                    Surface(
                        shape = styledShape(16.dp),
                        color = palette.warningSurface,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(message, color = palette.warningText, modifier = Modifier.errorAnnouncement())
                            TextButton(enabled = !loading && !busy, onClick = { refresh++ }) {
                                Text(stringResource(R.string.common_retry), color = palette.accentInk)
                            }
                        }
                    }
                }
            }
            // The first load shows a spinner in place of the empty list, so an empty screen never looks final.
            if (loading && sessions.isEmpty() && error == null) {
                item {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = palette.accent)
                    }
                }
            }
            if (!loading && sessions.isEmpty() && error == null) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.sessions_empty), style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                        TextButton(onClick = { refresh++ }) { Text(stringResource(R.string.common_refresh)) }
                    }
                }
            }
            current?.let { session ->
                item {
                    SettingsGroup(stringResource(R.string.feature_current_device)) {
                        SessionRow(session, onOpen = { detailId = session.id; detailError = null })
                    }
                }
            }
            if (sessions.isNotEmpty()) {
                item {
                    SettingsGroup(stringResource(R.string.sessions_other_count, others.size)) {
                        if (others.isEmpty()) {
                            Text(
                                stringResource(R.string.sessions_no_other_devices),
                                color = palette.textSecondary,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                            )
                        } else {
                            others.forEachIndexed { index, session ->
                                if (index > 0) SettingsRowDivider(startInset = sessionRowDividerInset)
                                SessionRow(session, onOpen = { detailId = session.id; detailError = null })
                            }
                        }
                    }
                }
                item {
                    SettingsGroup(stringResource(R.string.sessions_settings_group)) {
                        SessionValueRow(
                            icon = Icons.Rounded.Timer,
                            title = stringResource(R.string.sessions_auto_terminate_title),
                            value = inactiveTtlDays?.let { inactiveTtlLabel(it) } ?: stringResource(R.string.sessions_value_unknown),
                            enabled = !busy && inactiveTtlDays != null,
                            onClick = { ttlError = null; ttlOpen = true }
                        )
                    }
                }
            }
            if (others.isNotEmpty()) {
                item {
                    OutlinedButton(
                        onClick = ::askTerminateAll,
                        enabled = !busy && !loading,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp)
                    ) {
                        Text(stringResource(R.string.feature_terminate_other_sessions), color = palette.danger)
                    }
                }
            }
        }
    }

    if (target != null || confirmAll) {
        BeyGramDialog(
            onDismissRequest = { if (!busy) closeDialog() },
            title = stringResource(if (confirmAll) R.string.feature_terminate_other_sessions else R.string.feature_terminate_session),
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (confirmAll) {
                        Text(stringResource(R.string.sessions_other_count, others.size), fontWeight = FontWeight.SemiBold)
                    } else {
                        Text(chosen?.let { sessionTitle(it, unknown) } ?: unknown, fontWeight = FontWeight.SemiBold)
                    }
                    Text(stringResource(if (confirmAll) R.string.feature_terminate_all_warning else R.string.feature_terminate_warning))
                    actionError?.let { Text(it, color = palette.danger, modifier = Modifier.errorAnnouncement()) }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
                }
            },
            confirmText = stringResource(R.string.feature_confirm),
            // While a timed-out termination is unresolved, a second tap could end a device that is already gone.
            confirmEnabled = !busy && !uncertain,
            destructive = true,
            onConfirm = ::terminateConfirmed,
            dismissText = stringResource(R.string.common_cancel),
            dismissEnabled = !busy,
            onDismiss = ::closeDialog
        )
    }

    if (detail != null) {
        SessionDetailsSheet(
            session = detail,
            busy = busy,
            error = detailError,
            // The sheet is already hidden when this fires; a running write keeps going.
            onDismiss = { detailId = null; detailError = null },
            onConfirm = { confirmThisDevice(detail.id) },
            onTerminate = { askTerminate(detail.id) },
            onCanAcceptCallsChange = { setCalls(detail.id, it) },
            onCanAcceptSecretChatsChange = { setSecretChats(detail.id, it) }
        )
    }

    if (ttlOpen) {
        InactiveTtlSheet(
            currentDays = inactiveTtlDays,
            busy = busy,
            error = ttlError,
            onDismiss = { ttlOpen = false; ttlError = null },
            onPick = ::applyTtl
        )
    }
}

/** One session in a settings group: device icon, model, client and system, activity and a status when it matters. */
@Composable
private fun SessionRow(session: DeviceSession, onOpen: () -> Unit) {
    val palette = beyGramPalette()
    val unknown = stringResource(R.string.sessions_unknown_device)
    val kindLabel = stringResource(sessionKindLabelRes(session.kind))
    val title = sessionTitle(session, unknown)
    val subtitle = sessionSubtitle(session)
    val activity = sessionActivityText(session.lastActive)
    val statusText = when {
        session.current -> stringResource(R.string.sessions_current)
        session.unconfirmed -> stringResource(R.string.sessions_status_unconfirmed)
        session.passwordPending -> stringResource(R.string.sessions_status_password_pending)
        else -> null
    }
    // Warning colours mark an unconfirmed login; a waiting 2FA sign-in is neutral, and the current device takes the accent.
    val (statusContainer, statusContent) = when {
        session.current -> palette.accentSoft to palette.accentInk
        session.unconfirmed -> palette.warningSurface to palette.warningText
        else -> palette.surface2 to palette.textSecondary
    }
    Row(
        Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.sessions_open_details), onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).background(palette.accentSoft, styledShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                sessionKindIcon(session.kind),
                contentDescription = kindLabel,
                tint = settingsIconTint(),
                modifier = Modifier.size(22.dp)
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(
                title,
                color = palette.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (statusText != null) {
                    SessionStatusPill(statusText, statusContainer, statusContent)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    activity,
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(22.dp))
    }
}

/**
 * The details of one session. It shows what the server recorded about the sign-in. Place and IP come from the
 * authorization and are labelled that way, never as the device's current location.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionDetailsSheet(
    session: DeviceSession,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onTerminate: () -> Unit,
    onCanAcceptCallsChange: (Boolean) -> Unit,
    onCanAcceptSecretChatsChange: (Boolean) -> Unit
) {
    val palette = beyGramPalette()
    val unknown = stringResource(R.string.sessions_unknown_device)
    val notSpecified = stringResource(R.string.sessions_value_unknown)
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val kindLabel = stringResource(sessionKindLabelRes(session.kind))
    val system = listOf(session.platform, session.systemVersion)
        .filter { it.isNotBlank() }.joinToString(" ").ifBlank { notSpecified }
    val application = listOf(session.application, session.applicationVersion)
        .filter { it.isNotBlank() }.joinToString(" ").ifBlank { notSpecified }
    val applicationLine = application + " · " +
        stringResource(if (session.officialApplication) R.string.sessions_app_official else R.string.sessions_app_unofficial)
    val loggedIn = if (session.logInDate > 0L) dateFormat.format(Date(session.logInDate * 1000L)) else notSpecified
    val lastActive = if (session.lastActive > 0L) dateFormat.format(Date(session.lastActive * 1000L)) else notSpecified

    BeyGramSheet(onDismissRequest = onDismiss, title = sessionTitle(session, unknown)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            if (session.current) {
                SessionStatusPill(
                    stringResource(R.string.sessions_current),
                    palette.accentSoft,
                    palette.accentInk,
                    Modifier.padding(bottom = 12.dp)
                )
            }
            if (session.unconfirmed) {
                SessionNotice(
                    stringResource(R.string.sessions_banner_unconfirmed),
                    palette.warningSurface,
                    palette.warningText,
                    Icons.Rounded.WarningAmber
                )
            } else if (session.passwordPending) {
                SessionNotice(
                    stringResource(R.string.sessions_banner_password_pending),
                    palette.surface2,
                    palette.textPrimary,
                    Icons.Rounded.Lock
                )
            }
            SessionDetailRow(stringResource(R.string.sessions_detail_model), session.device.ifBlank { notSpecified })
            SessionDetailRow(stringResource(R.string.sessions_detail_type), kindLabel)
            SessionDetailRow(stringResource(R.string.sessions_detail_system), system)
            SessionDetailRow(stringResource(R.string.sessions_detail_app), applicationLine)
            SessionDetailRow(stringResource(R.string.sessions_detail_logged_in), loggedIn)
            SessionDetailRow(stringResource(R.string.sessions_detail_last_active), lastActive)
            SessionDetailRow(stringResource(R.string.sessions_detail_ip), session.ip.ifBlank { notSpecified })
            SessionDetailRow(stringResource(R.string.sessions_detail_location), session.location.ifBlank { notSpecified })
            Text(
                stringResource(R.string.sessions_location_note),
                color = palette.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )
            Text(
                stringResource(R.string.sessions_permissions),
                color = palette.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp)
            )
            SessionSwitchRow(
                label = stringResource(R.string.sessions_permission_calls),
                checked = session.canAcceptCalls,
                enabled = !busy,
                onCheckedChange = onCanAcceptCallsChange
            )
            SessionSwitchRow(
                label = stringResource(R.string.sessions_permission_secret_chats),
                checked = session.canAcceptSecretChats,
                enabled = !busy,
                onCheckedChange = onCanAcceptSecretChatsChange
            )
            error?.let {
                Text(
                    it,
                    color = palette.danger,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp).errorAnnouncement()
                )
            }
            if (session.unconfirmed) {
                Button(onClick = onConfirm, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text(stringResource(R.string.sessions_confirm_it_is_me))
                }
            }
            if (!session.current) {
                OutlinedButton(
                    onClick = onTerminate,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text(stringResource(R.string.feature_terminate_session), color = palette.danger)
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(4.dp)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
            }
        }
    }
}

/** The inactivity limit, chosen from the usual periods. A value set elsewhere is listed too, as its own choice. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InactiveTtlSheet(
    currentDays: Int?,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit
) {
    val palette = beyGramPalette()
    val options = if (currentDays != null && currentDays !in inactiveTtlOptions) {
        (inactiveTtlOptions + currentDays).sorted()
    } else {
        inactiveTtlOptions
    }
    BeyGramSheet(onDismissRequest = onDismiss, title = stringResource(R.string.sessions_auto_terminate_title)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.sessions_auto_terminate_note), color = palette.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            options.forEach { days ->
                SessionOptionRow(
                    label = inactiveTtlLabel(days),
                    selected = days == currentDays,
                    enabled = !busy,
                    onClick = { onPick(days) }
                )
            }
            error?.let {
                Text(
                    it,
                    color = palette.danger,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp).errorAnnouncement()
                )
            }
            Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(4.dp)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
            }
        }
    }
}

/** A settings-style row with an icon, a title, the current value and a chevron. */
@Composable
private fun SessionValueRow(icon: ImageVector, title: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = settingsIconTint(), modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 8.dp)) {
            Text(title, color = palette.textPrimary, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(value, color = palette.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SessionDetailRow(label: String, value: String) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, color = palette.textSecondary, fontSize = 13.sp)
        Text(value, color = palette.textPrimary, fontSize = 15.sp)
    }
}

@Composable
private fun SessionNotice(text: String, container: Color, content: Color, icon: ImageVector) {
    Row(
        Modifier.fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(styledShape(14.dp))
            .background(container)
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(text, color = content, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
    }
}

@Composable
private fun SessionStatusPill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = content,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.clip(styledShape(8.dp)).background(container).padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/** A switch row that is one toggle target for TalkBack, with the on or off state announced. */
@Composable
private fun SessionSwitchRow(label: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (enabled) palette.textPrimary else palette.textSecondary,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun SessionOptionRow(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (selected) palette.accentInk else palette.textPrimary,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = palette.accent, modifier = Modifier.size(22.dp))
    }
}

/** The device model; without one, the platform or the app name stands in, so a row is never blank. */
private fun sessionTitle(session: DeviceSession, unknown: String): String =
    session.device.ifBlank { session.platform.ifBlank { session.application.ifBlank { unknown } } }

/** "BeyGram 2.1 · Android 14": the client and the system, each only when the server sent it. */
private fun sessionSubtitle(session: DeviceSession): String {
    val application = listOf(session.application, session.applicationVersion).filter { it.isNotBlank() }.joinToString(" ")
    val system = listOf(session.platform, session.systemVersion).filter { it.isNotBlank() }.joinToString(" ")
    return listOf(application, system).filter { it.isNotBlank() }.joinToString(" · ")
}

/**
 * When the session was last used, as a short relative phrase. This is the server's last activity record, so the text
 * never claims that the device is online.
 */
@Composable
private fun sessionActivityText(lastActive: Long): String {
    if (lastActive <= 0L) return stringResource(R.string.sessions_activity_unknown)
    val millis = lastActive * 1000L
    val now = System.currentTimeMillis()
    val relative = if (now - millis < DateUtils.MINUTE_IN_MILLIS) {
        stringResource(R.string.sessions_activity_just_now)
    } else {
        DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
    }
    return stringResource(R.string.sessions_last_active, relative)
}

@Composable
private fun inactiveTtlLabel(days: Int): String = when (days) {
    7 -> stringResource(R.string.sessions_ttl_week)
    30 -> stringResource(R.string.sessions_ttl_month)
    90 -> stringResource(R.string.sessions_ttl_three_months)
    180 -> stringResource(R.string.sessions_ttl_six_months)
    365 -> stringResource(R.string.sessions_ttl_year)
    else -> stringResource(R.string.sessions_ttl_days, days)
}

private fun sessionKindIcon(kind: SessionDeviceKind): ImageVector = when (kind) {
    SessionDeviceKind.Android -> Icons.Rounded.PhoneAndroid
    SessionDeviceKind.IPhone -> Icons.Rounded.PhoneIphone
    SessionDeviceKind.IPad -> Icons.Rounded.TabletMac
    SessionDeviceKind.Mac -> Icons.Rounded.LaptopMac
    SessionDeviceKind.Apple, SessionDeviceKind.Unknown -> Icons.Rounded.Devices
    SessionDeviceKind.Windows -> Icons.Rounded.DesktopWindows
    SessionDeviceKind.Linux -> Icons.Rounded.Computer
    SessionDeviceKind.Xbox -> Icons.Rounded.SportsEsports
    SessionDeviceKind.Browser -> Icons.Rounded.Language
}

private fun sessionKindLabelRes(kind: SessionDeviceKind): Int = when (kind) {
    SessionDeviceKind.Android -> R.string.sessions_kind_android
    SessionDeviceKind.IPhone -> R.string.sessions_kind_iphone
    SessionDeviceKind.IPad -> R.string.sessions_kind_ipad
    SessionDeviceKind.Mac -> R.string.sessions_kind_mac
    SessionDeviceKind.Apple -> R.string.sessions_kind_apple
    SessionDeviceKind.Windows -> R.string.sessions_kind_windows
    SessionDeviceKind.Linux -> R.string.sessions_kind_linux
    SessionDeviceKind.Xbox -> R.string.sessions_kind_xbox
    SessionDeviceKind.Browser -> R.string.sessions_kind_browser
    SessionDeviceKind.Unknown -> R.string.sessions_kind_unknown
}
