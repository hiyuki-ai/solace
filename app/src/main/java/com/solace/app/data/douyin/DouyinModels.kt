package com.solace.app.data.douyin

/** 复制过来的作品类型。 */
enum class DouyinKind {
    VIDEO,
    IMAGES,
}

/** 抖音作者信息；uid / 抖音号在部分作品里可能缺失。 */
data class DouyinAuthor(
    val uid: String?,
    val secUid: String?,
    val uniqueId: String?,
    val shortId: String?,
    val nickname: String?,
    val avatarUrl: String?,
) {
    /** 优先展示抖音号，没有就退回 short_id。 */
    val displayId: String?
        get() = uniqueId?.takeIf { it.isNotBlank() } ?: shortId?.takeIf { it.isNotBlank() }
}

/** 一条待下载的素材；kind 取 [com.solace.app.data.work.MediaKind]。 */
data class DouyinMedia(
    val kind: String,
    val url: String,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    /** 视频或实况图的静态预览图；普通图片复用 [url]。 */
    val previewUrl: String? = null,
)

/** 图文作品的背景音乐；视频作品的音轨已经包含在 MP4 里。 */
data class DouyinAudio(
    val url: String,
    val title: String?,
    val durationMs: Long?,
)

/**
 * 解析结果：文案、作者、发布时间和素材直链。
 *
 * 素材此时还没有落盘，确认发布时才逐条下载。
 */
data class DouyinWork(
    val awemeId: String,
    val kind: DouyinKind,
    val caption: String,
    val author: DouyinAuthor?,
    /** 作品发布时间（毫秒时间戳）。 */
    val publishedAt: Long?,
    val media: List<DouyinMedia>,
    val coverUrl: String?,
    val audio: DouyinAudio?,
)
