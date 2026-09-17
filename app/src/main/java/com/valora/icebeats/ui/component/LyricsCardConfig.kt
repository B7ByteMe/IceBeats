/*
 * icebeats Project Original (2026)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.valora.icebeats.ui.component

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// -----------------------------------------------------------------------------
// Layout styles • cada valor representa un dise•o visual distinto para la tarjeta
// Agregar un nuevo estilo = a•adir un entry aqu• + un composable en LyricsCardLayouts
// -----------------------------------------------------------------------------

enum class LyricsLayoutStyle(
    val displayName: String,
    val description: String,
) {
    GlassCard(
        displayName = "Glass Card",
        description = "Panel de vidrio l•quido",
    ),
    Minimal(
        displayName = "Minimal",
        description = "Limpio y sin distracciones",
    ),
    CoverFocused(
        displayName = "Cover Focus",
        description = "Portada del •lbum destacada",
    ),
    Centered(
        displayName = "Centrado",
        description = "Letra como protagonista",
    ),
    BlurWash(
        displayName = "Blur Wash",
        description = "Fondo ultra difuminado",
    ),
    StreamingModern(
        displayName = "Streaming",
        description = "Estilo app de m•sica moderna",
    ),
}

// -----------------------------------------------------------------------------
// Tipo de fondo para layouts que aceptan variantes de fondo
// -----------------------------------------------------------------------------

enum class LyricsBackgroundType(val displayName: String) {
    AlbumArt("Portada"),
    SolidDark("Oscuro"),
    SolidLight("Claro"),
    Gradient("Degradado"),
}

// -----------------------------------------------------------------------------
// LyricsCardConfig • estado inmutable del usuario.
// Se pasa a LyricsCardByLayout y a LyricsShareCarouselSheet.
// Modifica con .copy(...) para aplicar cambios sin mutaci•n.
// -----------------------------------------------------------------------------

data class LyricsCardConfig(

    /** Qu• template visual se renderiza en la tarjeta */
    val layoutStyle: LyricsLayoutStyle = LyricsLayoutStyle.GlassCard,

    /** Estilo de vidrio/colores/blur; solo los layouts que usan cloudy/liquidGlass lo consumen */
    val glassStyle: LyricsGlassStyle = LyricsGlassStyle.FrostedDark,

    /**
     * Multiplicador sobre el tama•o de fuente calculado autom•ticamente.
     * Rango recomendado: 0.6f • 1.5f
     */
    val textSizeMultiplier: Float = 1f,

    /** Alineaci•n del bloque de letra */
    val textAlign: TextAlign = TextAlign.Center,

    /** Visibilidad de elementos dentro de la tarjeta */
    val showTitle: Boolean = true,
    val showArtist: Boolean = true,
    val showCoverArt: Boolean = true,
    val showBranding: Boolean = true,

    /** Tipo de fondo (consumido por Minimal y StreamingModern) */
    val backgroundType: LyricsBackgroundType = LyricsBackgroundType.AlbumArt,

    /**
     * Padding interno de la tarjeta.
     * Rango recomendado: 12.dp • 36.dp
     */
    val cardPadding: Dp = 24.dp,
)
