package org.maksec.messengerscommonfeature.navigation.components

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnResume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import maksec.shared.generated.resources.Res.string
import maksec.shared.generated.resources.*
import org.jetbrains.compose.resources.getString
import org.koin.mp.KoinPlatform.getKoin
import org.maksec.shared.AppLogger
import org.maksec.shared.core.ServiceManager
import org.maksec.shared.core.unixSecondsNow
import org.maksec.shared.data.db.incidents.Module
import org.maksec.shared.data.db.incidents.toEntity
import org.maksec.shared.data.network.util.onError
import org.maksec.shared.data.network.util.onSuccess
import org.maksec.shared.data.network.util.translateErrorIfPossible
import org.maksec.shared.di.AppPreferences
import org.maksec.shared.di.LocalDaoHolder.incidentDao
import org.maksec.shared.domain.ApiClient
import org.maksec.shared.domain.LicenseFeature
import org.maksec.shared.domain.LicenseManager
import org.maksec.messengerscommonfeature.di.MessengersFeatureDependencies
import org.maksec.messengerscommonfeature.navigation.events.MessagesSettingsLandingEvent
import org.maksec.shared.data.db.incidents.saveSignaturesForIosExtension
import org.maksec.shared.navigation.components.AppPermissionType
import org.maksec.shared.navigation.components.PermissionManager
import org.maksec.shared.screens.util.AutoBlockExceptions
import org.maksec.shared.screens.util.LabelType
import org.maksec.shared.screens.util.MessengerSource

class MessagesSettingsLandingComponent(
    componentContext: ComponentContext,
    private val onNavigateToMessagesLandingScreen: () -> Unit,
    private val onNavigateToTelegramConnectScreen: () -> Unit,
    private val onNavigateToRelativeConnectScreen: () -> Unit,
    val authorizedPhoneNumberTG: StateFlow<String?>,
    val authorizedPhoneNumberRelative: StateFlow<String?>,
) : ComponentContext by componentContext {
    companion object {
        private const val TAG = "org.maksec.module.messages"
    }

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val permissionManager: PermissionManager = getKoin().get()
    private val apiClient: ApiClient = getKoin().get()

    val isSmsCriticalEnabled: StateFlow<Boolean> =
        AppPreferences.getBooleanSettingFlow(AppPreferences.BooleanSetting.NOTIFY_MESSENGERS_CRITICAL)
            .stateIn(coroutineScope, SharingStarted.Eagerly, false)

    val isSmsSuspiciousEnabled: StateFlow<Boolean> =
        AppPreferences.getBooleanSettingFlow(AppPreferences.BooleanSetting.NOTIFY_MESSENGERS_SUSPICIOUS)
            .stateIn(coroutineScope, SharingStarted.Eagerly, false)

    val exceptionsFlow = AppPreferences.getAutoBlockExceptionsFlow()
    private val tgApiRepository by lazy {
        MessengersFeatureDependencies.tgApiRepository
    }
    val licenseManager: LicenseManager = getKoin().get()

    private val _dialogState =
        MutableStateFlow<MessagesSettingsLandingDialogState>(MessagesSettingsLandingDialogState.None)
    val dialogState: StateFlow<MessagesSettingsLandingDialogState> = _dialogState

    private val _notifyRelative = MutableStateFlow<Boolean>(false)
    val notifyRelative: StateFlow<Boolean> = _notifyRelative

    private suspend fun updateTelegramExceptions(updated: Map<LabelType, Boolean>) {
        AppPreferences.updateAutoBlockExceptionsForSource(MessengerSource.TELEGRAM, updated)
    }

    private suspend fun updateWhatsappExceptions(updated: Map<LabelType, Boolean>) {
        AppPreferences.updateAutoBlockExceptionsForSource(MessengerSource.WHATSAPP, updated)
    }

    private suspend fun updateSMSExceptions(updated: Map<LabelType, Boolean>) {
        AppPreferences.updateAutoBlockExceptionsForSource(MessengerSource.SMS, updated)
    }

    private suspend fun updateMaxExceptions(updated: Map<LabelType, Boolean>) {
        AppPreferences.updateAutoBlockExceptionsForSource(MessengerSource.MAX, updated)
    }

    val isDefendWhatsappEnabled: StateFlow<Boolean> =
        AppPreferences.getBooleanSettingFlow(AppPreferences.BooleanSetting.DEFEND_WHATSAPP)
            .stateIn(coroutineScope, SharingStarted.Eagerly, false)

    val isDefendMaxEnabled: StateFlow<Boolean> =
        AppPreferences.getBooleanSettingFlow(AppPreferences.BooleanSetting.DEFEND_MAX)
            .stateIn(coroutineScope, SharingStarted.Eagerly, false)

    val isDefendSMSEnabled: StateFlow<Boolean> =
        AppPreferences.getBooleanSettingFlow(AppPreferences.BooleanSetting.DEFEND_SMS)
            .stateIn(coroutineScope, SharingStarted.Eagerly, false)

    private val _sideEffect = MutableSharedFlow<MessagesLandingSideEffect>()
    val sideEffect = _sideEffect.asSharedFlow()

    private val analyzeContactsFlow = MutableStateFlow<Boolean?>(null)
    private val analyzeStrangersFlow = MutableStateFlow<Boolean?>(null)

    init {
        coroutineScope.launch {
            AppPreferences.getBooleanSettingFlow(AppPreferences.BooleanSetting.ANALYZE_CONTACTS).collect { value ->
                analyzeContactsFlow.value = value
            }
        }
        coroutineScope.launch {
            AppPreferences.getBooleanSettingFlow(AppPreferences.BooleanSetting.NOTIFY_RELATIVE).collect { value ->
                _notifyRelative.value = value
            }
        }
        coroutineScope.launch {
            AppPreferences.getBooleanSettingFlow(AppPreferences.BooleanSetting.ANALYZE_STRANGERS).collect { value ->
                analyzeStrangersFlow.value = value
            }
        }
        lifecycle.doOnResume {
            coroutineScope.launch {
                checkForNotificationPermissions()
                checkForBlockPermissions()
            }
        }
    }

    private suspend fun checkForNotificationPermissions() {
        if (!permissionManager.permissionsFlow.value.any {
                it.type == AppPermissionType.POST_NOTIFICATIONS && it.isGranted
            }) {
            AppLogger.d(TAG, "Disabling module notifications because permissions were revoked")

            AppPreferences.setBooleanSetting(
                AppPreferences.BooleanSetting.NOTIFY_MESSENGERS_CRITICAL,
                false
            )
            AppPreferences.setBooleanSetting(
                AppPreferences.BooleanSetting.NOTIFY_MESSENGERS_SUSPICIOUS,
                false
            )
        }
    }

    private suspend fun checkForBlockPermissions() {
        checkForSmsPermissions()
        if (!licenseManager.currentLicenseHasFeature(LicenseFeature.MESSAGE_DELETE_AND_BLOCK)) {
            AppLogger.d(TAG, "Disabling auto block because current license doesn't support it")

            AppPreferences.setBooleanSetting(
                AppPreferences.BooleanSetting.AUTO_BLOCK_SUSPICIOUS_TG,
                false
            )
            AppPreferences.setBooleanSetting(
                AppPreferences.BooleanSetting.AUTO_BLOCK_CRITICAL_TG,
                false
            )
        }
    }

    val selectedAnalyzeOption: StateFlow<AnalyzeSourceOption?> = combine(
        analyzeContactsFlow, analyzeStrangersFlow
    ) { contacts, strangers ->
        when {
            contacts == true && strangers == true -> AnalyzeSourceOption.ALL
            contacts == true -> AnalyzeSourceOption.CONTACTS_ONLY
            strangers == true -> AnalyzeSourceOption.STRANGERS_ONLY
            else -> null
        }
    }.stateIn(coroutineScope, SharingStarted.Eagerly, AnalyzeSourceOption.ALL)

    fun onEvent(event: MessagesSettingsLandingEvent) {
        when (event) {
            is MessagesSettingsLandingEvent.NavigateToMessagesLandingScreen ->
                onNavigateToMessagesLandingScreen()

            is MessagesSettingsLandingEvent.BooleanSettingChanged -> {
                coroutineScope.launch {
                    if (event.newValue && (
                                event.setting == AppPreferences.BooleanSetting.NOTIFY_MESSENGERS_CRITICAL ||
                                        event.setting == AppPreferences.BooleanSetting.NOTIFY_MESSENGERS_SUSPICIOUS
                                )
                    ) {
                        val permissions = permissionManager.permissionsFlow.value
                        val hasNotificationPermission = permissions.any {
                            it.type == AppPermissionType.POST_NOTIFICATIONS && it.isGranted
                        }
                        val isNotifyCriticalEnabled: StateFlow<Boolean> =
                            AppPreferences.getBooleanSettingFlow(AppPreferences.BooleanSetting.NOTIFY_MESSENGERS_CRITICAL)
                                .stateIn(coroutineScope, SharingStarted.Eagerly, false)

                        val isNotifySuspiciousEnabled: StateFlow<Boolean> =
                            AppPreferences.getBooleanSettingFlow(AppPreferences.BooleanSetting.NOTIFY_MESSENGERS_SUSPICIOUS)
                                .stateIn(coroutineScope, SharingStarted.Eagerly, false)

                        AppLogger.d(TAG, "Has Notification permission: $hasNotificationPermission")

                        if (!hasNotificationPermission) {
                            permissionManager.requestPermission(AppPermissionType.POST_NOTIFICATIONS)
                        }

                        val updatedPermissions = permissionManager.permissionsFlow.value
                        val granted = updatedPermissions.any {
                            it.type == AppPermissionType.POST_NOTIFICATIONS && it.isGranted
                        }

                        if (!granted) {
                            AppLogger.toast(getString(string.not_permitted))
                            return@launch
                        }
                        AppPreferences.setBooleanSetting(event.setting, event.newValue)

                    } else {
                        AppPreferences.setBooleanSetting(event.setting, event.newValue)
                    }
                }
            }

            is MessagesSettingsLandingEvent.StringSettingChanged -> {
                coroutineScope.launch {
                    AppPreferences.setStringSetting(event.setting, event.newValue)
                }
            }

            MessagesSettingsLandingEvent.ClickChangeNumberTG -> {
                if (!licenseManager.currentLicenseHasFeature(LicenseFeature.TELEGRAM_ANALYSIS)) {
                    _dialogState.value = MessagesSettingsLandingDialogState.AvailableWithPremium
                    return
                }
                tgApiRepository.logout(true)
                onNavigateToTelegramConnectScreen()
            }

            is MessagesSettingsLandingEvent.ToggleDefendWhatsapp -> {
                if (!licenseManager.currentLicenseHasFeature(LicenseFeature.WHATSAPP_ANALYSIS)) {
                    _dialogState.value = MessagesSettingsLandingDialogState.AvailableWithPremium
                    return
                }
                if (event.newValue) {
                    val havePermission = isNotificationServiceEnabled()
                    AppLogger.d(TAG, "Has Notification Listener permission: $havePermission")
                    if (!havePermission) {
                        coroutineScope.launch {
                            _sideEffect.emit(MessagesLandingSideEffect.RequestNotificationPermission(MessengerSource.WHATSAPP))
                        }
                        return
                    }
                }
                ServiceManager.updateUserServiceState(Module.MESSAGES, true)
                coroutineScope.launch {
                    AppPreferences.setBooleanSetting(AppPreferences.BooleanSetting.DEFEND_WHATSAPP, event.newValue)
                }
            }

            is MessagesSettingsLandingEvent.ToggleDefendMax -> {
                if (!licenseManager.currentLicenseHasFeature(LicenseFeature.MAX_ANALYSIS)) {
                    _dialogState.value = MessagesSettingsLandingDialogState.AvailableWithPremium
                    return
                }
                if (event.newValue) {
                    val havePermission = isNotificationServiceEnabled()
                    AppLogger.d(TAG, "Has Notification Listener permission: $havePermission")
                    if (!havePermission) {
                        coroutineScope.launch {
                            _sideEffect.emit(MessagesLandingSideEffect.RequestNotificationPermission(MessengerSource.MAX))
                        }
                        return
                    }
                }
                ServiceManager.updateUserServiceState(Module.MESSAGES, true)
                coroutineScope.launch {
                    AppPreferences.setBooleanSetting(AppPreferences.BooleanSetting.DEFEND_MAX, event.newValue)
                }
            }

            is MessagesSettingsLandingEvent.ToggleNotifyRelative -> {
                if (!licenseManager.currentLicenseHasFeature(LicenseFeature.NOTIFY_RELATIVE)) {
                    _dialogState.value = MessagesSettingsLandingDialogState.AvailableWithPremium
                    return
                }
                coroutineScope.launch {
                    AppPreferences.setBooleanSetting(AppPreferences.BooleanSetting.NOTIFY_RELATIVE, event.newValue)
                }
            }

            is MessagesSettingsLandingEvent.ToggleDefendSMS -> {
                if (!licenseManager.currentLicenseHasFeature(LicenseFeature.SMS_ANALYSIS)) {
                    _dialogState.value = MessagesSettingsLandingDialogState.AvailableWithPremium
                    return
                }
                coroutineScope.launch {
                    if (event.newValue) {
                        val permissions = permissionManager.permissionsFlow.value

                        val hasSmsPermission = permissions.any {
                            it.type == AppPermissionType.READ_AND_RECEIVE_SMS && it.isGranted
                        }
                        val hasContactsPermission = permissions.any {
                            it.type == AppPermissionType.READ_CONTACTS && it.isGranted
                        }

                        AppLogger.d(TAG, "Has SMS permission: $hasSmsPermission")
                        AppLogger.d(TAG, "Has Contacts permission: $hasContactsPermission")

                        if (!hasSmsPermission) {
                            permissionManager.requestPermission(AppPermissionType.READ_AND_RECEIVE_SMS)
                        }
                        if (!hasContactsPermission) {
                            permissionManager.requestPermission(AppPermissionType.READ_CONTACTS)
                        }

                        val updatedPermissions = permissionManager.permissionsFlow.value
                        val smsGranted = updatedPermissions.any {
                            it.type == AppPermissionType.READ_AND_RECEIVE_SMS && it.isGranted
                        }
                        val contactsGranted = updatedPermissions.any {
                            it.type == AppPermissionType.READ_CONTACTS && it.isGranted
                        }

                        AppLogger.d(TAG, "ToggleDefendSMS: Final permissions - SMS: $smsGranted, Contacts: $contactsGranted")

                        if (!smsGranted || !contactsGranted) {
                            AppLogger.toast(getString(string.not_permitted))
                            return@launch
                        }
                        ServiceManager.updateUserServiceState(Module.MESSAGES, true)
                    }
                    AppPreferences.setBooleanSetting(AppPreferences.BooleanSetting.DEFEND_SMS, event.newValue)
                }
            }

            MessagesSettingsLandingEvent.ClickChangeNumberRelative -> {
                if (!licenseManager.currentLicenseHasFeature(LicenseFeature.NOTIFY_RELATIVE)) {
                    _dialogState.value = MessagesSettingsLandingDialogState.AvailableWithPremium
                    return
                }
                onNavigateToRelativeConnectScreen()
            }

            is MessagesSettingsLandingEvent.UpdateExceptions -> {
                coroutineScope.launch {
                    when (event.source) {
                        MessengerSource.SMS -> updateSMSExceptions(event.updatedMap)
                        MessengerSource.TELEGRAM -> updateTelegramExceptions(event.updatedMap)
                        MessengerSource.WHATSAPP -> updateWhatsappExceptions(event.updatedMap)
                        MessengerSource.MAX -> updateMaxExceptions(event.updatedMap)
                    }
                }
            }

            is MessagesSettingsLandingEvent.NewAnalyzeOptionSelected -> {
                when (event.option) {
                    AnalyzeSourceOption.ALL -> {
                        updateAnalyzeContacts(true)
                        updateAnalyzeStrangers(true)
                    }
                    AnalyzeSourceOption.CONTACTS_ONLY -> {
                        updateAnalyzeContacts(true)
                        updateAnalyzeStrangers(false)
                    }
                    AnalyzeSourceOption.STRANGERS_ONLY -> {
                        updateAnalyzeContacts(false)
                        updateAnalyzeStrangers(true)
                    }
                }
            }

            is MessagesSettingsLandingEvent.ClickExceptions -> {
                coroutineScope.launch {
                    val curExceptions = exceptionsFlow.first()
                    _dialogState.value = MessagesSettingsLandingDialogState.ExceptionDialog(
                        source = event.source,
                        currentExceptions = curExceptions,
                        onConfirm = { map ->
                            onEvent(
                                MessagesSettingsLandingEvent.UpdateExceptions(
                                    source = event.source,
                                    updatedMap = map,
                                )
                            )
                        }
                    )
                }
            }

            is MessagesSettingsLandingEvent.ClickSms -> {
                _dialogState.value = MessagesSettingsLandingDialogState.SmsDialog
            }

            MessagesSettingsLandingEvent.DismissDialogue -> {
                _dialogState.value = MessagesSettingsLandingDialogState.None
            }

            MessagesSettingsLandingEvent.ClickPatternSync -> {
                syncPatterns()
            }
        }
    }

    private fun updateAnalyzeContacts(newValue: Boolean) {
        coroutineScope.launch {
            AppPreferences.setBooleanSetting(AppPreferences.BooleanSetting.ANALYZE_CONTACTS, newValue)
        }
    }

    private fun updateAnalyzeStrangers(newValue: Boolean) {
        coroutineScope.launch {
            AppPreferences.setBooleanSetting(AppPreferences.BooleanSetting.ANALYZE_STRANGERS, newValue)
        }
    }

    private fun syncPatterns() {
        coroutineScope.launch {
            apiClient.fullSyncSignatures(0)
                .onSuccess { res ->
                    val signatures = res.map { it.toEntity() }
                    signatures.forEach {
                        AppLogger.d(TAG, it.toString())
                    }
                    incidentDao.upsertSignatures(signatures)
                    saveSignaturesForIosExtension()

                    AppPreferences.setLongSetting(
                        AppPreferences.LongSetting.LAST_SIGNATURE_DB_UPDATE,
                        unixSecondsNow()
                    )
                }
                .onError {
                    AppLogger.toast("${it.type}: ${it.statusMessage.translateErrorIfPossible()}")
                }
        }
    }

    private suspend fun checkForSmsPermissions() {
        val permissions = permissionManager.permissionsFlow.value

        val hasSmsPermission = permissions.any {
            it.type == AppPermissionType.READ_AND_RECEIVE_SMS && it.isGranted
        }
        val hasContactsPermission = permissions.any {
            it.type == AppPermissionType.READ_CONTACTS && it.isGranted
        }

        if (!hasSmsPermission || !hasContactsPermission) {
            AppLogger.d(TAG, "Disabling SMS auto-actions due to missing permissions")

            AppPreferences.setBooleanSetting(
                AppPreferences.BooleanSetting.DEFEND_SMS,
                false)
        }
    }
}

enum class AnalyzeSourceOption {
    ALL, CONTACTS_ONLY, STRANGERS_ONLY
}

sealed interface MessagesLandingSideEffect {
    data class RequestNotificationPermission(val source: MessengerSource) : MessagesLandingSideEffect
}

sealed class MessagesSettingsLandingDialogState {
    data object None : MessagesSettingsLandingDialogState()
    data object AvailableWithPremium : MessagesSettingsLandingDialogState()
    data class ExceptionDialog(
        val source: MessengerSource,
        val currentExceptions: AutoBlockExceptions,
        val onConfirm: (Map<LabelType, Boolean>) -> Unit
    ) : MessagesSettingsLandingDialogState()

    data object SmsDialog : MessagesSettingsLandingDialogState()
}