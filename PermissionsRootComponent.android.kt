package org.maksec.navigation.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.judemanutd.autostarter.AutoStartPermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maksec.MainActivity
import org.maksec.navigation.components.NotificationListenerHelper
import org.maksec.shared.navigation.components.AppPermission
import org.maksec.shared.navigation.components.AppPermissionType
import org.maksec.shared.navigation.components.PermissionManager
import kotlin.coroutines.resume

class AndroidPermissionManager(
    private val context: Context
) : PermissionManager {

    private val _permissionsFlow = MutableStateFlow<List<AppPermission>>(emptyList())
    override val permissionsFlow: StateFlow<List<AppPermission>> = _permissionsFlow

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    init {
        coroutineScope.launch {
            _permissionsFlow.value = loadPermissions()
        }
    }

    override suspend fun requestPermission(type: AppPermissionType) {
        when (type) {
            AppPermissionType.AUTO_LAUNCH -> {
                val available = AutoStartPermissionHelper
                    .getInstance().isAutoStartPermissionAvailable(context)
                if (available) {
                    AutoStartPermissionHelper.getInstance().getAutoStartPermission(
                        context = context,
                        open = true,
                        newTask = true
                    )
                }
                updatePermissionList()
            }

            AppPermissionType.NOTIFICATION_LISTENER -> {
                NotificationListenerHelper.requestNotificationListenerPermission(context)
                kotlinx.coroutines.delay(1000)
                updatePermissionList()
            }

            else -> suspendCancellableCoroutine { cont ->
                (context as MainActivity).requestPermission(type) {
                    coroutineScope.launch {
                        _permissionsFlow.value = loadPermissions()
                    }
                    cont.resume(Unit)
                }
            }
        }
    }

    override fun updatePermissionList() {
        coroutineScope.launch {
            _permissionsFlow.value = loadPermissions()
        }
    }

    private fun isPermissionGranted(permission: String): Boolean {
        return when (permission) {
            Manifest.permission.MANAGE_EXTERNAL_STORAGE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager()
                } else false
            }
            else -> ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun loadPermissions(): List<AppPermission> {
        return buildList {
            // 1. POST_NOTIFICATIONS (только для API 33+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(
                    AppPermission(
                        type = AppPermissionType.POST_NOTIFICATIONS,
                        isGranted = isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)
                    )
                )
            }

            // 2. Контакты
            add(
                AppPermission(
                    type = AppPermissionType.READ_CONTACTS,
                    isGranted = isPermissionGranted(Manifest.permission.READ_CONTACTS)
                )
            )
            add(
                AppPermission(
                    type = AppPermissionType.WRITE_CONTACTS,
                    isGranted = isPermissionGranted(Manifest.permission.WRITE_CONTACTS)
                )
            )

            // 3. SMS
            add(
                AppPermission(
                    type = AppPermissionType.READ_AND_RECEIVE_SMS,
                    isGranted = isPermissionGranted(Manifest.permission.READ_SMS) &&
                            isPermissionGranted(Manifest.permission.RECEIVE_SMS)
                )
            )

            // 4. NOTIFICATION LISTENER (только один раз!)
            add(
                AppPermission(
                    type = AppPermissionType.NOTIFICATION_LISTENER,
                    isGranted = NotificationListenerHelper.isNotificationListenerEnabled(context)
                )
            )

            // 5. AUTO LAUNCH
            if (AutoStartPermissionHelper
                    .getInstance().isAutoStartPermissionAvailable(context)) {
                add(
                    AppPermission(
                        type = AppPermissionType.AUTO_LAUNCH,
                        isGranted = AutoStartPermissionHelper.getInstance()
                            .getAutoStartPermission(
                                context,
                                open = false,
                                newTask = false,
                            )
                    )
                )
            }

            // 6. Хранилище
            add(
                AppPermission(
                    type = AppPermissionType.STORAGE,
                    isGranted = isPermissionGranted(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Manifest.permission.MANAGE_EXTERNAL_STORAGE
                        } else {
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        }
                    )
                )
            )

            // 7. Микрофон
            add(
                AppPermission(
                    type = AppPermissionType.RECORD_AUDIO,
                    isGranted = isPermissionGranted(Manifest.permission.RECORD_AUDIO)
                )
            )

            // УБЕРИ ЭТУ СТРОКУ - она дублирует пункт 4!
            // add(
            //     AppPermission(
            //         type = AppPermissionType.NOTIFICATION_LISTENER,
            //         isGranted = isNotificationListenerEnabled()
            //     )
            // )
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        return try {
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: ""

            val packageName = context.packageName
            val listenerName = "$packageName/org.maksec.messengersDynamicFeature.data.NotificationListener"

            val isEnabled = enabledListeners.contains(listenerName)

            // Логирование для отладки
            android.util.Log.d(
                "PermissionManager",
                "Notification Listener: $isEnabled, Name: $listenerName"
            )
            android.util.Log.d(
                "PermissionManager",
                "All listeners: $enabledListeners"
            )

            isEnabled
        } catch (e: Exception) {
            android.util.Log.e(
                "PermissionManager",
                "Error checking notification listener: ${e.message}"
            )
            false
        }
    }
}