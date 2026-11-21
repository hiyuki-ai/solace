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

data class WorkWithMedia(
    @Embedded val work: WorkEntity,
    @Relation(parentColumn = "id", entityColumn = "workId")
    val media: List<WorkMediaEntity>,
) {
    val coverRelativePath: String? get() = work.coverFile ?: media.firstOrNull()?.fileName
}
