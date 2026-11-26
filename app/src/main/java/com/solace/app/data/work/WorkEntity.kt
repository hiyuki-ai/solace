package com.solace.app.data.work

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** 作品状态 */
object WorkStatus {
    const val DRAFT = "DRAFT"
    const val PUBLISHED = "PUBLISHED"
}

/** 素材类型 */
object MediaKind {
    const val VIDEO = "VIDEO"
    const val IMAGE = "IMAGE"
    const val AUDIO = "AUDIO"
}

@Entity(
    tableName = "works",
    indices = [Index(value = ["status", "updatedAt"])],
)
data class WorkEntity(
    @PrimaryKey val id: String,
    val caption: String,
    val status: String,
    val coverFile: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val publishedAt: Long?,
)

@Entity(
    tableName = "work_media",
    foreignKeys = [
        ForeignKey(
            entity = WorkEntity::class,
            parentColumns = ["id"],
            childColumns = ["workId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["workId"])],
)
data class WorkMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workId: String,
    val orderIndex: Int,
    val kind: String,
    /** 相对作品目录的路径，如 media/000.mp4 */
    val fileName: String,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    val sizeBytes: Long,
)

/**
 * 复制作品的来源信息，和作品是 1:1 关系。
 *
 * 单独建表而不是往 works 上加一串可空列：来源本身是可选的，
 * 本地原创作品没有这一行，查询 / 级联删除仍然由 Room 负责。
 */
@Entity(
    tableName = "work_origins",
    foreignKeys = [
        ForeignKey(
            entity = WorkEntity::class,
            parentColumns = ["id"],
            childColumns = ["workId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WorkOriginEntity(
    @PrimaryKey val workId: String,
    /** 来源平台，目前只有 douyin。 */
    val platform: String,
    val authorName: String?,
    /** 抖音号；作者没设置时是 short_id。 */
    val authorId: String?,
    val authorSecUid: String?,
    val awemeId: String?,
    /** 抖音侧的发布时间（毫秒时间戳）。 */
    val publishedAt: Long?,
)

object OriginPlatform {
    const val DOUYIN = "douyin"
}

data class WorkWithMedia(
    @Embedded val work: WorkEntity,
    @Relation(parentColumn = "id", entityColumn = "workId")
    val media: List<WorkMediaEntity>,
    @Relation(parentColumn = "id", entityColumn = "workId")
    val origin: WorkOriginEntity? = null,
) {
    /** 图文音轨也是 work_media 的一行，浏览与编辑时只把画面素材当作分页项。 */
    val visualMedia: List<WorkMediaEntity>
        get() = media
            .asSequence()
            .filter { it.kind == MediaKind.IMAGE || it.kind == MediaKind.VIDEO }
            .sortedBy { it.orderIndex }
            .toList()

    val audioFile: String?
        get() = media.firstOrNull { it.kind == MediaKind.AUDIO }?.fileName

    val coverRelativePath: String?
        get() = work.coverFile ?: visualMedia.firstOrNull()?.fileName
}
