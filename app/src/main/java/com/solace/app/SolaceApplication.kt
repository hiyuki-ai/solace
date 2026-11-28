package com.solace.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.video.VideoFrameDecoder
import com.solace.app.data.MediaLibrary
import com.solace.app.data.work.UserWithWorkCount
import com.solace.app.data.work.WorkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import okio.Path.Companion.toOkioPath

class SolaceApplication : Application(), SingletonImageLoader.Factory {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val mediaLibrary: MediaLibrary by lazy { MediaLibrary(this) }

    val workRepository: WorkRepository by lazy { WorkRepository(this) }

    /**
     * 分类页用户目录属于轻量级全局数据，保持在应用进程内，
     * 切换 Tab 时无需重新查询，也不会先闪一次错误空态。
     */
    val categoryUsers: StateFlow<List<UserWithWorkCount>?> by lazy {
        workRepository.observeUsers()
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = null,
            )
    }

    override fun onCreate() {
        super.onCreate()
        categoryUsers
        mediaLibrary.start()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .build()
}
