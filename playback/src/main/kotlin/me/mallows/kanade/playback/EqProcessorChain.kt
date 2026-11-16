package me.mallows.kanade.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * Media3標準の処理 (無音スキップ・再生速度の変更) の後ろにイコライザーを置く。
 * 標準の処理は16bitしか受け付けないため、floatを出すイコライザーは最後にする必要がある。
 */
@OptIn(UnstableApi::class)
class EqProcessorChain(private val eq: AudioProcessor) : DefaultAudioSink.AudioProcessorChain {

  private val base = DefaultAudioSink.DefaultAudioProcessorChain()

  override fun getAudioProcessors(): Array<AudioProcessor> = base.audioProcessors + eq

  override fun applyPlaybackParameters(playbackParameters: PlaybackParameters): PlaybackParameters =
    base.applyPlaybackParameters(playbackParameters)

  override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean =
    base.applySkipSilenceEnabled(skipSilenceEnabled)

  override fun getMediaDuration(playoutDuration: Long): Long = base.getMediaDuration(playoutDuration)

  override fun getSkippedOutputFrameCount(): Long = base.skippedOutputFrameCount
}
