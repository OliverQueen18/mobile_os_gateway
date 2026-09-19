package com.osgateway.distributor

import android.app.Application
import com.osgateway.distributor.data.FcmTokenStore
import com.osgateway.distributor.data.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DistributorApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.get(this)
        appScope.launch { FcmTokenStore.registerForNotifications(this@DistributorApplication) }
    }
}
