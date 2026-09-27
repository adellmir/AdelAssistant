package com.adel.assistant.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.adel.assistant.data.TaskStore

/** Internal widget actions that mutate app data. This receiver is not exported. */
class AdelWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AdelWidgetProvider.ACTION_COMPLETE_TASK) return

        val store = intent.getStringExtra(AdelWidgetProvider.EXTRA_STORE) ?: return
        if (store != "project_tasks" && store != "tunnel_tasks") return
        val title = intent.getStringExtra(AdelWidgetProvider.EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: return
        val created = intent.getLongExtra(AdelWidgetProvider.EXTRA_CREATED, 0L)
        if (created <= 0L) return

        val all = TaskStore.load(context, store)
        val index = all.indexOfFirst { it.title == title && it.createdAt == created }
        if (index < 0) return
        all[index] = all[index].copy(completed = true)
        TaskStore.save(context, store, all)
        AdelWidgetProvider.refreshAll(context)
    }
}
