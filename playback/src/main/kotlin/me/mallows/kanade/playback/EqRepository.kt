package me.mallows.kanade.playback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.mallows.kanade.audio.EqPreset
import me.mallows.kanade.audio.EqSettings
import me.mallows.kanade.core.data.db.EqPresetDao
import me.mallows.kanade.core.data.db.EqPresetEntity
import me.mallows.kanade.playback.output.DeviceKind
import me.mallows.kanade.playback.output.OutputDevice
import me.mallows.kanade.playback.output.OutputMonitor

/** ユーザーが保存したプリセット */
data class UserPreset(val id: Long, val preset: EqPreset)

/**
 * イコライザーの設定 (DataStore) とユーザーのプリセット (Room)。
 * 「出力先ごとに分ける」がオンなら、設定を出力先 (種類と製品名) ごとに持ち、出力先が変わると切り替わる。
 */
@Singleton
class EqRepository @Inject constructor(
  private val dataStore: DataStore<Preferences>,
  private val presetDao: EqPresetDao,
  monitor: OutputMonitor,
) {

  /** 出力先ごとに設定を分けるか */
  val perDevice: Flow<Boolean> = dataStore.data.map { it[PER_DEVICE] ?: false }.distinctUntilChanged()

  /** 今の設定の保存先。出力先ごとに分けないときや、出力先が分からないときはnull (共通の設定) */
  val target: Flow<String?> = combine(
    perDevice,
    monitor.info.map { it.device?.let(::deviceKey) }.distinctUntilChanged(),
  ) { on, key -> if (on) key else null }.distinctUntilChanged()

  val settings: Flow<EqSettings> = combine(dataStore.data, target) { prefs, key ->
    // その出力先の設定がまだ無ければ、共通の設定から始める
    EqSettings.decode(key?.let { prefs[deviceSettingsKey(it)] } ?: prefs[KEY])
  }.distinctUntilChanged()

  /** 今の保存先に保存する */
  suspend fun save(settings: EqSettings) = save(settings, target.first())

  /** [target] (nullなら共通の設定) に保存する */
  suspend fun save(settings: EqSettings, target: String?) {
    dataStore.edit { it[target?.let(::deviceSettingsKey) ?: KEY] = settings.encode() }
  }

  suspend fun setPerDevice(enabled: Boolean) {
    dataStore.edit { it[PER_DEVICE] = enabled }
  }

  val userPresets: Flow<List<UserPreset>> = presetDao.observeAll().map { list ->
    list.map { e ->
      UserPreset(
        id = e.id,
        preset = EqPreset(
          name = e.name,
          bands = e.bands.split(',').mapNotNull(String::toFloatOrNull).takeIf { it.size == 10 } ?: List(10) { 0f },
          preampDb = e.preampDb,
          bassDb = e.bassDb,
          trebleDb = e.trebleDb,
        ),
      )
    }
  }

  suspend fun savePreset(preset: EqPreset): Long = presetDao.insert(
    EqPresetEntity(
      name = preset.name,
      bands = preset.bands.joinToString(","),
      preampDb = preset.preampDb,
      bassDb = preset.bassDb,
      trebleDb = preset.trebleDb,
    )
  )

  suspend fun renamePreset(id: Long, name: String) = presetDao.rename(id, name)

  /** プリセットを削除する。元に戻すための内容を返す (なければnull) */
  suspend fun deletePreset(id: Long): EqPresetEntity? {
    val removed = presetDao.get(id) ?: return null
    presetDao.delete(id)
    return removed
  }

  /** [deletePreset]で削除したプリセットを戻す */
  suspend fun restorePreset(preset: EqPresetEntity) = presetDao.restore(preset)

  companion object {
    private val KEY = stringPreferencesKey("eq_settings")
    private val PER_DEVICE = booleanPreferencesKey("eq_per_device")

    private fun deviceSettingsKey(device: String) = stringPreferencesKey("eq_settings@$device")

    /** 出力先を区別するキー。内蔵スピーカーは1つにまとめ、それ以外は製品名で分ける */
    fun deviceKey(device: OutputDevice): String =
      if (device.kind == DeviceKind.SPEAKER) "SPEAKER" else "${device.kind.name}:${device.name}"
  }
}
