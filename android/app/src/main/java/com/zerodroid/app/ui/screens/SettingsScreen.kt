package com.zerodroid.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zerodroid.app.ui.theme.*

/**
 * Settings screen — provider config, model management, MCP, theme
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
    ) {
        TopAppBar(
            title = {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Black),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ─── AI Provider Section ────────────
            SettingsSection("AI Provider") {
                SettingsItem(
                    icon = Icons.Filled.Memory,
                    title = "Local Model (NPU)",
                    subtitle = "gemma4:e2b — On-device inference",
                    onClick = { },
                )
                SettingsItem(
                    icon = Icons.Filled.Cloud,
                    title = "Gemini API",
                    subtitle = "Free cloud inference",
                    onClick = { },
                )
                SettingsItem(
                    icon = Icons.Filled.Key,
                    title = "API Keys",
                    subtitle = "Configure cloud provider keys",
                    onClick = { },
                )
            }

            // ─── Model Management ───────────────
            SettingsSection("Models") {
                SettingsItem(
                    icon = Icons.Filled.Download,
                    title = "Download Models",
                    subtitle = "Browse HuggingFace LiteRT models",
                    onClick = { },
                )
                SettingsItem(
                    icon = Icons.Filled.Storage,
                    title = "Manage Storage",
                    subtitle = "Delete downloaded models",
                    onClick = { },
                )
                SettingsItem(
                    icon = Icons.Filled.Speed,
                    title = "Benchmark",
                    subtitle = "Test model speed on your device",
                    onClick = { },
                )
            }

            // ─── MCP Section ────────────────────
            SettingsSection("MCP Servers") {
                SettingsItem(
                    icon = Icons.Filled.Extension,
                    title = "Configure MCP Servers",
                    subtitle = "Connect external tools and services",
                    onClick = { },
                )
                SettingsItem(
                    icon = Icons.Filled.Add,
                    title = "Add MCP Server",
                    subtitle = "stdio or HTTP transport",
                    onClick = { },
                )
            }

            // ─── Appearance ─────────────────────
            SettingsSection("Appearance") {
                SettingsItem(
                    icon = Icons.Filled.DarkMode,
                    title = "Theme",
                    subtitle = "AMOLED Dark",
                    onClick = { },
                )
                SettingsItem(
                    icon = Icons.Filled.TextFields,
                    title = "Font Size",
                    subtitle = "Medium",
                    onClick = { },
                )
            }

            // ─── About ──────────────────────────
            SettingsSection("About") {
                SettingsItem(
                    icon = Icons.Filled.Info,
                    title = "ZeroDroid",
                    subtitle = "v1.0.0 — Open Source AI Coding Agent",
                    onClick = { },
                )
                SettingsItem(
                    icon = Icons.Filled.Code,
                    title = "GitHub",
                    subtitle = "github.com/ZeroTheGOAT/Zerodroid",
                    onClick = { },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.padding(top = 12.dp),
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Surface(
            color = SurfaceContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = SurfaceContainer,
        shape = RoundedCornerShape(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = TextDim,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
