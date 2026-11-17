package me.mallows.kanade.core.domain.folder

/**
 * スキャン対象のフォルダー。volumeとrelativePathの組で、同じ名前のフォルダーが別のストレージにあっても区別する。
 * フォルダーの比較は英字の大小を区別しない (Androidのストレージも、MediaStoreへのLIKEも区別しないため)。
 */
data class ScanFolder(
  /** MediaStoreのボリューム名。内部ストレージは"external_primary"、SDカードは"1234-abcd"のような名前 */
  val volume: String,
  /** ストレージの中のフォルダー。"/"で終わる ("Music/"、"Music/Artist/")。空ならストレージ全体 */
  val relativePath: String,
) {
  init {
    require(relativePath.isEmpty() || relativePath.endsWith("/")) { "relativePathは空 (ストレージ全体) か、/で終わる: $relativePath" }
  }

  /** [volume]の[path] (曲のフォルダー) がこのフォルダーの配下 (このフォルダー自身を含む) にあるか */
  fun contains(volume: String, path: String): Boolean =
    this.volume.equals(volume, ignoreCase = true) && dir(path).startsWith(relativePath, ignoreCase = true)

  /** 保存用の1つの文字列にする */
  fun encode(): String = volume + SEPARATOR + relativePath

  companion object {
    const val PRIMARY = "external_primary"
    /** 端末のストレージを標準のフォルダー選択の画面に出す提供元 */
    const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    private const val SEPARATOR = '\u0000'

    fun decode(text: String): ScanFolder? {
      val i = text.indexOf(SEPARATOR)
      if (i <= 0) return null
      val path = dir(text.substring(i + 1))
      return ScanFolder(text.substring(0, i), path)
    }

    /** "Music"・"/Music/"・"Music/"をどれも"Music/"にそろえる。空ならストレージの最上位 ("") */
    fun dir(path: String): String {
      val trimmed = path.trim('/')
      return if (trimmed.isEmpty()) "" else "$trimmed/"
    }

    /**
     * 標準のフォルダー選択の画面 (SAF) で選んだフォルダーの文書ID (DocumentsContract.getTreeDocumentId) と提供元から作る。
     * 外部ストレージの文書IDは「primary:Music/Artist」(内部ストレージ) や「1234-ABCD:Music」(SDカード) の形。
     * 外部ストレージ以外の提供元 (「ダウンロード」の一覧、クラウドなど) で選んだときはnull。
     * @param authority 選んだフォルダーのURIの提供元
     */
    fun fromTree(authority: String?, documentId: String): ScanFolder? {
      if (authority != EXTERNAL_STORAGE_AUTHORITY) return null
      val i = documentId.indexOf(':')
      if (i <= 0) return null
      val root = documentId.substring(0, i)
      val volume = if (root.equals("primary", ignoreCase = true)) PRIMARY else root.lowercase()
      return ScanFolder(volume, dir(documentId.substring(i + 1)))
    }

    /** 初期値: 各ストレージの「Music/」 */
    fun defaults(volumes: Collection<String>): List<ScanFolder> = volumes.map { ScanFolder(it, "Music/") }
  }
}

/** 親と子を両方選んでいたら、子は親に含まれるので除く。重なりも除く */
fun normalize(folders: Collection<ScanFolder>): List<ScanFolder> {
  val sorted = folders
    .distinctBy { it.volume.lowercase() to it.relativePath.lowercase() }
    .sortedWith(compareBy({ it.volume.lowercase() }, { it.relativePath.length }, { it.relativePath.lowercase() }))
  val result = mutableListOf<ScanFolder>()
  for (f in sorted) {
    if (result.none { it.contains(f.volume, f.relativePath) }) result += f
  }
  return result
}

/** SQLのLIKEで文字どおりに扱えるよう、\と%と_の前に\を付ける (ESCAPE '\'と組み合わせる) */
fun escapeLike(text: String): String = buildString {
  for (c in text) {
    if (c == '\\' || c == '%' || c == '_') append('\\')
    append(c)
  }
}

/** フォルダーの配下に当たるLIKEの値。末尾の"/"があるので"Music/"は"MusicBackup/"に当たらない */
fun likePrefix(relativePath: String): String = escapeLike(ScanFolder.dir(relativePath)) + "%"

/** スキャンの結果、DBの曲をどう扱うか */
data class SyncPlan(
  /** 端末にはあるが、対象フォルダーの外になった曲 (消さずに印を付ける) */
  val excluded: Set<Long>,
  /** 端末から消えた曲 (DBから消す) */
  val deleted: Set<Long>,
)

/**
 * DBにある曲・対象フォルダーの曲・端末にある曲のIDから、扱いを決める。対象フォルダーの曲は更新する (印を外す)。
 * @param stored DBにある曲
 * @param included 対象フォルダーの曲
 * @param existing 端末にある全曲
 */
fun classify(stored: Set<Long>, included: Set<Long>, existing: Set<Long>): SyncPlan {
  val outside = stored - included
  return SyncPlan(excluded = outside intersect existing, deleted = outside - existing)
}
