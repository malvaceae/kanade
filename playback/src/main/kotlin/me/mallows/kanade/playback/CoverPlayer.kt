package me.mallows.kanade.playback

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import java.io.File

/**
 * 再生中の曲に、プレイリストのカバー画像の印 ([EXTRA_COVER]) があれば、曲のアートワークの代わりにその画像を出す。
 * 再生サービスのセッションに渡すので、アプリのプレイヤー画面・通知・ロック画面のどれにも同じ画像が出る
 */
@OptIn(UnstableApi::class)
class CoverPlayer(player: Player) : ForwardingPlayer(player) {
  private var cachedPath: String? = null
  private var cachedBytes: ByteArray? = null

  override fun getMediaMetadata(): MediaMetadata {
    val base = super.getMediaMetadata()
    val path = currentMediaItem?.mediaMetadata?.extras?.getString(EXTRA_COVER) ?: return base
    val bytes = bytes(path) ?: return base
    return base.buildUpon()
      .setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
      .setArtworkUri(null)
      .build()
  }

  // セッションは、メタデータが変わった知らせに付いてくる値をそのまま使う。
  // その値もカバーに置き換えたものにするため、登録された受け手を包んで渡す
  private val wrapped = HashMap<Player.Listener, Player.Listener>()

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
