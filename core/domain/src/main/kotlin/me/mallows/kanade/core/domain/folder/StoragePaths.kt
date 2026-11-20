package me.mallows.kanade.core.domain.folder

/**
 * ストレージの根と、曲の絶対パスの扱い。Android 9以前 (API 28以下) は、MediaStoreの`RELATIVE_PATH`・`VOLUME_NAME`がないので、
 * 曲の絶対パス (`DATA`) から、ボリューム名と相対パス ("Music/Artist/") を作る。Android 10以降のそれと同じ形にそろえるので、
 * OSを上げても、スキャン対象のフォルダの設定とデータベースの`volume`はそのまま使える。
 */
object StoragePaths {

  /** 曲の置き場所 (根) と、その中の相対パス */
  data class Located(val root: String, val relativePath: String)

  /**
   * アプリの外部ファイル用フォルダ (`Context.getExternalFilesDirs`の結果。`/storage/emulated/0/Android/data/<パッケージ>/files`) から、
   * ストレージの根 (`/storage/emulated/0`) を取り出す。形が違えばnull。根を決め打ちしないために使う
   */
  fun rootOfFilesDir(filesDir: String): String? {
    val i = filesDir.indexOf(ANDROID_DATA)
    return if (i > 0) filesDir.substring(0, i) else null
  }

  /** 根のボリューム名。主ストレージは"external_primary"、ほかは根の最後の部分を小文字にしたもの (`/storage/1234-ABCD` → `1234-abcd`) */
  fun volumeName(root: String, primary: Boolean): String =
    if (primary) ScanFolder.PRIMARY else root.trimEnd('/').substringAfterLast('/').lowercase()

  /**
   * 曲の絶対パス ([absolutePath]。ファイル名を含む) から、根と相対パスを作る。どの根にも当たらなければnull。
   * 根の比較は英字の大小を区別しない (ストレージも区別しないため)。根が入れ子のときは、長い方を使う。
   */
  fun relativePathOf(roots: Collection<String>, absolutePath: String): Located? {
    val root = roots
      .map { it.trimEnd('/') }
      .filter { absolutePath.length > it.length && absolutePath[it.length] == '/' && absolutePath.startsWith(it, ignoreCase = true) }
      .maxByOrNull { it.length }
      ?: return null
    val inside = absolutePath.substring(root.length + 1)
    val slash = inside.lastIndexOf('/')
    return Located(root, if (slash < 0) "" else ScanFolder.dir(inside.substring(0, slash)))
  }

  /** フォルダの配下の曲の絶対パスに当たるLIKEの値 (`ESCAPE '\'`と組み合わせる)。末尾の"/"があるので"Music/"は"MusicBackup/"に当たらない */
  fun likePrefix(root: String, relativePath: String): String =
    escapeLike(root.trimEnd('/') + "/" + ScanFolder.dir(relativePath)) + "%"

  private const val ANDROID_DATA = "/Android/data/"
}
