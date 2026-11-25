package com.soundflow.music

import android.app.Application
import androidx.room.Room
import com.soundflow.music.data.local.AppDatabase

class SoundFlowApp : Application() {

    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "soundflow.db"
        ).fallbackToDestructiveMigration().build()
    }

    companion object {
        lateinit var instance: SoundFlowApp
            private set
    }
}
