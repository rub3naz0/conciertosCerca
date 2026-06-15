package org.rubenazo.conciertosfront

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.rubenazo.conciertosfront.core.di.commonModule
import org.rubenazo.conciertosfront.core.di.platformModule

class ConciertosFrontApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@ConciertosFrontApp)
            modules(commonModule, platformModule())
        }
    }
}
