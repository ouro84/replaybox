package com.replaybox.app.ui

import android.content.Context
import android.graphics.Color
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Caption text size, as a fraction of the video height (Media3 SubtitleView).
 * NORMAL is Media3's own default; LARGE and XLARGE are 1.25x and 1.5x.
 */
enum class SubtitleSize(val fraction: Float) {
    NORMAL(0.0533f),
    LARGE(0.0666f),
    XLARGE(0.08f),
}

/** What sits behind the caption text. */
enum class SubtitleBackground { OUTLINE, SEMI, OPAQUE }

/**
 * Subtitle legibility (ROADMAP M4): one app-wide setting that replaces the
 * system caption style and the styles embedded in the cues. Default: large
 * text on a semi-opaque band, the household's sofa-distance pick.
 */
data class SubtitleStyle(
    val size: SubtitleSize = SubtitleSize.LARGE,
    val background: SubtitleBackground = SubtitleBackground.SEMI,
) {
    /** ARGB of the band behind the text; 0 means no band. Ground colour, never pure black. */
    val backgroundArgb: Int
        get() = when (background) {
            SubtitleBackground.OUTLINE -> 0
            SubtitleBackground.SEMI -> 0xB30E1013.toInt()
            SubtitleBackground.OPAQUE -> 0xFF0E1013.toInt()
        }

    /** Without a band, the text carries a dark outline to stay readable on bright frames. */
    val outlined: Boolean get() = background == SubtitleBackground.OUTLINE

    companion object {
        fun decode(size: String?, background: String?): SubtitleStyle = SubtitleStyle(
            size = SubtitleSize.values().firstOrNull { it.name == size } ?: SubtitleSize.LARGE,
            background = SubtitleBackground.values().firstOrNull { it.name == background } ?: SubtitleBackground.SEMI,
        )
    }
}

interface SubtitleStyleStore {
    val style: StateFlow<SubtitleStyle>
    fun set(style: SubtitleStyle)
}

/** Device-local, plain SharedPreferences, observed live by the player and the Paramètres preview. */
class SharedPrefsSubtitleStyleStore(context: Context) : SubtitleStyleStore {
    private val prefs = context.getSharedPreferences("subtitles", Context.MODE_PRIVATE)
    private val _style = MutableStateFlow(
        SubtitleStyle.decode(prefs.getString(KEY_SIZE, null), prefs.getString(KEY_BACKGROUND, null)),
    )
    override val style: StateFlow<SubtitleStyle> = _style.asStateFlow()

    override fun set(style: SubtitleStyle) {
        _style.value = style
        prefs.edit()
            .putString(KEY_SIZE, style.size.name)
            .putString(KEY_BACKGROUND, style.background.name)
            .apply()
    }

    private companion object {
        const val KEY_SIZE = "size"
        const val KEY_BACKGROUND = "background"
    }
}

/**
 * Applies [style] to a Media3 SubtitleView. Embedded colours and font sizes
 * are ignored so every Provider renders the same; cue positions are kept.
 */
internal fun SubtitleView.applyStyle(style: SubtitleStyle) {
    setApplyEmbeddedStyles(false)
    setApplyEmbeddedFontSizes(false)
    setStyle(
        CaptionStyleCompat(
            CAPTION_TEXT,
            style.backgroundArgb,
            Color.TRANSPARENT,
            if (style.outlined) CaptionStyleCompat.EDGE_TYPE_OUTLINE else CaptionStyleCompat.EDGE_TYPE_NONE,
            CAPTION_EDGE,
            null,
        ),
    )
    setFractionalTextSize(style.size.fraction)
}

/** Paper White text, ground-coloured outline. */
private const val CAPTION_TEXT = 0xFFF3F4F6.toInt()
private const val CAPTION_EDGE = 0xFF0E1013.toInt()
