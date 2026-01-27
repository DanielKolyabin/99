package org.maksec.screens.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.maksec.navigation.components.profile.SettingsComponent

@Composable
fun SettingsScreen(component: SettingsComponent) {
    val childStack by component.stack.subscribeAsState()
    Children (
        stack = childStack,
        animation = stackAnimation(slide())
    ) { child ->
        when (val instance = child.instance) {
            is SettingsComponent.Child.AccountSettingsScreen -> AccountSettingsScreen(component = instance.component)
            is SettingsComponent.Child.PhoneChangeScreen -> PhoneChangeScreen(component = instance.component)
            is SettingsComponent.Child.EmailChangeScreen -> EmailChangeScreen(component = instance.component)
            is SettingsComponent.Child.DeleteProfileScreen -> DeleteProfileScreen(component = instance.component)
        }
    }
}