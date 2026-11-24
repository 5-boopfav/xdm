package com.olaph.xdm.youtube

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * محرك استخراج الوسائط باستخدام yt-dlp
 */
object YtDlpEngine {

    private const val TAG = "YtDlpEngine"

    @Volatile
    private var initialized = false

    /**
     * تهيئة المحرك مرة واحدة.
     */
    fun initialize(context: Context): Result<Unit> {
        return try {
            if (!initialized) {
                synchronized(this) {
                    if (!initialized) {
                        YoutubeDL.getInstance().init(
                            context.applicationContext
                        )
                        initialized = true
                    }
                }
            }

            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Engine initialization failed", e)
            Result.failure(e)
        }
    }

    /**
     * استخراج معلومات الفيديو والصيغ.
     */
    suspend fun getInfo(url: String): Result<VideoInfo> =
        withContext(Dispatchers.IO) {

            if (!initialized) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "YtDlpEngine is not initialized"
                    )
                )
            }

            if (url.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException("URL is empty")
                )
            }

            try {
                val request = YoutubeDLRequest(url).apply {
                    addOption("--no-warnings")
                    addOption("--no-playlist")
                }

                val info = YoutubeDL.getInstance().getInfo(request)

                Result.success(info)

            } catch (e: Exception) {
                Log.e(TAG, "Extraction failed", e)
                Result.failure(e)
            }
        }

    /**
     * إيقاف عملية تنزيل.
     */
    fun stopDownload(processId: String): Result<Unit> {
        return try {
            YoutubeDL.getInstance()
                .destroyProcessById(processId)

            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Stop download failed", e)
            Result.failure(e)
        }
    }

    /**
     * التحقق من جاهزية المحرك.
     */
    fun isInitialized(): Boolean {
        return initialized
    }
}
