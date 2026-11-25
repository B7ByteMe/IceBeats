package com.valora.icebeats.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.valora.icebeats.LocalPlayerAwareWindowInsets
import com.valora.icebeats.R
import com.valora.icebeats.ui.utils.isScrollingUp

/**
 * Stacked floating action buttons for Library screens.
 * Places the Generator button prominently directly ABOVE the floating '+' action button.
 */
@Composable
fun BoxScope.LibraryFloatingActions(
    visible: Boolean = true,
    lazyListState: LazyListState? = null,
    lazyGridState: LazyGridState? = null,
    onOpenGenerator: () -> Unit,
    onCreatePlaylist: (() -> Unit)? = null,
    onShuffle: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isScrollingUp = when {
        lazyListState != null -> lazyListState.isScrollingUp()
        lazyGridState != null -> lazyGridState.isScrollingUp()
        else -> true
    }

    AnimatedVisibility(
        visible = visible && isScrollingUp,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
            )
            .then(modifier),
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            // Satellite Companion Generator Button - Always floating above
            SmallFloatingActionButton(
                onClick = onOpenGenerator,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 8.dp,
                ),
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = stringResource(R.string.generator),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }

            // Primary Bottom Action: Create Playlist (+) or Shuffle
            if (onCreatePlaylist != null) {
                FloatingActionButton(
                    onClick = onCreatePlaylist,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 10.dp,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.create_playlist),
                        modifier = Modifier.size(24.dp),
                    )
                }
            } else if (onShuffle != null) {
                FloatingActionButton(
                    onClick = onShuffle,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 10.dp,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.shuffle),
                        contentDescription = stringResource(R.string.shuffle),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}
