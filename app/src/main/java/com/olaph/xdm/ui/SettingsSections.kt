package com.olaph.xdm.ui

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.olaph.xdm.core.BackgroundHelp
import com.olaph.xdm.core.BatteryAdvice
import com.olaph.xdm.core.Formatters
import com.olaph.xdm.core.RunGate
import com.olaph.xdm.core.SpeedLimitInput
import com.olaph.xdm.data.AppSettings
import com.olaph.xdm.engine.FileCategory
import com.olaph.xdm.service.ScheduleAlarm

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun InfoText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** أداة اختيار مجلد النظام مع طلب صلاحية دائمة تستمر بعد إعادة تشغيل الجهاز. */
private class PickFolder : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: Context, input: Uri?): Intent =
        super.createIntent(context, input).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
        )
}

/** موقع حفظ الملفات: مجلد التنزيلات أو مجلد يختاره المستخدم. */
@Composable
fun SaveLocationSettings(vm: MainViewModel, settings: AppSettings) {
    val folder by vm.saveFolder.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(PickFolder()) { uri ->
        if (uri != null) vm.chooseSaveFolder(uri)
    }

    LaunchedEffect(Unit) {
        vm.refreshSaveFolder()
    }

    Text("موقع حفظ الملفات", style = MaterialTheme.typography.titleMedium)

    Column(Modifier.padding(vertical = 8.dp)) {
        Text(
            folder?.label ?: "Download/XDM",
            style = MaterialTheme.typography.titleSmall
        )

        InfoText(
            when {
                folder == null ->
                    "مجلد Download/XDM العام، ويمكن الوصول إليه من معرض الصور والتطبيقات الأخرى."

                folder?.available == false ->
                    "المجلد غير متاح حالياً (ربما حُذف أو أُزيلت بطاقة الذاكرة أو أُلغيت صلاحية الوصول). سيتم حفظ الملفات المكتملة في Download/XDM حتى تختار مجلداً آخر."

                else ->
                    "المجلد الذي اخترته. سيتم حفظ الملفات المكتملة هنا."
            }
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { picker.launch(null) }) {
            Text("اختيار مجلد...")
        }

        TextButton(
            onClick = vm::resetSaveFolder,
            enabled = settings.saveTreeUri != null
        ) {
            Text("استعادة Download/XDM الافتراضي")
        }
    }

    SwitchRow(
        "ترتيب الملفات في مجلدات فرعية حسب النوع",
        FileCategory.entries.joinToString(", ") { it.folder },
        settings.sortByType,
        vm::setSortByType,
    )
}

/** جدولة التنزيلات: تحديد فترة يومية، مع إمكانية اشتراط توصيل الشاحن. */
@Composable
fun ScheduleSettings(vm: MainViewModel, settings: AppSettings) {
    val context = LocalContext.current

    var exactAllowed by remember {
        mutableStateOf(ScheduleAlarm.canScheduleExact(context))
    }

    LifecycleResumeEffect(Unit) {
        exactAllowed = ScheduleAlarm.canScheduleExact(context)
        onPauseOrDispose {}
    }

    Text("جدولة التنزيلات", style = MaterialTheme.typography.titleMedium)

    SwitchRow(
        "التنزيل خلال فترة زمنية يومية فقط",
        "خارج الفترة المحددة، تنتظر التنزيلات الموجودة في قائمة الانتظار، وتعود التنزيلات الجارية إلى قائمة الانتظار. خيار «ابدأ الآن» يتجاوز الجدولة.",
        settings.scheduleEnabled,
        vm::setScheduleEnabled,
    )

    if (!settings.scheduleEnabled) return

    TimeRow("من", settings.scheduleStartMinute) {
        vm.setScheduleWindow(it, settings.scheduleEndMinute)
    }

    TimeRow("إلى", settings.scheduleEndMinute) {
        vm.setScheduleWindow(settings.scheduleStartMinute, it)
    }

    if (settings.scheduleStartMinute == settings.scheduleEndMinute) {
        InfoText("وقت البداية والنهاية متطابق، لذلك تكون فترة التنزيل مفتوحة طوال الوقت.")
    }

    SwitchRow(
        "التنزيل أثناء الشحن فقط",
        "تنتظر التنزيلات توصيل الشاحن، حتى أثناء الفترة المحددة.",
        settings.scheduleOnlyCharging,
        vm::setScheduleOnlyCharging
    )

    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        settings.scheduleStartMinute != settings.scheduleEndMinute
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("البدء في الوقت المحدد", style = MaterialTheme.typography.titleSmall)

                InfoText(
                    if (exactAllowed)
                        "يسمح أندرويد لتطبيق XDM بالتنبيه بدقة عند بدء فترة التنزيل."
                    else
                        "من دون صلاحية «المنبهات والتذكيرات»، قد يمنع أندرويد بدء التنزيلات في الخلفية. عندها يعرض XDM إشعاراً بعنوان «التنزيلات المجدولة جاهزة» لتضغط عليه وتبدأ التنزيلات."
                )
            }

            if (!exactAllowed) {
                TextButton(
                    onClick = {
                        if (!BackgroundHelp.openExactAlarmSettings(context)) {
                            cantOpen(context)
                        }
                    }
                ) {
                    Text("السماح")
                }
            }
        }
    }
}

@Composable
private fun TimeRow(
    label: String,
    minuteOfDay: Int,
    onPicked: (Int) -> Unit
) {
    val context = LocalContext.current

    Row(
        Modifier.fillMaxWidth().clickable {
            TimePickerDialog(
                context,
                { _, h, m -> onPicked(h * 60 + m) },
                minuteOfDay / 60,
                minuteOfDay % 60,
                DateFormat.is24HourFormat(context),
            ).show()
        }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall
        )

        Text(
            RunGate.clock(minuteOfDay),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

/** تحديد سرعة التنزيل لجميع الملفات، والإعداد الافتراضي هو عدم وجود حد للسرعة. */
@Composable
fun SpeedLimitSettings(vm: MainViewModel, settings: AppSettings) {
    val limit = settings.speedLimitBytesPerSec

    val (shownText, shownMegabytes) =
        if (limit > 0) SpeedLimitInput.display(limit) else ("" to true)

    var text by remember(limit) { mutableStateOf(shownText) }
    var megabytes by remember(limit) { mutableStateOf(shownMegabytes) }

    val parsed = SpeedLimitInput.parse(text, megabytes)

    Text("حد سرعة التنزيل", style = MaterialTheme.typography.titleMedium)

    Column(Modifier.padding(top = 8.dp)) {
        Text(
            if (limit > 0)
                "السرعة المحددة: ${Formatters.speed(limit)}"
            else
                "غير محدودة",
            style = MaterialTheme.typography.titleSmall
        )

        InfoText(
            "يُطبق الحد على جميع التنزيلات مجتمعة، ويسري فوراً حتى على التنزيلات الجارية."
        )
    }

    Row(
        Modifier.padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("الحد الأقصى للسرعة") },
            singleLine = true,
            isError = text.isNotBlank() && parsed == null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            ),
            modifier = Modifier.weight(1f),
        )

        FilterChip(
            selected = !megabytes,
            onClick = { megabytes = false },
            label = { Text("KB/s") }
        )

        FilterChip(
            selected = megabytes,
            onClick = { megabytes = true },
            label = { Text("MB/s") }
        )
    }

    Row {
        TextButton(
            onClick = { parsed?.let(vm::setSpeedLimit) },
            enabled = parsed != null && parsed != limit
        ) {
            Text("تطبيق الحد")
        }

        TextButton(
            onClick = { vm.setSpeedLimit(0) },
            enabled = limit > 0
        ) {
            Text("إلغاء الحد")
        }
    }
}

/** إعدادات عمل التطبيق في الخلفية وإرشادات توفير البطارية. */
@Composable
fun BackgroundSettings() {
    val context = LocalContext.current

    var exempt by remember {
        mutableStateOf(BackgroundHelp.isExempt(context))
    }

    LifecycleResumeEffect(Unit) {
        exempt = BackgroundHelp.isExempt(context)
        onPauseOrDispose {}
    }

    val xiaomi = remember {
        BatteryAdvice.isXiaomiFamily(Build.MANUFACTURER, Build.BRAND)
    }

    Text("إعدادات الخلفية", style = MaterialTheme.typography.titleMedium)

    Column(
        Modifier.fillMaxWidth().clickable {
            if (
                BackgroundHelp.openBatterySettings(context) ==
                BackgroundHelp.Opened.NOTHING
            ) {
                cantOpen(context)
            }
        }.padding(vertical = 8.dp)
    ) {
        Text("تحسين استهلاك البطارية", style = MaterialTheme.typography.titleSmall)

        InfoText(
            if (exempt)
                "تطبيق XDM غير مقيّد حالياً. اضغط لعرض القائمة."
            else
                "قد يوقف النظام تطبيق XDM في الخلفية لتوفير البطارية. اضغط للسماح له بمواصلة التنزيل."
        )
    }

    Column(
        Modifier.fillMaxWidth().clickable {
            if (
                BackgroundHelp.openBackgroundTips(context) ==
                BackgroundHelp.Opened.NOTHING
            ) {
                cantOpen(context)
            }
        }.padding(vertical = 8.dp)
    ) {
        Text("إرشادات تشغيل التطبيق في الخلفية", style = MaterialTheme.typography.titleSmall)

        InfoText(
            if (xiaomi)
                "يفتح قائمة التشغيل التلقائي. فعّل تطبيق XDM هناك، وإلا فقد يوقفه نظام MIUI أو HyperOS عند إطفاء الشاشة."
            else
                "يفتح معلومات تطبيق XDM، حيث يمكنك مراجعة إعدادات البطارية والتشغيل في الخلفية."
        )
    }

    InfoText(
        "يقيّد أندرويد 15 أيضاً خدمات التنزيل التي تعمل في الخلفية إلى نحو 6 ساعات خلال 24 ساعة. بعد ذلك يوقف XDM التنزيلات مؤقتاً ويتيح لك متابعتها. فتح التطبيق يعيد بدء احتساب المدة."
    )
}

private fun cantOpen(context: Context) {
    Toast.makeText(
        context,
        "لا توجد شاشة إعدادات متاحة لهذا الخيار في هذا الهاتف.",
        Toast.LENGTH_SHORT
    ).show()
}
