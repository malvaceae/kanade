package me.mallows.kanade.playback

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import me.mallows.kanade.audio.DspChain

@Module
@InstallIn(SingletonComponent::class)
object AudioModule {

  /** 再生サービスとイコライザー画面で共有する音声処理 */
  @Provides
  @Singleton
  fun provideDspChain(): DspChain = DspChain()
}
