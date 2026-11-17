package me.mallows.kanade.core.domain.folder

/**
 * スキャン対象のフォルダー。volumeとrelativePathの組で、同じ名前のフォルダーが別のストレージにあっても区別する。
 * フォルダーの比較は英字の大小を区別しない (Androidのストレージも、MediaStoreへのLIKEも区別しないため)。
 */
data class ScanFolder(
  /** MediaStoreのボリューム名。内部ストレージは"external_primary"、SDカードは"1234-abcd"のような名前 */
  val volume: String,
  /** ストレージの中のフォルダー。必ず"/"で終わる ("Music/"、"Music/Artist/") */
  val relativePath: String,
) {
  init {
    require(relativePath.endsWith("/")) { "relativePathは/で終わる: $relativePath" }
  }

  /** [volume]の[path] (曲のフォルダー) がこのフォルダーの配下 (このフォルダー自身を含む) にあるか */
  fun contains(volume: String, path: String): Boolean =
    this.volume.equals(volume, ignoreCase = true) && dir(path).startsWith(relativePath, ignoreCase = true)

  /** 保存用の1つの文字列にする */
  fun encode(): String = volume + SEPARATOR + relativePath

  companion object {
    const val PRIMARY = "external_primary"
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

/**
 * ストレージの根。API 28以下では、MediaStoreの絶対パス (DATA) からボリュームと相対パスを作るのに使う。
 * @param path 根の絶対パス ("/storage/emulated/0"、"/storage/1234-ABCD")
 */
data class StorageRoot(val volume: String, val path: String) {
  companion object {
    /**
     * アプリの外部ファイル置き場 (Context.getExternalFilesDirs) から根を作る。根の場所は決め打ちしない。
     * @param appDir "…/Android/data/<パッケージ>/files"
     * @param primary 主ストレージ (内部ストレージ) なら"external_primary"にする
     */
    fun fromAppDir(appDir: String, primary: Boolean): StorageRoot? {
      val i = appDir.indexOf("/Android/data/")
      if (i <= 0) return null
      val root = appDir.substring(0, i)
      val volume = if (primary) ScanFolder.PRIMARY else root.substringAfterLast('/').lowercase()
      return StorageRoot(volume, root)
    }
  }
}

/**
 * 絶対パスのファイルが、どのストレージのどのフォルダーにあるか。どの根にも当たらなければnull。
 * 例: "/storage/1234-ABCD/Music/Artist/a.flac" → ("1234-abcd", "Music/Artist/")
 */
fun locate(absolutePath: String, roots: List<StorageRoot>): Pair<String, String>? {
  // 長い根から試す (根が別の根の中にある場合に備える)
  val root = roots.sortedByDescending { it.path.length }
    .firstOrNull { absolutePath.startsWith(it.path.trimEnd('/') + "/") } ?: return null
  val inside = absolutePath.substring(root.path.trimEnd('/').length + 1)
  val folder = inside.substringBeforeLast('/', missingDelimiterValue = "")
  return root.volume to ScanFolder.dir(folder)
}

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

/** フォルダーの選択画面の1行。曲数は配下 (子のフォルダーを含む) の合計 */
data class FolderNode(
  val volume: String,
  /** "Music/Artist/"。ストレージの最上位は"" */
  val relativePath: String,
  /** 行に出す名前 ("Artist") */
  val name: String,
  val totalTracks: Int,
  val children: List<FolderNode>,
)

/**
 * フォルダーごとの曲数 (直接入っている数) から、ストレージごとの木を作る。
 * 曲が直接入っていない途中のフォルダーも、配下に曲があれば行として出す。配下にも曲がないフォルダーは出ない。
 * @return ストレージ (volume) ごとの最上位の行
 */
fun buildFolderTree(counts: List<Triple<String, String, Int>>): Map<String, List<FolderNode>> {
  // ストレージごとに、フォルダー → 直接の曲数。途中のフォルダーも0曲で足す
  val byVolume = linkedMapOf<String, MutableMap<String, Int>>()
  for ((volume, path, count) in counts) {
    if (count <= 0) continue
    val dirs = byVolume.getOrPut(volume) { mutableMapOf() }
    val dir = ScanFolder.dir(path)
    dirs[dir] = (dirs[dir] ?: 0) + count
    var parent = parentOf(dir)
    while (parent != null) {
      dirs.putIfAbsent(parent, 0)
      parent = parentOf(parent)
    }
  }
  return byVolume.mapValues { (volume, dirs) ->
    fun node(dir: String): FolderNode {
      val children = dirs.keys.filter { it != dir && parentOf(it) == dir }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
        .map(::node)
      return FolderNode(
        volume = volume,
        relativePath = dir,
        name = dir.trimEnd('/').substringAfterLast('/'),
        totalTracks = (dirs[dir] ?: 0) + children.sumOf { it.totalTracks },
        children = children,
      )
    }
    // 最上位 ("") に直接ある曲は選べる行にしない (ストレージの見出しで扱う)。その下のフォルダーを並べる
    dirs.keys.filter { it.isNotEmpty() && parentOf(it) == "" }.sortedWith(String.CASE_INSENSITIVE_ORDER).map(::node)
  }
}

/** "Music/Artist/" → "Music/"、"Music/" → ""、"" → null */
private fun parentOf(dir: String): String? {
  if (dir.isEmpty()) return null
  val trimmed = dir.trimEnd('/')
  val i = trimmed.lastIndexOf('/')
  return if (i < 0) "" else trimmed.substring(0, i + 1)
}
