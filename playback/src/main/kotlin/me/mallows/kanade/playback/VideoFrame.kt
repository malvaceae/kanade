package me.mallows.kanade.playback

import androidx.media3.common.VideoSize
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 最初の映像が出た動画の[mediaId]と、その映像の縦横比 (横 / 縦)。まだなら[mediaId]はnull */
data class VideoFrame(val mediaId: String?, val aspect: Float)

/**
 * 今の動画の、最初の映像が出たか、とその縦横比 (再生サービスが更新し、画面が読む)。
 * 映像の面 (SurfaceView) は、動画が切り替わっても、新しい動画の最初のコマが出るまで、前の動画の最後のコマを残す。
 * また、新しい動画の大きさが分かるまで、枠の縦横比も前のままになり、分かった瞬間に枠が跳ねる。
 * 画面は、最初のコマが出るまで映像を覆い、枠の縦横比も、そのコマの縦横比が分かってから変える
 */
@Singleton
class VideoFrameMonitor @Inject constructor() {
  private val state = MutableStateFlow(VideoFrame(null, 0f))
  val frame: StateFlow<VideoFrame> = state.asStateFlow()

  fun set(mediaId: String?, aspect: Float = 0f) {
    state.value = VideoFrame(mediaId, aspect)
  }
}

/** 映像の縦横比 (横 / 縦、画素の縦横比を含む)。大きさが分からなければ0 */
fun VideoSize.aspect(): Float = if (width > 0 && height > 0) width * pixelWidthHeightRatio / height else 0f
