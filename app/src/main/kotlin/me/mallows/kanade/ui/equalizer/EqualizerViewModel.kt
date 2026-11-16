package me.mallows.kanade.ui.equalizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.mallows.kanade.audio.DspChain
import me.mallows.kanade.audio.EqPreset
import me.mallows.kanade.audio.EqSettings
import me.mallows.kanade.playback.EqRepository
import me.mallows.kanade.playback.UserPreset
import me.mallows.kanade.playback.output.BitPerfectState
import me.mallows.kanade.playback.output.OutputDevice
import me.mallows.kanade.playback.output.OutputMonitor

/**
 * イコライザーの編集。変更はすぐに音声処理へ反映し、保存は0.3秒待ってまとめて行う
 * (スライダーを動かしている間に何度も書き込まないため)。
 */
@HiltViewModel
class EqualizerViewModel @Inject constructor(
  private val repository: EqRepository,
  private val dsp: DspChain,
  output: OutputMonitor,
) : ViewModel() {

  /** ビットパーフェクトで出力中 (EQは通らない) */
  val bitPerfectActive: StateFlow<Boolean> = output.info
    .map { it.bitPerfect == BitPerfectState.ACTIVE }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

  /** 今の出力先 */
  val device: StateFlow<OutputDevice?> = output.info
    .map { it.device }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  /** 出力先ごとに設定を分けるか */
  val perDevice: StateFlow<Boolean> =
    repository.perDevice.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

  /** 編集中の設定の保存先 (出力先が変わっても、変更前の出力先に保存するため) */
  private var target: String? = null

  private val _settings = MutableStateFlow(dsp.settings)
  val settings: StateFlow<EqSettings> = _settings.asStateFlow()

  val userPresets: StateFlow<List<UserPreset>> =
    repository.userPresets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  private var saveJob: Job? = null

  init {
    // 出力先が変わって設定が切り替わったら、画面にも反映する (保存待ちの編集があるときは除く)
    viewModelScope.launch {
      repository.target.combine(repository.settings) { t, s -> t to s }.collect { (t, s) ->
        if (saveJob?.isActive == true) return@collect
        target = t
        _settings.value = s
      }
    }
  }

  private fun edit(transform: (EqSettings) -> EqSettings) {
    val next = transform(_settings.value)
    _settings.value = next
    dsp.update(next)
    saveJob?.cancel()
    val to = target
    saveJob = viewModelScope.launch {
      delay(300)
      repository.save(next, to)
    }
  }

  fun setPerDevice(enabled: Boolean) {
    viewModelScope.launch {
      // 分けるときは今の設定をこの出力先の設定として引き継ぐ
      saveJob?.join()
      repository.setPerDevice(enabled)
      if (enabled) repository.save(_settings.value)
    }
  }

  fun setEnabled(enabled: Boolean) = edit { it.copy(enabled = enabled) }
  fun setBand(index: Int, db: Float) = edit { it.withBand(index, db) }
  fun setPreamp(db: Float) = edit { it.copy(preampDb = db) }
  fun setBass(db: Float) = edit { it.copy(bassDb = db) }
  fun setTreble(db: Float) = edit { it.copy(trebleDb = db) }
  fun setLimiter(on: Boolean) = edit { it.copy(limiter = on) }

  /** プリセットを選ぶと、EQがオフでもオンにする */
  fun applyPreset(preset: EqPreset) = edit { it.applyPreset(preset).copy(enabled = true) }

  fun reset() = edit { it.applyPreset(EqPreset.BUILT_IN.first()) }

  fun savePreset(name: String) {
    val preset = _settings.value.toPreset(name.trim())
    viewModelScope.launch { repository.savePreset(preset) }
  }

  fun deletePreset(id: Long) {
    viewModelScope.launch { repository.deletePreset(id) }
  }

  override fun onCleared() {
    // 画面を閉じたときは待たずに保存する
    val pending = _settings.value
    val to = target
    if (saveJob?.isActive == true) {
      kotlinx.coroutines.MainScope().launch { repository.save(pending, to) }
    }
  }
}

/** 今の値と一致するプリセットの名前。どれとも違えばnull (カスタム) */
fun EqSettings.matchingPresetName(userPresets: List<UserPreset>): String? {
  val presets = EqPreset.BUILT_IN + userPresets.map { it.preset }
  return presets.firstOrNull {
    it.bands == bands && it.preampDb == preampDb && it.bassDb == bassDb && it.trebleDb == trebleDb
  }?.name
}
