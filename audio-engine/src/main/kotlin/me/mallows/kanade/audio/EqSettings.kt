package me.mallows.kanade.audio

/** イコライザーの設定。dBの値はすべて ±[EqSettings.MAX_DB] の範囲 */
data class EqSettings(
  val enabled: Boolean = false,
  /** 10バンドのゲイン (dB)。周波数は [BAND_FREQUENCIES] */
  val bands: List<Float> = List(BAND_FREQUENCIES.size) { 0f },
  /** EQの前にかける音量 (dB)。ブーストによる音割れを防ぐために下げる */
  val preampDb: Float = 0f,
  val bassDb: Float = 0f,
  val trebleDb: Float = 0f,
  /** 音割れを防ぐリミッター */
  val limiter: Boolean = true,
  /** 左右のバランス。-1で左だけ、1で右だけ。EQのオン・オフに関係なく効く */
  val balance: Float = 0f,
  /** 左右を混ぜてモノラルにする。EQのオン・オフに関係なく効く */
  val mono: Boolean = false,
) {
  init {
    require(bands.size == BAND_FREQUENCIES.size) { "バンド数が違います" }
  }

  fun withBand(index: Int, db: Float) = copy(bands = bands.toMutableList().also { it[index] = db.coerceIn(-MAX_DB, MAX_DB) })

  /** 値の部分だけをプリセットとして取り出す */
  fun toPreset(name: String) = EqPreset(name, bands, preampDb, bassDb, trebleDb)

  fun applyPreset(preset: EqPreset) = copy(
    bands = preset.bands,
    preampDb = preset.preampDb,
    bassDb = preset.bassDb,
    trebleDb = preset.trebleDb,
  )

  /** 保存用の文字列 */
  fun encode(): String =
    listOf(enabled, preampDb, bassDb, trebleDb, limiter, bands.joinToString(","), balance, mono).joinToString(";")

  companion object {
    /** 各バンドの中心周波数 (Hz) */
    val BAND_FREQUENCIES = listOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)

    /** バンドの幅 (Q)。1オクターブ間隔の10バンドに合う値 */
    const val BAND_Q = 1.41

    const val MAX_DB = 15f
    const val BASS_FREQUENCY = 100.0
    const val TREBLE_FREQUENCY = 10000.0

    /** [encode]で保存した文字列から戻す。壊れていれば既定値 */
    fun decode(text: String?): EqSettings {
      val parts = text?.split(';') ?: return EqSettings()
      return runCatching {
        EqSettings(
          enabled = parts[0].toBooleanStrict(),
          preampDb = parts[1].toFloat(),
          bassDb = parts[2].toFloat(),
          trebleDb = parts[3].toFloat(),
          limiter = parts[4].toBooleanStrict(),
          bands = parts[5].split(',').map(String::toFloat),
          // バランスとモノラルは後から足したので、古い保存データには無い
          balance = parts.getOrNull(6)?.toFloat()?.coerceIn(-1f, 1f) ?: 0f,
          mono = parts.getOrNull(7)?.toBooleanStrict() ?: false,
        )
      }.getOrDefault(EqSettings())
    }
  }
}

/** プリセット (名前つきの値の組) */
data class EqPreset(
  val name: String,
  val bands: List<Float>,
  val preampDb: Float = 0f,
  val bassDb: Float = 0f,
  val trebleDb: Float = 0f,
) {
  companion object {
    /** 組み込みのプリセット */
    val BUILT_IN = listOf(
      EqPreset("フラット", List(10) { 0f }),
      EqPreset("低音強調", listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f), preampDb = -3f),
      EqPreset("高音強調", listOf(0f, 0f, 0f, 0f, 0f, 1f, 2f, 4f, 5f, 6f), preampDb = -3f),
      EqPreset("ボーカル", listOf(-2f, -2f, -1f, 0f, 2f, 4f, 4f, 2f, 0f, -1f), preampDb = -2f),
      EqPreset("クラシック", listOf(3f, 2f, 1f, 0f, 0f, 0f, -1f, -1f, 1f, 2f), preampDb = -1f),
      EqPreset("ロック", listOf(4f, 3f, 1f, -1f, -2f, -1f, 1f, 3f, 4f, 4f), preampDb = -2f),
      EqPreset("ラウドネス", listOf(5f, 4f, 2f, 0f, -1f, -1f, 0f, 2f, 4f, 5f), preampDb = -3f),
    )
  }
}
