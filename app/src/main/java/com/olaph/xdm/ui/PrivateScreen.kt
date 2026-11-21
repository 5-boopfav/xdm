package com.olaph.xdm.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.olaph.xdm.core.Formatters
import com.olaph.xdm.data.DownloadEntity
import com.olaph.xdm.data.DownloadStatus
import com.olaph.xdm.media.MediaFiles
import com.olaph.xdm.vault.PinAttemptPolicy
import com.olaph.xdm.vault.PinHasher
import com.olaph.xdm.vault.UnlockResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** منع لقطات الشاشة وتسجيل الشاشة وظهور صورة مصغرة للمجلد الخاص في التطبيقات الأخيرة. */
@Composable
private fun SecureWindow() {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

private object Biometrics {
    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun available(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /** يظل الرقم السري متاحاً دائماً؛ وإلغاء التحقق بالبصمة يعيد المستخدم إلى إدخال الرقم السري. */
    fun prompt(activity: FragmentActivity, title: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val cancelled = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_CANCELED
                    if (!cancelled) onError(errString.toString())
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText("استخدام الرقم السري")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        runCatching { prompt.authenticate(info) }.onFailure { onError(it.message ?: "الفتح بالبصمة غير متاح") }
    }
}

@Composable
fun PrivateScreen(onBack: () -> Unit) {
    val vm: PrivateViewModel = viewModel()
    SecureWindow()
    val unlocked by vm.unlocked.collectAsStateWithLifecycle()
    val pinSet by vm.pinSet.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    when {
        pinSet == null -> Unit
        pinSet == false -> SetupPin(vm, snackbar, onBack)
        !unlocked -> LockScreen(vm, snackbar, onBack)
        else -> VaultContent(vm, snackbar, onBack)
    }
}

@Composable
private fun PinField(value: String, onChange: (String) -> Unit, label: String, isError: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it in '0'..'9' }.take(8)) },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
    )
}

@Composable
private fun SetupPin(vm: PrivateViewModel, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val valid = PinHasher.isValidPin(pin) && pin == confirm
    Scaffold(topBar = { SimpleTopBar("المجلد الخاص", onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("إنشاء رقم سري", style = MaterialTheme.typography.titleLarge)
            Text(
                "تُحفظ الملفات الموجودة في المجلد الخاص داخل مساحة التخزين الخاصة بتطبيق بحمد IDM. " +
                    "لن تظهر في معرض الصور أو مدير الملفات أو التطبيقات الأخرى، كما لن تُضمّن في النسخ الاحتياطية. " +
                    "يتطلب فتح هذا المجلد إدخال الرقم السري.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "لا توجد طريقة لاستعادة الرقم السري. إذا نسيته، فالطريقة الوحيدة للدخول مجدداً هي «إعادة ضبط المجلد الخاص»، " +
                    "وهذا سيؤدي إلى حذف جميع الملفات الموجودة بداخله.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            PinField(pin, { pin = it }, "الرقم السري (من 4 إلى 8 أرقام)")
            PinField(confirm, { confirm = it }, "أعد إدخال الرقم السري", isError = confirm.isNotEmpty() && confirm != pin)
            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        if (!vm.setupPin(pin)) snackbar.showSnackbar("تعذّر إنشاء الرقم السري")
                        busy = false
                    }
                },
                enabled = valid && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("إنشاء الرقم السري") }
        }
    }
}

@Composable
private fun LockScreen(vm: PrivateViewModel, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val biometricEnabled by vm.biometricEnabled.collectAsStateWithLifecycle()
    val attempts by vm.attempts.collectAsStateWithLifecycle()
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    var resetting by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val remaining = PinAttemptPolicy.remaining(attempts, now)
    LaunchedEffect(attempts) {
        while (true) {
            now = System.currentTimeMillis()
            if (PinAttemptPolicy.remaining(attempts, now) <= 0) break
            delay(500)
        }
    }
    val activity = context.findActivity() as? FragmentActivity
    val canUseBiometric = biometricEnabled && activity != null && Biometrics.available(context)
    fun biometric() {
        if (activity != null) Biometrics.prompt(activity, "فتح المجلد الخاص", vm::unlockWithBiometric) { hint = it }
    }
    // عرض نافذة البصمة أو الوجه تلقائياً مرة واحدة عند الدخول إلى الشاشة.
    LaunchedEffect(canUseBiometric) { if (canUseBiometric) biometric() }

    fun submit() {
        if (busy || remaining > 0 || pin.isEmpty()) return
        busy = true
        scope.launch {
            hint = when (val r = vm.unlock(pin)) {
                UnlockResult.Ok, UnlockResult.NoPin -> null
                is UnlockResult.CoolingDown -> null
                is UnlockResult.Wrong -> when {
                    r.cooldownMs > 0 -> "الرقم السري غير صحيح"
                    r.attemptsLeft in 1..2 -> "الرقم السري غير صحيح · تبقى ${r.attemptsLeft} محاولة قبل الإيقاف المؤقت"
                    else -> "الرقم السري غير صحيح"
                }
            }
            pin = ""
            busy = false
        }
    }

    Scaffold(topBar = { SimpleTopBar("المجلد الخاص", onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Text("أدخل الرقم السري", style = MaterialTheme.typography.titleLarge)
            PinField(pin, { pin = it; hint = null }, "الرقم السري", isError = hint != null)
            when {
                remaining > 0 -> Text(
                    "تم إدخال الرقم السري بشكل خاطئ مرات عديدة. حاول مجدداً بعد ${(remaining + 999) / 1000} ثانية.",
                    color = MaterialTheme.colorScheme.error,
                )
                hint != null -> Text(hint.orEmpty(), color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = ::submit, enabled = !busy && remaining <= 0 && pin.length >= 4, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "جارٍ التحقق..." else "فتح المجلد")
            }
            if (canUseBiometric) {
                OutlinedButton(onClick = ::biometric, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Fingerprint, null)
                    Spacer(Modifier.width(8.dp))
                    Text("استخدام البصمة أو الوجه")
                }
            }
            TextButton(onClick = { resetting = true }) { Text("نسيت الرقم السري؟") }
        }
    }
    if (resetting) ResetDialog(onDismiss = { resetting = false }) { resetting = false; vm.reset() }
}

/** الطريقة الوحيدة لتجاوز الرقم السري المنسي هي حذف جميع الملفات. يجب كتابة كلمة التأكيد. */
@Composable
private fun ResetDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إعادة ضبط المجلد الخاص؟") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("سيؤدي هذا الإجراء إلى حذف جميع الملفات في المجلد الخاص، وأي تنزيل خاص لا يزال قيد التنفيذ، والرقم السري.")
                Text("لا توجد طريقة لاستعادة الملفات بعد حذفها.", color = MaterialTheme.colorScheme.error)
                Text("اكتب كلمة $RESET_WORD للتأكيد.")
                OutlinedTextField(value = typed, onValueChange = { typed = it.take(20) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = typed.trim() == RESET_WORD) {
                Text("حذف جميع الملفات", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

private const val RESET_WORD = "إعادة"

private sealed interface PendingAction {
    data class Share(val d: DownloadEntity) : PendingAction
    data class OpenElsewhere(val d: DownloadEntity) : PendingAction
    data class MoveOut(val d: DownloadEntity) : PendingAction
    data class Rename(val d: DownloadEntity) : PendingAction
    data class Delete(val items: List<DownloadEntity>) : PendingAction
    data object ChangePin : PendingAction
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultContent(vm: PrivateViewModel, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val context = LocalContext.current
    val items by vm.items.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val biometricEnabled by vm.biometricEnabled.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<PendingAction?>(null) }
    var overflow by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
    val active = items.filter { it.entity.status != DownloadStatus.COMPLETED }
    val finished = items.filter { it.entity.status == DownloadStatus.COMPLETED }
    val activity = context.findActivity() as? FragmentActivity

    BackHandler(enabled = selecting) { vm.clearSelection() }

    fun open(d: DownloadEntity) {
        pending = PendingAction.OpenElsewhere(d)
    }

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text("${selected.size} عنصر محدد") },
                    navigationIcon = { IconButton(onClick = vm::clearSelection) { Icon(Icons.Filled.Close, "إلغاء التحديد") } },
                    actions = {
                        IconButton(onClick = { vm.selectAll(items.map { it.entity.id }) }) { Icon(Icons.Filled.SelectAll, "تحديد الكل") }
                        IconButton(onClick = { pending = PendingAction.Delete(items.map { it.entity }.filter { it.id in selected }) }) {
                            Icon(Icons.Filled.Delete, "حذف")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("المجلد الخاص") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
                    actions = {
                        IconButton(onClick = { vm.lockNow(); onBack() }) { Icon(Icons.Filled.Lock, "قفل الآن") }
                        Box {
                            IconButton(onClick = { overflow = true }) { Icon(Icons.Filled.MoreVert, "المزيد") }
                            DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                                DropdownMenuItem(
                                    text = { Text("تغيير الرقم السري") },
                                    leadingIcon = { Icon(Icons.Filled.Password, null) },
                                    onClick = { overflow = false; pending = PendingAction.ChangePin },
                                )
                                if (activity != null && Biometrics.available(context)) {
                                    DropdownMenuItem(
                                        text = { Text("الفتح بالبصمة أو الوجه") },
                                        leadingIcon = { Icon(Icons.Filled.Fingerprint, null) },
                                        trailingIcon = { Switch(checked = biometricEnabled, onCheckedChange = null) },
                                        onClick = {
                                            overflow = false
                                            if (biometricEnabled) {
                                                vm.setBiometricEnabled(false)
                                            } else {
                                                // لا يتم تفعيل الخيار إلا بعد نجاح التحقق بالبصمة أو الوجه مرة واحدة.
                                                Biometrics.prompt(activity, "استخدام البصمة أو الوجه", { vm.setBiometricEnabled(true) }) {}
                                            }
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("حذف الكل (${items.size})") },
                                    leadingIcon = { Icon(Icons.Filled.DeleteSweep, null) },
                                    enabled = items.isNotEmpty(),
                                    onClick = { overflow = false; pending = PendingAction.Delete(items.map { it.entity }) },
                                )
                            }
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "لا توجد ملفات هنا حتى الآن.\nفعّل خيار «الحفظ في المجلد الخاص» عند إضافة تنزيل، أو استخدم «نقل إلى المجلد الخاص» مع ملف مكتمل.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (active.isNotEmpty()) {
                item { SectionLabel("التنزيلات قيد التنفيذ") }
                items(active, key = { it.entity.id }) { item ->
                    ActiveRow(item, vm, selecting, item.entity.id in selected, onDelete = { pending = PendingAction.Delete(listOf(item.entity)) })
                }
                item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
            }
            items(finished, key = { it.entity.id }) { item ->
                val d = item.entity
                FileRow(
                    d = d,
                    selecting = selecting,
                    checked = d.id in selected,
                    onClick = { if (selecting) vm.toggleSelected(d.id) else open(d) },
                    onLongClick = { if (!selecting) vm.toggleSelected(d.id) },
                    onAction = { pending = it },
                    onOpen = { open(d) },
                )
            }
        }
    }

    when (val p = pending) {
        null -> Unit
        is PendingAction.Share -> LeaveWarning(
            title = "مشاركة الملف خارج المجلد الخاص؟",
            text = "سيحصل التطبيق الذي تشارك الملف معه على نسخة خاصة به. ويمكنه الاحتفاظ بها، ولن تكون هذه النسخة محمية بواسطة بحمد IDM.",
            confirm = "مشاركة",
            onDismiss = { pending = null },
        ) { pending = null; FileActions.share(context, vm.container, p.d) }
        is PendingAction.OpenElsewhere -> LeaveWarning(
            title = "فتح الملف في تطبيق آخر؟",
            text = "يمكن للتطبيق الآخر قراءة هذا الملف، وقد يحتفظ بنسخة منه أو بصورة مصغرة له.",
            confirm = "فتح",
            onDismiss = { pending = null },
        ) { pending = null; FileActions.openWith(context, vm.container, p.d) }
        is PendingAction.MoveOut -> LeaveWarning(
            title = "نقل الملف خارج المجلد الخاص؟",
            text = "سيعود الملف إلى Download/XDM، حيث يمكن لمعرض الصور والتطبيقات الأخرى الوصول إليه.",
            confirm = "نقل",
            onDismiss = { pending = null },
        ) { pending = null; vm.moveOut(p.d) }
        is PendingAction.Rename -> RenameDialog(p.d.fileName, onDismiss = { pending = null }) { name -> pending = null; vm.rename(p.d, name) }
        is PendingAction.Delete -> AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(if (p.items.size == 1) "حذف هذا الملف؟" else "حذف ${p.items.size} عناصر؟") },
            text = {
                Text(
                    "سيتم حذف الملفات نهائياً من المجلد الخاص" +
                        p.items.sumOf { it.totalBytes.coerceAtLeast(0) }.takeIf { it > 0 }?.let { " (${Formatters.bytes(it)})" }.orEmpty() +
                        ". وسيتم إلغاء التنزيلات قيد التنفيذ.",
                )
            },
            confirmButton = {
                TextButton(onClick = { pending = null; vm.delete(p.items.map { it.id }) }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("احتفاظ بالملفات") } },
        )
        PendingAction.ChangePin -> ChangePinDialog(vm, onDone = { message ->
            pending = null
            message?.let(vm::say)
        })
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActiveRow(item: DownloadItem, vm: PrivateViewModel, selecting: Boolean, checked: Boolean, onDelete: () -> Unit) {
    val context = LocalContext.current
    val d = item.entity
    var menu by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().combinedClickable(
            onClick = { if (selecting) vm.toggleSelected(d.id) },
            onLongClick = { if (!selecting) vm.toggleSelected(d.id) },
        ),
        colors = if (checked) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.cardColors(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selecting) Checkbox(checked = checked, onCheckedChange = { vm.toggleSelected(d.id) })
            Column(Modifier.weight(1f)) {
                Text(d.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                val fraction = item.fraction
                when {
                    fraction != null -> LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    d.status == DownloadStatus.RUNNING -> LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(4.dp))
                Text(sizeLine(item), style = MaterialTheme.typography.bodySmall)
                Text(
                    statusLine(item, QueueStatus(null, null)),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (d.status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!selecting) {
                when (d.status) {
                    DownloadStatus.RUNNING, DownloadStatus.QUEUED -> IconButton(onClick = { vm.pause(d.id) }) { Icon(Icons.Filled.Pause, "إيقاف مؤقت") }
                    DownloadStatus.PAUSED -> IconButton(onClick = { vm.resume(d.id) }) { Icon(Icons.Filled.PlayArrow, "استئناف") }
                    else -> IconButton(onClick = { vm.resume(d.id) }) { Icon(Icons.Filled.Refresh, "إعادة المحاولة") }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "المزيد") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (d.status != DownloadStatus.FAILED && d.status != DownloadStatus.CANCELLED) {
                            DropdownMenuItem(
                                text = { Text("إلغاء التنزيل") },
                                leadingIcon = { Icon(Icons.Filled.Close, null) },
                                onClick = { menu = false; vm.cancel(d.id) },
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
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    d: DownloadEntity,
    selecting: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAction: (PendingAction) -> Unit,
    onOpen: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val duration = if (MediaFiles.isMedia(d)) mediaDuration(d) else -1
    Card(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = if (checked) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.cardColors(),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selecting) Checkbox(checked = checked, onCheckedChange = { onClick() })
            MediaThumbnail(d, Modifier.size(width = 96.dp, height = 54.dp)) {
                Box(Modifier.size(width = 96.dp, height = 54.dp), contentAlignment = Alignment.Center) { FileTypeIcon(d.fileName, d.mimeType) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(d.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(Formatters.bytes(d.totalBytes), duration.takeIf { it > 0 }?.let { MediaFiles.formatTime(it) })
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!selecting) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "المزيد") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("فتح باستخدام...") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                            onClick = { menu = false; onOpen() },
                        )
                        DropdownMenuItem(
                            text = { Text("نقل خارج المجلد الخاص") },
                            leadingIcon = { Icon(Icons.Filled.LockOpen, null) },
                            onClick = { menu = false; onAction(PendingAction.MoveOut(d)) },
                        )
                        DropdownMenuItem(
                            text = { Text("إعادة تسمية") },
                            leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            onClick = { menu = false; onAction(PendingAction.Rename(d)) },
                        )
                        DropdownMenuItem(
                            text = { Text("مشاركة") },
                            leadingIcon = { Icon(Icons.Filled.Share, null) },
                            onClick = { menu = false; onAction(PendingAction.Share(d)) },
                        )
                        DropdownMenuItem(
                            text = { Text("حذف") },
                            leadingIcon = { Icon(Icons.Filled.Delete, null) },
                            onClick = { menu = false; onAction(PendingAction.Delete(listOf(d))) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaveWarning(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إعادة تسمية الملف") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank() && name.trim() != current) { Text("إعادة تسمية") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun ChangePinDialog(vm: PrivateViewModel, onDone: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text("تغيير الرقم السري") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PinField(old, { old = it; error = null }, "الرقم السري الحالي", isError = error != null)
                PinField(new, { new = it }, "الرقم السري الجديد (من 4 إلى 8 أرقام)")
                PinField(repeat, { repeat = it }, "أعد إدخال الرقم السري الجديد", isError = repeat.isNotEmpty() && repeat != new)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && old.length >= 4 && PinHasher.isValidPin(new) && new == repeat,
                onClick = {
                    busy = true
                    scope.launch {
                        when (val r = vm.changePin(old, new)) {
                            UnlockResult.Ok -> onDone("تم تغيير الرقم السري")
                            is UnlockResult.CoolingDown -> error = "محاولات خاطئة كثيرة. حاول مجدداً بعد ${(r.remainingMs + 999) / 1000} ثانية."
                            is UnlockResult.Wrong -> error = "الرقم السري الحالي غير صحيح"
                            UnlockResult.NoPin -> onDone(null)
                        }
                        busy = false
                    }
                },
            ) { Text("تغيير") }
        },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("إلغاء") } },
    )
}
