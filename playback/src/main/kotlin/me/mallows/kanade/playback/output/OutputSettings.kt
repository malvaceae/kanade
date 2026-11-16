package me.mallows.kanade.playback.output

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import me.mallows.kanade.audio.ReplayGainMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** 出力の設定 */
@Singleton
class OutputSettings @Inject constructor(private val dataStore: DataStore<Preferences>) {

  /** USB DACへビットパーフェクトで出す (Android 14以降) */
  val bitPerfect: Flow<Boolean> = dataStore.data.map { it[BIT_PERFECT] ?: false }.distinctUntilChanged()

  suspend fun setBitPerfect(enabled: Boolean) {
    dataStore.edit { it[BIT_PERFECT] = enabled }
  }

  /** ReplayGain (曲ごとの音量の補正) の使い方 */
  val replayGain: Flow<ReplayGainMode> = dataStore.data
    .map { p -> p[REPLAY_GAIN]?.let { runCatching { ReplayGainMode.valueOf(it) }.getOrNull() } ?: ReplayGainMode.OFF }
    .distinctUntilChanged()

  suspend fun setReplayGain(mode: ReplayGainMode) {
    dataStore.edit { it[REPLAY_GAIN] = mode.name }
  }

  private companion object {
    val REPLAY_GAIN = stringPreferencesKey("replay_gain")
    val BIT_PERFECT = booleanPreferencesKey("usb_bit_perfect")
  }
}
