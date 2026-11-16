package me.mallows.kanade.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** テーマの選択 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** アプリの設定。DataStoreに保存する */
@Singleton
class SettingsRepository @Inject constructor(
  private val dataStore: DataStore<Preferences>,
) {

  val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
    prefs[THEME_MODE]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.DARK
  }

  suspend fun setThemeMode(mode: ThemeMode) {
    dataStore.edit { it[THEME_MODE] = mode.name }
  }

  private companion object {
    val THEME_MODE = stringPreferencesKey("theme_mode")
  }
}
