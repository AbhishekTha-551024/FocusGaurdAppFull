package com.focusguard.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val SkeletonBase      = Color(0xFF0E2419)
private val SkeletonHighlight = Color(0xFF163827)
private val SkeletonShimmer   = Color(0xFF1E4A32)

/**
 * FocusGuard Shimmer Skeleton System
 * ====================================
 * Provides a premium animated shimmer effect for initial data loading states.
 * Usage:
 *   if (uiState.isLoading) AppListSkeleton() else { /* real list */ }
 */

/** Animated shimmer brush — use as background for any placeholder shape. */
@Composable
fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )
    return Brush.linearGradient(
        colors = listOf(SkeletonBase, SkeletonHighlight, SkeletonShimmer, SkeletonHighlight, SkeletonBase),
        start = Offset(translateAnim - 200f, 0f),
        end   = Offset(translateAnim + 200f, 0f)
    )
}

/** A single shimmer placeholder rectangle. */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, height: Dp = 16.dp, width: Dp? = null, cornerRadius: Dp = 8.dp) {
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(shimmerBrush())
    )
}

/** Skeleton for a single app list row: [Icon] [Name/Package] [Toggle] */
@Composable
fun AppListSkeletonItem(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SkeletonBase)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(shimmerBrush()))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBox(height = 14.dp, width = 120.dp)
            SkeletonBox(height = 11.dp, width = 80.dp)
        }
        Box(modifier = Modifier.width(46.dp).height(26.dp).clip(CircleShape).background(shimmerBrush()))
    }
}

/** Full skeleton list with [count] rows. Use on AppBlocker/TimeLimits/Schedules during load. */
@Composable
fun AppListSkeleton(count: Int = 8, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SkeletonBox(height = 12.dp, width = 90.dp, cornerRadius = 6.dp)
        repeat(count) { AppListSkeletonItem() }
    }
}

/** Skeleton for a home screen card. */
@Composable
fun HomeCardSkeleton(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SkeletonBase).padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(shimmerBrush()))
                SkeletonBox(height = 16.dp, width = 140.dp)
            }
            SkeletonBox(height = 32.dp, width = 60.dp, cornerRadius = 10.dp)
            SkeletonBox(height = 12.dp, width = 200.dp)
        }
    }
}
