package com.etelevizor.iptv

import android.app.Application
import com.etelevizor.iptv.data.AppPreferences
import com.etelevizor.iptv.data.EpgDatabase
import com.etelevizor.iptv.data.IptvRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class ETelevizorApp : Application() {

    lateinit var prefs: AppPreferences
        private set
    lateinit var epgDb: EpgDatabase
        private set
    lateinit var httpClient: OkHttpClient
        private set
    lateinit var repository: IptvRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = AppPreferences(this)
        epgDb = EpgDatabase(this)
        httpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        repository = IptvRepository(this, prefs, epgDb, httpClient)
    }

    companion object {
        lateinit var instance: ETelevizorApp
            private set
    }
}
