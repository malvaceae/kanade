package me.mallows.kanade.ui.equalizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.pow
import kotlin.math.abs
import kotlin.math.roundToInt
import me.mallows.kanade.R
import me.mallows.kanade.audio.ReplayGainMode
import me.mallows.kanade.ui.player.deviceLabel
import me.mallows.kanade.audio.DspChain
import me.mallows.kanade.audio.EqPreset
import me.mallows.kanade.audio.EqSettings
import me.mallows.kanade.ui.icons.KanadeIcons
import me.mallows.kanade.ui.playlist.PlaylistNameDialog
import me.mallows.kanade.playback.UserPreset

/** イコライザー */
@Composable
fun EqualizerScreen(onBack: () -> Unit, viewModel: EqualizerViewModel = hiltViewModel()) {
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val userPresets by viewModel.userPresets.collectAsStateWithLifecycle()
  var presetMenu by remember { mutableStateOf(false) }
  var saving by remember { mutableStateOf(false) }
  var moreMenu by remember { mutableStateOf(false) }
  var renaming by remember { mutableStateOf<UserPreset?>(null) }
  var deleting by remember { mutableStateOf<UserPreset?>(null) }
  val scheme = MaterialTheme.colorScheme
  val bitPerfectActive by viewModel.bitPerfectActive.collectAsStateWithLifecycle()
  val device by viewModel.device.collectAsStateWithLifecycle()
  val perDevice by viewModel.perDevice.collectAsStateWithLifecycle()
  val replayGain by viewModel.replayGain.collectAsStateWithLifecycle()
  var deviceMenu by remember { mutableStateOf(false) }
  val presetName = settings.matchingPresetName(userPresets) ?: stringResource(R.string.eq_custom)

  Column(
    Modifier
      .fillMaxSize()
      .statusBarsPadding()
      .navigationBarsPadding()
      .verticalScroll(rememberScrollState()),
  ) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) { Icon(KanadeIcons.Back, contentDescription = stringResource(R.string.action_back)) }
      Text(
        stringResource(R.string.eq_title),
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.weight(1f),
      )
      Switch(checked = settings.enabled, onCheckedChange = viewModel::setEnabled, modifier = Modifier.padding(end = 12.dp))
    }
    if (bitPerfectActive) {
      Text(
        stringResource(R.string.eq_bit_perfect_note),
        style = MaterialTheme.typography.bodySmall,
        color = scheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
      )
    }

    // プリセット
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.weight(1f)) {
        OutlinedButton(onClick = { presetMenu = true }) { Text(presetName) }
        DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
          EqPreset.BUILT_IN.forEach { p ->
            DropdownMenuItem(text = { Text(p.name) }, onClick = { presetMenu = false; viewModel.applyPreset(p) })
          }
          if (userPresets.isNotEmpty()) HorizontalDivider()
          userPresets.forEach { u ->
            DropdownMenuItem(text = { Text(u.preset.name) }, onClick = { presetMenu = false; viewModel.applyPreset(u.preset) })
          }
        }
      }
      // 今の値がどれかのプリセットと同じなら保存は要らない。すでにフラットならリセットも要らない
      val matched = settings.matchingPresetName(userPresets)
      TextButton(onClick = { saving = true }, enabled = matched == null) { Text(stringResource(R.string.eq_save_preset)) }
      TextButton(onClick = viewModel::reset, enabled = matched != EqPreset.BUILT_IN.first().name) { Text(stringResource(R.string.eq_reset)) }
      // 自分で保存したプリセットの名前の変更・削除。消す前に、そのプリセットを選んで確かめられるようにする
      val selectedUser = settings.matchingUserPreset(userPresets)
      Box {
        IconButton(onClick = { moreMenu = true }) { Icon(KanadeIcons.More, contentDescription = stringResource(R.string.action_more)) }
        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
          DropdownMenuItem(
            text = { Text(stringResource(R.string.playlist_rename)) },
            enabled = selectedUser != null,
            onClick = { moreMenu = false; renaming = selectedUser },
          )
          DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete)) },
            enabled = selectedUser != null,
            onClick = { moreMenu = false; deleting = selectedUser },
          )
        }
      }
    }

    // どの出力先の設定を編集しているか (EQがオフでも切り替えられる)
    Box(Modifier.padding(start = 16.dp, top = 4.dp)) {
      val current = device
      val label = when {
        !perDevice -> stringResource(R.string.eq_device_shared)
        current == null -> stringResource(R.string.eq_device_unknown)
        else -> stringResource(R.string.eq_device_current, deviceLabel(current))
      }
      OutlinedButton(onClick = { deviceMenu = true }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Icon(KanadeIcons.ChevronDown, contentDescription = null, modifier = Modifier.padding(start = 4.dp).size(18.dp))
      }
      DropdownMenu(expanded = deviceMenu, onDismissRequest = { deviceMenu = false }) {
        listOf(false to R.string.eq_device_shared_option, true to R.string.eq_device_split_option).forEach { (split, text) ->
          DropdownMenuItem(
            text = { Text(stringResource(text)) },
            onClick = { deviceMenu = false; viewModel.setPerDevice(split) },
            trailingIcon = { if (perDevice == split) Icon(KanadeIcons.Check, contentDescription = null) },
          )
        }
      }
    }

    val dim = Modifier.alpha(if (settings.enabled) 1f else 0.45f)

    // 周波数特性
    ResponseCurve(settings, Modifier.fillMaxWidth().height(140.dp).padding(horizontal = 16.dp, vertical = 8.dp).then(dim))

    // 10バンド
    BandSliders(
      settings = settings,
      onChange = viewModel::setBand,
      modifier = Modifier.fillMaxWidth().height(240.dp).padding(horizontal = 8.dp).then(dim),
    )

    // トーン (EQのオン・オフに従う)
    SectionTitle(stringResource(R.string.eq_section_tone), null)
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).then(dim), horizontalArrangement = Arrangement.SpaceEvenly) {
      DbKnob(stringResource(R.string.eq_preamp), "dB", settings.preampDb, -EqSettings.MAX_DB, 6f, viewModel::setPreamp)
      DbKnob(stringResource(R.string.eq_bass), "100Hz", settings.bassDb, -12f, 12f, viewModel::setBass)
      DbKnob(stringResource(R.string.eq_treble), "10kHz", settings.trebleDb, -12f, 12f, viewModel::setTreble)
    }
    SwitchRow(stringResource(R.string.eq_limiter), stringResource(R.string.eq_limiter_desc), settings.limiter, viewModel::setLimiter, dim)

    // 音量と左右 (EQがオフでも効く)
    // 上のスイッチの行は下に余白を持つので、そのぶん詰めて「トーン」の上と同じ間隔にする
    SectionTitle(stringResource(R.string.eq_section_volume), stringResource(R.string.eq_section_volume_note), top = 4.dp)
    Row(Modifier.padding(horizontal = 16.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(stringResource(R.string.eq_balance), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(88.dp))
      Slider(
        value = settings.balance,
        // 中央付近では中央に吸い付ける
        onValueChange = { v -> viewModel.setBalance(((v * 20).roundToInt() / 20f).let { if (abs(it) < 0.05f) 0f else it }) },
        valueRange = -1f..1f,
        modifier = Modifier.weight(1f),
      )
      Text(
        balanceText(settings.balance),
        style = MaterialTheme.typography.bodySmall,
        color = scheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        modifier = Modifier.width(56.dp),
      )
    }
    SwitchRow(stringResource(R.string.eq_mono), null, settings.mono, viewModel::setMono, Modifier)
    // 3択のボタンは横幅が要るので、名前の下に並べる
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)) {
      Text(stringResource(R.string.replay_gain), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
      SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        ReplayGainMode.entries.forEachIndexed { i, m ->
          SegmentedButton(
            selected = replayGain == m,
            onClick = { viewModel.setReplayGain(m) },
            shape = SegmentedButtonDefaults.itemShape(i, ReplayGainMode.entries.size),
            icon = {},
          ) { Text(replayGainLabel(m), maxLines = 1) }
        }
      }
    }
  }

  renaming?.let { u ->
    PlaylistNameDialog(
      title = stringResource(R.string.playlist_rename),
      initial = u.preset.name,
      onConfirm = { name -> renaming = null; viewModel.renamePreset(u.id, name) },
      onDismiss = { renaming = null },
    )
  }
  deleting?.let { u ->
    AlertDialog(
      onDismissRequest = { deleting = null },
      title = { Text(stringResource(R.string.playlist_delete_confirm_title, u.preset.name)) },
      confirmButton = {
        TextButton(onClick = { deleting = null; viewModel.deletePreset(u.id) }) { Text(stringResource(R.string.action_delete)) }
      },
      dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
    )
  }

  if (saving) {
    PlaylistNameDialog(
      title = stringResource(R.string.eq_save_preset),
      initial = "",
      onConfirm = { name -> saving = false; viewModel.savePreset(name) },
      onDismiss = { saving = false },
    )
  }
}

/** 全体の周波数特性のカーブ (20Hz ~ 20kHz、±15dB) */
@Composable
private fun ResponseCurve(settings: EqSettings, modifier: Modifier) {
  val accent = MaterialTheme.colorScheme.primary
  val grid = MaterialTheme.colorScheme.outlineVariant
  val points = remember(settings) {
    (0..120).map { i ->
      val freq = 20.0 * 1000.0.pow(i / 120.0)
      DspChain.responseDb(settings, freq).toFloat()
    }
  }
  val description = stringResource(R.string.eq_curve_description)
  Canvas(modifier.semantics { contentDescription = description }) {
    val maxDb = EqSettings.MAX_DB
    fun y(db: Float) = size.height / 2 - (db.coerceIn(-maxDb, maxDb) / maxDb) * (size.height / 2)
    listOf(-12f, -6f, 0f, 6f, 12f).forEach { db ->
      drawLine(grid, Offset(0f, y(db)), Offset(size.width, y(db)), strokeWidth = if (db == 0f) 2f else 1f)
    }
    val path = Path()
    points.forEachIndexed { i, db ->
      val x = size.width * i / (points.size - 1)
      if (i == 0) path.moveTo(x, y(db)) else path.lineTo(x, y(db))
    }
    drawPath(path, accent, style = Stroke(width = 3.dp.toPx()))
  }
}

/** 10本の縦スライダー。つまみを掴んで動かし、そのまま横に動かすと、通ったバンドを続けて設定できる */
@Composable
private fun BandSliders(settings: EqSettings, onChange: (Int, Float) -> Unit, modifier: Modifier) {
  val scheme = MaterialTheme.colorScheme
  val labels = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
  val current by rememberUpdatedState(settings)
  val bandsDescription = stringResource(R.string.eq_bands_description)
  Column(modifier) {
    Row(Modifier.fillMaxWidth()) {
      settings.bands.forEach { db ->
        Text(
          formatDb(db),
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
          color = scheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.weight(1f),
        )
      }
    }
    Canvas(
      Modifier
        .fillMaxWidth()
        .weight(1f)
        .padding(vertical = 8.dp)
        .pointerInput(Unit) {
          val max = EqSettings.MAX_DB
          fun bandAt(x: Float) = ((x / size.width) * 10).toInt().coerceIn(0, 9)
          fun dbAt(y: Float): Float {
            val v = (max - (y / size.height) * 2 * max).coerceIn(-max, max)
            return (v * 2).roundToInt() / 2f // 0.5dB刻み
          }
          fun yOf(db: Float) = size.height / 2 - (db / max) * (size.height / 2)
          val grabRange = 24.dp.toPx()
          awaitEachGesture {
            val down = awaitFirstDown()
            // つまみの近くを触ったときだけ動かす。ほかの場所は画面のスクロールに回す
            val grab = down.position.y - yOf(current.bands[bandAt(down.position.x)])
            if (kotlin.math.abs(grab) > grabRange) return@awaitEachGesture
            down.consume()
            while (true) {
              val event = awaitPointerEvent()
              val change = event.changes.firstOrNull { it.id == down.id } ?: break
              if (!change.pressed) break
              // 掴んだ位置のずれを保ったまま動かす (触っただけでは値が飛ばない)
              val band = bandAt(change.position.x)
              val db = dbAt(change.position.y - grab)
              if (current.bands[band] != db) onChange(band, db)
              change.consume()
            }
          }
        }
        .semantics { contentDescription = bandsDescription },
    ) {
      val colW = size.width / 10
      val max = EqSettings.MAX_DB
      fun y(db: Float) = size.height / 2 - (db / max) * (size.height / 2)
      drawLine(scheme.outlineVariant, Offset(0f, y(0f)), Offset(size.width, y(0f)), strokeWidth = 1f)
      settings.bands.forEachIndexed { i, db ->
        val cx = colW * i + colW / 2
        val track = 4.dp.toPx()
        drawRoundRect(
          scheme.surfaceContainerHighest,
          topLeft = Offset(cx - track / 2, 0f),
          size = Size(track, size.height),
          cornerRadius = CornerRadius(track / 2),
        )
        val top = minOf(y(db), y(0f))
        drawRoundRect(
          scheme.primary,
          topLeft = Offset(cx - track / 2, top),
          size = Size(track, kotlin.math.abs(y(db) - y(0f))),
          cornerRadius = CornerRadius(track / 2),
        )
        drawCircle(scheme.primary, radius = 9.dp.toPx(), center = Offset(cx, y(db)))
      }
    }
    Row(Modifier.fillMaxWidth()) {
      labels.forEach { l ->
        Text(
          l,
          style = MaterialTheme.typography.labelSmall,
          color = scheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

/** 見出し。[note]は見出しの横に小さく出す補足 */
@Composable
private fun SectionTitle(title: String, note: String?, top: Dp = 20.dp) {
  Row(Modifier.padding(start = 16.dp, end = 16.dp, top = top, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
    Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
    if (note != null) {
      Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
    }
  }
}

/** 名前と短い説明、スイッチの1行 */
@Composable
private fun SwitchRow(title: String, desc: String?, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier) {
  Row(
    modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, onValueChange = onChange, role = Role.Switch).padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(title, style = MaterialTheme.typography.bodyLarge)
    if (desc != null) {
      Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp))
    }
    Spacer(Modifier.weight(1f))
    Switch(checked = checked, onCheckedChange = null)
  }
}

@Composable
private fun replayGainLabel(mode: ReplayGainMode): String = stringResource(
  when (mode) {
    ReplayGainMode.OFF -> R.string.replay_gain_off
    ReplayGainMode.TRACK -> R.string.replay_gain_track
    ReplayGainMode.ALBUM -> R.string.replay_gain_album
  },
)

@Composable
private fun balanceText(balance: Float): String {
  val percent = (abs(balance) * 100).roundToInt()
  return when {
    percent == 0 -> stringResource(R.string.eq_balance_center)
    balance < 0 -> stringResource(R.string.eq_balance_left, percent)
    else -> stringResource(R.string.eq_balance_right, percent)
  }
}

internal fun formatDb(db: Float): String = when {
  db == 0f -> "0dB"
  db % 1f == 0f -> "%+ddB".format(db.toInt())
  else -> "%+.1fdB".format(db)
}
