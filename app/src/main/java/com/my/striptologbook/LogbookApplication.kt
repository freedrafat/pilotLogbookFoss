package com.my.striptologbook

import android.app.Application
import com.my.striptologbook.data.AppDatabase
import com.my.striptologbook.repository.LegRepository

class LogbookApplication : Application() {
    lateinit var repository: LegRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getInstance(this)
        repository = LegRepository(
            legDao = database.legDao(),
            openingBalanceDao = database.openingBalanceDao()
        )
    }
}