package com.olaph.xdm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.olaph.xdm.core.BatchCheck
import com.olaph.xdm.core.DuplicateCheck
import com.olaph.xdm.core.DuplicateMatch
import com.olaph.xdm.core.DuplicateReason

/**
 * تنزيل يبدو أنه موجود بالفعل في القائمة.
 * إذا كان التنزيل الموجود متوقفاً مؤقتاً أو فاشلاً، يظهر زر «استئناف الموجود» كزر رئيسي.
 * وفي الحالات الأخرى يظهر زر «فتح الموجود».
 * خيار «تنزيل مرة أخرى» يضيف الملف مجدداً باسم فريد.
 */
@Composable
fun DuplicateDialog(
    match: DuplicateMatch,
    onResume: () -> Unit,
    onOpen: () -> Unit,
    onAddAgain: () -> Unit,
    onCancel: () -> Unit,
) {
    val existing = match.existing

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text("قد يكون هذا الملف موجوداً بالفعل في قائمتك")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    existing.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    DuplicateCheck.describe(existing),
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    when (match.reason) {
                        DuplicateReason.SAME_LINK -> "الرابط نفسه"
                        DuplicateReason.SAME_NAME -> "اسم الملف نفسه في المجلد نفسه"
                        DuplicateReason.SAME_SIZE -> "الحجم نفسه واسم مشابه"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                if (match.resumable) {
                    Button(onClick = onResume) {
                        Text("استئناف التنزيل الموجود")
                    }

                    TextButton(onClick = onOpen) {
                        Text("فتح التنزيل الموجود")
                    }
                } else {
                    Button(onClick = onOpen) {
                        Text("فتح التنزيل الموجود")
                    }
                }

                TextButton(onClick = onAddAgain) {
                    Text("تنزيل مرة أخرى")
                }

                TextButton(onClick = onCancel) {
                    Text("إلغاء")
                }
            }
        },
    )
}

/** عدة تنزيلات دفعة واحدة (مثل قائمة تشغيل)، بعضها موجود بالفعل: تخطي المكرر أو إضافة الجميع. */
@Composable
fun BatchDuplicateDialog(
    check: BatchCheck<*>,
    onSkip: () -> Unit,
    onAddAll: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(check.summary.orEmpty())
        },
        text = {
            Text(
                if (check.duplicates == check.total) {
                    "لن تؤدي عملية التخطي إلى إضافة أي شيء."
                } else {
                    "هل تريد تخطي الملفات المكررة أم إضافتها جميعاً مرة أخرى؟"
                },
                modifier = Modifier.padding(top = 4.dp),
            )
        },
        confirmButton = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                Button(onClick = onSkip) {
                    Text("تخطي الملفات المكررة")
                }

                TextButton(onClick = onAddAll) {
                    Text("إضافة الكل")
                }

                TextButton(onClick = onCancel) {
                    Text("إلغاء")
                }
            }
        },
    )
}
