package com.zerodroid.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Termux-style extra keyboard bar with modifier keys, F-keys, arrows, and special characters.
 *
 * Features:
 *   • Top modifier row: ⊞ Shift Ctrl Alt AltGr Del Esc Tab [123/ABC]
 *   • 123 mode: F1-F12, arrows, Home/End/PgUp/PgDn, / * - + Enter
 *   • Toggleable modifiers (Ctrl, Alt, Shift stay active until next key)
 *   • Haptic feedback on key press
 *   • Matches Termux/Windows RDP keyboard exactly
 */

// ─── Key Types ──────────────────────────────────
enum class ExtraKeyType {
    NORMAL,      // Types the character
    MODIFIER,    // Toggleable — Ctrl, Alt, Shift, AltGr
    FUNCTION,    // F1-F12
    NAVIGATION,  // Home, End, PgUp, PgDn, Insert, Delete
    ARROW,       // ←→↑↓
    ACTION,      // Enter, Esc, Tab, Del
    TOGGLE,      // ABC/123 mode switch
    SPECIAL,     // Win key, Backspace, etc.
}

data class ExtraKey(
    val label: String,
    val type: ExtraKeyType = ExtraKeyType.NORMAL,
    val value: String = label,      // What gets sent/inserted
    val widthWeight: Float = 1f,    // Relative width
)

// ─── Active modifier state ──────────────────────
data class ModifierState(
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
    val altGr: Boolean = false,
    val superKey: Boolean = false,
)

@Composable
fun ExtraKeysBar(
    onKeyPress: (ExtraKey, ModifierState) -> Unit,
    onModifierChanged: (ModifierState) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var modifiers by remember { mutableStateOf(ModifierState()) }
    var show123 by remember { mutableStateOf(false) }

    // Colors matching the screenshot
    val keyBg = Color(0xFF2D2D2D)
    val keyBgActive = Color(0xFF505050)
    val keyBorder = Color(0xFF3D3D3D)
    val keyText = Color(0xFFCCCCCC)
    val barBg = Color(0xFF1A1A1A)

    Column(modifier = modifier.fillMaxWidth().background(barBg)) {
        // ─── Top Modifier Row ───────────────────
        // ⊞  Shift  Ctrl  Alt  AltGr  Del  Esc  Tab  [123/ABC]
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)) {

            // ⊞ Super/Win key
            ExtraKeyButton("⊞", isActive = modifiers.superKey, bg = keyBg, bgActive = keyBgActive,
                border = keyBorder, textColor = keyText, weight = 0.8f) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                modifiers = modifiers.copy(superKey = !modifiers.superKey)
                onModifierChanged(modifiers)
            }

            // Shift
            ExtraKeyButton("Shift", isActive = modifiers.shift, bg = keyBg, bgActive = keyBgActive,
                border = keyBorder, textColor = keyText, weight = 1f) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                modifiers = modifiers.copy(shift = !modifiers.shift)
                onModifierChanged(modifiers)
            }

            // Ctrl
            ExtraKeyButton("Ctrl", isActive = modifiers.ctrl, bg = keyBg, bgActive = keyBgActive,
                border = keyBorder, textColor = keyText, weight = 1f) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                modifiers = modifiers.copy(ctrl = !modifiers.ctrl)
                onModifierChanged(modifiers)
            }

            // Alt
            ExtraKeyButton("Alt", isActive = modifiers.alt, bg = keyBg, bgActive = keyBgActive,
                border = keyBorder, textColor = keyText, weight = 0.8f) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                modifiers = modifiers.copy(alt = !modifiers.alt)
                onModifierChanged(modifiers)
            }

            // AltGr
            ExtraKeyButton("AltGr", isActive = modifiers.altGr, bg = keyBg, bgActive = keyBgActive,
                border = keyBorder, textColor = keyText, weight = 0.9f) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                modifiers = modifiers.copy(altGr = !modifiers.altGr)
                onModifierChanged(modifiers)
            }

            // Del
            ExtraKeyButton("Del", bg = keyBg, border = keyBorder, textColor = keyText, weight = 0.8f) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onKeyPress(ExtraKey("Del", ExtraKeyType.ACTION, "\u007F"), modifiers)
                resetModifiers { modifiers = it; onModifierChanged(it) }
            }

            // Esc
            ExtraKeyButton("Esc", bg = keyBg, border = keyBorder, textColor = keyText, weight = 0.8f) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onKeyPress(ExtraKey("Esc", ExtraKeyType.ACTION, "\u001B"), modifiers)
                resetModifiers { modifiers = it; onModifierChanged(it) }
            }

            // Tab
            ExtraKeyButton("Tab", bg = keyBg, border = keyBorder, textColor = keyText, weight = 0.8f) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onKeyPress(ExtraKey("Tab", ExtraKeyType.ACTION, "\t"), modifiers)
                resetModifiers { modifiers = it; onModifierChanged(it) }
            }

            // 123/ABC toggle
            ExtraKeyButton(if (show123) "ABC" else "123", bg = keyBg, border = keyBorder,
                textColor = Color(0xFF9CDCFE), weight = 0.8f) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                show123 = !show123
            }
        }

        // ─── Extended Keys Grid (123 mode) ──────
        if (show123) {
            ExtendedKeysGrid(
                keyBg = keyBg, keyBorder = keyBorder, keyText = keyText,
                onKeyPress = { key ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onKeyPress(key, modifiers)
                    resetModifiers { modifiers = it; onModifierChanged(it) }
                },
            )
        }
    }
}

/**
 * Extended 123 grid — F-keys, arrows, Home/End, PgUp/PgDn, special chars
 * Layout matches the Termux/Windows RDP screenshot exactly
 */
@Composable
private fun ExtendedKeysGrid(
    keyBg: Color, keyBorder: Color, keyText: Color,
    onKeyPress: (ExtraKey) -> Unit,
) {
    val rowHeight = 38.dp

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 1.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {

        // Row 1: F1 F2 F3 | [paste] [bksp] / | Home ↑ PgUp
        Row(Modifier.fillMaxWidth().height(rowHeight), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            GridKey("F1", keyBg, keyBorder, keyText, 1f) { onKeyPress(ExtraKey("F1", ExtraKeyType.FUNCTION, "\u001BOP")) }
            GridKey("F2", keyBg, keyBorder, keyText, 1f) { onKeyPress(ExtraKey("F2", ExtraKeyType.FUNCTION, "\u001BOQ")) }
            GridKey("F3", keyBg, keyBorder, keyText, 1f) { onKeyPress(ExtraKey("F3", ExtraKeyType.FUNCTION, "\u001BOR")) }
            Spacer(Modifier.width(4.dp))
            GridKey("📋", keyBg, keyBorder, keyText, 1f) { onKeyPress(ExtraKey("Paste", ExtraKeyType.SPECIAL, "PASTE")) }
            GridKey("⌫", keyBg, keyBorder, keyText, 1f) { onKeyPress(ExtraKey("Backspace", ExtraKeyType.ACTION, "\b")) }
            GridKey("/", keyBg, keyBorder, keyText, 1f) { onKeyPress(ExtraKey("/", ExtraKeyType.NORMAL, "/")) }
            Spacer(Modifier.width(4.dp))
            GridKey("Home", keyBg, keyBorder, keyText, 1.2f) { onKeyPress(ExtraKey("Home", ExtraKeyType.NAVIGATION, "\u001B[H")) }
            GridKey("↑", keyBg, keyBorder, keyText, 1f) { onKeyPress(ExtraKey("↑", ExtraKeyType.ARROW, "\u001B[A")) }
            GridKey("PgUp", keyBg, keyBorder, keyText, 1.2f) { onKeyPress(ExtraKey("PgUp", ExtraKeyType.NAVIGATION, "\u001B[5~")) }
        }

        // Row 2: F4 F5 F6 | [screenshot] ← * | ← [blank] →
        Row(Modifier.fillMaxWidth().height(rowHeight), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            GridKey("F4", keyBg, keyBorder, keyText, 1f) { onKeyPress(ExtraKey("F4", ExtraKeyType.FUNCTION, "\u001BOS")) }
            GridKey("F5", keyBg, keyBorder, keyText, 1f) { onKeyPress(ExtraKey("F5", ExtraKeyType.FUNCTION, "\u001B[15~")) }
            GridKey("F6", keyBg, keyBorder, keyText, 1f) { onKeyPress(ExtraKey("F6", ExtraKeyType.FUNCTION, "\u001B[17~")) }
            Spacer(Modifier.width(4.dp))
            GridKey("📷", keyBg, keyBorder, keyText, 1f) { onKeyPress(ExtraKey("Screenshot", ExtraKeyType.SPECIAL, "SCREENSHOT")) }
            GridKey("↵", keyBg, keyBorder, keyText, 1f) { onKeyPress(ExtraKey("Enter", ExtraKeyType.ACTION, "\n")) }
            GridKey("*", keyBg, keyBorder, keyText, 1f) { onKeyPress(ExtraKey("*", ExtraKeyType.NORMAL, "*")) }
            Spacer(Modifier.width(4.dp))
            GridKey("←", keyBg, keyBorder, keyText, 1f) { onKeyPress(ExtraKey("←", ExtraKeyType.ARROW, "\u001B[D")) }
            GridKey("", keyBg, keyBorder, keyText, 1f) { /* spacer key */ }
            GridKey("→", keyBg, keyBorder, keyText, 1f) { onKeyPress(ExtraKey("→", ExtraKeyType.ARROW, "\u001B[C")) }
        }

        // Row 3: F7 F8 F9 | [blank] [blank] - | End ↓ PgDn
        Row(Modifier.fillMaxWidth().height(rowHeight), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            GridKey("F7", keyBg, keyBorder, keyText, 1f) { onKeyPress(ExtraKey("F7", ExtraKeyType.FUNCTION, "\u001B[18~")) }
            GridKey("F8", keyBg, keyBorder, keyText, 1f) { onKeyPress(ExtraKey("F8", ExtraKeyType.FUNCTION, "\u001B[19~")) }
            GridKey("F9", keyBg, keyBorder, keyText, 1f) { onKeyPress(ExtraKey("F9", ExtraKeyType.FUNCTION, "\u001B[20~")) }
            Spacer(Modifier.width(4.dp))
            GridKey("|", keyBg, keyBorder, keyText, 1f) { onKeyPress(ExtraKey("|", ExtraKeyType.NORMAL, "|")) }
            GridKey("\\", keyBg, keyBorder, keyText, 1f) { onKeyPress(ExtraKey("\\", ExtraKeyType.NORMAL, "\\")) }
            GridKey("-", keyBg, keyBorder, keyText, 1f) { onKeyPress(ExtraKey("-", ExtraKeyType.NORMAL, "-")) }
            Spacer(Modifier.width(4.dp))
            GridKey("End", keyBg, keyBorder, keyText, 1.2f) { onKeyPress(ExtraKey("End", ExtraKeyType.NAVIGATION, "\u001B[F")) }
            GridKey("↓", keyBg, keyBorder, keyText, 1f) { onKeyPress(ExtraKey("↓", ExtraKeyType.ARROW, "\u001B[B")) }
            GridKey("PgDn", keyBg, keyBorder, keyText, 1.2f) { onKeyPress(ExtraKey("PgDn", ExtraKeyType.NAVIGATION, "\u001B[6~")) }
        }

        // Row 4: F10 F11 F12 | Number Keys | + | Insert Delete Enter
        Row(Modifier.fillMaxWidth().height(rowHeight), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            GridKey("F10", keyBg, keyBorder, keyText, 1f) { onKeyPress(ExtraKey("F10", ExtraKeyType.FUNCTION, "\u001B[21~")) }
            GridKey("F11", keyBg, keyBorder, keyText, 1f) { onKeyPress(ExtraKey("F11", ExtraKeyType.FUNCTION, "\u001B[23~")) }
            GridKey("F12", keyBg, keyBorder, keyText, 1f) { onKeyPress(ExtraKey("F12", ExtraKeyType.FUNCTION, "\u001B[24~")) }
            Spacer(Modifier.width(4.dp))
            GridKey("~ ` _", keyBg, keyBorder, keyText, 2f) { onKeyPress(ExtraKey("~", ExtraKeyType.NORMAL, "~")) }
            GridKey("+", keyBg, keyBorder, keyText, 1f) { onKeyPress(ExtraKey("+", ExtraKeyType.NORMAL, "+")) }
            Spacer(Modifier.width(4.dp))
            GridKey("Ins", keyBg, keyBorder, keyText, 1.2f) { onKeyPress(ExtraKey("Insert", ExtraKeyType.NAVIGATION, "\u001B[2~")) }
            GridKey("Del", keyBg, keyBorder, keyText, 1.2f) { onKeyPress(ExtraKey("Delete", ExtraKeyType.NAVIGATION, "\u001B[3~")) }
            GridKey("Enter", keyBg, keyBorder, keyText, 1.2f) { onKeyPress(ExtraKey("Enter", ExtraKeyType.ACTION, "\n")) }
        }
    }
}

// ─── Individual Key Button ──────────────────────

@Composable
private fun RowScope.ExtraKeyButton(
    label: String,
    isActive: Boolean = false,
    bg: Color, bgActive: Color = bg,
    border: Color, textColor: Color,
    weight: Float = 1f,
    onClick: () -> Unit,
) {
    val actualBg = if (isActive) bgActive else bg
    val actualTextColor = if (isActive) Color(0xFF9CDCFE) else textColor

    Box(modifier = Modifier.weight(weight).height(36.dp)
        .clip(RoundedCornerShape(4.dp))
        .background(actualBg)
        .border(0.5.dp, border, RoundedCornerShape(4.dp))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Text(label, color = actualTextColor, fontSize = 12.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun RowScope.GridKey(
    label: String, bg: Color, border: Color, textColor: Color,
    weight: Float = 1f, onClick: () -> Unit,
) {
    Box(modifier = Modifier.weight(weight).fillMaxHeight()
        .clip(RoundedCornerShape(3.dp))
        .background(bg)
        .border(0.5.dp, border, RoundedCornerShape(3.dp))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Text(label, color = textColor, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}

// Reset non-sticky modifiers after a key press
private fun resetModifiers(update: (ModifierState) -> Unit) {
    update(ModifierState()) // Reset all modifiers after each key press
}
