package com.zerodroid.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.zerodroid.app.ui.theme.*

/**
 * Navigation destinations
 */
enum class Screen(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Chat("Chat", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline),
    Files("Files", Icons.Filled.Folder, Icons.Outlined.FolderOpen),
    History("History", Icons.Filled.History, Icons.Outlined.History),
    Settings("Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
}

/**
 * Main screen with bottom navigation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var currentScreen by remember { mutableStateOf(Screen.Chat) }

    Scaffold(
        containerColor = Black,
        bottomBar = {
            NavigationBar(
                containerColor = Surface,
                contentColor = TextPrimary,
                tonalElevation = androidx.compose.ui.unit.dp.times(0),
            ) {
                Screen.entries.forEach { screen ->
                    val selected = currentScreen == screen
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentScreen = screen },
                        icon = {
                            Icon(
                                imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.label,
                            )
                        },
                        label = {
                            Text(
                                text = screen.label,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Primary,
                            selectedTextColor = Primary,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = Primary.copy(alpha = 0.12f),
                        ),
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                Screen.Chat -> ChatScreen()
                Screen.Files -> FilesScreen()
                Screen.History -> HistoryScreen()
                Screen.Settings -> SettingsScreen()
            }
        }
    }
}
