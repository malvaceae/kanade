package me.mallows.kanade.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** アプリで使うアイコン。24×24の線画 (塗りのものは[filled]) */
object KanadeIcons {

  val MusicNote = stroke("music_note", "M9 18V5l11-2v13", "M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0z", "M20 16a3 3 0 1 1-6 0 3 3 0 0 1 6 0z")
  val Play = filled("play", "M8 5.5v13l10.5-6.5z")
  val Pause = filled("pause", "M7 5h3.5v14H7z", "M13.5 5H17v14h-3.5z")
  val Next = filled("next", "M5 5.5v13L15 12z", "M16.6 5h2.4v14h-2.4z")
  val Previous = filled("previous", "M19 5.5v13L9 12z", "M5 5h2.4v14H5z")
  val Shuffle = stroke(
    "shuffle",
    "M3 7h3c4.5 0 6.5 10 11 10h3", "M17 14l3 3-3 3", "M3 17h3c1.6 0 2.8-1.3 3.8-3",
    "M13.6 9.6C14.6 8 15.7 7 17 7h3", "M17 4l3 3-3 3",
  )
  val Repeat = stroke("repeat", "M17 3l3 3-3 3", "M20 6H8a4 4 0 0 0-4 4v1", "M7 21l-3-3 3-3", "M4 18h12a4 4 0 0 0 4-4v-1")
  val RepeatOne = stroke(
    "repeat_one",
    "M17 3l3 3-3 3", "M20 6H8a4 4 0 0 0-4 4v1", "M7 21l-3-3 3-3", "M4 18h12a4 4 0 0 0 4-4v-1", "M11 10.5l1.5-1v5",
  )
  val ChevronDown = stroke("chevron_down", "M6 9l6 6 6-6")
  val Back = stroke("back", "M15 5l-7 7 7 7")
  val Playing = filled("playing", "M4 9h3.5v11H4z", "M10.25 4h3.5v16h-3.5z", "M16.5 12H20v8h-3.5z")

  private fun stroke(name: String, vararg paths: String) = build(name, paths, filled = false)
  private fun filled(name: String, vararg paths: String) = build(name, paths, filled = true)

  private fun build(name: String, paths: Array<out String>, filled: Boolean): ImageVector {
    val builder = ImageVector.Builder(
      name = name,
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    )
    for (d in paths) {
      val nodes = PathParser().parsePathString(d).toNodes()
      if (filled) {
        builder.addPath(pathData = nodes, fill = SolidColor(Color.Black))
      } else {
        builder.addPath(
          pathData = nodes,
          stroke = SolidColor(Color.Black),
          strokeLineWidth = 2f,
          strokeLineCap = StrokeCap.Round,
          strokeLineJoin = StrokeJoin.Round,
        )
      }
    }
    return builder.build()
  }
}
