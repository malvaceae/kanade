package me.mallows.kanade.core.domain.reorder

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionedListEditorTest {

  /** id = 1..n、並び順の値 = 1.0..n */
  private fun list(n: Int) = (1..n).map { Positioned(it.toLong(), it.toDouble()) }

  private fun ids(vararg v: Long) = v.toSet()

  /** 結果の値を適用した並びと、結果の order が一致することを確かめる */
  private fun assertConsistent(items: List<Positioned>, result: ReorderResult) {
    val applied = PositionedListEditor.apply(items, result.changes)
    assertEquals(result.order, applied.map { it.id })
    assertEquals(items.map { it.id }.toSet(), applied.map { it.id }.toSet())
    // 並び順の値に重複がない
    assertEquals(applied.size, applied.map { it.position }.toSet().size)
    // 元に戻すと元の並びになる
    val undone = PositionedListEditor.apply(applied, result.previous)
    assertEquals(items.map { it.id }, undone.map { it.id })
  }

  @Test
  fun 一曲を後ろへ移動() {
    val items = list(5)
    val r = PositionedListEditor.moveBlock(items, ids(2), 3)
    assertEquals(listOf(1L, 3, 4, 2, 5), r.order)
    assertEquals(setOf(2L), r.changes.keys)
    assertConsistent(items, r)
  }

  @Test
  fun 離れた複数曲をまとめて移動すると元の順番を保つ() {
    val items = list(8)
    val r = PositionedListEditor.moveBlock(items, ids(7, 2, 5), 1)
    assertEquals(listOf(1L, 2, 5, 7, 3, 4, 6, 8), r.order)
    assertEquals(setOf(2L, 5L, 7L), r.changes.keys)
    assertConsistent(items, r)
  }

  @Test
  fun 先頭へと末尾へ() {
    val items = list(6)
    val top = PositionedListEditor.moveToTop(items, ids(4, 6))
    assertEquals(listOf(4L, 6, 1, 2, 3, 5), top.order)
    assertConsistent(items, top)
    val bottom = PositionedListEditor.moveToBottom(items, ids(1, 3))
    assertEquals(listOf(2L, 4, 5, 6, 1, 3), bottom.order)
    assertConsistent(items, bottom)
  }

  @Test
  fun 番号を指定して移動し範囲外は丸める() {
    val items = list(5)
    val r = PositionedListEditor.moveToNumber(items, ids(5), 2)
    assertEquals(listOf(1L, 5, 2, 3, 4), r.order)
    assertConsistent(items, r)
    val over = PositionedListEditor.moveToNumber(items, ids(1), 99)
    assertEquals(listOf(2L, 3, 4, 5, 1), over.order)
    val under = PositionedListEditor.moveToNumber(items, ids(5), -3)
    assertEquals(listOf(5L, 1, 2, 3, 4), under.order)
  }

  @Test
  fun 並びが変わらない移動は変更なし() {
    val items = list(5)
    assertTrue(PositionedListEditor.moveBlock(items, ids(2, 3), 1).isEmpty)
    assertTrue(PositionedListEditor.moveToTop(items, ids(1)).isEmpty)
    assertTrue(PositionedListEditor.moveBlock(items, emptySet(), 0).isEmpty)
  }

  @Test
  fun 間隔が詰まったら全体を振り直す() {
    val items = listOf(Positioned(1, 1.0), Positioned(2, 1.0 + 1e-10), Positioned(3, 2.0))
    val r = PositionedListEditor.moveBlock(items, ids(3), 1)
    assertEquals(listOf(1L, 3, 2), r.order)
    val applied = PositionedListEditor.apply(items, r.changes)
    assertEquals(listOf(1.0, 2.0, 3.0), applied.map { it.position })
    assertConsistent(items, r)
  }

  @Test
  fun 入れ替え() {
    val items = list(5)
    val r = PositionedListEditor.swap(items, 2, 4)
    assertEquals(listOf(1L, 4, 3, 2, 5), r.order)
    assertEquals(setOf(2L, 4L), r.changes.keys)
    assertConsistent(items, r)
  }

  @Test
  fun 選んだ曲だけ逆順() {
    val items = list(6)
    val r = PositionedListEditor.reverse(items, ids(2, 3, 5))
    assertEquals(listOf(1L, 5, 3, 4, 2, 6), r.order)
    // 真ん中の3は位置が変わらない
    assertEquals(setOf(2L, 5L), r.changes.keys)
    assertConsistent(items, r)
  }

  @Test
  fun 指定した並びで振り直す() {
    val items = list(4)
    val r = PositionedListEditor.renumber(items, listOf(4L, 3, 2, 1))
    assertEquals(listOf(4L, 3, 2, 1), r.order)
    assertConsistent(items, r)
  }

  @Test
  fun ランダムな操作でも矛盾しない() {
    val random = Random(42)
    repeat(500) {
      var items = (1..random.nextInt(1, 30)).map { Positioned(it.toLong(), it.toDouble() * random.nextDouble(0.5, 2.0)) }
        .sortedBy { it.position }
        .let { list -> list.mapIndexed { i, p -> p.copy(position = list[i].position + i) } }
      repeat(20) {
        val selected = items.filter { random.nextInt(4) == 0 }.map { it.id }.toSet()
        val result = when (random.nextInt(4)) {
          0 -> PositionedListEditor.moveBlock(items, selected, random.nextInt(-2, items.size + 3))
          1 -> PositionedListEditor.reverse(items, selected)
          2 -> if (items.size >= 2) PositionedListEditor.swap(items, items.random(random).id, items.random(random).id) else ReorderResult.unchanged(items)
          else -> PositionedListEditor.moveToBottom(items, selected)
        }
        assertConsistent(items, result)
        items = PositionedListEditor.apply(items, result.changes)
      }
    }
  }
}
