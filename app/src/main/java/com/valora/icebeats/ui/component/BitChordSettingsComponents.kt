package com.valora.icebeats.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

internal val BitChordGroupShape = RoundedCornerShape(14.dp)
internal val BIT_CHORD_GROUP_INSET = 16.dp
internal val BIT_CHORD_ROW_INSET = 16.dp
internal val BIT_CHORD_ICON_SIZE = 22.dp
internal val BIT_CHORD_ICON_GAP = 14.dp
internal val BIT_CHORD_TEXT_INSET = BIT_CHORD_ROW_INSET + BIT_CHORD_ICON_SIZE + BIT_CHORD_ICON_GAP

val LocalInsideBitChordGroup = compositionLocalOf { false }

@Composable
fun isBitChordDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
fun bitChordSettingsBackground(): Color =
    if (isBitChordDarkTheme()) Color.Black else Color.White

@Composable
fun bitChordSettingsCardColor(): Color =
    if (isBitChordDarkTheme()) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)

@Composable
fun bitChordSettingsOutlineColor(): Color =
    if (isBitChordDarkTheme()) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)

@Composable
fun bitChordSettingsSecondaryTextColor(): Color =
    if (isBitChordDarkTheme()) Color(0xFF8E8E93) else Color(0xFF6E6E73)

private val IosSwitchTrackWidth = 64.dp
private val IosSwitchTrackHeight = 28.dp
private val IosSwitchThumbWidth = 40.dp
private val IosSwitchThumbHeight = 24.dp
private val IosSwitchThumbPadding = 2.dp

/**
 * BitChord / iOS 26 style capsule switch with spring stretch animation.
 */
@Composable
fun BitChordIosSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val dark = isBitChordDarkTheme()
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val position by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 700f),
        label = "iosSwitchThumb",
    )
    val stretch by animateFloatAsState(
        targetValue = if (pressed) 1.18f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 900f),
        label = "iosSwitchStretch",
    )
    val track by animateColorAsState(
        targetValue = when {
            checked && dark -> Color(0xFF30D158)
            checked -> Color(0xFF34C759)
            else -> Color(0xFF8E8E93).copy(alpha = if (dark) 0.36f else 0.22f)
        },
        label = "iosSwitchTrack",
    )
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr

    Box(
        modifier = modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .then(
                if (onCheckedChange != null) {
                    Modifier.minimumInteractiveComponentSize().toggleable(
                        value = checked,
                        enabled = enabled,
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Switch,
                        onValueChange = { next ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onCheckedChange(next)
                        },
                    )
                } else {
                    Modifier
                },
            )
            .size(IosSwitchTrackWidth, IosSwitchTrackHeight)
            .clip(CircleShape)
            .background(track),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    val travel = (IosSwitchTrackWidth - IosSwitchThumbWidth - IosSwitchThumbPadding * 2).toPx()
                    val x = IosSwitchThumbPadding.toPx() + travel * position
                    translationX = if (ltr) x else -x
                    transformOrigin = TransformOrigin(if (checked == ltr) 1f else 0f, 0.5f)
                    scaleX = stretch
                }
                .size(IosSwitchThumbWidth, IosSwitchThumbHeight)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

@Composable
fun BitChordSettingsPageTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.displayLarge.copy(
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
        ),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
    )
}

@Composable
fun BitChordSettingsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val secondaryColor = bitChordSettingsSecondaryTextColor()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bitChordSettingsCardColor())
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = secondaryColor,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    color = secondaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = null,
                tint = secondaryColor,
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .clickable { onQueryChange("") },
            )
        }
    }
}

@Composable
fun BitChordSettingsGroup(
    header: String? = null,
    footer: String? = null,
    topSpacing: Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val secondaryColor = bitChordSettingsSecondaryTextColor()
    if (header != null) {
        Text(
            text = header.uppercase(Locale.ROOT),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
            ),
            color = secondaryColor,
            modifier = Modifier.padding(
                start = BIT_CHORD_GROUP_INSET + 4.dp,
                end = BIT_CHORD_GROUP_INSET,
                top = 24.dp,
                bottom = 8.dp,
            ),
        )
    } else if (topSpacing > 0.dp) {
        Spacer(Modifier.height(topSpacing))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = BIT_CHORD_GROUP_INSET)
            .clip(BitChordGroupShape)
            .background(bitChordSettingsCardColor()),
        content = content,
    )

    if (footer != null) {
        Text(
            text = footer,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = secondaryColor,
            modifier = Modifier.padding(
                start = BIT_CHORD_GROUP_INSET + 4.dp,
                end = BIT_CHORD_GROUP_INSET + 4.dp,
                top = 8.dp,
            ),
        )
    }
}

@Composable
fun BitChordRowDivider(
    inset: Dp = BIT_CHORD_TEXT_INSET,
) {
    HorizontalDivider(
        modifier = Modifier.padding(start = inset),
        thickness = 0.5.dp,
        color = bitChordSettingsOutlineColor(),
    )
}

@Composable
fun BitChordChevron(
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = bitChordSettingsSecondaryTextColor().copy(alpha = 0.7f),
        modifier = modifier.size(20.dp),
    )
}

@Composable
fun BitChordSettingsBadge(text: String) {
    Text(
        text = text.uppercase(Locale.ROOT),
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun BitChordSettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    iconContent: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    value: String? = null,
    badge: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val secondaryColor = bitChordSettingsSecondaryTextColor()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.45f)
            .heightIn(min = 52.dp)
            .padding(horizontal = BIT_CHORD_ROW_INSET, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconContent != null) {
            iconContent()
            Spacer(Modifier.width(BIT_CHORD_ICON_GAP))
        } else if (iconPainter != null) {
            Icon(
                painter = iconPainter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(BIT_CHORD_ICON_SIZE),
            )
            Spacer(Modifier.width(BIT_CHORD_ICON_GAP))
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(BIT_CHORD_ICON_SIZE),
            )
            Spacer(Modifier.width(BIT_CHORD_ICON_GAP))
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    BitChordSettingsBadge(badge)
                }
            }
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                    color = secondaryColor,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.width(12.dp))
        if (trailing != null) {
            trailing()
        } else if (value != null || onClick != null) {
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    color = secondaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(4.dp))
            }
            BitChordChevron()
        }
    }
}

