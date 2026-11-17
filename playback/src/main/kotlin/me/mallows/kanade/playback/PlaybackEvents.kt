package me.mallows.kanade.playback

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 再生サービスから画面へ知らせること */
enum class PlaybackEvent {
  /** ファイルが見つからなかったので、その曲を飛ばした (再スキャンする前に端末から消えた曲など) */
  FILE_NOT_FOUND,
  /** ファイルを読めなかったので、その曲を飛ばした (壊れたファイルなど) */
  UNPLAYABLE,
}

/** 再生サービスが流し、画面が受け取る */
@Singleton
class PlaybackEvents @Inject constructor() {
  private val events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 8)
  val flow: SharedFlow<PlaybackEvent> = events.asSharedFlow()

  fun emit(event: PlaybackEvent) {
    events.tryEmit(event)
  }
}
