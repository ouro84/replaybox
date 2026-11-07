package com.replaybox.app.image

import android.os.SystemClock
import android.util.Log
import coil.EventListener
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult

/**
 * Debug-only Coil listener: logs how long each image took and where it came from
 * (MEMORY_CACHE, DISK, NETWORK), to measure on the box how fast rails fill
 * (M4 "Images M6 mesurées"). Filter with `adb logcat -s ReplayboxImage`.
 */
class ImageTimingListener : EventListener {
    private var startedAt = 0L

    override fun onStart(request: ImageRequest) {
        startedAt = SystemClock.elapsedRealtime()
    }

    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
        Log.d(TAG, "ok ${elapsed()} ms ${result.dataSource} ${request.data}")
    }

    override fun onError(request: ImageRequest, result: ErrorResult) {
        Log.d(TAG, "error ${elapsed()} ms ${request.data}: ${result.throwable.javaClass.simpleName}")
    }

    private fun elapsed() = SystemClock.elapsedRealtime() - startedAt

    object Factory : EventListener.Factory {
        override fun create(request: ImageRequest): EventListener = ImageTimingListener()
    }

    companion object {
        const val TAG = "ReplayboxImage"
    }
}
