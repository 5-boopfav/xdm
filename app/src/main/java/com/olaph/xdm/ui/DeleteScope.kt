package com.olaph.xdm.ui

import com.olaph.xdm.core.Formatters
import com.olaph.xdm.data.DownloadEntity
import com.olaph.xdm.data.DownloadStatus

/** ملخص العناصر التي ستتأثر بعملية الحذف الجماعي، ويُعرض في نافذة التأكيد. */
data class DeleteSummary(
    val count: Int,
    /** العناصر النشطة أو المنتظرة أو المتوقفة مؤقتاً، وسيتم إلغاؤها أولاً. */
    val activeCount: Int,
    val completedCount: Int,
    /** حجم الملفات المكتملة التي سيحذفها خيار حذف الملفات أيضاً. */
    val bytesOnDisk: Long,
)

object DeleteScope {
    /** العناصر التي يشملها خيار حذف الكل: العناصر الظاهرة في علامة التبويب المحددة فقط، دون الملفات الخاصة. */
    fun inFilter(items: List<DownloadEntity>, filter: Filter): List<DownloadEntity> =
        items.filter { !it.isPrivate && filter.matches(it.status) }

    fun summarize(items: List<DownloadEntity>): DeleteSummary {
        val completed = items.filter { it.status == DownloadStatus.COMPLETED }

        return DeleteSummary(
            count = items.size,
            activeCount = items.count { it.status in ACTIVE },
            completedCount = completed.size,
            bytesOnDisk = completed.sumOf { it.totalBytes.coerceAtLeast(0) },
        )
    }

    /** رسالة توضح عدد التنزيلات المحذوفة والمساحة المستعادة، وعدد الملفات التي تعذر حذفها. */
    fun message(removed: Int, freedBytes: Long, failed: Int): String = buildString {
        append("Deleted ")
            .append(removed)
            .append(if (removed == 1) " download" else " downloads")

        if (freedBytes > 0) {
            append(" (")
                .append(Formatters.bytes(freedBytes))
                .append(" freed)")
        }

        if (failed > 0) {
            append(" · ")
                .append(failed)
                .append(if (failed == 1) " file could not be deleted" else " files could not be deleted")
        }
    }

    private val ACTIVE = setOf(
        DownloadStatus.QUEUED,
        DownloadStatus.RUNNING,
        DownloadStatus.PAUSED
    )
}
