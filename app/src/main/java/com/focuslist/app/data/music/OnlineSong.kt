package com.focuslist.app.data.music

/**
 * 在线曲库里的一首歌。
 *
 * @property id 曲目 ID（用于拼播放外链与命名缓存文件）
 * @property name 歌名
 * @property artist 歌手
 * @property vip 是否属于会员/付费曲目（外链通常无法直接播放，界面上做弱化提示）
 */
data class OnlineSong(
    val id: Long,
    val name: String,
    val artist: String,
    val vip: Boolean = false
) {
    /** 列表展示用「歌名 - 歌手」 */
    val displayName: String
        get() = if (artist.isBlank()) name else "$name - $artist"
}
