package it.alessandro.fotoftp

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Carica una foto in FTP. Se manca la rete aspetta che torni; se l'upload fallisce
 * riprova automaticamente (fino a 5 tentativi).
 */
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val path = inputData.getString(KEY_PATH) ?: return@withContext Result.failure()
        val file = File(path)
        if (!file.exists()) {
            return@withContext Result.failure(workDataOf(KEY_ERROR to "File non trovato"))
        }
        val settings = SettingsStore(applicationContext).load()
        if (!settings.isComplete) {
            return@withContext Result.failure(workDataOf(KEY_ERROR to "Impostazioni FTP mancanti"))
        }
        try {
            FtpUploader.upload(settings, file)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 4) Result.retry()
            else Result.failure(workDataOf(KEY_ERROR to (e.message ?: e.javaClass.simpleName)))
        }
    }

    companion object {
        const val TAG = "ftp_upload"
        const val KEY_PATH = "path"
        const val KEY_ERROR = "error"

        fun enqueue(context: Context, file: File) {
            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(workDataOf(KEY_PATH to file.absolutePath))
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(TAG)
                .addTag("file:${file.name}")
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
