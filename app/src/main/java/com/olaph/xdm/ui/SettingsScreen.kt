package com.olaph.xdm.ui

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.olaph.xdm.BuildConfig
import com.olaph.xdm.adblock.PopupMode
import com.olaph.xdm.adblock.UpdateState
import com.olaph.xdm.core.RestartResume
import com.olaph.xdm.crash.TestReportResult
import com.olaph.xdm.data.AppSettings
import com.olaph.xdm.data.AutoLockDelay
import com.olaph.xdm.data.ThemeMode
import com.olaph.xdm.engine.SearchEngine
import com.olaph.xdm.youtube.YoutubeQuality
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SliderSetting(
                title = "عدد الاتصالات الافتراضي لكل تنزيل",
                value = settings.connections,
                range = 1..16,
                onCommit = vm::setConnections,
            )
            SliderSetting(
                title = "الحد الأقصى للتنزيلات المتزامنة",
                value = settings.maxParallel,
                range = 1..10,
                onCommit = vm::setMaxParallel,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Row(
                Modifier.fillMaxWidth().clickable { vm.setWifiOnly(!settings.wifiOnly) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("شبكة Wi-Fi فقط", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "انتظر حتى الاتصال بشبكة غير محدودة، مثل بيانات الهاتف أو نقطة الاتصال",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.wifiOnly, onCheckedChange = vm::setWifiOnly)
            }
            Row(
                Modifier.fillMaxWidth().clickable { vm.setResumeAfterRestart(!settings.resumeAfterRestart) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("استئناف التنزيلات بعد إعادة التشغيل", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (Build.VERSION.SDK_INT >= RestartResume.BOOT_FGS_RESTRICTED_SDK) {
                            "بعد إعادة تشغيل الجهاز، سيظهر إشعار يتيح استئناف التنزيلات المتوقفة، لأن أندرويد لا يسمح ببدئها تلقائياً. أما بعد تحديث التطبيق فتستمر تلقائياً."
                        } else {
                            "تستمر التنزيلات المتوقفة تلقائياً بعد إعادة تشغيل الجهاز أو تحديث التطبيق."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.resumeAfterRestart, onCheckedChange = vm::setResumeAfterRestart)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SaveLocationSettings(vm, settings)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            ScheduleSettings(vm, settings)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SpeedLimitSettings(vm, settings)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            BackgroundSettings()
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text("المجلد الخاص", style = MaterialTheme.typography.titleMedium)
            Row(
                Modifier.fillMaxWidth().clickable { vm.setSavePrivateByDefault(!settings.savePrivateByDefault) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("حفظ التنزيلات الجديدة في المجلد الخاص", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "تفعيل خيار «الحفظ في المجلد الخاص» تلقائياً عند إضافة التنزيلات. يتطلب تعيين رمز PIN (أيقونة القفل في صفحة التنزيلات).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.savePrivateByDefault, onCheckedChange = vm::setSavePrivateByDefault)
            }
            Text("قفل المجلد الخاص", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
            Text(
                "عند انتقال بحمد إلى الخلفية. كما يتم قفله دائماً عند إطفاء الشاشة.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AutoLockDelay.entries.forEach { delay ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.setAutoLockDelay(delay) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.autoLockDelay == delay, onClick = { vm.setAutoLockDelay(delay) })
                    Text(delay.label)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text("المظهر", style = MaterialTheme.typography.titleSmall)
            ThemeMode.entries.forEach { mode ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.setTheme(mode) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.theme == mode, onClick = { vm.setTheme(mode) })
                    Text(
                        when (mode) {
                            ThemeMode.SYSTEM -> "حسب إعدادات النظام"
                            ThemeMode.LIGHT -> "فاتح"
                            ThemeMode.DARK -> "داكن"
                        },
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text("محرك البحث في المتصفح", style = MaterialTheme.typography.titleSmall)
            SearchEngine.entries.forEach { engine ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.setSearchEngine(engine) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.searchEngine == engine, onClick = { vm.setSearchEngine(engine) })
                    Text(engine.label)
                }
            }
            Row(
                Modifier.fillMaxWidth().clickable { vm.setInterceptDownloads(!settings.interceptDownloads) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("اعتراض جميع تنزيلات المتصفح", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "تحويل التنزيلات التي تبدأ من المتصفح المدمج إلى بحمد بدلاً من مدير التنزيلات الافتراضي للنظام.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.interceptDownloads, onCheckedChange = vm::setInterceptDownloads)
            }
            Row(
                Modifier.fillMaxWidth().clickable { vm.setConvertHlsToMp4(!settings.convertHlsToMp4) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("تحويل HLS إلى MP4", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "تحويل فيديوهات HLS المكتملة (MPEG-TS) إلى ملفات MP4 عادية تعمل في أي معرض صور أو مشغل فيديو. " +
                            "لا تتم إعادة ترميز الفيديو، لكن يلزم توفر مساحة فارغة تقارب حجم الفيديو.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.convertHlsToMp4, onCheckedChange = vm::setConvertHlsToMp4)
            }
            Row(
                Modifier.fillMaxWidth().clickable { vm.setAutoRefreshLinks(!settings.autoRefreshLinks) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("تحديث الروابط المنتهية تلقائياً", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "عندما يفشل التنزيل بسبب خطأ HTTP 403 أو 410، يفتح بحمد صفحة الملف في الخلفية ويحاول الحصول على رابط جديد لملف بالحجم نفسه تماماً. لا يعمل مع التنزيلات الخاصة أو HLS أو YouTube.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.autoRefreshLinks, onCheckedChange = vm::setAutoRefreshLinks)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text("جودة YouTube الافتراضية", style = MaterialTheme.typography.titleSmall)
            Text(
                "خيار «السؤال في كل مرة» يعرض قائمة الصيغ عند إضافة كل رابط من YouTube. أما الخيارات الأخرى فتضيف التنزيل مباشرة، مع اختيار أقرب دقة أقل إذا لم تكن الدقة المطلوبة متاحة.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            YoutubeQuality.entries.forEach { q ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.setYoutubeQuality(q) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.youtubeQuality == q, onClick = { vm.setYoutubeQuality(q) })
                    Text(q.label)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            AdBlockSettings(vm, settings)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            UserAgentSetting(settings.userAgent, onSave = vm::setUserAgent)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            CrashReportSettings(vm)
            Spacer(Modifier.height(16.dp))
            var licensesOpen by remember { mutableStateOf(false) }
            Text(
                "حول التطبيق والتراخيص",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth().clickable { licensesOpen = true }.padding(vertical = 8.dp),
            )
            if (licensesOpen) LicensesDialog(vm) { licensesOpen = false }
            Spacer(Modifier.height(8.dp))
            Text(
                "بحمد ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AdBlockSettings(vm: MainViewModel, settings: AppSettings) {
    val info by vm.filterListsInfo.collectAsStateWithLifecycle()
    val update by vm.filterListsUpdate.collectAsStateWithLifecycle()

    Text("حاجب الإعلانات", style = MaterialTheme.typography.titleMedium)
    Row(
        Modifier.fillMaxWidth().clickable { vm.setBlockAds(!settings.blockAds) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("حظر الإعلانات", style = MaterialTheme.typography.titleSmall)
            Text(
                "في المتصفح المدمج: يحظر طلبات الإعلانات وأدوات التتبع باستخدام قوائم التصفية مثل EasyList وAdGuard، كما يخفي عناصر الإعلانات.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = settings.blockAds, onCheckedChange = vm::setBlockAds)
    }

    Text("حظر النوافذ المنبثقة", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    Text(
        when (settings.popupMode) {
            PopupMode.OFF -> "يُسمح للصفحات بفتح نوافذ جديدة."
            PopupMode.ADS_ONLY -> "حظر النوافذ المنبثقة الإعلانية والنوافذ التي تُفتح دون النقر عليها."
            PopupMode.ALL -> "حظر جميع النوافذ التي تحاول الصفحة فتحها. يظل خيار «فتح» في الإشعار متاحاً لفتحها."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        PopupMode.entries.forEach { mode ->
            Row(Modifier.clickable { vm.setPopupMode(mode) }.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = settings.popupMode == mode, onClick = { vm.setPopupMode(mode) })
                Text(
                    when (mode) {
                        PopupMode.OFF -> "إيقاف"
                        PopupMode.ADS_ONLY -> "الإعلانات فقط"
                        PopupMode.ALL -> "الكل"
                    }
                )
            }
        }
    }

    Column(Modifier.padding(vertical = 8.dp)) {
        Text("قوائم التصفية", style = MaterialTheme.typography.titleSmall)
        Text(
            when {
                info.loading -> "جارٍ التحميل…"
                !info.loaded -> "لم يتم التحميل"
                else -> "%,d قاعدة نشطة · اكتمل التحميل خلال %.1f ثانية%s".format(info.totalRules, info.loadMillis / 1000.0, if (info.fromCache) " (من الذاكرة المؤقتة)" else "")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val dateFormat = remember { java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM) }
    info.lists.forEach { list ->
        Row(
            Modifier.fillMaxWidth().clickable { vm.setFilterListEnabled(list.id, !list.enabled) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(list.title, style = MaterialTheme.typography.bodyLarge)
                val date = if (list.updatedAtMillis > 0) dateFormat.format(java.util.Date(list.updatedAtMillis)) else null
                Text(
                    when {
                        !list.enabled -> "متوقف"
                        !info.loaded -> "…"
                        !list.updatable -> "%,d قاعدة · مدمجة في التطبيق".format(list.ruleCount)
                        date == null -> "%,d قاعدة".format(list.ruleCount)
                        else -> "%,d قاعدة · %s %s".format(list.ruleCount, if (list.bundled) "نسخة من" else "آخر تحديث:", date)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = list.enabled, onCheckedChange = { vm.setFilterListEnabled(list.id, it) })
        }
    }
    Column(Modifier.padding(vertical = 4.dp)) {
        when (val u = update) {
            is UpdateState.Failed -> Text(u.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            is UpdateState.Done -> {
                Text("تم تحديث %d قائمة · إجمالي القواعد النشطة: %,d".format(u.updated, u.totalRules), style = MaterialTheme.typography.bodySmall)
                u.failures.forEach { (title, reason) ->
                    Text("$title: $reason", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            else -> Unit
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.clearFilterListsResult(); vm.updateFilterLists() }, enabled = update !is UpdateState.Running) {
                Text(if (update is UpdateState.Running) "جارٍ التحديث…" else "تحديث قوائم التصفية")
            }
            if (update is UpdateState.Running) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        if (settings.wifiOnly) {
            Text("التنزيل عبر Wi-Fi فقط (الخيار مفعّل)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    val sites = settings.adAllowedSites.sorted()
    Column(Modifier.padding(vertical = 8.dp)) {
        Text("المواقع المسموح بها (${sites.size})", style = MaterialTheme.typography.titleSmall)
        Text(
            if (sites.isEmpty()) "ستظهر هنا المواقع التي سمحت بظهور الإعلانات فيها." else "لا يتم حظر الإعلانات في هذه المواقع.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    sites.forEach { site ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(site, Modifier.weight(1f).padding(start = 8.dp))
            IconButton(onClick = { vm.removeAllowedSite(site) }) {
                Icon(Icons.Filled.Close, "إزالة $site")
            }
        }
    }
}

@Composable
private fun CrashReportSettings(vm: MainViewModel) {
    val enabled by vm.crashReportsEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }

    if (!vm.crashReportsAvailable) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text("إرسال تقارير الأعطال", style = MaterialTheme.typography.titleSmall)
            Text(
                "لم يتم إعداد تقارير الأعطال في هذا الإصدار.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Row(
        Modifier.fillMaxWidth().clickable { vm.setCrashReportsEnabled(!enabled) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("إرسال تقارير الأعطال", style = MaterialTheme.typography.titleSmall)
            Text(
                "إذا توقف التطبيق عن العمل بشكل مفاجئ، فسيتم إرسال تفاصيل الخطأ إلى المطور عند تشغيل التطبيق مرة أخرى. " +
                    "لا يتم تضمين الروابط أو عناوين الصفحات أو أسماء الملفات أو سجل التصفح.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = vm::setCrashReportsEnabled)
    }
    TextButton(
        onClick = {
            sending = true
            scope.launch {
                val message = when (vm.sendTestCrashReport()) {
                    TestReportResult.SENT -> "تم إرسال التقرير التجريبي."
                    TestReportResult.RETRY_LATER -> "تعذر الإرسال الآن، ستتم إعادة المحاولة لاحقاً."
                    TestReportResult.REJECTED -> "رفض الخادم التقرير التجريبي."
                    TestReportResult.DISABLED -> "فعّل خيار إرسال تقارير الأعطال أولاً."
                    TestReportResult.UNAVAILABLE -> "لم يتم إعداد تقارير الأعطال في هذا الإصدار."
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                sending = false
            }
        },
        enabled = enabled && !sending,
    ) {
        Text(if (sending) "جارٍ الإرسال…" else "إرسال تقرير تجريبي")
    }
}

@Composable
private fun LicensesDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val texts = remember {
        listOf(
            "دعم YouTube (NewPipeExtractor)" to vm.licenseText("licenses/newpipe-extractor-NOTICE.txt"),
            "قوائم التصفية" to vm.licenseText("licenses/filter-lists-NOTICE.txt"),
            "StevenBlack/hosts — ترخيص MIT" to vm.licenseText("licenses/stevenblack-hosts-LICENSE.txt"),
            "رخصة GNU العامة الإصدار الثالث (NewPipeExtractor ومرشحات EasyList وAdGuard)" to vm.licenseText("licenses/GPL-3.0.txt"),
            "رخصة Mozilla العامة الإصدار 2.0 (قائمة النطاقات العامة)" to vm.licenseText("licenses/MPL-2.0.txt"),
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
        title = { Text("حول التطبيق والتراخيص") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                texts.forEach { (title, body) ->
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(body, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                }
            }
        },
    )
}

@Composable
private fun SliderSetting(title: String, value: Int, range: IntRange, onCommit: (Int) -> Unit) {
    // Keep the thumb responsive while dragging; only write to DataStore when released.
    var dragging by remember { mutableFloatStateOf(Float.NaN) }
    val shown = if (dragging.isNaN()) value else dragging.toInt()
    Column(Modifier.padding(vertical = 8.dp)) {
        Text("$title: $shown", style = MaterialTheme.typography.titleSmall)
        Slider(
            value = shown.toFloat(),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                if (!dragging.isNaN()) onCommit(dragging.toInt())
                dragging = Float.NaN
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
        )
    }
}

@Composable
private fun UserAgentSetting(current: String, onSave: (String) -> Unit) {
    var text by remember(current) { mutableStateOf(current) }
    Column {
        Text("معرّف المتصفح (User-Agent)", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            minLines = 2,
            maxLines = 4,
        )
        Row {
            TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank() && text != current) {
                Text("حفظ")
            }
            TextButton(onClick = { onSave(AppSettings.DEFAULT_USER_AGENT) }, enabled = current != AppSettings.DEFAULT_USER_AGENT) {
                Text("إعادة ضبط إلى إعداد Chrome الافتراضي")
            }
        }
    }
}
