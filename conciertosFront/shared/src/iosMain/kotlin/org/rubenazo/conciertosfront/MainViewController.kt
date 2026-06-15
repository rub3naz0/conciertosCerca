package org.rubenazo.conciertosfront

import androidx.compose.ui.window.ComposeUIViewController
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform
import org.rubenazo.conciertosfront.core.di.commonModule
import org.rubenazo.conciertosfront.core.di.platformModule
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    if (KoinPlatform.getKoinOrNull() == null) {
        startKoin {
            modules(commonModule, platformModule())
        }
    }
    return ComposeUIViewController { App() }
}
