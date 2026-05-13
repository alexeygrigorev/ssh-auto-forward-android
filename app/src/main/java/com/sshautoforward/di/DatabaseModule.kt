package com.sshautoforward.di

import android.content.Context
import androidx.room.Room
import com.sshautoforward.data.db.AppDatabase
import com.sshautoforward.data.db.dao.HostDao
import com.sshautoforward.data.db.dao.PortRemappingDao
import com.sshautoforward.data.db.dao.SshKeyDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "ssh-auto-forward.db").build()

    @Provides
    fun provideHostDao(db: AppDatabase): HostDao = db.hostDao()

    @Provides
    fun provideSshKeyDao(db: AppDatabase): SshKeyDao = db.sshKeyDao()

    @Provides
    fun providePortRemappingDao(db: AppDatabase): PortRemappingDao = db.portRemappingDao()
}
