package org.maksec.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import maksec.shared.generated.resources.*
import maksec.shared.generated.resources.Res.string
import maksec.shared.generated.resources.Res.drawable
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.maksec.shared.navigation.components.AppPermissionType
import org.maksec.shared.navigation.components.AppPermission
import org.maksec.navigation.components.PermissionsRootComponent
import org.maksec.navigation.events.PermissionsRootEvent
import org.maksec.shared.screens.util.AppCard
import org.maksec.shared.screens.util.ButtonPrimary
import org.maksec.shared.screens.util.LocalAppColorScheme
import org.maksec.shared.shareLogFile

@Composable
fun PermissionsRootScreen(component: PermissionsRootComponent) {
    val colors = LocalAppColorScheme.current

    val permissions by component.permissions.collectAsState()

    LaunchedEffect(permissions) {
        if (permissions.isNotEmpty() && permissions.all { it.isGranted })
            component.onEvent(PermissionsRootEvent.GoToNextScreen)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(Modifier.height(26.dp))
        Icon(
            painter = painterResource(drawable.maksec_logo),
            contentDescription = "Logo",
            tint = Color.Unspecified,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(string.permissions),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.primary
            )

            Text(
                text = stringResource(string.permissions_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            permissions.forEach { permission ->
                PermissionItem(
                    permission = permission,
                    onClick = { component.onEvent(PermissionsRootEvent.RequestPermission(permission)) }
                )
            }
        }
        ButtonPrimary( // TODO Delete or move this
            text = stringResource(string.send_logs),
            onClick = {
                shareLogFile()
            }
        )
        Spacer(modifier = Modifier.weight(1f))
        ButtonPrimary(
            text = stringResource(string.continue_string),
            onClick = { component.onEvent(PermissionsRootEvent.GoToNextScreen) }
        )
        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
fun PermissionItem(
    permission: AppPermission,
    onClick: () -> Unit,
) {
    val colors = LocalAppColorScheme.current

    val icon = when (permission.type) {
        AppPermissionType.READ_CONTACTS -> painterResource(drawable.group_of_people)
        AppPermissionType.WRITE_CONTACTS -> painterResource(drawable.group_of_people)
        AppPermissionType.READ_AND_RECEIVE_SMS -> painterResource(drawable.sms)
        AppPermissionType.POST_NOTIFICATIONS -> painterResource(drawable.bell)
        AppPermissionType.AUTO_LAUNCH -> painterResource(drawable.settings)
        AppPermissionType.RECORD_AUDIO -> painterResource(drawable.microphone)
        AppPermissionType.STORAGE -> painterResource(drawable.folder)
        AppPermissionType.NOTIFICATION_LISTENER -> painterResource(drawable.bell)
    }

    val label = when (permission.type) {
        AppPermissionType.READ_CONTACTS -> stringResource(string.permission_read_contacts)
        AppPermissionType.WRITE_CONTACTS -> stringResource(string.permission_write_contacts)
        AppPermissionType.READ_AND_RECEIVE_SMS -> stringResource(string.permission_sms)
        AppPermissionType.POST_NOTIFICATIONS -> stringResource(string.permission_post_notifications)
        AppPermissionType.AUTO_LAUNCH -> stringResource(string.permission_auto_launch)
        AppPermissionType.RECORD_AUDIO -> stringResource(string.permission_microphone)
        AppPermissionType.STORAGE -> stringResource(string.permission_memory)
        AppPermissionType.NOTIFICATION_LISTENER -> stringResource(string.permission_notification_listener)
    }



    val description = when (permission.type) {
        AppPermissionType.NOTIFICATION_LISTENER ->
            "Для перехвата опасных SMS и защищённых уведомлений" // Хардкод
        else -> null
    }
    val status = if (permission.isGranted) stringResource(string.permitted) else stringResource(string.not_permitted)
    val statusColor = if (permission.isGranted) Color(0xff00af66) else colors.errorContainer

    AppCard(
        cornerRadius = 12.dp,
        contentPadding = PaddingValues(4.dp, 4.dp, 12.dp, 4.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        painter = icon,
                        tint = colors.primary,
                        contentDescription = null,
                        modifier = Modifier
                            .background(
                                color = colors.background,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(6.dp)
                            .size(24.dp)
                    )
                    BasicText(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = { colors.onSurfaceVariant },
                        maxLines = 2,
                        autoSize = TextAutoSize.StepBased(
                            maxFontSize = MaterialTheme.typography.bodySmall.fontSize,
                            minFontSize = 6.sp
                        ),
                    )
                }
                BasicText(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = { statusColor },
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(
                        maxFontSize = MaterialTheme.typography.bodySmall.fontSize,
                        minFontSize = 6.sp
                    ),
                )
            }

            // Показываем описание для Notification Listener
            if (description != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
            }
        }
    }
}