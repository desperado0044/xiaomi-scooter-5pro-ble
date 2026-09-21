package com.scooterre.client.viewmodel

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import com.scooterre.client.protocol.*
import com.scooterre.client.reminder.InsuranceReminders
import com.scooterre.client.reminder.InsuranceSchedule
import com.scooterre.client.ui.*
import com.scooterre.client.update.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.update

/** The scooter documents: which scooter's list is open, adding, renaming and deleting, and merging a documents file. */
internal class DocumentsController(
    private val shared: Shared,
    private val deviceRegistry: DeviceRegistry,
    private val documentStore: DocumentStore,
    private val pushScreen: (Screen) -> Unit,
    private val insuranceApplied: () -> Set<String>,
) {
    private val _state get() = shared.state
    private val prefs get() = shared.prefs
    private val s get() = shared.s
    private val app get() = shared.app
    private val scope get() = shared.scope

    fun refreshDocuments() {
        val mac = _state.value.documentsMac
        _state.update { st ->
            st.copy(
                documents = mac?.let(documentStore::list) ?: emptyList(),
                documentCounts = deviceRegistry.list().associate { it.mac to documentStore.count(it.mac) },
                // The notification's button can tick scooters off while the app is closed.
                insuranceApplied = insuranceApplied(),
            )
        }
    }

    /** Opens the documents of [mac] - or, with null, of the scooter used last (else the first one). */
    fun openDocuments(mac: String?) {
        val known = deviceRegistry.list()
        val target = mac
            ?: prefs.getString(KEY_LAST_CONNECTED, null)?.takeIf { last -> known.any { it.mac.equals(last, ignoreCase = true) } }
            ?: known.firstOrNull()?.mac
            ?: return
        _state.update { it.copy(documentsMac = target, error = null, docsMessage = null) }
        refreshDocuments()
        pushScreen(Screen.DOCUMENTS)
    }

    fun selectDocumentsDevice(mac: String) {
        _state.update { it.copy(documentsMac = mac, error = null, docsMessage = null) }
        refreshDocuments()
    }

    fun openDocument(id: String) {
        _state.update { it.copy(viewerDocId = id) }
        pushScreen(Screen.DOCUMENT_VIEWER)
    }

    /** The first photo becomes the document, the rest are appended as further pages. */
    fun addDocumentPhotos(uris: List<Uri>, name: String) = documentJob { mac ->
        val resolver = app.contentResolver
        val first = uris.firstOrNull() ?: return@documentJob
        val doc = documentStore.addImage(mac, name) { resolver.openInputStream(first) }
        uris.drop(1).forEach { u -> documentStore.appendImage(mac, doc.id) { resolver.openInputStream(u) } }
    }

    fun addDocumentFromUri(uri: Uri, name: String) = documentJob { mac ->
        val resolver = app.contentResolver
        if (resolver.getType(uri) == "application/pdf") {
            documentStore.addPdf(mac, name) { resolver.openInputStream(uri) }
        } else {
            documentStore.addImage(mac, name) { resolver.openInputStream(uri) }
        }
    }

    fun appendDocumentPhotos(docId: String, uris: List<Uri>) = documentJob { mac ->
        val resolver = app.contentResolver
        uris.forEach { u -> documentStore.appendImage(mac, docId) { resolver.openInputStream(u) } }
    }

    fun renameDocument(docId: String, name: String) = documentJob { mac -> documentStore.rename(mac, docId, name) }

    fun deleteDocument(docId: String) = documentJob { mac -> documentStore.delete(mac, docId) }

    private fun documentJob(block: (String) -> Unit) {
        val mac = _state.value.documentsMac ?: return
        scope.launch {
            try {
                withContext(Dispatchers.IO) { block(mac) }
                _state.update { it.copy(error = null) }
            } catch (e: Exception) {
                android.util.Log.e("ScooterVM", "document operation failed", e)
                _state.update { it.copy(error = s.docsImportError) }
            }
            refreshDocuments()
        }
    }

    /** A documents-only file picked in the documents screen: merged into the scooter with the same MAC. */
    fun importDocumentsBundle(uri: Uri) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { app.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                    ?.let { DocumentsBundle.import(app, it) }
            }
            when (result) {
                is DocumentsBundle.ImportResult.Ok -> {
                    refreshDocuments()
                    _state.update { it.copy(docsMessage = s.docsReceived(result.added), error = null) }
                }
                DocumentsBundle.ImportResult.UnknownScooter -> _state.update { it.copy(error = s.docsUnknownScooter, docsMessage = null) }
                else -> _state.update { it.copy(error = s.importInvalidCodeError, docsMessage = null) }
            }
        }
    }
}
