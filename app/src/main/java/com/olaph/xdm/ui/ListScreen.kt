package com.olaph.xdm.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.olaph.xdm.core.BackgroundHelp
import com.olaph.xdm.core.BatteryAdvice
import com.olaph.xdm.core.Formatters
import com.olaph.xdm.core.Hold
import com.olaph.xdm.core.LinkRefresh
import com.olaph.xdm.core.RunGate
import com.olaph.xdm.data.AppSettings
import com.olaph.xdm.data.DownloadEntity
import com.olaph.xdm.data.DownloadStatus
import com.olaph.xdm.engine.MimeTypes
import com.olaph.xdm.engine.SegmentPlanner
import com.olaph.xdm.media.MediaFiles

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(vm: MainViewModel, onOpenDetail: (Long) -> Unit, onOpenSettings: () -> Unit, onOpenPrivate: () -> Unit) {
    val context = LocalContext.current
    val items by vm.items.collectAsStateWithLifecycle()
    val queue by vm.queueStatus.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val clipboardOffer by vm.clipboardOffer.collectAsStateWithLifecycle()

    // يتم منح الاستثناء من شاشة النظام، لذلك نتحقق منه مجددًا عند العودة.
    var batteryExempt by remember { mutableStateOf(BackgroundHelp.isExempt(context)) }
    LifecycleResumeEffect(Unit) {
        batteryExempt = BackgroundHelp.isExempt(context)
        onPauseOrDispose {}
    }

    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var pendingDelete by remember { mutableStateOf<DownloadEntity?>(null) }
    var bulkDelete by remember { mutableStateOf<List<DownloadEntity>?>(null) }
    var overflow by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val selected by vm.selected.collectAsStateWithLifecycle()
    val deleteProgress by vm.deleteProgress.collectAsStateWithLifecycle()
    val selecting = selected.isNotEmpty()
    val shown = items?.filter { filter.matches(it.entity.status) }
    val scoped = DeleteScope.inFilter(shown.orEmpty().map { it.entity }, filter)

    BackHandler(enabled = selecting) { vm.clearSelection() }

    // الملفات التي لا يستطيع التطبيق حذفها بنفسه: تعرض في نافذة نظام واحدة.
    val systemDelete = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        vm.onSystemDeleteResult(it.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is MainViewModel.Event.Message -> snackbar.showSnackbar(e.text, duration = SnackbarDuration.Long)
                is MainViewModel.Event.SystemDelete -> systemDelete.launch(IntentSenderRequest.Builder(e.request).build())
            }
        }
    }

    LaunchedEffect(clipboardOffer) {
        val offer = clipboardOffer ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = "رابط في الحافظة: ${offer.take(60)}",
            actionLabel = "تنزيل",
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) vm.openAddSheet(offer) else vm.dismissClipboardOffer()
    }

    Scaffold(
        topBar = {
            if (selecting) {
                val chosen = items.orEmpty().map { it.entity }.filter { it.id in selected }
                TopAppBar(
                    title = { Text("تم تحديد ${selected.size}") },
                    navigationIcon = {
                        IconButton(onClick = { vm.clearSelection() }) {
                            Icon(Icons.Filled.Close, "إلغاء التحديد")
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.selectAll(scoped.map { it.id }) }) {
                            Icon(Icons.Filled.SelectAll, "تحديد الكل")
                        }

                        val completed = chosen.filter { it.status == DownloadStatus.COMPLETED }

                        if (completed.isNotEmpty()) {
                            IconButton(onClick = { FileActions.shareMany(context, vm.container, completed) }) {
                                Icon(Icons.Filled.Share, "مشاركة")
                            }
                        }

                        IconButton(onClick = { bulkDelete = chosen }) {
                            Icon(Icons.Filled.Delete, "حذف")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("بحمد") },
                    actions = {
                        IconButton(onClick = onOpenPrivate) {
                            Icon(Icons.Filled.Lock, "المجلد الخاص")
                        }

                        IconButton(onClick = { vm.pauseAll() }) {
                            Icon(Icons.Filled.PauseCircle, "إيقاف الكل مؤقتًا")
                        }

                        IconButton(onClick = { vm.resumeAll() }) {
                            Icon(Icons.Filled.PlayCircle, "استئناف الكل")
                        }

                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, "الإعدادات")
                        }

                        Box {
                            IconButton(onClick = { overflow = true }) {
                                Icon(Icons.Filled.MoreVert, "المزيد")
                            }

                            DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                                DropdownMenuItem(
                                    text = { Text("حذف الكل (${filter.label}: ${scoped.size})") },
                                    leadingIcon = { Icon(Icons.Filled.DeleteSweep, null) },
                                    enabled = scoped.isNotEmpty() && deleteProgress == null,
                                    onClick = { overflow = false; bulkDelete = scoped },
                                )
                            }
                        }
                    },
                )
            }
        },

        floatingActionButton = {
            if (!selecting) {
                FloatingActionButton(onClick = { vm.openAddSheet() }) {
                    Icon(Icons.Filled.Add, "إضافة تنزيل")
                }
            }
        },

        snackbarHost = { SnackbarHost(snackbar) },

    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            TabRow(selectedTabIndex = filter.ordinal) {
                Filter.entries.forEach { f ->
                    val count = items?.count { f.matches(it.entity.status) }

                    Tab(
                        selected = f == filter,
                        onClick = { filter = f },
                        text = {
                            Text(if (count != null && count > 0) "${f.label} ($count)" else f.label)
                        },
                    )
                }
            }

            deleteProgress?.let { p ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(
                        "جارٍ الحذف ${p.done} / ${p.total}…",
                        style = MaterialTheme.typography.bodySmall
                    )

                    LinearProgressIndicator(
                        progress = { if (p.total > 0) p.done.toFloat() / p.total else 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            limitsLine(settings, queue)?.let { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            if (settings.saveFolderWarning) {
                InfoCard(
                    "مجلد التنزيلات الذي اخترته غير متاح، لذلك ستُحفظ الملفات الجديدة في Download/XDM. اختر مجلدًا آخر من الإعدادات.",
                    actionLabel = "الإعدادات",
                    onAction = onOpenSettings,
                    onDismiss = vm::dismissFolderWarning,
                )
            }

            if (BatteryAdvice.shouldShowCard(batteryExempt, settings.batteryCardDismissed, items.orEmpty().map { it.entity })) {
                InfoCard(
                    "قد يؤدي توفير البطارية إلى إيقاف التنزيلات الطويلة في الخلفية؛ اسمح للتطبيق بتجاهل قيود توفير البطارية لمواصلة التنزيل.",
                    actionLabel = "السماح",
                    onAction = { BackgroundHelp.openBatterySettings(context) },
                    onDismiss = vm::dismissBatteryCard,
                )
            }

            when {
                shown == null -> Unit

                shown.isEmpty() -> EmptyState(filter)

                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown, key = { it.entity.id }) { item ->
                        DownloadRow(
                            item = item,
                            queue = queue,
                            vm = vm,
                            selecting = selecting,
                            checked = item.entity.id in selected,

                            onClick = {
                                val d = item.entity
                                when {
                                    selecting -> vm.toggleSelected(d.id)
                                    // يفتح الملف المكتمل في تطبيق آخر، أما غير المكتمل فتُعرض تفاصيله.
                                    d.status == DownloadStatus.COMPLETED -> vm.open(context, d)
                                    else -> onOpenDetail(d.id)
                                }
                            },

                            onLongClick = {
                                if (!selecting) vm.toggleSelected(item.entity.id)
                            },

                            onDelete = { pendingDelete = item.entity },
                            onDetails = { onOpenDetail(item.entity.id) },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { d ->
        DeleteDialog(d, onDismiss = { pendingDelete = null }) { alsoFile ->
            vm.delete(d.id, alsoFile)
            pendingDelete = null
        }
    }

    bulkDelete?.let { list ->
        BulkDeleteDialog(DeleteScope.summarize(list), onDismiss = { bulkDelete = null }) { alsoFiles ->
            vm.deleteDownloads(list.map { it.id }, alsoFiles)
            bulkDelete = null
        }
    }
}

/** حذف الكل أو حذف العناصر المحددة. الحذف نهائي ولا يمكن التراجع عنه. */
@Composable
fun BulkDeleteDialog(summary: DeleteSummary, onDismiss: () -> Unit, onConfirm: (alsoFiles: Boolean) -> Unit) {
    var alsoFiles by rememberSaveable { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,

        title = {
            Text(
                if (summary.count == 1)
                    "هل تريد حذف تنزيل واحد؟"
                else
                    "هل تريد حذف عدد ${summary.count} من التنزيلات؟"
            )
        },

        text = {
            Column {
                if (summary.completedCount > 0) {
                    Text("${summary.completedCount} مكتمل · ${Formatters.bytes(summary.bytesOnDisk)} على الجهاز")
                }

                if (summary.activeCount > 0) {
                    Text("سيتم إلغاء ${summary.activeCount} من التنزيلات غير المكتملة وحذف بياناتها الجزئية.")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = alsoFiles, onCheckedChange = { alsoFiles = it })
                    Text("حذف الملفات من مساحة التخزين أيضًا")
                }

                Text(
                    if (alsoFiles)
                        "سيتم حذف الملفات نهائيًا، ولا يمكن التراجع عن ذلك."
                    else
                        "ستُحذف السجلات فقط، وستبقى الملفات في مجلد Downloads/XDM.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (alsoFiles)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },

        confirmButton = {
            TextButton(onClick = { onConfirm(alsoFiles) }) {
                Text("حذف", color = MaterialTheme.colorScheme.error)
            }
        },

        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("احتفاظ")
            }
        },
    )
}

@Composable
private fun EmptyState(filter: Filter) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            if (filter == Filter.ALL)
                "لا توجد تنزيلات بعد.\nاضغط على + أو شارك رابطًا مع بحمد."
            else
                "لا يوجد شيء هنا.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun DeleteDialog(d: DownloadEntity, onDismiss: () -> Unit, onConfirm: (deleteFile: Boolean) -> Unit) {
    var alsoFile by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,

        title = {
            Text("هل تريد حذف التنزيل؟")
        },

        text = {
            Column {
                Text(d.fileName, maxLines = 2, overflow = TextOverflow.Ellipsis)

                if (d.status == DownloadStatus.COMPLETED) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = alsoFile, onCheckedChange = { alsoFile = it })
                        Text("حذف الملف أيضًا")
                    }
                } else {
                    Text(
                        "ستُحذف البيانات التي تم تنزيلها جزئيًا.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },

        confirmButton = {
            TextButton(onClick = { onConfirm(alsoFile) }) {
                Text("حذف")
            }
        },

        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("احتفاظ")
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadRow(
    item: DownloadItem,
    queue: QueueStatus,
    vm: MainViewModel,
    selecting: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onDetails: () -> Unit,
) {
    val d = item.entity

    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = if (checked)
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        else
            CardDefaults.cardColors(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {

            if (selecting) {
                Checkbox(checked = checked, onCheckedChange = { onClick() })
                Spacer(Modifier.width(4.dp))
            }

            DownloadIcon(d)
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {

                    if (d.isHls || d.isYoutube) {
                        HlsChip(if (d.isYoutube) "YouTube" else "HLS")
                        Spacer(Modifier.width(6.dp))
                    }

                    Text(
                        d.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }

                if (d.status != DownloadStatus.COMPLETED) {
                    Spacer(Modifier.height(6.dp))

                    val fraction = item.fraction

                    when {
                        fraction != null -> LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth()
                        )

                        d.status == DownloadStatus.RUNNING ->
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                    }

                    Spacer(Modifier.height(4.dp))
                }

                Text(sizeLine(item), style = MaterialTheme.typography.bodySmall)

                Text(
                    statusLine(item, queue),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (d.status == DownloadStatus.FAILED)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (!selecting) RowActions(d, vm, onDelete, onDetails)
        }
    }
}

/** تظهر صورة مصغرة للفيديو المكتمل، وأيقونة نوع الملف لبقية الملفات. */
@Composable
private fun DownloadIcon(d: DownloadEntity) {
    if (d.status == DownloadStatus.COMPLETED && MediaFiles.isMedia(d)) {
        MediaThumbnail(d, Modifier.size(width = 72.dp, height = 44.dp)) {
            Box(
                Modifier.size(width = 72.dp, height = 44.dp),
                contentAlignment = Alignment.Center
            ) {
                FileTypeIcon(d.fileName, d.mimeType)
            }
        }
    } else {
        FileTypeIcon(d.fileName, d.mimeType)
    }
}

@Composable
fun RowActions(d: DownloadEntity, vm: MainViewModel, onDelete: () -> Unit, onDetails: (() -> Unit)? = null) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }

    when (d.status) {
        DownloadStatus.RUNNING, DownloadStatus.QUEUED ->
            IconButton(onClick = { vm.pause(d.id) }) {
                Icon(Icons.Filled.Pause, "إيقاف مؤقت")
            }

        DownloadStatus.PAUSED ->
            IconButton(onClick = { vm.resume(d.id) }) {
                Icon(Icons.Filled.PlayArrow, "استئناف")
            }

        DownloadStatus.FAILED, DownloadStatus.CANCELLED ->
            if (LinkRefresh.retryRefreshesLink(d)) {
                IconButton(onClick = { vm.requestLinkRefresh(d.id) }) {
                    Icon(Icons.Filled.Refresh, "تحديث الرابط")
                }
            } else {
                IconButton(onClick = { vm.retry(d.id) }) {
                    Icon(Icons.Filled.Refresh, "إعادة المحاولة")
                }
            }

        DownloadStatus.COMPLETED ->
            IconButton(onClick = { vm.open(context, d) }) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, "فتح")
            }
    }

    Box {
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Filled.MoreVert, "المزيد")
        }

        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {

            when (d.status) {
                DownloadStatus.RUNNING, DownloadStatus.QUEUED, DownloadStatus.PAUSED ->
                    DropdownMenuItem(
                        text = { Text("إلغاء") },
                        leadingIcon = { Icon(Icons.Filled.Close, null) },
                        onClick = { menu = false; vm.cancel(d.id) },
                    )

                DownloadStatus.COMPLETED -> {
                    DropdownMenuItem(
                        text = { Text("فتح") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                        onClick = { menu = false; vm.open(context, d) },
                    )

                    DropdownMenuItem(
                        text = { Text("فتح باستخدام…") },
                        leadingIcon = { Icon(Icons.Filled.Apps, null) },
                        onClick = { menu = false; FileActions.openWith(context, vm.container, d) },
                    )

                    DropdownMenuItem(
                        text = { Text("مشاركة الملف") },
                        leadingIcon = { Icon(Icons.Filled.Share, null) },
                        onClick = { menu = false; FileActions.share(context, vm.container, d) },
                    )

                    DropdownMenuItem(
                        text = { Text("نقل إلى المجلد الخاص") },
                        leadingIcon = { Icon(Icons.Filled.Lock, null) },
                        onClick = { menu = false; vm.moveToPrivate(d) },
                    )
                }

                else -> Unit
            }

            onDetails?.let { open ->
                DropdownMenuItem(
                    text = { Text("التفاصيل") },
                    leadingIcon = { Icon(Icons.Filled.Info, null) },
                    onClick = { menu = false; open() },
                )
            }

            DropdownMenuItem(
                text = { Text("نسخ الرابط") },
                leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                onClick = { menu = false; FileActions.copyLink(context, d.url) },
            )

            if (LinkRefresh.retryRefreshesLink(d)) {
                DropdownMenuItem(
                    text = { Text("إعادة المحاولة بالرابط نفسه") },
                    leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                    onClick = { menu = false; vm.retry(d.id) },
                )
            }

            if (LinkRefresh.canRefreshFromPage(d)) {
                DropdownMenuItem(
                    text = { Text("تحديث الرابط") },
                    leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                    onClick = { menu = false; vm.requestLinkRefresh(d.id) },
                )
            }

            if (LinkRefresh.canChangeLink(d)) {
                DropdownMenuItem(
                    text = { Text("تغيير الرابط…") },
                    leadingIcon = { Icon(Icons.Filled.Link, null) },
                    onClick = { menu = false; vm.changeUrlFor.value = d },
                )
            }

            DropdownMenuItem(
                text = { Text("حذف") },
                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                onClick = { menu = false; onDelete() },
            )
        }
    }
}

@Composable
fun HlsChip(label: String = "HLS") {
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun FileTypeIcon(name: String, mime: String?) {
    val icon = iconFor(name, mime)

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

private fun iconFor(name: String, mime: String?): ImageVector {
    val ext = name.substringAfterLast('.', "").lowercase()
    val type = MimeTypes.forFileName(name) ?: mime.orEmpty()

    return when {
        ext == "apk" -> Icons.Filled.Android
        type.startsWith("video/") -> Icons.Filled.Movie
        type.startsWith("audio/") -> Icons.Filled.MusicNote
        type.startsWith("image/") -> Icons.Filled.Image
        ext == "pdf" -> Icons.Filled.PictureAsPdf
        ext in setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso") -> Icons.Filled.FolderZip
        ext in setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "epub", "csv") -> Icons.Filled.Description
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}

// ---- دوال النصوص المشتركة مع شاشة التفاصيل ----

fun sizeLine(item: DownloadItem): String {
    val d = item.entity

    return when {
        d.status == DownloadStatus.COMPLETED ->
            Formatters.bytes(d.totalBytes)

        d.isHls -> buildString {
            if (item.segmentsTotal > 0) {
                append("${item.segmentsDone} / ${item.segmentsTotal} مقاطع")
            } else {
                append("جارٍ قراءة قائمة التشغيل…")
            }

            if (item.downloaded > 0) {
                append(" · ").append(Formatters.bytes(item.downloaded))
            }

            if (item.total > 0) {
                append(" (~").append(Formatters.bytes(item.total)).append(")")
            }
        }

        item.total > 0 ->
            "${Formatters.bytes(item.downloaded)} / ${Formatters.bytes(item.total)}" +
                " (${(item.downloaded * 100 / item.total)}%)"

        else ->
            "${Formatters.bytes(item.downloaded)} / حجم غير معروف"
    }
}

fun effectiveConnections(d: DownloadEntity): Int =
    if (d.isHls) d.connections
    else if (d.notResumable || (d.totalBytes in 1 until SegmentPlanner.SPLIT_THRESHOLD)) 1 else d.connections

/** حد السرعة والجدولة في سطر واحد مختصر. */
fun limitsLine(settings: AppSettings, queue: QueueStatus): String? {
    val parts = buildList {
        if (settings.speedLimitBytesPerSec > 0) {
            add("Speed limit ${Formatters.speed(settings.speedLimitBytesPerSec)}")
        }

        if (settings.scheduleEnabled) {
            when {
                queue.hold == Hold.SCHEDULE ->
                    add("Scheduled: starts at ${queue.startsAt ?: RunGate.clock(settings.scheduleStartMinute)}")

                settings.scheduleStartMinute != settings.scheduleEndMinute ->
                    add("Schedule ${RunGate.clock(settings.scheduleStartMinute)}–${RunGate.clock(settings.scheduleEndMinute)}")

                settings.scheduleOnlyCharging ->
                    add("Only while charging")
            }
        }
    }

    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun InfoCard(text: String, actionLabel: String, onAction: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("إخفاء")
                }

                TextButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

fun statusLine(item: DownloadItem, queue: QueueStatus): String {
    val d = item.entity
    val conn = effectiveConnections(d)

    val connText = when {
        d.isHls -> if (conn == 1) "one segment at a time" else "$conn segments in parallel"
        conn == 1 -> "1 connection"
        else -> "$conn connections"
    }

    val resumeNote =
        if (d.notResumable && d.status != DownloadStatus.COMPLETED)
            " · Cannot resume"
        else
            ""

    return when (d.status) {

        DownloadStatus.RUNNING -> {
            val live = item.live
            val speed = live?.bytesPerSecond ?: 0

            if (live?.converting == true) {
                return "${live.convertLabel} ${live.convertPercent}%"
            }

            buildString {
                append(live?.statusText ?: "Downloading")

                if (speed > 0) {
                    append(" · ").append(Formatters.speed(speed))
                }

                if (live != null && live.etaSeconds >= 0) {
                    append(" · Time remaining ").append(Formatters.eta(live.etaSeconds))
                }

                append(" · ").append(connText).append(resumeNote)
            }
        }

        DownloadStatus.QUEUED -> when (queue.hold) {
            Hold.WIFI -> "Waiting for Wi-Fi"
            Hold.SCHEDULE -> "Scheduled" + (queue.startsAt?.let { " · starts at $it" } ?: "")
            Hold.CHARGING -> "Waiting for the charger"
            null -> "Queued"
        } + resumeNote

        // الإيقاف اليدوي لا يعرض سببًا، أما حد الوقت في النظام فيعرض سببه.
        DownloadStatus.PAUSED ->
            (d.error ?: "Paused") + resumeNote

        DownloadStatus.COMPLETED ->
            "Completed" +
                (d.completedAt?.let { " · ${Formatters.dateTime(it)}" } ?: "") +
                (d.error?.let { "\n$it" } ?: "")

        DownloadStatus.FAILED ->
            "Failed" + (d.error?.let { ": $it" } ?: "")

        DownloadStatus.CANCELLED ->
            "Cancelled"
    }
}
