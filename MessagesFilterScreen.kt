package org.maksec.messengerscommonfeature.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import maksec.shared.generated.resources.Res.string
import maksec.shared.generated.resources.*
import maksec.shared.generated.resources.Res.drawable
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.maksec.shared.data.Attachment
import org.maksec.shared.data.DateRange
import org.maksec.shared.data.FilterState
import org.maksec.shared.data.db.messengers.DangerLevel
import org.maksec.shared.screens.util.*
import org.maksec.shared.data.db.messengers.MessageAction
import org.maksec.shared.data.db.messengers.Tag
import org.maksec.messengerscommonfeature.navigation.components.*
import org.maksec.messengerscommonfeature.navigation.events.MessagesFilterEvent
import org.maksec.shared.Platform
import org.maksec.shared.currentPlatform


@Composable
fun MessagesFilterScreen(component: MessagesFilterComponent) {
    val colors = LocalAppColorScheme.current
    val filter by component.filter.collectAsState()
    val criticalCount by component.criticalCount.collectAsState()
    val suspiciousCount by component.suspiciousCount.collectAsState()
    val safeCount by component.safeCount.collectAsState()

    ScaffoldThemed(
        title = stringResource(string.filter),
        onBackClick = {
            component.onEvent(MessagesFilterEvent.GoToMessagesList)
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            overscrollEffect = null,
        ) {
            item {
                Spacer(modifier = Modifier.height(62.dp))
            }
            item { // Date filter
                val filters = listOf(DateRange.WEEK, DateRange.MONTH, DateRange.ALL)
                FilterButtonGroup(
                    filters = filters,
                    selectedFilter = filter.dateRange,
                    onFilterSelected = { filter -> component.onEvent(MessagesFilterEvent.SetDateRange(filter)) },
                    labelFor = {
                        when (it) {
                            DateRange.WEEK -> stringResource(string.week)
                            DateRange.MONTH -> stringResource(string.month)
                            DateRange.ALL -> stringResource(string.all)
                        }
                    }
                )
            }
            item {
                AppCard( // Threat filter
                    contentPadding = PaddingValues(16.dp),
                    gap = 12.dp,
                ) {
                    Text(
                        text = stringResource(string.threat_types) + ":",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.tertiary
                    )
                    ThreatLevelFilterRow(
                        filter = filter,
                        onEvent = component::onEvent,
                        criticalCount = criticalCount,
                        suspiciousCount = suspiciousCount,
                        safeCount = safeCount,
                    )
                }
            }
            item { // Platform filter
                AppCard(
                    contentPadding = PaddingValues(16.dp),
                    gap = 12.dp,
                ) {
                    Text(
                        text = stringResource(string.platform) + ":",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.tertiary
                    )
                    if (currentPlatform() == Platform.Android)
                        RowToggle( // SMS
                            iconPainter = painterResource(drawable.sms),
                            label = stringResource(string.sms),
                            isChecked = filter.includeSources.contains(MessengerSource.SMS)
                        ) { newValue ->
                            component.onEvent(MessagesFilterEvent.ToggleSource(MessengerSource.SMS, newValue))
                        }
                    RowToggle( // Telegram
                        iconPainter = painterResource(drawable.telegram),
                        label = stringResource(string.telegram),
                        isChecked = filter.includeSources.contains(MessengerSource.TELEGRAM)
                    ) { newValue ->
                        component.onEvent(MessagesFilterEvent.ToggleSource(MessengerSource.TELEGRAM, newValue))
                    }
                    if (currentPlatform() == Platform.Android) {
                        RowToggle( // Whatsapp
                            iconPainter = painterResource(drawable.whatsapp),
                            label = stringResource(string.whatsapp),
                            isChecked = filter.includeSources.contains(MessengerSource.WHATSAPP)
                        ) { newValue ->
                            component.onEvent(MessagesFilterEvent.ToggleSource(MessengerSource.WHATSAPP, newValue))
                        }
                        RowToggle( // Max
                            iconPainter = painterResource(drawable.max_logo),
                            label = stringResource(string.max),
                            isChecked = filter.includeSources.contains(MessengerSource.MAX)
                        ) { newValue ->
                            component.onEvent(MessagesFilterEvent.ToggleSource(MessengerSource.MAX, newValue))
                        }
                    }
                }
            }
            item {
                AppCard( // Tag filter
                    contentPadding = PaddingValues(16.dp),
                    gap = 12.dp,
                ) {
                    Text(
                        text = stringResource(string.tags) + ":",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.tertiary
                    )
                    TagFilterRow(
                        filter = filter,
                        onEvent = component::onEvent
                    )
                }
            }
            item { // Attachments filter
                AppCard(
                    contentPadding = PaddingValues(16.dp),
                    gap = 12.dp,
                ) {
                    Text(
                        text = stringResource(string.attachments) + ":",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.tertiary
                    )
                    RowToggle( // Photo
                        iconPainter = painterResource(drawable.image),
                        label = stringResource(string.photo),
                        isChecked = filter.includeAttachment.contains(Attachment.PHOTO)
                    ) { newValue ->
                        component.onEvent(MessagesFilterEvent.ToggleAttachment(Attachment.PHOTO, newValue))
                    }
                    RowToggle( // Documents
                        iconPainter = painterResource(drawable.document),
                        label = stringResource(string.documents),
                        isChecked = filter.includeAttachment.contains(Attachment.DOCUMENT)
                    ) { newValue ->
                        component.onEvent(MessagesFilterEvent.ToggleAttachment(Attachment.DOCUMENT, newValue))
                    }
                    RowToggle( // Links
                        iconPainter = painterResource(drawable.link),
                        label = stringResource(string.links),
                        isChecked = filter.includeAttachment.contains(Attachment.LINK)
                    ) { newValue ->
                        component.onEvent(MessagesFilterEvent.ToggleAttachment(Attachment.LINK, newValue))
                    }
                    RowToggle( // Voice messages
                        iconPainter = painterResource(drawable.microphone),
                        label = stringResource(string.voice_messages),
                        isChecked = filter.includeAttachment.contains(Attachment.VOICE_MESSAGE)
                    ) { newValue ->
                        component.onEvent(MessagesFilterEvent.ToggleAttachment(Attachment.VOICE_MESSAGE, newValue))
                    }
                }
            }
            item { // Actions filter
                AppCard(
                    contentPadding = PaddingValues(16.dp),
                    gap = 12.dp,
                ) {
                    RowToggle( // Blocked chats
                        label = stringResource(string.blocked_chats),
                        isChecked = filter.includeActions.contains(MessageAction.BLOCK)
                    ) { newValue ->
                        component.onEvent(MessagesFilterEvent.ToggleAction(MessageAction.BLOCK, newValue))
                    }
                }
            }
            item {
                ButtonSecondary( // Reset button
                    onClick = { component.onEvent(MessagesFilterEvent.ResetFilter) },
                    text = stringResource(string.reset)
                )
            }
            item {
                ButtonPrimary( // Apply button
                    onClick = { component.onEvent(MessagesFilterEvent.ApplyFilter) },
                    text = stringResource(string.apply)
                )
            }
            item {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}
@Composable
fun ThreatLevelFilterRow(
    filter: FilterState,
    onEvent: (MessagesFilterEvent) -> Unit,
    criticalCount: Int,
    suspiciousCount: Int,
    safeCount: Int,
) {
    val colors = LocalAppColorScheme.current

    val chips = listOf(
        FilterChipItem(
            id = DangerLevel.CRITICAL,
            text = stringResource(string.critical_plural),
            selected = filter.dangerLevels.contains(DangerLevel.CRITICAL),
            indicatorColor = colors.errorContainer,
            trailingCount = criticalCount,
            chipType = ChipType.Extended,
        ),
        FilterChipItem(
            id = DangerLevel.SUSPICIOUS,
            text = stringResource(string.suspicious_plural),
            selected = filter.dangerLevels.contains(DangerLevel.SUSPICIOUS),
            indicatorColor = Color(0xfffab607),
            trailingCount = suspiciousCount,
            chipType = ChipType.Extended,
        ),
        FilterChipItem(
            id = DangerLevel.SAFE,
            text = stringResource(string.safe_plural),
            selected = filter.dangerLevels.contains(DangerLevel.SAFE),
            indicatorColor = Color(0xff00af66),
            trailingCount = safeCount,
            chipType = ChipType.Extended,
        ),
    )

    FilterChipFlowRow(
        allSelected = filter.dangerLevels.containsAll(listOf(
            DangerLevel.CRITICAL,
            DangerLevel.SUSPICIOUS,
            DangerLevel.SAFE,
        )),
        items = chips,
        onAllToggle = { shouldSelectAll ->
            listOf(
                DangerLevel.CRITICAL,
                DangerLevel.SUSPICIOUS,
                DangerLevel.SAFE,
            ).forEach {
                onEvent(MessagesFilterEvent.ToggleDangerLevel(it, shouldSelectAll))
            }
        },
        onItemToggle = { id, selected ->
            onEvent(MessagesFilterEvent.ToggleDangerLevel(id, selected))
        }
    )
}

@Composable
fun TagFilterRow(
    filter: FilterState,
    onEvent: (MessagesFilterEvent) -> Unit
) {
    val chips = listOf(
        FilterChipItem(Tag.SAFE, "\uD83D\uDE42  ${stringResource(string.safe)}", filter.includeTags.contains(Tag.SAFE)),
        FilterChipItem(Tag.SPAM, "\uD83D\uDEAB  ${stringResource(string.spam)}", filter.includeTags.contains(Tag.SPAM)),
        FilterChipItem(Tag.SCAM, "\uD83D\uDCB0  ${stringResource(string.scam)}", filter.includeTags.contains(Tag.SCAM)),
        FilterChipItem(Tag.AD, "\uD83D\uDCE2  ${stringResource(string.ad)}", filter.includeTags.contains(Tag.AD)),
    )

    FilterChipFlowRow(
        allSelected = filter.includeTags.containsAll(listOf(Tag.SAFE, Tag.SCAM, Tag.SPAM, Tag.AD)),
        items = chips,
        onAllToggle = { shouldSelectAll ->
            listOf(Tag.SAFE, Tag.SCAM, Tag.SPAM, Tag.AD).forEach {
                onEvent(MessagesFilterEvent.ToggleTag(it, shouldSelectAll))
            }
        },
        onItemToggle = { tag, selected ->
            onEvent(MessagesFilterEvent.ToggleTag(tag, selected))
        }
    )
}
