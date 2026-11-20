package me.mallows.kanade.core.data.library

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import me.mallows.kanade.core.domain.folder.StoragePaths

/**
 * 端末のストレージ (内部ストレージ・SDカード) の根のパスとボリューム名。
 * Android 10以降のMediaStoreは自分でボリューム名を教えてくれるが、Android 9以前にはないので、ここで作る。
 * 根は`/storage/emulated/0`と決め打ちせず、アプリの外部ファイル用フォルダ (各ストレージに1つずつある) から求める。
 */
@Singleton
class StorageVolumes @Inject constructor(@param:ApplicationContext private val context: Context) {

  /** [name]はMediaStoreのボリューム名 (Android 10以降の`VOLUME_NAME`と同じ形)、[root]は根の絶対パス */
  data class Volume(val name: String, val root: String, val primary: Boolean)

  /** つながっているストレージ。先頭が主ストレージ (内部ストレージ) */
  fun list(): List<Volume> {
    val dirs = context.getExternalFilesDirs(null)
    return dirs.mapIndexedNotNull { i, dir ->
      val root = dir?.absolutePath?.let(StoragePaths::rootOfFilesDir) ?: return@mapIndexedNotNull null
      Volume(StoragePaths.volumeName(root, primary = i == 0), root, primary = i == 0)
    }
  }
}
