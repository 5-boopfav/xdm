package com.olaph.xdm.ui

import android.content.ClipboardManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.olaph.xdm.core.Formatters
import com.olaph.xdm.data.DownloadEntity
import com.olaph.xdm.engine.LinkMatch
import com.olaph.xdm.engine.UrlUtils

/** «تغيير الرابط»: لصق رابط جديد لتنزيل لم يكتمل بعد. */
@Composable
fun ChangeUrlDialog(download: DownloadEntity, onDismiss: () -> Unit, onCheck: (String) -> Unit) {
    val context = LocalContext.current
    var url by remember { mutableStateOf("") }
    val trimmed = url.trim()
    val valid = UrlUtils.isHttpUrl(trimmed)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تغيير الرابط") },
        text = {
            Column {
                Text(download.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                Text(
                    "يجب أن يؤدي الرابط الجديد إلى الملف نفسه. سيتحقق بحمد IDM من حجم الملف أولاً، وسيحتفظ بالجزء الذي تم تنزيله بالفعل.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("الرابط الجديد") },
                    maxLines = 3,
                    isError = url.isNotBlank() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    trailingIcon = {
                        IconButton(onClick = {
                            val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
                            val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                            url = UrlUtils.extractFirstUrl(text) ?: text.trim()
                        }) { Icon(Icons.Filled.ContentPaste, "لصق") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onCheck(trimmed) }, enabled = valid) { Text("التحقق من الرابط") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

/** نتيجة فحص الرابط البديل: استخدامه، أو بدء التنزيل من جديد، أو توضيح سبب عدم صلاحيته. */
@Composable
fun ReplacementDialog(offer: ReplacementOffer, onConfirm: (restart: Boolean) -> Unit, onDismiss: () -> Unit) {
    val check = offer.check
    val verdict = check?.verdict
    val title = when {
        check == null && offer.error == null -> "جارٍ التحقق من الرابط..."
        offer.error != null -> "تعذّر استخدام الرابط"
        verdict is LinkMatch.Verdict.Same -> "هل تريد استخدام هذا الرابط؟"
        verdict is LinkMatch.Verdict.Different -> "هذا رابط لملف مختلف"
        else -> "هل تريد بدء التنزيل من جديد بهذا الرابط؟"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(offer.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                Spacer(Modifier.size(8.dp))
                when {
                    offer.error != null -> Text(offer.error, style = MaterialTheme.typography.bodyMedium)
                    check == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("جارٍ الاتصال بالخادم...")
                    }
                    verdict is LinkMatch.Verdict.Same -> Text(
                        "حجم الملف مطابق، لذلك سيستمر التنزيل من النقطة التي وصل إليها بالفعل.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    verdict is LinkMatch.Verdict.Different -> Text(
                        "حجم الملف الجديد ${Formatters.bytes(verdict.newSize)}، بينما حجم الملف الحالي ${Formatters.bytes(verdict.oldSize)}. " +
                            "لم يتم استخدام الرابط.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    verdict is LinkMatch.Verdict.RestartOnly -> Text(
                        when (verdict.reason) {
                            LinkMatch.Reason.ETAG_CHANGED ->
                                "يشير الخادم إلى أن الملف قد تغيّر. دمج الملف الجديد مع الجزء المحفوظ قد يؤدي إلى تلفه، لذلك يجب بدء التنزيل من الصفر."
                            LinkMatch.Reason.SIZE_UNKNOWN ->
                                "الخادم لا يحدد حجم الملف، لذلك لا يستطيع بحمد IDM التأكد من أنه الملف نفسه. يجب بدء التنزيل من الصفر."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            when (verdict) {
                is LinkMatch.Verdict.Same -> TextButton(onClick = { onConfirm(false) }) { Text("استبدال الرابط") }
                is LinkMatch.Verdict.RestartOnly -> TextButton(onClick = { onConfirm(true) }) { Text("إعادة التنزيل من الصفر") }
                else -> if (check != null || offer.error != null) TextButton(onClick = onDismiss) { Text("حسناً") }
            }
        },
        dismissButton = {
            if (verdict is LinkMatch.Verdict.Same || verdict is LinkMatch.Verdict.RestartOnly || check == null && offer.error == null) {
                TextButton(onClick = onDismiss) { Text("إلغاء") }
            }
        },
    )
}
