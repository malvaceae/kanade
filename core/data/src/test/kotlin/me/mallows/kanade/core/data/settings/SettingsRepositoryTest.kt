package me.mallows.kanade.core.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {

  @get:Rule val tmp = TemporaryFolder()

  private fun TestScope.repository(): SettingsRepository {
    val file = File(tmp.newFolder(), "settings.preferences_pb")
    val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
    return SettingsRepository(dataStore)
  }

  @Test
  fun 既定のテーマはダーク() = runTest(UnconfinedTestDispatcher()) {
    assertEquals(ThemeMode.DARK, repository().themeMode.first())
  }

  @Test
  fun テーマを保存して読み出せる() = runTest(UnconfinedTestDispatcher()) {
    val repo = repository()
    repo.setThemeMode(ThemeMode.LIGHT)
    assertEquals(ThemeMode.LIGHT, repo.themeMode.first())
  }
}
