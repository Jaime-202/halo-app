package com.lifedashboard.presentation.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifedashboard.presentation.theme.*

/**
 * Barra de navegación "Órbita".
 *
 * Una cápsula con las pestañas: la activa se expande y muestra su nombre, las demás
 * solo el icono. A la derecha, separado, el botón "+" para registrar cualquier cosa.
 */
@Composable
fun OrbitNavBar(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Obsidian)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .clip(CircleShape)
                .background(Panel)
                .border(1.dp, LineStrong, CircleShape)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            items.forEach { item ->
                val selected = currentRoute?.substringBefore("?") ==
                    item.screen.route.substringBefore("?")
                OrbitTab(
                    item = item,
                    selected = selected,
                    onClick = {
                        if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigate(item.screen)
                    },
                )
            }
        }

        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(12.dp, CircleShape, ambientColor = Ion, spotColor = Ion)
                .clip(CircleShape)
                .background(Ion)
                .clickable(role = Role.Button, onClickLabel = "Registrar") {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onAction()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Registrar", tint = Obsidian, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun OrbitTab(
    item: BottomNavItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(if (selected) PanelHigh else Panel, label = "tabBg")
    val fg by animateColorAsState(if (selected) Ion else TextMute, label = "tabFg")

    Row(
        modifier = Modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(bg)
            .then(if (selected) Modifier.border(1.dp, Ion.copy(alpha = 0.25f), CircleShape) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClickLabel = item.label,
                onClick = onClick,
            )
            .padding(horizontal = if (selected) 14.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (selected) item.selectedIcon else item.icon,
            contentDescription = item.label,
            tint = fg,
            modifier = Modifier.size(20.dp),
        )
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + expandHorizontally(spring(stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut() + shrinkHorizontally(spring(stiffness = Spring.StiffnessMediumLow)),
        ) {
            Text(
                text = item.label,
                modifier = Modifier.padding(start = 8.dp),
                style = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp, letterSpacing = 0.3.sp),
                color = TextHigh,
                maxLines = 1,
            )
        }
    }
}
