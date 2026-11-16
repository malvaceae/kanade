package me.mallows.kanade.core.domain.reorder

/**
 * 位置だけで扱うリスト (再生キューなど) の並べ替え。
 * どの関数も「新しい並びの各位置に、元のどの位置の項目が来るか」を返す。
 */
object IndexReorder {

  /** [selected]の位置の項目をまとめて、それ以外の項目の中の[slot]番目に移す (選んだ項目どうしの順番は保つ) */
  fun moveBlock(size: Int, selected: Set<Int>, slot: Int): List<Int> {
    val moving = (0 until size).filter { it in selected }
    val others = (0 until size).filter { it !in selected }
    val s = slot.coerceIn(0, others.size)
    return others.subList(0, s) + moving + others.subList(s, others.size)
  }

  fun moveToTop(size: Int, selected: Set<Int>): List<Int> = moveBlock(size, selected, 0)

  fun moveToBottom(size: Int, selected: Set<Int>): List<Int> = moveBlock(size, selected, size)

  /** 並びが変わらないか */
  fun isIdentity(order: List<Int>): Boolean = order.withIndex().all { (i, v) -> i == v }
}

/** 並び順の値を持つ項目 (プレイリストの曲など) */
data class Positioned(val id: Long, val position: Double)

/**
 * 並べ替えの結果。
 * @property order 並べ替えた後のIDの並び
 * @property changes 値が変わった項目の新しい値
 * @property previous 値が変わった項目の元の値 (元に戻す用)
 */
data class ReorderResult(
  val order: List<Long>,
  val changes: Map<Long, Double>,
  val previous: Map<Long, Double>,
) {
  val isEmpty: Boolean get() = changes.isEmpty()

  companion object {
    fun unchanged(items: List<Positioned>) = ReorderResult(items.map { it.id }, emptyMap(), emptyMap())
  }
}

/**
 * 並び順の値 (小数) を使った並べ替えの計算。
 * 移動では、動かした項目の値だけを前後の項目の間の値に書き換えるので、大きなリストでも更新が少なくて済む。
 * 間隔が詰まりすぎたときだけ全体を 1, 2, 3… に振り直す。
 *
 * どの関数も、[items]は並び順の値の小さい順に並んでいる前提。
 */
object PositionedListEditor {

  /** これより間隔が狭くなる場合は全体を振り直す */
  const val MIN_GAP = 1e-9

  /**
   * [ids]の項目をまとめて、それ以外の項目の中の[slot]番目の位置に移す。
   * 移す項目どうしの順番は元の並びのまま保つ。
   * @param slot 0なら先頭、「それ以外の項目の数」なら末尾
   */
  fun moveBlock(items: List<Positioned>, ids: Set<Long>, slot: Int): ReorderResult {
    val moving = items.filter { it.id in ids }
    if (moving.isEmpty()) return ReorderResult.unchanged(items)
    val others = items.filter { it.id !in ids }
    val s = slot.coerceIn(0, others.size)
    val newOrder = others.subList(0, s) + moving + others.subList(s, others.size)
    if (newOrder.map { it.id } == items.map { it.id }) return ReorderResult.unchanged(items)

    val k = moving.size
    val lo = if (s > 0) others[s - 1].position else (others.firstOrNull()?.position ?: 0.0) - (k + 1)
    val hi = if (s < others.size) others[s].position else (others.lastOrNull()?.position ?: 0.0) + (k + 1)
    val step = (hi - lo) / (k + 1)
    if (step < MIN_GAP) return renumber(items, newOrder.map { it.id })

    val changes = LinkedHashMap<Long, Double>()
    moving.forEachIndexed { i, item -> changes[item.id] = lo + step * (i + 1) }
    return ReorderResult(
      order = newOrder.map { it.id },
      changes = changes,
      previous = moving.associate { it.id to it.position },
    )
  }

  /** 先頭へ */
  fun moveToTop(items: List<Positioned>, ids: Set<Long>): ReorderResult = moveBlock(items, ids, 0)

  /** 末尾へ */
  fun moveToBottom(items: List<Positioned>, ids: Set<Long>): ReorderResult =
    moveBlock(items, ids, items.count { it.id !in ids })

  /**
   * 1始まりの[number]番目から並ぶように移す。
   * 範囲外の番号は先頭または末尾に丸める。
   */
  fun moveToNumber(items: List<Positioned>, ids: Set<Long>, number: Int): ReorderResult =
    moveBlock(items, ids, number - 1)

  /** 2つの項目を入れ替える */
  fun swap(items: List<Positioned>, a: Long, b: Long): ReorderResult {
    val pa = items.firstOrNull { it.id == a } ?: return ReorderResult.unchanged(items)
    val pb = items.firstOrNull { it.id == b } ?: return ReorderResult.unchanged(items)
    if (a == b) return ReorderResult.unchanged(items)
    val order = items.map {
      when (it.id) {
        a -> b
        b -> a
        else -> it.id
      }
    }
    return ReorderResult(
      order = order,
      changes = mapOf(a to pb.position, b to pa.position),
      previous = mapOf(a to pa.position, b to pb.position),
    )
  }

  /** [ids]の項目だけを、今いる位置の中で逆順にする */
  fun reverse(items: List<Positioned>, ids: Set<Long>): ReorderResult {
    val selected = items.filter { it.id in ids }
    if (selected.size < 2) return ReorderResult.unchanged(items)
    val positions = selected.map { it.position }
    val reversed = selected.reversed()
    val changes = LinkedHashMap<Long, Double>()
    reversed.forEachIndexed { i, item -> if (item.position != positions[i]) changes[item.id] = positions[i] }
    val iterator = reversed.iterator()
    val order = items.map { if (it.id in ids) iterator.next().id else it.id }
    return ReorderResult(order, changes, selected.filter { it.id in changes }.associate { it.id to it.position })
  }

  /** [order]の並びで確定し、全体を 1, 2, 3… に振り直す (ソート結果の保存など) */
  fun renumber(items: List<Positioned>, order: List<Long>): ReorderResult {
    val byId = items.associateBy { it.id }
    require(order.size == items.size && order.toSet() == byId.keys) { "並びに含まれる項目が一致しません" }
    val changes = LinkedHashMap<Long, Double>()
    val previous = LinkedHashMap<Long, Double>()
    order.forEachIndexed { i, id ->
      val newPos = (i + 1).toDouble()
      val old = byId.getValue(id).position
      if (old != newPos) {
        changes[id] = newPos
        previous[id] = old
      }
    }
    return ReorderResult(order, changes, previous)
  }

  /** [changes]を適用した並びを返す (画面の先行反映やテスト用) */
  fun apply(items: List<Positioned>, changes: Map<Long, Double>): List<Positioned> =
    items.map { item -> changes[item.id]?.let { item.copy(position = it) } ?: item }
      .sortedBy { it.position }
}
