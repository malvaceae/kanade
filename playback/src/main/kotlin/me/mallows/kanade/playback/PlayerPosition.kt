package me.mallows.kanade.playback

import androidx.media3.common.Player
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 再生サービスの実際のプレイヤーの再生位置 (サービスが入れ、画面が読む)。
 * MediaControllerの位置は見積もりで、再生を始めた直後の約0.1秒は、実際の位置が動かないのに時計どおりに進むため、
 * 先に進みすぎて、あとで戻る。サービスは画面と同じプロセスなので、実際の位置を直接読める。
 * 読むのは、プレイヤーを動かしているスレッド (メイン) から
 */
@Singleton
class PlayerPosition @Inject constructor() {
  @Volatile
  private var player: Player? = null

  fun attach(player: Player) {
    this.player = player
  }

  fun detach(player: Player) {
    if (this.player === player) this.player = null
  }

  /** [mediaId]の項目の、実際の再生位置。今の項目が[mediaId]でないとき (切り替わる途中) や、プレイヤーがないときはnull */
  fun read(mediaId: String?): Long? {
    val player = player ?: return null
    return positionIfCurrent(player.currentMediaItem?.mediaId, player.currentPosition, mediaId)
  }
}

/** 今の項目 ([current]) が、知りたい項目 ([wanted]) のときだけ、位置を返す */
internal fun positionIfCurrent(current: String?, position: Long, wanted: String?): Long? =
  if (current != null && current == wanted) position else null
