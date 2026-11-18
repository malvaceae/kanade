package me.mallows.kanade.core.data.backup

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.mallows.kanade.core.data.db.EqPresetDao
import me.mallows.kanade.core.data.db.EqPresetEntity
import me.mallows.kanade.core.data.db.PlaylistDao
import me.mallows.kanade.core.data.db.TrackDao
import me.mallows.kanade.core.data.playlist.LibraryFiles
import me.mallows.kanade.core.data.playlist.PlaylistRepository
import me.mallows.kanade.core.domain.m3u.PathMatcher
import org.json.JSONArray
import org.json.JSONObject

/** 復元の結果 */
data class RestoreResult(val settings: Int, val presets: Int, val playlists: Int, val missingTracks: Int)

/**
 * 設定・EQの自作プリセット・プレイリストを1つのJSONファイルに書き出し、読み戻す。
 * プレイリストの曲はファイルのパスで持つため、別の端末や入れ直した後でも照合できる。
 * 復元では同じ名前のプリセット・プレイリストは飛ばす (何度復元しても増えない)。
 */
@Singleton
class BackupManager @Inject constructor(
  @param:ApplicationContext private val context: Context,
  private val dataStore: DataStore<Preferences>,
  private val presetDao: EqPresetDao,
  private val playlistDao: PlaylistDao,
  private val playlists: PlaylistRepository,
  private val trackDao: TrackDao,
  private val files: LibraryFiles,
) {

  suspend fun export(uri: Uri): Unit = withContext(Dispatchers.IO) {
    val root = JSONObject().put("app", "kanade").put("version", VERSION)

    val settings = JSONObject()
    for ((key, value) in dataStore.data.first().asMap()) {
      val (type, json) = when (value) {
        is Boolean -> "boolean" to value
        is Int -> "int" to value
        is Long -> "long" to value
        is Float -> "float" to value.toDouble()
        is Double -> "double" to value
        is String -> "string" to value
        is Set<*> -> "stringSet" to JSONArray(value.map { it.toString() })
        else -> continue
      }
      settings.put(key.name, JSONObject().put("type", type).put("value", json))
    }
    root.put("settings", settings)

    root.put(
      "eqPresets",
      JSONArray(
        presetDao.observeAll().first().map {
          JSONObject()
            .put("name", it.name)
            .put("bands", it.bands)
            .put("preampDb", it.preampDb.toDouble())
            .put("bassDb", it.bassDb.toDouble())
            .put("trebleDb", it.trebleDb.toDouble())
        },
      ),
    )

    val lists = JSONArray()
    for (summary in playlistDao.observePlaylists().first()) {
      val items = playlistDao.observeItems(summary.id).first().sortedBy { it.position }
      val paths = files.list(items.map { it.track.mediaStoreId }.distinct()).toMap()
      val tracks = JSONArray(items.mapNotNull { paths[it.track.mediaStoreId] })
      lists.put(JSONObject().put("name", summary.name).put("tracks", tracks))
    }
    root.put("playlists", lists)

    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(root.toString(2).toByteArray()) }
      ?: error("ファイルに書けない: $uri")
  }

  suspend fun restore(uri: Uri): RestoreResult = withContext(Dispatchers.IO) {
    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
      ?: error("ファイルを開けない: $uri")
    val root = JSONObject(text)
    require(root.optString("app") == "kanade") { "Kanadeのバックアップではない" }

    // 設定: 書かれている項目だけを上書きする
    val settings = root.optJSONObject("settings") ?: JSONObject()
    var settingCount = 0
    dataStore.edit { prefs ->
      for (name in settings.keys()) {
        val item = settings.getJSONObject(name)
        when (item.optString("type")) {
          "boolean" -> prefs[booleanPreferencesKey(name)] = item.getBoolean("value")
          "int" -> prefs[intPreferencesKey(name)] = item.getInt("value")
          "long" -> prefs[longPreferencesKey(name)] = item.getLong("value")
          "float" -> prefs[floatPreferencesKey(name)] = item.getDouble("value").toFloat()
          "double" -> prefs[doublePreferencesKey(name)] = item.getDouble("value")
          "string" -> prefs[stringPreferencesKey(name)] = item.getString("value")
          "stringSet" -> {
            val array = item.getJSONArray("value")
            prefs[stringSetPreferencesKey(name)] = (0 until array.length()).map(array::getString).toSet()
          }
          else -> continue
        }
        settingCount++
      }
    }

    // EQの自作プリセット
    val existingPresets = presetDao.observeAll().first().map { it.name }.toSet()
    val presets = root.optJSONArray("eqPresets") ?: JSONArray()
    var presetCount = 0
    for (i in 0 until presets.length()) {
      val p = presets.getJSONObject(i)
      val name = p.getString("name")
      if (name in existingPresets) continue
      presetDao.insert(
        EqPresetEntity(
          name = name,
          bands = p.getString("bands"),
          preampDb = p.optDouble("preampDb", 0.0).toFloat(),
          bassDb = p.optDouble("bassDb", 0.0).toFloat(),
          trebleDb = p.optDouble("trebleDb", 0.0).toFloat(),
        ),
      )
      presetCount++
    }

    // プレイリスト: パスで曲を照合する
    val existingLists = playlistDao.observePlaylists().first().map { it.name }.toMutableSet()
    val lists = root.optJSONArray("playlists") ?: JSONArray()
    val matcher = PathMatcher(files.list())
    var listCount = 0
    var missing = 0
    for (i in 0 until lists.length()) {
      val l = lists.getJSONObject(i)
      val name = l.getString("name")
      if (!existingLists.add(name)) continue
      val paths = l.optJSONArray("tracks") ?: JSONArray()
      val mediaIds = (0 until paths.length()).map { paths.getString(it) }.map { matcher.match(it) }
      missing += mediaIds.count { it == null }
      val tracks = trackDao.getByMediaStoreIds(mediaIds.filterNotNull().distinct()).associateBy { it.mediaStoreId }
      val id = playlists.create(name)
      playlists.add(id, mediaIds.mapNotNull { m -> m?.let { tracks[it]?.id } })
      listCount++
    }
    RestoreResult(settingCount, presetCount, listCount, missing)
  }

  private companion object {
    const val VERSION = 1
  }
}
