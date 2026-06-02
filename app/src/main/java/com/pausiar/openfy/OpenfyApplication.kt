package com.pausiar.openfy

import android.app.Application

class OpenfyApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer(this) }
}