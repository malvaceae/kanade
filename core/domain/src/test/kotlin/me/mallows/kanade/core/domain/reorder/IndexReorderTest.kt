package me.mallows.kanade.core.domain.reorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IndexReorderTest {

  @Test
  fun 離れた項目をまとめて移す() {
    assertEquals(listOf(0, 1, 5, 2, 3, 4), IndexReorder.moveBlock(6, setOf(5), 2))
    assertEquals(listOf(1, 4, 0, 2, 3, 5), IndexReorder.moveBlock(6, setOf(1, 4), 0))
  }

  @Test
  fun 先頭へと末尾へ() {
    assertEquals(listOf(3, 0, 1, 2), IndexReorder.moveToTop(4, setOf(3)))
    assertEquals(listOf(1, 3, 0, 2), IndexReorder.moveToBottom(4, setOf(0, 2)))
  }

  @Test
  fun 変わらない並び() {
    assertTrue(IndexReorder.isIdentity(IndexReorder.moveBlock(4, setOf(1, 2), 1)))
  }
}
