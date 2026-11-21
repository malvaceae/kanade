package me.mallows.kanade.playback

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 今の動画の、最初の映像が出たか (再生サービスが更新し、画面が読む)。
 * 映像の面 (SurfaceView) は、動画が切り替わっても、新しい動画の最初のコマが出るまで、前の動画の最後のコマを残す。
 * 画面は、最初のコマが出るまで映像をカバーで覆って、前の動画が見えないようにする (MediaのPlayerViewのシャッターと同じ)
 */
@Singleton
class VideoFrameMonitor @Inject constructor() {
  private val state = MutableStateFlow<String?>(null)

  /** 最初の映像が出た項目のmediaId。項目や映像のトラックが変わると、nullに戻る */
  val renderedMediaId: StateFlow<String?> = state.asStateFlow()

  fun set(mediaId: String?) {
    state.value = mediaId
  }
}
