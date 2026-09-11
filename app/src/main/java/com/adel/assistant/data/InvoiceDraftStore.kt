package com.adel.assistant.data

object InvoiceDraftStore {
    @Volatile
    var selectedProjects: List<ProjectEntry> = emptyList()
}
