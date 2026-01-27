package org.maksec

import android.os.Build
import android.os.Bundle
import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.content.ContextCompat
import com.arkivanov.decompose.retainedComponent
import org.maksec.data.AndroidFilePicker
import org.maksec.shared.navigation.components.AppPermissionType
import org.maksec.navigation.components.RootComponent
import org.maksec.shared.screens.util.AppTheme
import ru.rustore.sdk.pay.IntentInteractor
import ru.rustore.sdk.pay.RuStorePayClient

class MainActivity : ComponentActivity() {
    private val intentInteractor: IntentInteractor by lazy {
        RuStorePayClient.instance.getIntentInteractor()
    }
    lateinit var filePickerLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            intentInteractor.proceedIntent(intent)
        }
        filePickerLauncher =
            registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                AndroidFilePicker.callbacks?.invoke(uris)
            }
        enableEdgeToEdge()
        CurrentActivityHolder.currentActivity = this

        val root = retainedComponent {
            RootComponent(
                componentContext = it,
            )
        }
        setContent {
            AppTheme(useDarkTheme = isSystemInDarkTheme()) {
                App(
                    root = root
                )
            }
        }
    }
    private var permissionRequestCallback: ((Boolean) -> Unit)? = null

    private val singlePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionRequestCallback?.invoke(isGranted)
        permissionRequestCallback = null
    }
    // for SMS, there are 2 permissions
    private val multiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        permissionRequestCallback?.invoke(allGranted)
        permissionRequestCallback = null
    }

    private val storageAccessLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
        permissionRequestCallback?.invoke(granted)
    }

    fun requestPermission(
        type: AppPermissionType,
        onResult: (Boolean) -> Unit
    ) {
        permissionRequestCallback = onResult

        when (type) {
            AppPermissionType.READ_CONTACTS -> {
                singlePermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
            AppPermissionType.WRITE_CONTACTS -> {
                singlePermissionLauncher.launch(Manifest.permission.WRITE_CONTACTS)
            }
            AppPermissionType.POST_NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    singlePermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onResult(true) // granted automatically on SDK < 33
                }
            }
            AppPermissionType.READ_AND_RECEIVE_SMS -> {
                multiplePermissionsLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_SMS,
                        Manifest.permission.RECEIVE_SMS
                    )
                )
            }
            AppPermissionType.NOTIFICATION_LISTENER -> {
                // Notification Listener нельзя запросить через стандартный диалог
                // Нужно открыть системные настройки
                showNotificationListenerDialog(onResult as () -> Unit)
            }


            AppPermissionType.AUTO_LAUNCH -> {
                // Not a manifest permission
                // Handled via AutoLaunch library
            }

            AppPermissionType.RECORD_AUDIO -> singlePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            AppPermissionType.STORAGE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.fromParts("package", packageName, null)

                    storageAccessLauncher.launch(intent)
                } else {
                    singlePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        }
    }
    private fun showNotificationListenerDialog(onResult: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Доступ к уведомлениям")
            .setMessage("Для защиты SMS и мессенджеров нужно разрешить доступ к уведомлениям. Нажмите \"Открыть настройки\", затем включите MaKSec в списке служб.")
            .setPositiveButton("Открыть настройки") { _, _ ->
                val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                startActivity(intent)

                // Сохраняем колбэк
                notificationListenerCallback = onResult
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
                onResult()
            }
            .setCancelable(false)
            .show()
    }
    private var notificationListenerCallback: (() -> Unit)? = null




    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intentInteractor.proceedIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        window.decorView.requestFocus()
    }
}

object CurrentActivityHolder {
    var currentActivity: ComponentActivity? = null
}
