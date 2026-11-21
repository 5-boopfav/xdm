package com.olaph.xdm.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.olaph.xdm.AppContainer
import android.widget.Toast
import com.olaph.xdm.core.DuplicateCandidate
import com.olaph.xdm.core.DuplicateMatch
import com.olaph.xdm.core.Formatters
import com.olaph.xdm.core.PendingDownload
import com.olaph.xdm.data.DownloadKind
import com.olaph.xdm.engine.DownloadHeaders
import com.olaph.xdm.engine.FilenameUtils
import com.olaph.xdm.engine.ProbeResult
import com.olaph.xdm.engine.SegmentPlanner
import com.olaph.xdm.engine.UrlUtils
import com.olaph.xdm.media.MediaClassifier
import com.olaph.xdm.youtube.YoutubeLink
import com.olaph.xdm.youtube.YoutubeUrls
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface ProbeState {
    data object Idle : ProbeState
    data object Loading : ProbeState
    data class Done(val result: ProbeResult) : ProbeState
    data class Failed(val message: String) : ProbeState
}

/** نافذة إضافة التنزيل المشتركة بين الشاشة الرئيسية ومستقبل المشاركة. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDownloadSheet(
    container: AppContainer,
    prefill: PendingDownload?,
    defaultConnections: Int,
    /** يوجد رقم PIN، لذلك يمكن حفظ التنزيلات في المجلد الخاص. */
    privateAvailable: Boolean,
    defaultPrivate: Boolean,
    onDismiss: () -> Unit,
    onAdded: () -> Unit,
    /** يتم تمرير رابط YouTube الملصق أو المكتوب بدلاً من فحصه كملف عادي. */
    onYoutube: (YoutubeLink) -> Unit = {},
    /** فتح تنزيل موجود من نافذة التكرار. */
    onOpenExisting: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // ترويسات المتصفح (Cookie وReferer وUser-Agent) تُستخدم في الفحص والتنزيل بالكامل.
    val headers = prefill?.headers.orEmpty()
    val origin = DownloadHeaders.origin(prefill?.pageUrl)
    var url by rememberSaveable { mutableStateOf(prefill?.url.orEmpty()) }
    var fileName by rememberSaveable { mutableStateOf(prefill?.fileName.orEmpty()) }
    var nameEdited by rememberSaveable { mutableStateOf(false) }
    var connections by rememberSaveable { mutableIntStateOf(defaultConnections) }
    var probe by remember { mutableStateOf<ProbeState>(ProbeState.Idle) }
    var adding by remember { mutableStateOf(false) }
    var savePrivate by rememberSaveable { mutableStateOf(prefill?.isPrivate ?: defaultPrivate) }

    val trimmed = url.trim()
    val validUrl = UrlUtils.isHttpUrl(trimmed)

    // تُنزّل قوائم التشغيل مقطعاً تلو الآخر، ولا تحتاج إلى فحص نطاقات البايت.
    val hls = validUrl && ((prefill?.kind == DownloadKind.HLS && trimmed == prefill.url) || MediaClassifier.isHlsUrl(trimmed))

    LaunchedEffect(trimmed) {
        YoutubeUrls.parse(trimmed)?.let {
            onYoutube(it)
            return@LaunchedEffect
        }
        if (!validUrl || hls) {
            probe = ProbeState.Idle
            if (hls && !nameEdited && fileName.isBlank()) fileName = FilenameUtils.resolve(null, trimmed, null)
            return@LaunchedEffect
        }
        probe = ProbeState.Loading
        delay(500)
        try {
            val result = container.manager.probe(trimmed, headers)
            probe = ProbeState.Done(result)
            if (!nameEdited) fileName = result.fileName
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            probe = ProbeState.Failed(e.message ?: "تعذر الاتصال بالخادم")
            // الاحتفاظ بالاسم القادم من المتصفح عند تعذر تحسينه بواسطة الفحص.
            if (!nameEdited && (trimmed != prefill?.url || fileName.isBlank())) {
                fileName = FilenameUtils.resolve(null, trimmed, null)
            }
        }
    }

    // ينتظر التنزيل المشابه لتنزيل موجود في القائمة إجابة المستخدم.
    var duplicate by remember { mutableStateOf<Pair<DuplicateMatch, Boolean>?>(null) }

    suspend fun addNow(startNow: Boolean) {
        val result = (probe as? ProbeState.Done)?.result
        val name = fileName.ifBlank { result?.fileName ?: FilenameUtils.resolve(null, trimmed, null) }
        container.manager.add(
            trimmed, name, connections, startNow, result, headers,
            kind = if (hls) DownloadKind.HLS else DownloadKind.HTTP,
            isPrivate = savePrivate && privateAvailable,
        )
        onAdded()
    }

    fun submit(startNow: Boolean) {
        if (adding || !validUrl) return
        adding = true
        scope.launch {
            val result = (probe as? ProbeState.Done)?.result
            val name = fileName.ifBlank { result?.fileName ?: FilenameUtils.resolve(null, trimmed, null) }

            // حجم قائمة التشغيل تقديري فقط، ولا يحدد الملف نفسه.
            val size = if (hls) -1L else result?.contentLength ?: prefill?.contentLength?.takeIf { trimmed == prefill.url } ?: -1L

            val match = container.manager.findDuplicate(
                DuplicateCandidate(trimmed, name, size, isPrivate = savePrivate && privateAvailable)
            )

            if (match == null) {
                addNow(startNow)
            } else {
                duplicate = match to startNow
                adding = false
            }
        }
    }

    duplicate?.let { (match, startNow) ->
        val appContext = context.applicationContext
        DuplicateDialog(
            match = match,
            onResume = {
                duplicate = null
                container.manager.resume(match.existing.id)
                Toast.makeText(
                    appContext,
                    "جارٍ استئناف ${match.existing.fileName}",
                    Toast.LENGTH_SHORT
                ).show()
                onDismiss()
            },
            onOpen = {
                duplicate = null
                onOpenExisting(match.existing.id)
                onDismiss()
            },
            onAddAgain = {
                duplicate = null
                adding = true
                scope.launch { addNow(startNow) }
            },
            onCancel = { duplicate = null },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("إضافة تنزيل", style = MaterialTheme.typography.titleLarge)

            if (origin != null) {
                Text(
                    "المصدر: $origin" +
                        if (headers.containsKey("Cookie")) " · سيتم تمرير جلسة تسجيل الدخول" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("الرابط") },
                singleLine = false,
                maxLines = 3,
                isError = url.isNotBlank() && !validUrl,
                supportingText = {
                    if (url.isNotBlank() && !validUrl) {
                        Text("أدخل رابط HTTP أو HTTPS صالحاً")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                trailingIcon = {
                    IconButton(onClick = { pasteInto(context) { url = it } }) {
                        Icon(
                            Icons.Filled.ContentPaste,
                            contentDescription = "لصق"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (hls) {
                val estimate = prefill?.contentLength?.takeIf { it > 0 && trimmed == prefill.url }
                Text(
                    "بث HLS · سيتم جلب المقاطع بالتوازي ودمجها في ملف واحد دون تحويل" +
                        (estimate?.let {
                            " · " + (if (prefill?.lengthEstimated == true) "~" else "") + Formatters.bytes(it)
                        } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when (val p = probe) {
                ProbeState.Idle -> Unit

                ProbeState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    val hint = prefill?.contentLength?.takeIf { it > 0 && trimmed == prefill.url }
                    Text(
                        "  جارٍ فحص الرابط..." +
                            if (hint != null) " (${Formatters.bytes(hint)})" else "",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is ProbeState.Done -> {
                    val r = p.result
                    Text(
                        buildString {
                            append("الحجم: ")
                                .append(if (r.contentLength > 0) Formatters.bytes(r.contentLength) else "غير معروف")

                            append(" · ").append(
                                when {
                                    !r.resumable -> "لا يدعم الاستئناف، اتصال واحد"
                                    r.contentLength < SegmentPlanner.SPLIT_THRESHOLD -> "ملف صغير، اتصال واحد"
                                    else -> "يدعم الاستئناف"
                                },
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (r.resumable) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        },
                    )
                }

                is ProbeState.Failed -> Text(
                    "تعذر فحص الرابط (${p.message}). لا يزال بإمكانك إضافته." +
                        (
                            prefill?.contentLength
                                ?.takeIf { it > 0 && trimmed == prefill.url }
                                ?.let { " الحجم: ${Formatters.bytes(it)}." }
                                ?: ""
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            OutlinedTextField(
                value = fileName,
                onValueChange = {
                    fileName = it
                    nameEdited = true
                },
                label = { Text("اسم الملف") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            val resumable = hls || (probe as? ProbeState.Done)?.result?.resumable != false

            Column {
                Text(
                    when {
                        hls -> "المقاطع المتوازية: $connections"
                        resumable -> "عدد الاتصالات: $connections"
                        else -> "عدد الاتصالات: 1 (الخادم لا يسمح بالمزيد)"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )

                Slider(
                    value = connections.toFloat(),
                    onValueChange = { connections = it.toInt() },
                    valueRange = 1f..16f,
                    steps = 14,
                    enabled = resumable,
                )
            }

            PrivateCheckbox(
                checked = savePrivate && privateAvailable,
                available = privateAvailable,
                onChange = { savePrivate = it }
            )

            Spacer(Modifier.height(4.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { submit(false) },
                    enabled = validUrl && !adding,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("إضافة إلى قائمة الانتظار")
                }

                Button(
                    onClick = { submit(true) },
                    enabled = validUrl && !adding,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("ابدأ الآن")
                }
            }
        }
    }
}

/** خيار الحفظ في المجلد الخاص، مشترك بين نوافذ التنزيل والمتصفح وYouTube. */
@Composable
fun PrivateCheckbox(
    checked: Boolean,
    available: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().clickable(enabled = available) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            enabled = available
        )

        Column {
            Text(
                "الحفظ في المجلد الخاص",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                if (available) {
                    "مخفي عن المعرض والتطبيقات الأخرى ومحمي برقم PIN"
                } else {
                    "قم بإعداد المجلد الخاص أولاً (أيقونة القفل في التنزيلات)"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun pasteInto(context: Context, set: (String) -> Unit) {
    val clip = context.getSystemService(ClipboardManager::class.java).primaryClip ?: return
    if (clip.itemCount == 0) return
    val text = clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
    set(UrlUtils.extractFirstUrl(text) ?: text.trim())
}
