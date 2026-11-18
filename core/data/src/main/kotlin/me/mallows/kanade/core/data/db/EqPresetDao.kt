package me.mallows.kanade.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** ユーザーが保存したイコライザーのプリセット。[bands]は「1.5,0,-2,…」の形 */
@Entity(tableName = "eq_preset")
data class EqPresetEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val bands: String,
  val preampDb: Float,
  val bassDb: Float,
  val trebleDb: Float,
)

@Dao
interface EqPresetDao {
  @Query("SELECT * FROM eq_preset ORDER BY name COLLATE NOCASE")
  fun observeAll(): Flow<List<EqPresetEntity>>

  @Insert
  suspend fun insert(preset: EqPresetEntity): Long

  @Query("UPDATE eq_preset SET name = :name WHERE id = :id")
  suspend fun rename(id: Long, name: String)

  @Query("DELETE FROM eq_preset WHERE id = :id")
  suspend fun delete(id: Long)
}
