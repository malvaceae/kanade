package me.mallows.kanade.playback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import me.mallows.kanade.audio.EqPreset
import me.mallows.kanade.audio.EqSettings
import me.mallows.kanade.core.data.db.EqPresetDao
import me.mallows.kanade.core.data.db.EqPresetEntity

/** ユーザーが保存したプリセット */
data class UserPreset(val id: Long, val preset: EqPreset)

/** イコライザーの設定 (DataStore) とユーザーのプリセット (Room) */
@Singleton
class EqRepository @Inject constructor(
  private val dataStore: DataStore<Preferences>,
  private val presetDao: EqPresetDao,
) {

  val settings: Flow<EqSettings> = dataStore.data
    .map { EqSettings.decode(it[KEY]) }
    .distinctUntilChanged()

  suspend fun save(settings: EqSettings) {
    dataStore.edit { it[KEY] = settings.encode() }
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

  suspend fun deletePreset(id: Long) = presetDao.delete(id)

  private companion object {
    val KEY = stringPreferencesKey("eq_settings")
  }
}
