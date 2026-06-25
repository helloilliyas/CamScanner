package com.aurorascan.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aurorascan.data.repository.DocumentRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Persistent OCR job (blueprint 17.4): survives app restarts and runs ML Kit
 * recognition for every page of a document that still needs it.
 */
@HiltWorker
class OcrWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: DocumentRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val documentId = inputData.getString(KEY_DOCUMENT_ID) ?: return Result.failure()
        val languages = inputData.getStringArray(KEY_LANGUAGES)?.toSet() ?: setOf("en")
        return try {
            repository.runOcrForDocument(documentId, languages)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_DOCUMENT_ID = "document_id"
        const val KEY_LANGUAGES = "languages"
        private const val MAX_ATTEMPTS = 3
    }
}
