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
    indices = [
        Index(value = ["status", "updatedAt"]),
        Index(value = ["userId"]),
    ],
)
data class WorkEntity(
    @PrimaryKey val id: String,
    val caption: String,
    val status: String,
    val coverFile: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val publishedAt: Long?,
    /** 作品关联的本地用户；未关联用户的本地原创作品为空。 */
    val userId: Long? = null,
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

object UserKind {
    const val DOUYIN = "DOUYIN"
    const val CUSTOM = "CUSTOM"
}

/**
 * 通用用户资料。
 *
 * 当前复制抖音作品会创建 [UserKind.DOUYIN] 用户，并优先按 [uid]、没有 [uid]
 * 时按 [secUid] 去重。后续自定义用户使用 [UserKind.CUSTOM]，这些抖音身份字段
 * 可以为空。抖音号等展示资料可能变化，不参与身份判定。
 */
@Entity(
    tableName = "users",
    indices = [
        Index(value = ["kind", "uid"], unique = true),
        Index(value = ["kind", "secUid"], unique = true),
    ],
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [UserKind.DOUYIN] 或 [UserKind.CUSTOM]。 */
    val kind: String,
    val uid: String?,
    val secUid: String?,
    val uniqueId: String?,
    val nickname: String?,
    /** 应用私有目录下的相对路径，如 users/<hash>/avatar.jpg。 */
    val avatarFile: String?,
    val avatarUrl: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class UserWithWorkCount(
    @Embedded val user: UserEntity,
    val workCount: Int,
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
    @Relation(parentColumn = "userId", entityColumn = "id")
    val user: UserEntity? = null,
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
