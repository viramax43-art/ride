package com.rideminiapp.ui.web

import android.net.Uri
import android.webkit.ValueCallback
import androidx.lifecycle.ViewModel
import org.json.JSONArray
import org.json.JSONObject

class WebAppStateViewModel : ViewModel() {
    data class FileUploadPayload(
        val name: String,
        val mimeType: String,
        val base64: String,
    )

    var pendingFilePathCallback: ValueCallback<Array<Uri>>? = null
    var pendingCameraCaptureUri: Uri? = null
    var fileChooserInFlight: Boolean = false
    var fileChooserJustReturned: Boolean = false
    var webUrlBeforeFileChooser: String? = null
    var lastKnownWebUrl: String? = null
    var pendingFileUploadPayloads: List<FileUploadPayload>? = null

    fun clearFileChooserState() {
        pendingFilePathCallback?.onReceiveValue(null)
        pendingFilePathCallback = null
        pendingCameraCaptureUri = null
        fileChooserInFlight = false
        fileChooserJustReturned = false
        webUrlBeforeFileChooser = null
        lastKnownWebUrl = null
    }

    fun consumeFileUploadPayloads(): List<FileUploadPayload>? {
        val payloads = pendingFileUploadPayloads
        pendingFileUploadPayloads = null
        return payloads
    }
}
