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
  val Check = stroke("check", "M5 12.5l4.5 4.5L19 7.5")
  val Close = stroke("close", "M6 6l12 12", "M18 6L6 18")
  val Sort = stroke("sort", "M4 7h10", "M4 12h7", "M4 17h4", "M18 5v14", "M15 16l3 3 3-3")
  val Search = stroke("search", "M10.5 17a6.5 6.5 0 1 1 0-13 6.5 6.5 0 0 1 0 13z", "M20 20l-4.8-4.8")
  val Grid = stroke("grid", "M4 4h7v7H4z", "M13 4h7v7h-7z", "M4 13h7v7H4z", "M13 13h7v7h-7z")
  val ListView = stroke("list", "M8 6h12", "M8 12h12", "M8 18h12", "M4 6h.01", "M4 12h.01", "M4 18h.01")
  val More = filled("more", "M12 7.2a1.7 1.7 0 1 0 0-3.4 1.7 1.7 0 0 0 0 3.4z", "M12 13.7a1.7 1.7 0 1 0 0-3.4 1.7 1.7 0 0 0 0 3.4z", "M12 20.2a1.7 1.7 0 1 0 0-3.4 1.7 1.7 0 0 0 0 3.4z")
  val Grip = filled(
    "grip",
    "M9 8.1a1.6 1.6 0 1 0 0-3.2 1.6 1.6 0 0 0 0 3.2z", "M15 8.1a1.6 1.6 0 1 0 0-3.2 1.6 1.6 0 0 0 0 3.2z",
    "M9 13.6a1.6 1.6 0 1 0 0-3.2 1.6 1.6 0 0 0 0 3.2z", "M15 13.6a1.6 1.6 0 1 0 0-3.2 1.6 1.6 0 0 0 0 3.2z",
    "M9 19.1a1.6 1.6 0 1 0 0-3.2 1.6 1.6 0 0 0 0 3.2z", "M15 19.1a1.6 1.6 0 1 0 0-3.2 1.6 1.6 0 0 0 0 3.2z",
  )
  val Move = stroke("move", "M12 4v16", "M8 8l4-4 4 4", "M8 16l4 4 4-4")
  val Swap = stroke("swap", "M7 4L4 7l3 3", "M4 7h13", "M17 14l3 3-3 3", "M20 17H7")
  val Reverse = stroke("reverse", "M8 4v16", "M4 16l4 4 4-4", "M16 20V4", "M12 8l4-4 4 4")
  val PlayNext = stroke("play_next", "M4 6h12", "M4 11h12", "M4 16h7", "M17 14v6", "M14 17h6")
  val AddToQueue = stroke("add_queue", "M4 6h16", "M4 11h10", "M4 16h7", "M18 13v8", "M14 17h8")
  val AddToPlaylist = stroke("add_playlist", "M4 6h11", "M4 11h11", "M4 16h7", "M18 9v8", "M14 13h8")
  val Delete = stroke("delete", "M5 7h14", "M10 7V5h4v2", "M7 7l1 12h8l1-12")
  val Queue = stroke("queue", "M4 6h16", "M4 12h16", "M4 18h10")
  val Timer = stroke("timer", "M12 21a8 8 0 1 0 0-16 8 8 0 0 0 0 16z", "M12 9v4l2.5 2", "M9 2h6")
  val Info = stroke("info", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z", "M12 11v5", "M12 7.5h.01")
  val Plus = stroke("plus", "M12 5v14", "M5 12h14")
  val ToTop = stroke("to_top", "M5 4h14", "M12 20V9", "M7 13l5-5 5 5")
  val ToBottom = stroke("to_bottom", "M5 20h14", "M12 4v11", "M7 11l5 5 5-5")
  val Edit = stroke("edit", "M4 20h4L19 9l-4-4L4 16z", "M13.5 6.5l4 4")

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
