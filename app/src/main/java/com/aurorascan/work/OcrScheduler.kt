package com.aurorascan.work

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OcrScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun schedule(documentId: String, languageHints: Set<String> = setOf("en")) {
        val request = OneTimeWorkRequestBuilder<OcrWorker>()
            .setInputData(
                Data.Builder()
                    .putString(OcrWorker.KEY_DOCUMENT_ID, documentId)
                    .putStringArray(OcrWorker.KEY_LANGUAGES, languageHints.toTypedArray())
                    .build(),
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "ocr-$documentId",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
