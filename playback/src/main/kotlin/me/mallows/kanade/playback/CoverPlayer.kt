package me.mallows.kanade.playback

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import java.io.File

/**
 * 再生中の曲がプレイリストから再生したもの ([EXTRA_COVER_PLAYLIST]) で、そのプレイリストにカバー画像があれば、
 * 曲のアートワークの代わりにその画像を出す。再生サービスのセッションに渡すので、
 * アプリのプレイヤー画面・通知・ロック画面のどれにも同じ画像が出る。
 * カバーは [setCovers] で最新のものを受け取り、変わったらすぐ知らせ直す (再生中に変えても反映される)
 */
@OptIn(UnstableApi::class)
class CoverPlayer(player: Player) : ForwardingPlayer(player) {
  /** プレイリストのID → カバー画像のファイル */
  private var covers: Map<Long, String> = emptyMap()
  private var cachedPath: String? = null
  private var cachedBytes: ByteArray? = null

  /** 今の再生中の曲のカバー画像のファイル。なければnull */
  private fun currentCover(): String? {
    val extras = currentMediaItem?.mediaMetadata?.extras ?: return null
    if (!extras.containsKey(EXTRA_COVER_PLAYLIST)) return null
    return covers[extras.getLong(EXTRA_COVER_PLAYLIST)]
  }

  override fun getMediaMetadata(): MediaMetadata {
    val base = super.getMediaMetadata()
    val path = currentCover() ?: return base
    val bytes = bytes(path) ?: return base
    return base.buildUpon()
      .setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
      .setArtworkUri(null)
      .build()
  }

  /** プレイリストのカバーが変わったら呼ぶ。今の曲の絵が変わるなら、メタデータが変わったと知らせ直す */
  fun setCovers(newCovers: Map<Long, String>) {
    val before = currentCover()
    covers = newCovers
    if (currentCover() != before) {
      val metadata = mediaMetadata
      wrapped.keys.toList().forEach { it.onMediaMetadataChanged(metadata) }
    }
  }

  // セッションは、メタデータが変わった知らせに付いてくる値をそのまま使う。
  // その値もカバーに置き換えたものにするため、登録された受け手を包んで渡す
  private val wrapped = LinkedHashMap<Player.Listener, Player.Listener>()

  override fun addListener(listener: Player.Listener) {
    // ほかの知らせはそのまま渡す (インターフェースの既定の実装を持つ知らせも含め、すべて元の受け手へ)
    val wrapper = java.lang.reflect.Proxy.newProxyInstance(
      Player.Listener::class.java.classLoader,
      arrayOf(Player.Listener::class.java),
    ) { _, method, args ->
      if (method.name == "onMediaMetadataChanged") {
        listener.onMediaMetadataChanged(mediaMetadata)
        null
      } else {
        method.invoke(listener, *(args ?: emptyArray()))
      }
    } as Player.Listener
    wrapped[listener] = wrapper
    super.addListener(wrapper)
  }

  override fun removeListener(listener: Player.Listener) {
    wrapped.remove(listener)?.let { super.removeListener(it) }
  }

  /** 同じ画像を曲ごとに読み直さないよう、最後に読んだ1枚を覚えておく (アプリ内の小さなJPEG) */
  private fun bytes(path: String): ByteArray? {
    if (path != cachedPath) {
      cachedPath = path
      cachedBytes = runCatching { File(path).readBytes() }.getOrNull()
    }
    return cachedBytes
  }
}
