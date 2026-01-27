package org.maksec.messengerscommonfeature.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import maksec.shared.generated.resources.*
import maksec.shared.generated.resources.Res.drawable
import maksec.shared.generated.resources.Res.string
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.maksec.shared.data.db.messengers.DangerLevel
import org.maksec.shared.di.AppPreferences
import org.maksec.shared.domain.LicenseFeature
import org.maksec.messengerscommonfeature.navigation.components.*
import org.maksec.messengerscommonfeature.navigation.events.MessagesSettingsLandingEvent
import org.maksec.shared.Platform
import org.maksec.shared.currentPlatform
import org.maksec.shared.screens.util.*


@Composable
fun MessagesSettingsLandingScreen(component: MessagesSettingsLandingComponent) {
//    val context = LocalContext.current
    val colors = LocalAppColorScheme.current

    // Telegram
    val authorizedPhoneNumberTG by component.authorizedPhoneNumberTG.collectAsState()

    // Relative
    val authorizedPhoneNumberRelative by component.authorizedPhoneNumberRelative.collectAsState()

    val exceptions by component.exceptionsFlow.collectAsState(initial = null)

    val dialogState by component.dialogState.collectAsState()
    val isDefendWhatsappEnabled by component.isDefendWhatsappEnabled.collectAsState()
    val isDefendMaxEnabled by component.isDefendMaxEnabled.collectAsState()
    val isDefendSMSEnabled by component.isDefendSMSEnabled.collectAsState()
    val isNotifyRelativeEnabled by component.notifyRelative.collectAsState()



    val licenseManager = component.licenseManager

    //Other settings:
    val isNotifyCriticalEnabled by AppPreferences.getBooleanSettingFlow(AppPreferences.BooleanSetting.NOTIFY_MESSENGERS_CRITICAL)
        .collectAsState(initial = false)

    val isAutoBlockCriticalTGEnabled by AppPreferences.getBooleanSettingFlow(AppPreferences.BooleanSetting.AUTO_BLOCK_CRITICAL_TG)
        .collectAsState(initial = false)

    val isNotifySuspiciousEnabled by AppPreferences.getBooleanSettingFlow(AppPreferences.BooleanSetting.NOTIFY_MESSENGERS_SUSPICIOUS)
        .collectAsState(initial = false)

    val isAutoBlockSuspiciousTGEnabled by AppPreferences.getBooleanSettingFlow(AppPreferences.BooleanSetting.AUTO_BLOCK_SUSPICIOUS_TG)
        .collectAsState(initial = false)

    val selectedAnalyzeOption by component.selectedAnalyzeOption.collectAsState()
    val isCriticalEnabled by AppPreferences.getBooleanSettingFlow(AppPreferences.BooleanSetting.NOTIFY_MESSENGERS_CRITICAL)
        .collectAsState(initial = false)

    val isSuspiciousEnabled by AppPreferences.getBooleanSettingFlow(AppPreferences.BooleanSetting.NOTIFY_MESSENGERS_SUSPICIOUS)
        .collectAsState(initial = false)


    ScaffoldThemed(
        title = stringResource(string.settings),
        onBackClick = {
            component.onEvent(MessagesSettingsLandingEvent.NavigateToMessagesLandingScreen)
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(64.dp))
            }

            val hasLicenseForSMS = licenseManager.currentLicenseHasFeature(LicenseFeature.SMS_ANALYSIS)
            val hasLicenseForWhatsApp = licenseManager.currentLicenseHasFeature(LicenseFeature.WHATSAPP_ANALYSIS)
            val hasLicenseForTelegram = licenseManager.currentLicenseHasFeature(LicenseFeature.TELEGRAM_ANALYSIS)
            val hasLicenseForDeleteAndBlock =
                licenseManager.currentLicenseHasFeature(LicenseFeature.MESSAGE_DELETE_AND_BLOCK)
            val hasLicenseForMax = licenseManager.currentLicenseHasFeature(LicenseFeature.MAX_ANALYSIS)

            item { //Connected accounts
                AppCard(
                    gap = 12.dp,
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(string.connected_accounts),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.tertiary,
                        )
                        HintButton(
                            hint = stringResource(string.connected_accounts_hint)
                        )
                    }

                    Column( // WhatsApp and Telegram
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RowToggle(
                            // Sms toggle
                            iconPainter = painterResource(drawable.sms),
                            label = stringResource(string.sms),
                            isChecked = isDefendSMSEnabled,
                            enabled = hasLicenseForSMS,
                        ) { newValue ->
                            if (currentPlatform() == Platform.Ios)
                                component.onEvent(MessagesSettingsLandingEvent.ClickSms)
                            else
                                component.onEvent(MessagesSettingsLandingEvent.ToggleDefendSMS(newValue))
                        }
                        if (currentPlatform() == Platform.Android) {
                            RowToggle( // WhatsApp toggle
                                iconPainter = painterResource(drawable.whatsapp),
                                label = stringResource(string.whatsapp),
                                isChecked = isDefendWhatsappEnabled,
                                enabled = hasLicenseForWhatsApp
                            ) { newValue ->
                                component.onEvent(MessagesSettingsLandingEvent.ToggleDefendWhatsapp(newValue))
                            }
                            RowToggle(
                                // Max toggle
                                iconPainter = painterResource(drawable.max_logo),
                                label = stringResource(string.max),
                                isChecked = isDefendMaxEnabled,
                                enabled = hasLicenseForMax,
                            ) { newValue ->
                                component.onEvent(MessagesSettingsLandingEvent.ToggleDefendMax(newValue))
                            }
                        }
                        Column( // Telegram
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(drawable.telegram),
                                        tint = colors.secondary,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = stringResource(string.telegram),
                                        color = colors.surfaceVariant,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                                val (displayText, icon, iconColor) = if (authorizedPhoneNumberTG.isNullOrBlank())
                                    Triple(
                                        stringResource(string.not_connected),
                                        painterResource(drawable.cross),
                                        colors.errorContainer
                                    )
                                else Triple(
                                    stringResource(string.connected),
                                    painterResource(drawable.checkmark),
                                    Color(0xff00af66)
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        painter = icon,
                                        tint = iconColor,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = displayText,
                                        color = if (hasLicenseForTelegram) colors.surfaceVariant else colors.surfaceVariant.copy(
                                            alpha = 0.4f
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            DisabledTextField(
                                // Telegram Phone number
                                value = authorizedPhoneNumberTG ?: "",
                                onChangePressed = { component.onEvent(MessagesSettingsLandingEvent.ClickChangeNumberTG) },
                                modifier = Modifier.fillMaxWidth(),
                                enabledChangeButton = hasLicenseForTelegram,
                            )
                        }
                    }
                }
            }
            item { // Whose messages to analyze card
                RadioButtonsCard(
                    title = stringResource(string.whose_messages_to_analyze),
                    options = listOf(
                        AnalyzeSourceOption.ALL to stringResource(string.all_messages),
                        AnalyzeSourceOption.CONTACTS_ONLY to stringResource(string.my_contacts),
                        AnalyzeSourceOption.STRANGERS_ONLY to stringResource(string.only_unknown)
                    ),
                    selectedOption = selectedAnalyzeOption,
                    onOptionSelected = {
                        component.onEvent(MessagesSettingsLandingEvent.NewAnalyzeOptionSelected(it))
                    }
                )
            }

            item { //Relative notification
                AppCard(
                    gap = 12.dp,
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(string.notify_relative_via_sms),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.tertiary,
                        )
                        HintButton(
                            hint = stringResource(string.notify_relative_hint)
                        )
                    }
                    Row( // Row with connected relative phone and a switch
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DisabledTextField(
                            value = authorizedPhoneNumberRelative ?: stringResource(string.not_connected),
                            onChangePressed = { component.onEvent(MessagesSettingsLandingEvent.ClickChangeNumberRelative) },
                            modifier = Modifier.weight(1f),
                            enabledChangeButton = licenseManager.currentLicenseHasFeature(LicenseFeature.NOTIFY_RELATIVE)
                        )
                        CustomSwitch(
                            checked = isNotifyRelativeEnabled,
                            onClicked = { newValue ->
                                component.onEvent(MessagesSettingsLandingEvent.ToggleNotifyRelative(newValue))
                            },
                            enabled = !authorizedPhoneNumberRelative.isNullOrBlank() &&
                                    licenseManager.currentLicenseHasFeature(LicenseFeature.NOTIFY_RELATIVE)
                        )
                    }
                }
            }

            item { //Notification settings
                AppCard(
                    gap = 12.dp,
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(string.send_notifications),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.tertiary,
                        )
                        HintButton(
                            hint = stringResource(string.notifications_hint)
                        )
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        SettingToggleRow(
                            label = stringResource(string.critical),
                            labelColor = colors.errorContainer,
                            isChecked = isNotifyCriticalEnabled,
                            onCheckedChange = {
                                component.onEvent(
                                    MessagesSettingsLandingEvent.BooleanSettingChanged(
                                        setting = AppPreferences.BooleanSetting.NOTIFY_MESSENGERS_CRITICAL,
                                        newValue = it
                                    )
                                )
                            }
                        )
                        SettingToggleRow(
                            label = stringResource(string.suspicious),
                            labelColor = Color(0xfffab607),
                            isChecked = isNotifySuspiciousEnabled,
                            onCheckedChange = {
                                component.onEvent(
                                    MessagesSettingsLandingEvent.BooleanSettingChanged(
                                        setting = AppPreferences.BooleanSetting.NOTIFY_MESSENGERS_SUSPICIOUS,
                                        newValue = it
                                    )
                                )
                            }
                        )
                    }
                }
            }
            if (hasLicenseForDeleteAndBlock) {
                item { //Auto Threat Block title
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(string.auto_block),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.tertiary,
                        )
                    }
                }
                item {
                    AutoBlockSection( // Telegram Auto block section
                        icon = drawable.telegram,
                        messengerSource = MessengerSource.TELEGRAM,
                        label = stringResource(string.telegram),
                        isCriticalChecked = isAutoBlockCriticalTGEnabled,
                        isSuspiciousChecked = isAutoBlockSuspiciousTGEnabled,
                        onCriticalChanged = {
                            component.onEvent(
                                MessagesSettingsLandingEvent.BooleanSettingChanged(
                                    setting = AppPreferences.BooleanSetting.AUTO_BLOCK_CRITICAL_TG,
                                    newValue = it
                                )
                            )
                        },
                        onSuspiciousChanged = {
                            component.onEvent(
                                MessagesSettingsLandingEvent.BooleanSettingChanged(
                                    setting = AppPreferences.BooleanSetting.AUTO_BLOCK_SUSPICIOUS_TG,
                                    newValue = it
                                )
                            )
                        },
                        onExceptionsClick = {
                            component.onEvent(MessagesSettingsLandingEvent.ClickExceptions(source = MessengerSource.TELEGRAM))
                        },
                        exceptions = exceptions
                    )
                }
                if (hasLicenseForSMS && hasLicenseForDeleteAndBlock) {
                    item {
                        SmsAutoBlockSection(
                            component = component,
                            isCriticalEnabled = isCriticalEnabled,
                            isSuspiciousEnabled = isSuspiciousEnabled,
                            exceptions = exceptions
                        )
                    }
                }
            }

            if (licenseManager.currentLicenseHasFeature(LicenseFeature.SIGNATURE_DB_ANALYSIS))
                item {
                    ButtonPrimary(
                        text = stringResource(string.full_signature_sync),
                        onClick = {
                            component.onEvent(MessagesSettingsLandingEvent.ClickPatternSync)
                        }
                    )
                }
            item {
                Spacer(modifier = Modifier.height(90.dp))
            }
        }

        when (val state = dialogState) {
            is MessagesSettingsLandingDialogState.None -> Unit
            is MessagesSettingsLandingDialogState.ExceptionDialog -> {
                AddAutoBlockExceptionsDialog(
                    source = state.source,
                    currentExceptions = state.currentExceptions,
                    onConfirm = { updatedMap ->
                        component.onEvent(MessagesSettingsLandingEvent.UpdateExceptions(state.source, updatedMap))
                        component.onEvent(MessagesSettingsLandingEvent.DismissDialogue)
                    },
                    onDismiss = {
                        component.onEvent(MessagesSettingsLandingEvent.DismissDialogue)
                    },
                )
            }

            MessagesSettingsLandingDialogState.AvailableWithPremium -> {
                ConfirmDialogBig(
                    title = stringResource(string.error_unavailable),
                    firstButtonText = stringResource(string.available_with_premium),
                    onFirstButtonClicked = { component.onEvent(MessagesSettingsLandingEvent.DismissDialogue) },
                    onCloseRequest = { component.onEvent(MessagesSettingsLandingEvent.DismissDialogue) },
                )
            }
            is MessagesSettingsLandingDialogState.SmsDialog -> {
                ConfirmDialogSmall(
                    title = "СМС Фильтрация",
                    description = "Для корректной работы модуля вам необходимо подключить фильтрацию Настройки -> Приложения -> Сообщения -> Неизвестные и спам -> Выбрать MakSec",
                    confirmText = "Ок",
                    dismissText = "Закрыть",
                    onConfirm = { component.onEvent(MessagesSettingsLandingEvent.DismissDialogue) },
                    onCloseRequest = { component.onEvent(MessagesSettingsLandingEvent.DismissDialogue) },
                    onDismiss = { component.onEvent(MessagesSettingsLandingEvent.DismissDialogue) },
                )
            }
        }
    }
}


@Composable
fun AutoBlockSection(
    messengerSource: MessengerSource,
    icon: DrawableResource,
    label: String,
    isCriticalChecked: Boolean,
    isSuspiciousChecked: Boolean,
    onCriticalChanged: (Boolean) -> Unit,
    onSuspiciousChanged: (Boolean) -> Unit,
    onExceptionsClick: () -> Unit,
    modifier: Modifier = Modifier,
    exceptions: AutoBlockExceptions?,
    hideExceptions: Boolean = false
) {
    val colors = LocalAppColorScheme.current

    AppCard(
        gap = 12.dp,
        contentPadding = PaddingValues(16.dp),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                painter = painterResource(icon),
                tint = colors.secondary,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.surfaceVariant
            )
        }

        SettingToggleRow(
            label = stringResource(string.critical),
            labelColor = colors.errorContainer,
            isChecked = isCriticalChecked,
            onCheckedChange = onCriticalChanged,
        )

        SettingToggleRow(
            label = stringResource(string.suspicious),
            labelColor = Color(0xFFFAB607),
            isChecked = isSuspiciousChecked,
            onCheckedChange = onSuspiciousChanged,
        )

        if (hideExceptions)
            return@AppCard

        TextButtonThemed(
            text = stringResource(string.exceptions),
            textStyle = MaterialTheme.typography.titleMedium,
            onClick = onExceptionsClick,
            modifier = Modifier.clickable { onExceptionsClick() }
        )
        if (exceptions != null && exceptions.flags.any { it.key.first == messengerSource && it.value }) {
            val relevantFlags = exceptions.flags.filter { it.key.first == messengerSource && it.value }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(relevantFlags.toList()) { (key, _) ->
                    val threatType = key.second
                    val (labelText, color) = getLabelAndColor(threatType)
                    ResultLabel(
                        text = labelText,
                        color = color
                    )
                }
            }
        }
    }
}

@Composable
fun AddAutoBlockExceptionsDialog(
    source: MessengerSource,
    currentExceptions: AutoBlockExceptions,
    onConfirm: (Map<LabelType, Boolean>) -> Unit,
    onDismiss: () -> Unit,
) {
    val labelTypes = LabelType.entries.toTypedArray()

    val checkboxStates = remember(labelTypes, currentExceptions) {
        labelTypes.associateWith { threat ->
            mutableStateOf(currentExceptions.flags[source to threat] ?: false)
        }
    }
    ConfirmDialogSmall(
        title = stringResource(string.add_exceptions),
        confirmText = stringResource(string.save),
        dismissText = stringResource(string.cancel),
        onConfirm = {
            val result = checkboxStates.mapValues { it.value.value }
            onConfirm(result)
        },
        onCloseRequest = onDismiss,
        onDismiss = onDismiss,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            labelTypes.forEach { threat ->
                if (threat.toDangerLevel() == DangerLevel.SAFE) return@forEach
                val (labelText, color) = getLabelAndColor(threat)

                CheckBoxWithLabelRow(
                    text = labelText,
                    color = color,
                    checked = checkboxStates[threat]?.value ?: false,
                    onCheckedChange = { isChecked ->
                        checkboxStates[threat]?.value = isChecked
                    },
                )
            }
        }
    }
}

@Composable
fun SmsAutoBlockSection(
    component: MessagesSettingsLandingComponent,
    isCriticalEnabled: Boolean,
    isSuspiciousEnabled: Boolean,
    exceptions: AutoBlockExceptions?
) {
    val colors = LocalAppColorScheme.current

    AppCard(
        gap = 12.dp,
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                painter = painterResource(drawable.sms),
                tint = colors.secondary,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = stringResource(string.sms),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.surfaceVariant
            )
        }

        SettingToggleRow(
            label = "SMS (critical)",
            labelColor = colors.errorContainer,
            isChecked = isCriticalEnabled,
            onCheckedChange = { newValue ->
                component.onEvent(
                    MessagesSettingsLandingEvent.BooleanSettingChanged(
                        setting = AppPreferences.BooleanSetting.NOTIFY_MESSENGERS_CRITICAL,
                        newValue = newValue
                    )
                )
            }
        )

        SettingToggleRow(
            label = "SMS (suspicious)",
            labelColor = Color(0xFFFAB607),
            isChecked = isSuspiciousEnabled,
            onCheckedChange = { newValue ->
                component.onEvent(
                    MessagesSettingsLandingEvent.BooleanSettingChanged(
                        setting = AppPreferences.BooleanSetting.NOTIFY_MESSENGERS_SUSPICIOUS,
                        newValue = newValue
                    )
                )
            }
        )



        // Кнопка исключений
        TextButtonThemed(
            text = stringResource(string.exceptions),
            textStyle = MaterialTheme.typography.titleMedium,
            onClick = {
                component.onEvent(
                    MessagesSettingsLandingEvent.ClickExceptions(source = MessengerSource.SMS)
                )
            },
            modifier = Modifier.clickable {
                component.onEvent(
                    MessagesSettingsLandingEvent.ClickExceptions(source = MessengerSource.SMS)
                )
            }
        )

        // ✅ ОСТАВЛЯЕМ отображение активных исключений
        if (exceptions != null && exceptions.flags.any { it.key.first == MessengerSource.SMS && it.value }) {
            val relevantFlags = exceptions.flags.filter { it.key.first == MessengerSource.SMS && it.value }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(relevantFlags.toList()) { (key, _) ->
                    val threatType = key.second
                    val (labelText, color) = getLabelAndColor(threatType)
                    ResultLabel(
                        text = labelText,
                        color = color
                    )
                }
            }
        }
    }
}
