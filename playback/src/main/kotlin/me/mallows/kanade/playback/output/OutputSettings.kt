package me.mallows.kanade.playback.output

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

  private companion object {
    val BIT_PERFECT = booleanPreferencesKey("usb_bit_perfect")
  }
}
