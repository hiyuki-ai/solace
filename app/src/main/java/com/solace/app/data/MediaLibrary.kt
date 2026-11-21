package com.solace.app.data

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 进程内媒体库缓存：只查询一次，之后由 ContentObserver 在媒体变化时刷新。
 * 避免每次页面切换都重新扫描 MediaStore。
 */
class MediaLibrary(context: Context) {

    private val appContext = context.applicationContext
    private val repository = MediaStoreVideoRepository(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _folders = MutableStateFlow<List<VideoFolder>>(emptyList())
    val folders: StateFlow<List<VideoFolder>> = _folders.asStateFlow()

    private var refreshJob: Job? = null
    private var started = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            refresh()
        }
    }

    fun start() {
        if (started) return
        started = true
        appContext.contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true,
            observer,
        )
        refresh()
    }

    fun refreshIfEmpty() {
        if (_folders.value.isEmpty()) refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            // 合并短时间内的连续变更通知。
            delay(150)
            _folders.value = repository.loadFolders()
        }
    }
}
