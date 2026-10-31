package com.sangita.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sangita.app.ui.theme.DarkBorder
import com.sangita.app.ui.theme.SangitaEmerald
import com.sangita.app.ui.theme.SangitaSurface
import com.sangita.app.ui.theme.SangitaTextDim

enum class NavTab {
    HOME, DISCOVER, LIBRARY, DOWNLOADS, SEARCH, PROFILE
}

@Composable
fun SangitaBottomNavBar(
    currentTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(SangitaSurface)
            .border(width = 1.dp, color = DarkBorder)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomNavItem(
            label = "Home",
            icon = Icons.Default.Home,
            isSelected = currentTab == NavTab.HOME,
            onClick = { onTabSelected(NavTab.HOME) }
        )
        BottomNavItem(
            label = "Discover",
            icon = Icons.Default.Explore,
            isSelected = currentTab == NavTab.DISCOVER,
            onClick = { onTabSelected(NavTab.DISCOVER) }
        )
        BottomNavItem(
            label = "Library",
            icon = Icons.Default.LibraryMusic,
            isSelected = currentTab == NavTab.LIBRARY,
            onClick = { onTabSelected(NavTab.LIBRARY) }
        )
        BottomNavItem(
            label = "Downloads",
            icon = Icons.Default.Download,
            isSelected = currentTab == NavTab.DOWNLOADS,
            onClick = { onTabSelected(NavTab.DOWNLOADS) }
        )
    }
}

@Composable
private fun BottomNavItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val activeColor = SangitaEmerald
    val inactiveColor = SangitaTextDim

    Column(
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) activeColor else inactiveColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) activeColor else inactiveColor
        )
    }
}
