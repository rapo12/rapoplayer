package com.rapoplayer.tv.ui

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rapoplayer.tv.R

/** Font Awesome Free 6.7.2 Solid, CC BY 4.0. See bundled licenses and THIRD_PARTY_NOTICES.md. */
enum class RapoGlyph(@param:DrawableRes val drawable: Int) {
    Back(R.drawable.fa_arrow_left), Refresh(R.drawable.fa_arrows_rotate),
    Previous(R.drawable.fa_backward_step), ClearCache(R.drawable.fa_broom),
    Guide(R.drawable.fa_calendar_days), Check(R.drawable.fa_check),
    Up(R.drawable.fa_chevron_up), Down(R.drawable.fa_chevron_down),
    Live(R.drawable.fa_circle_dot), About(R.drawable.fa_circle_info),
    Recent(R.drawable.fa_clock_rotate_left), Subtitles(R.drawable.fa_closed_captioning),
    Fit(R.drawable.fa_compress), Fill(R.drawable.fa_expand),
    Show(R.drawable.fa_eye), Hide(R.drawable.fa_eye_slash),
    License(R.drawable.fa_file_lines), Next(R.drawable.fa_forward_step),
    Settings(R.drawable.fa_gear), Pin(R.drawable.fa_key),
    Language(R.drawable.fa_language), Link(R.drawable.fa_link),
    Playlist(R.drawable.fa_list_ul), Lock(R.drawable.fa_lock),
    Search(R.drawable.fa_magnifying_glass), Pause(R.drawable.fa_pause),
    Edit(R.drawable.fa_pen_to_square), Play(R.drawable.fa_play),
    Connect(R.drawable.fa_plug), Server(R.drawable.fa_server),
    Parental(R.drawable.fa_shield_halved), Favorite(R.drawable.fa_star),
    Category(R.drawable.fa_tag), Delete(R.drawable.fa_trash_can),
    Television(R.drawable.fa_tv), Unlock(R.drawable.fa_unlock),
    User(R.drawable.fa_user), Audio(R.drawable.fa_volume_high),
    Close(R.drawable.fa_xmark),
}

object IconSizes {
    val Action = 24.dp
    val Transport = 28.dp
    val Status = 16.dp
}

/** Decorative by default: a labeled action already supplies its accessible name. */
@Composable fun RapoIcon(
    glyph: RapoGlyph,
    modifier: Modifier = Modifier,
    description: String? = null,
    tint: Color = androidx.tv.material3.LocalContentColor.current,
    size: Dp = IconSizes.Action,
) {
    Icon(painterResource(glyph.drawable), description, modifier.size(size), tint = tint)
}
