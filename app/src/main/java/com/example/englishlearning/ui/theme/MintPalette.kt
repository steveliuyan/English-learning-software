package com.example.englishlearning.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The single source of truth for the app's mint palette.
 *
 * These tokens were previously redeclared privately in every screen, and F1-03 would have
 * added a third identical copy. Keep them here instead: a colour that exists only inside one
 * screen is invisible to the rest of the app, and small hex differences then creep in between
 * screens that are meant to look the same.
 */
internal val MintBackground: Color = AppPalette.Background
internal val MintSurface: Color = AppPalette.Surface
internal val MintTint: Color = Color(0xFFDDF7E8)
internal val MintPrimary: Color = DomainColors.AiSpeech.base
internal val MintPrimaryDark: Color = AppPalette.TextPrimary
internal val MintOutline: Color = AppPalette.Separator
internal val MintTextMuted: Color = AppPalette.TextSecondary

/** Brand gradient stops used by the launch/lock surfaces. */
internal val AppleMintStart: Color = Color(0xFFA8F3C8)
internal val AppleMintMiddle: Color = Color(0xFF5DDFB4)
internal val AppleMintEnd: Color = Color(0xFF35B9B5)
internal val AppleMintLight: Color = Color(0xFFD9FFF0)
