package me.mallows.kanade.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import me.mallows.kanade.core.data.db.KanadeDatabase
import me.mallows.kanade.core.data.db.PlaybackStateDao
import me.mallows.kanade.core.data.db.PlaylistDao
import me.mallows.kanade.core.data.db.TrackDao

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

  @Provides
  @Singleton
  fun provideDatabase(@ApplicationContext context: Context): KanadeDatabase =
    Room.databaseBuilder(context, KanadeDatabase::class.java, "kanade.db").build()

  @Provides
  fun provideTrackDao(db: KanadeDatabase): TrackDao = db.trackDao()

  @Provides
  fun providePlaybackStateDao(db: KanadeDatabase): PlaybackStateDao = db.playbackStateDao()

  @Provides
  fun providePlaylistDao(db: KanadeDatabase): PlaylistDao = db.playlistDao()

  @Provides
  fun provideEqPresetDao(db: KanadeDatabase): me.mallows.kanade.core.data.db.EqPresetDao = db.eqPresetDao()

  @Provides
  fun providePlayStatsDao(db: KanadeDatabase): me.mallows.kanade.core.data.db.PlayStatsDao = db.playStatsDao()

  @Provides
  @Singleton
  fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
    PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
}
