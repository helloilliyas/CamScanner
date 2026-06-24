package com.aurorascan.di

import android.content.Context
import androidx.room.Room
import com.aurorascan.data.local.AuroraDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AuroraDatabase =
        Room.databaseBuilder(context, AuroraDatabase::class.java, AuroraDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()
}
