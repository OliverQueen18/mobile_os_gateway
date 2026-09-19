package com.osgateway.gateway.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.osgateway.gateway.data.JournalRepository
import com.osgateway.gateway.data.ServiceLocator
import com.osgateway.gateway.worker.HeartbeatWorker
import com.osgateway.gateway.worker.TaskPollingWorker

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val store = ServiceLocator.get(context).tokenStore
        if (store.getAccessToken().isNullOrBlank()) return
        JournalRepository.append("BOOT_COMPLETED — redémarrage workers")
        HeartbeatWorker.enqueue(context)
        TaskPollingWorker.enqueue(context)
        GatewayForegroundService.start(context)
    }
}
