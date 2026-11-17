package me.mallows.kanade.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp

/**
 * 日本語の文を文節で折り返す (「削除します / か？」のように単語の途中で行が分かれないようにする)。
 * 文節の区切りは文の言語で決まるので、端末の言語が英語でも日本語として扱う。英字の折り返しは変わらない
 */
private fun TextStyle.japanese(): TextStyle = copy(
  lineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase),
  localeList = LocaleList("ja"),
)

private val Base = Typography(
  bodyLarge = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.5.sp,
  ),
)

val Typography = Base.copy(
  displayLarge = Base.displayLarge.japanese(),
  displayMedium = Base.displayMedium.japanese(),
  displaySmall = Base.displaySmall.japanese(),
  headlineLarge = Base.headlineLarge.japanese(),
  headlineMedium = Base.headlineMedium.japanese(),
  headlineSmall = Base.headlineSmall.japanese(),
  titleLarge = Base.titleLarge.japanese(),
  titleMedium = Base.titleMedium.japanese(),
  titleSmall = Base.titleSmall.japanese(),
  bodyLarge = Base.bodyLarge.japanese(),
  bodyMedium = Base.bodyMedium.japanese(),
  bodySmall = Base.bodySmall.japanese(),
  labelLarge = Base.labelLarge.japanese(),
  labelMedium = Base.labelMedium.japanese(),
  labelSmall = Base.labelSmall.japanese(),
)
