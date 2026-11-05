package com.replaybox.app

import android.app.Application
import com.replaybox.provider.tf1.Tf1Auth
import com.replaybox.provider.tf1.Tf1CredentialStore

class ReplayboxApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        private lateinit var instance: ReplayboxApplication

        /** TF1 sign-in session: token cache + encrypted credential store. */
        val tf1Auth: Tf1Auth by lazy { Tf1Auth(Tf1CredentialStore(instance)) }
    }
}
