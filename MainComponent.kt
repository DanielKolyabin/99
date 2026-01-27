package org.maksec.navigation.components

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.*
import kotlinx.serialization.Serializable
import com.arkivanov.decompose.value.Value
import com.badoo.reaktive.maybe.subscribe
import com.google.protobuf.Timestamp
import db_transfer_service.v1.DbTransferService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import maksec.shared.generated.resources.*
import maksec.shared.generated.resources.Res.string
import org.jetbrains.compose.resources.getString
import org.koin.mp.KoinPlatform.getKoin
import org.maksec.shared.AppLogger
import org.maksec.shared.core.ServiceManager
import org.maksec.shared.core.isOlderThan
import org.maksec.shared.core.unixSecondsNow
import org.maksec.shared.data.db.incidents.Module
import org.maksec.shared.data.db.incidents.toEntity
import org.maksec.shared.data.network.util.onError
import org.maksec.shared.data.network.util.onSuccess
import org.maksec.shared.data.network.util.translateErrorIfPossible
import org.maksec.shared.di.AppPreferences
import org.maksec.shared.di.LocalDaoHolder.incidentDao
import org.maksec.shared.domain.ApiClient
import org.maksec.navigation.components.services.ServicesComponent
import org.maksec.features.util.FeatureInstaller
import org.maksec.navigation.components.profile.ProfileComponent
import org.maksec.navigation.components.services.DecoysDependencies
import org.maksec.navigation.components.services.MessagesDependencies
import org.maksec.shared.data.db.incidents.saveSignaturesForIosExtension
import org.maksec.shared.navigation.components.AppPermissionType
import org.maksec.shared.navigation.components.PermissionManager
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalTime::class)
class MainComponent(
    componentContext: ComponentContext,
    private val onNavigateToAuthScreen: () -> Unit,
    private val onNavigateToPermissionsScreen: () -> Unit,
    private val messagesDependencies: MessagesDependencies,
    private val decoysDependencies: DecoysDependencies,
) : ComponentContext by componentContext {
    companion object {
        const val TAG = "org.maksec.MainComponent"
    }

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val navigation = StackNavigation<Configuration>()
    private val permissionManager: PermissionManager = getKoin().get()
    private val apiClient: ApiClient = getKoin().get()
    private val featureInstaller: FeatureInstaller = getKoin().get()

    private val _loggingEnabled = MutableStateFlow(false)
    val loggingEnabled: StateFlow<Boolean> = _loggingEnabled

    init {
        apiClient.onUnauthorized = { onNavigateToAuthScreen() }


        coroutineScope.launch {
            AppPreferences.getBooleanSettingFlow(AppPreferences.BooleanSetting.LOGGING_ENABLED).collect { value ->
                _loggingEnabled.value = value
            }
        }

        ServiceManager.setInitialServices(
            listOf(
                Module.MESSAGES,
                Module.CALLS,
                Module.TRAFFIC,
                Module.ANTIVIRUS,
                Module.DECOYS,
            )
        )
        initializeMessagesFeatureRepositories()
        initializeDecoysFeatureRepositories()

        // If any permission is revoked during the lifetime of this component, create log
        // and, if necessary, disable the corresponding defender/module
        coroutineScope.launch {
            permissionManager.permissionsFlow.collect { permissions ->
                val revokedPermissions = permissions.filter { !it.isGranted }
                if (revokedPermissions.isNotEmpty()) {
                    val revokedTypes = revokedPermissions.joinToString(", ") { it.type.name }
                    AppLogger.i(TAG, "${getString(string.some_permissions_were_revoked)}: $revokedTypes")

                    revokedPermissions.forEach { permission ->
                        when (permission.type) {
                            AppPermissionType.READ_CONTACTS, AppPermissionType.READ_AND_RECEIVE_SMS -> {
                                AppPreferences.setBooleanSetting(AppPreferences.BooleanSetting.DEFEND_SMS, false)
                            }

                            AppPermissionType.POST_NOTIFICATIONS -> {
                                // Nothing
                            }

                            AppPermissionType.WRITE_CONTACTS -> {
                                // Nothing
                            }

                            AppPermissionType.AUTO_LAUNCH -> {
                                // Nothing
                            }

                            AppPermissionType.RECORD_AUDIO -> {
                                // Nothing
                            }

                            AppPermissionType.STORAGE -> {
                                ServiceManager.updateUserServiceState(Module.DECOYS, false)
                            }
                            AppPermissionType.NOTIFICATION_LISTENER -> {
                                AppLogger.d(
                                    TAG,
                                    "Отозван доступ к уведомлениям, отключаем защиту SMS/WhatsApp/MAX"
                                )

                                coroutineScope.launch {
                                    // Отключаем все модули, которые требуют Notification Listener
                                    AppPreferences.setBooleanSetting(
                                        AppPreferences.BooleanSetting.DEFEND_SMS,
                                        false
                                    )
                                    AppPreferences.setBooleanSetting(
                                        AppPreferences.BooleanSetting.DEFEND_WHATSAPP,
                                        false
                                    )
                                    AppPreferences.setBooleanSetting(
                                        AppPreferences.BooleanSetting.DEFEND_MAX,
                                        false
                                    )

                                    // Можно показать уведомление пользователю
                                    /*showNotificationListenerRevokedNotification()*/
                                }
                            }
                        }
                    }
                }
            }
        }

        // If LAST_SIGNATURE_DB_UPDATE is null, we fully sync Signatures
        // If a day passed since LAST_SIGNATURE_DB_UPDATE, we call checkUpdates
        coroutineScope.launch {
            val lastUpdate =
                AppPreferences.getLongSettingFlow(AppPreferences.LongSetting.LAST_SIGNATURE_DB_UPDATE).first()
            if (lastUpdate == null || lastUpdate == 0L) {
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
                    .onError { error ->
                        AppLogger.e(TAG, "Sync signatures error: ${error.type}: ${error.statusMessage}")
                        AppLogger.toast("Sync signatures error: ${error.type}: ${error.statusMessage.translateErrorIfPossible()}")
                    }
            } else if (lastUpdate.isOlderThan(24)) {
                val currentSignatures = incidentDao.getAllSignatures()
                val infoList = currentSignatures.map {
                    DbTransferService.MessageSignatureInfo(
                        id = it.id,
                        updated_at = Timestamp(it.updatedAt)
                    )
                }

                apiClient.checkUpdates(infoList)
                    .onSuccess { result ->
                        val updated = result.updated.map { it.toEntity() }
                        val deleted = result.deletedIds.toSet()

                        val kept = currentSignatures.filter { it.id !in deleted }
                        kept.forEach {
                            AppLogger.d("$TAG.keptSignatures", it.toString())
                        }
                        updated.forEach {
                            AppLogger.d("$TAG.updatedSignatures", it.toString())
                        }
                        incidentDao.replaceSignatures(kept + updated)
                        saveSignaturesForIosExtension()

                        AppPreferences.setLongSetting(
                            AppPreferences.LongSetting.LAST_SIGNATURE_DB_UPDATE,
                            unixSecondsNow()
                        )
                    }
                    .onError { error ->
                        AppLogger.toast("${error.type}: ${error.statusMessage.translateErrorIfPossible()}")
                    }
            }
        }
    }

    private val _stack =
        childStack(
            source = navigation,
            serializer = Configuration.serializer(),
            initialConfiguration = Configuration.ServicesScreen(), // TODO: Once DashboardScreen done, it should be default here
            handleBackButton = true,
            childFactory = ::createChild,
        )


    val stack: Value<ChildStack<*, Child>> = _stack

    private fun initializeMessagesFeatureRepositories() {
        coroutineScope.launch {
            featureInstaller.isFeatureInstalled("messengersDynamicFeature")
                .subscribe(
                    onSuccess = {
                        // The dynamic module is installed, trying to initiate repositories and then launch the service
                        initializeMessagesFeatureRepositoriesIfInstalled(
                            featureInstaller, messagesDependencies
                        )
                        updateMessagesServiceStateIfInstalled(coroutineScope)
                    },
                )
        }
    }

    private fun initializeDecoysFeatureRepositories() {
        coroutineScope.launch {
            featureInstaller.isFeatureInstalled("decoysDynamicFeature")
                .subscribe(
                    onSuccess = {
                        // The dynamic module is installed, trying to initiate repositories and then launch the service
                        initializeDecoysFeatureRepositoriesIfInstalled(
                            featureInstaller, decoysDependencies
                        )
                    },
                )
        }
    }

    private fun createChild(config: Configuration, componentContext: ComponentContext): Child =
        when (config) {
            Configuration.DashboardScreen -> Child.DashboardScreen(
                DashboardComponent(
                    componentContext = componentContext,
                )
            )

            is Configuration.ServicesScreen -> Child.ServicesScreen(
                ServicesComponent(
                    componentContext = componentContext,
                    messagesDependencies = messagesDependencies,
                    decoysDependencies = decoysDependencies,
                    onNavigateToProfileScreen = {
                        navigation.bringToFront(Configuration.ProfileScreen)
                    },
                    initialConfiguration = config.initialConfiguration?: ServicesComponent.Configuration.ServicesListScreen,
                )
            )

            Configuration.CallScreen -> Child.CallsScreen(
                CallsComponent(
                    componentContext = componentContext,
                )
            )

            Configuration.ThreatsScreen -> Child.ThreatsScreen(
                ThreatsComponent(
                    componentContext = componentContext,
                )
            )

            Configuration.ProfileScreen -> Child.ProfileScreen(
                ProfileComponent(
                    componentContext = componentContext,
                    onNavigateToAuthScreen = { onNavigateToAuthScreen() },
                    onNavigateToServicesScreen = {
                        navigation.bringToFront(
                            Configuration.ServicesScreen(
                                initialConfiguration = ServicesComponent.Configuration.MessengersFeature
                            )
                        )
                    },
                    onNavigateToPermissionsScreen = {
                        onNavigateToPermissionsScreen()
                    }
                )
            )
        }

    sealed class Child {
        data class DashboardScreen(val component: DashboardComponent) : Child()
        data class ServicesScreen(val component: ServicesComponent) : Child()
        data class CallsScreen(val component: CallsComponent) : Child()
        data class ThreatsScreen(val component: ThreatsComponent) : Child()
        data class ProfileScreen(val component: ProfileComponent) : Child()
    }

    fun onDashboardClicked() {
        navigation.bringToFront(Configuration.DashboardScreen)
    }

    fun onServicesClicked() {
        navigation.bringToFront(Configuration.ServicesScreen())
    }

    fun onCallsClicked() {
        navigation.bringToFront(Configuration.CallScreen)
    }

    fun onThreatsClicked() {
        navigation.bringToFront(Configuration.ThreatsScreen)
    }

    fun onProfileClicked() {
        navigation.bringToFront(Configuration.ProfileScreen)
    }

    @Serializable
    private sealed interface Configuration {
        @Serializable
        data object DashboardScreen : Configuration

        @Serializable
        data class ServicesScreen(val initialConfiguration: ServicesComponent.Configuration? = null) : Configuration

        @Serializable
        data object CallScreen : Configuration

        @Serializable
        data object ThreatsScreen : Configuration

        @Serializable
        data object ProfileScreen : Configuration
    }
}

expect fun initializeMessagesFeatureRepositoriesIfInstalled(
    featureInstaller: FeatureInstaller,
    messagesDependencies: MessagesDependencies,
)

expect fun initializeDecoysFeatureRepositoriesIfInstalled(
    featureInstaller: FeatureInstaller,
    decoysDependencies: DecoysDependencies,
)

expect fun updateMessagesServiceStateIfInstalled(
    coroutineScope: CoroutineScope
)