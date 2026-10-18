package com.pausiar.openfy.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pausiar.openfy.presentation.theme.OpenfyShapes
import com.pausiar.openfy.presentation.theme.OpenfyTheme

@Immutable
data class BottomBarItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

/**
 * Compact bottom navigation: hairline top border, accent pill indicator behind the
 * selected icon, handles navigation-bar insets itself.
 */
@Composable
fun OpenfyBottomBar(
    items: List<BottomBarItem>,
    selectedRoute: String?,
    onSelect: (BottomBarItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hairline = OpenfyTheme.colors.hairline
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(OpenfyTheme.colors.navBar)
            .drawBehind {
                drawLine(
                    color = hairline,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .navigationBarsPadding()
            .height(64.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            BottomBarTab(
                item = item,
                selected = item.route == selectedRoute,
                onClick = { onSelect(item) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BottomBarTab(
    item: BottomBarItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "tabContent",
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.primary.copy(alpha = 0f),
        animationSpec = tween(200),
        label = "tabIndicator",
    )
    val indicatorWidth by animateDpAsState(
        targetValue = if (selected) 56.dp else 32.dp,
        animationSpec = tween(220),
        label = "tabIndicatorWidth",
    )
    Column(
        modifier = modifier
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(indicatorWidth)
                .height(30.dp)
                .background(indicatorColor, OpenfyShapes.pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (selected) item.selectedIcon else item.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
