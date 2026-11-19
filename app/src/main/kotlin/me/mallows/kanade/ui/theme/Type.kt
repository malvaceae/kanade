package me.mallows.kanade.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp

/**
 * 日本語の文の折り返し。禁則 (「？」「。」を行頭に置かない) は文の言語で決まるので、端末の言語が英語でも日本語として扱う。
 * 文節で折る設定 (WordBreak.Phrase) は使わない。Android 13以降にしか効かず、版によって折り方が変わるうえ、
 * 行の右端がそろわなくなり、最後の行が短く余りやすくなる (単語の途中で折れるのは、日本語では普通)
 */
private fun TextStyle.japanese(): TextStyle = copy(
  lineBreak = LineBreak.Paragraph,
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
