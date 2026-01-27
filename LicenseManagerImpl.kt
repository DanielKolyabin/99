package org.maksec.data.licensing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.maksec.shared.AppLogger
import org.maksec.shared.data.network.util.onError
import org.maksec.shared.data.network.util.onSuccess
import org.maksec.shared.data.network.util.translateErrorIfPossible
import org.maksec.shared.di.AppPreferences
import org.maksec.shared.domain.ApiClient
import org.maksec.shared.domain.LicenseFeature
import org.maksec.shared.domain.LicenseInfo
import org.maksec.shared.domain.LicenseManager
import org.maksec.shared.domain.LicenseType
import org.maksec.shared.domain.MyLicenseInfo

class LicenseManagerImpl : LicenseManager {
    companion object {
        const val TAG = "org.maksec.LicenseManager"
    }

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _onTelegramDisconnect = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val onTelegramDisconnect: SharedFlow<Unit> = _onTelegramDisconnect

    private val _isFamilyLicenseOwner = MutableStateFlow(false)
    override val isFamilyLicenseOwner: StateFlow<Boolean> = _isFamilyLicenseOwner

    private val currentLicenseFlow = MutableStateFlow<MyLicenseInfo?>(null)
    private val currentCorporateLicenseFlow = MutableStateFlow<MyLicenseInfo?>(null)
    private val availableLicenses = mutableMapOf<LicenseType, LicenseInfo>()

    override fun getCurrentLicenseFlow(): Flow<MyLicenseInfo?> = currentLicenseFlow.asStateFlow()
    override fun getCurrentCorporateLicenseFlow(): Flow<MyLicenseInfo?> = currentCorporateLicenseFlow.asStateFlow()

    override suspend fun setCurrentLicenses(
        myLicenseInfo: MyLicenseInfo?,
        corporateLicenseInfo: MyLicenseInfo?,
        isFamilyLicenseOwner: Boolean,
    ) {
        AppLogger.d(TAG, "Setting current license: $myLicenseInfo")
        AppLogger.d(TAG, "Setting current corporate license: $corporateLicenseInfo")

        _isFamilyLicenseOwner.value = isFamilyLicenseOwner
        currentLicenseFlow.value = myLicenseInfo
        currentCorporateLicenseFlow.value = corporateLicenseInfo
        updateAppPreferencesForNewLicenses()
    }

    override fun currentLicenseHasFeature(feature: LicenseFeature): Boolean {
        /*return true*/
        val currentLicense = currentLicenseFlow.value
        val corporateLicense = currentCorporateLicenseFlow.value
        return currentLicense?.licenseInfo?.licenseFeatures?.contains(feature) == true ||
                corporateLicense?.licenseInfo?.licenseFeatures?.contains(feature) == true
    }


    override suspend fun fetchLicenses(apiClient: ApiClient) {
        apiClient.getLicenses()
            .onSuccess { licenses ->
                availableLicenses.clear()
                availableLicenses.putAll(licenses.associateBy { it.license })
            }
            .onError {
                withContext(Dispatchers.Main) {
                    AppLogger.toast("Failed to fetch: ${it.statusMessage.translateErrorIfPossible()}")
                }
            }
    }

    override fun getLicensesInfo(): List<LicenseInfo> = availableLicenses.values.toList()

    override fun hasFeature(type: LicenseType?, licenseFeature: LicenseFeature): Boolean {
        return availableLicenses[type]?.licenseFeatures?.contains(licenseFeature) == true
    }

    private suspend fun updateAppPreferencesForNewLicenses() {
        val myLicense = currentLicenseFlow.value
        val corporateLicense = currentCorporateLicenseFlow.value

        AppPreferences.setLongSetting(
            AppPreferences.LongSetting.LICENSE_ID, myLicense?.licenseInfo?.id ?: 0
        )
        AppPreferences.setLongSetting(
            AppPreferences.LongSetting.CORPORATE_LICENSE_ID, corporateLicense?.licenseInfo?.id ?: 0
        )

        val features =
            myLicense?.licenseInfo?.licenseFeatures.orEmpty() +
                    corporateLicense?.licenseInfo?.licenseFeatures.orEmpty()

        if (!features.contains(LicenseFeature.MAX_ANALYSIS))
            AppPreferences.setBooleanSetting(
                AppPreferences.BooleanSetting.DEFEND_MAX,
                false
            )

        if (!features.contains(LicenseFeature.WHATSAPP_ANALYSIS))
            AppPreferences.setBooleanSetting(
                AppPreferences.BooleanSetting.DEFEND_WHATSAPP,
                false
            )

        if (!features.contains(LicenseFeature.SMS_ANALYSIS))
            AppPreferences.setBooleanSetting(
                AppPreferences.BooleanSetting.DEFEND_SMS,
                false
            )
        if (!features.contains(LicenseFeature.NOTIFY_RELATIVE)) {
            AppPreferences.setBooleanSetting(
                AppPreferences.BooleanSetting.NOTIFY_RELATIVE,
                false
            )
            AppPreferences.setStringSetting(
                AppPreferences.StringSetting.RELATIVE_PHONE,
                ""
            )
        }
        if (!features.contains(LicenseFeature.MESSAGE_DELETE_AND_BLOCK)) {
            AppPreferences.setBooleanSetting(
                AppPreferences.BooleanSetting.AUTO_BLOCK_CRITICAL_TG,
                false
            )
            AppPreferences.setBooleanSetting(
                AppPreferences.BooleanSetting.AUTO_BLOCK_SUSPICIOUS_TG,
                false
            )
        }

        if (!features.contains(LicenseFeature.TELEGRAM_ANALYSIS))
            _onTelegramDisconnect.tryEmit(Unit)

    }
}