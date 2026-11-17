package me.mallows.kanade.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.mallows.kanade.core.domain.folder.ScanFolder
import me.mallows.kanade.core.domain.folder.normalize

/** テーマの選択 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 一覧のソート条件。[key]の意味は一覧ごとに決める */
data class SortSetting(val key: String, val descending: Boolean)

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

  /** ライブラリのタブの並びと表示 ("TRACKS,!ALBUMS,..."、!は隠す)。未設定ならnull */
  val libraryTabs: Flow<String?> = dataStore.data.map { it[LIBRARY_TABS] }

  suspend fun setLibraryTabs(value: String) {
    dataStore.edit { it[LIBRARY_TABS] = value }
  }

  /** 一覧 ([category]) ごとに覚えているソート条件。未設定ならnull */
  fun sort(category: String): Flow<SortSetting?> = dataStore.data.map { prefs ->
    prefs[sortKey(category)]?.split(':')?.takeIf { it.size == 2 }?.let { SortSetting(it[0], it[1] == "desc") }
  }

  suspend fun setSort(category: String, setting: SortSetting) {
    dataStore.edit { it[sortKey(category)] = "${setting.key}:${if (setting.descending) "desc" else "asc"}" }
  }

  /** 一覧ごとの表示形式 (リスト／グリッド) */
  fun gridView(category: String): Flow<Boolean?> = dataStore.data.map { prefs ->
    prefs[stringPreferencesKey("grid_$category")]?.toBooleanStrictOrNull()
  }

  suspend fun setGridView(category: String, grid: Boolean) {
    dataStore.edit { it[stringPreferencesKey("grid_$category")] = grid.toString() }
  }

  /**
   * スキャン対象のフォルダー。まだ保存していなければnull (初期値の「各ストレージのMusic/」を使う)。
   * 空のリストは「1つも選んでいない」で、nullとは区別する
   */
  val scanFolders: Flow<List<ScanFolder>?> = dataStore.data.map { prefs ->
    prefs[SCAN_FOLDERS]?.mapNotNull(ScanFolder::decode)?.let(::normalize)
  }

  suspend fun setScanFolders(folders: List<ScanFolder>) {
    dataStore.edit { it[SCAN_FOLDERS] = normalize(folders).map(ScanFolder::encode).toSet() }
  }

  /** 初回のスキャンを済ませたか。済ませた後は、アプリを起動しただけではスキャンしない */
  val initialScanDone: Flow<Boolean> = dataStore.data.map { it[INITIAL_SCAN_DONE] == true }

  suspend fun setInitialScanDone() {
    dataStore.edit { it[INITIAL_SCAN_DONE] = true }
  }

  private fun sortKey(category: String) = stringPreferencesKey("sort_$category")

  private companion object {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val LIBRARY_TABS = stringPreferencesKey("library_tabs")
    val SCAN_FOLDERS = stringSetPreferencesKey("scan_folders")
    val INITIAL_SCAN_DONE = booleanPreferencesKey("initial_scan_done")
  }
}
