package com.zerodroid.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zerodroid.app.ui.theme.*
import com.zerodroid.app.ui.viewmodel.ChatViewModel
import com.zerodroid.app.ui.viewmodel.EditorViewModel
import com.zerodroid.app.ui.viewmodel.FileExplorerViewModel
import com.zerodroid.app.ui.viewmodel.SettingsViewModel
import com.zerodroid.app.ui.viewmodel.TerminalViewModel

/**
 * Navigation destinations — 6 tabs
 */
enum class Screen(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Chat("Chat", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline),
    Editor("Editor", Icons.Filled.Code, Icons.Outlined.Code),
    Terminal("Terminal", Icons.Filled.Terminal, Icons.Filled.Terminal),
    Files("Files", Icons.Filled.Folder, Icons.Outlined.FolderOpen),
    History("History", Icons.Filled.History, Icons.Outlined.History),
    Settings("Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var currentScreen by remember { mutableStateOf(Screen.Chat) }

    val chatViewModel: ChatViewModel = viewModel()
    val editorViewModel: EditorViewModel = viewModel()
    val fileExplorerViewModel: FileExplorerViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val terminalViewModel: TerminalViewModel = viewModel()

    Scaffold(
        containerColor = Black,
        bottomBar = {
            NavigationBar(
                containerColor = Surface, contentColor = TextPrimary, tonalElevation = 0.dp,
            ) {
                Screen.entries.forEach { screen ->
                    val selected = currentScreen == screen
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentScreen = screen },
                        icon = { Icon(if (selected) screen.selectedIcon else screen.unselectedIcon, screen.label,
                            modifier = Modifier.size(20.dp)) },
                        label = { Text(screen.label, style = MaterialTheme.typography.labelSmall, fontSize = androidx.compose.ui.unit.sp(9)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Primary, selectedTextColor = Primary,
                            unselectedIconColor = TextSecondary, unselectedTextColor = TextSecondary,
                            indicatorColor = Primary.copy(alpha = 0.12f),
                        ),
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                Screen.Chat -> ChatScreen(chatViewModel)
                Screen.Editor -> EditorScreen(editorViewModel)
                Screen.Terminal -> TerminalScreen(terminalViewModel)
                Screen.Files -> FilesScreen(fileExplorerViewModel)
                Screen.History -> HistoryScreen(chatViewModel)
                Screen.Settings -> SettingsScreen(settingsViewModel)
            }
        }
    }
}
