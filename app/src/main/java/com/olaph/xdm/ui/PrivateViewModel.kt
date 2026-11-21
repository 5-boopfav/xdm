package com.olaph.xdm.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.olaph.xdm.AppContainer
import com.olaph.xdm.XdmApp
import com.olaph.xdm.core.MoveResult
import com.olaph.xdm.data.DownloadEntity
import com.olaph.xdm.vault.AttemptState
import com.olaph.xdm.vault.UnlockResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * شاشة المجلد الخاص:
 * القفل، والملفات الموجودة فيه (المكتملة وقيد التنزيل)، والعمليات المتاحة عليها.
 */
class PrivateViewModel(app: Application) : AndroidViewModel(app) {

    val container: AppContainer = (app as XdmApp).container
    private val manager = container.manager
    private val lock = container.vaultLock

    val unlocked: StateFlow<Boolean> = lock.unlocked

    /** تكون القيمة فارغة حتى يتم تحديد حالة رمز PIN. */
    val pinSet: StateFlow<Boolean?> =
        lock.pinSet
            .map<Boolean, Boolean?> { it }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val biometricEnabled: StateFlow<Boolean> =
        lock.biometricEnabled.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            false
        )

    val attempts: StateFlow<AttemptState> =
        lock.attempts.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            AttemptState()
        )

    /**
     * لا تتم قراءة أي بيانات خاصة من قاعدة البيانات
     * ما دام المجلد مقفلاً.
     */
    val items: StateFlow<List<DownloadItem>> =
        combine(container.dao.observeAll(), manager.live, lock.unlocked) {
                list, live, open ->

            if (!open) {
                emptyList()
            } else {
                list.filter { it.isPrivate }
                    .map { DownloadItem(it, live[it.id]) }
            }

        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    /** عرض رسالة في الشريط السفلي للإشعارات داخل الشاشة. */
    fun say(text: String) {
        viewModelScope.launch {
            _messages.send(text)
        }
    }

    // ---- القفل والحماية ----

    suspend fun setupPin(pin: String): Boolean =
        lock.setPin(pin)

    suspend fun unlock(pin: String): UnlockResult =
        lock.tryUnlock(pin)

    fun unlockWithBiometric() =
        lock.unlockWithBiometric()

    fun lockNow() {
        clearSelection()
        lock.lock()
    }

    suspend fun changePin(old: String, new: String): UnlockResult =
        lock.changePin(old, new)

    fun setBiometricEnabled(enabled: Boolean) =
        viewModelScope.launch {
            lock.setBiometricEnabled(enabled)
        }

    /**
     * إعادة ضبط المجلد الخاص عند نسيان رمز PIN.
     *
     * سيتم حذف جميع التنزيلات الخاصة، حتى التي لا تزال قيد التنفيذ،
     * وجميع الملفات الخاصة ورمز PIN.
     *
     * لا يمكن التراجع عن هذه العملية.
     */
    fun reset() {
        clearSelection()

        container.scope.launch {

            manager.deleteMany(
                container.dao.privateIds(),
                deleteFiles = true,
                privateItems = true
            )

            container.vault.wipe()

            Thumbnails.forgetPrivate(getApplication())

            lock.clear()

            _messages.send("تمت إعادة ضبط المجلد الخاص")
        }
    }

    // ---- إدارة الملفات ----

    private val _selected = MutableStateFlow<Set<Long>>(emptySet())
    val selected: StateFlow<Set<Long>> = _selected

    /** تحديد ملف أو إلغاء تحديده. */
    fun toggleSelected(id: Long) =
        _selected.update {
            if (id in it) it - id else it + id
        }

    /** تحديد جميع الملفات. */
    fun selectAll(ids: Collection<Long>) {
        _selected.value = ids.toSet()
    }

    /** إلغاء تحديد جميع الملفات. */
    fun clearSelection() {
        _selected.value = emptySet()
    }

    // ---- التحكم في التنزيلات ----

    fun pause(id: Long) =
        manager.pause(id)

    fun resume(id: Long) =
        manager.resume(id)

    fun cancel(id: Long) =
        manager.cancel(id)

    /**
     * حذف الملفات الخاصة فقط، بما في ذلك الملفات الفعلية.
     *
     * تُنفذ العملية ضمن نطاق التطبيق حتى لا تتوقف
     * عند مغادرة المستخدم للشاشة.
     */
    fun delete(ids: Collection<Long>) {

        _selected.update {
            it - ids.toSet()
        }

        container.scope.launch {

            val report = manager.deleteMany(
                ids,
                deleteFiles = true,
                privateItems = true
            )

            Thumbnails.forget(getApplication(), ids)

            _messages.send(
                DeleteScope.message(
                    report.removed,
                    report.freedBytes,
                    report.failed
                )
            )
        }
    }

    // ---- نقل الملفات إلى الخارج ----

    fun moveOut(d: DownloadEntity) {

        container.scope.launch {

            when (val r = manager.moveOutOfPrivate(d.id)) {

                is MoveResult.Moved -> {

                    Thumbnails.forget(
                        getApplication(),
                        listOf(d.id)
                    )

                    _messages.send(
                        "تم نقل الملف إلى Download/XDM: ${r.fileName}"
                    )
                }

                is MoveResult.Failed -> {

                    _messages.send(
                        "تعذر نقل الملف (${r.reason}). سيبقى داخل المجلد الخاص."
                    )
                }

                is MoveResult.PublicCopyLeft -> Unit
                // تحدث هذه الحالة فقط عند نقل ملف إلى المجلد الخاص.
            }
        }
    }

    // ---- إعادة تسمية الملفات ----

    fun rename(d: DownloadEntity, name: String) {

        viewModelScope.launch {

            val newName = manager.renamePrivate(d.id, name)

            if (newName == null) {

                say("تعذرت إعادة تسمية الملف")

            } else {

                Thumbnails.forget(
                    getApplication(),
                    listOf(d.id)
                )
            }
        }
    }
}
