package com.olaph.xdm.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.olaph.xdm.AppContainer
import com.olaph.xdm.data.DownloadEntity
import com.olaph.xdm.engine.MimeTypes

object FileActions {
    private fun mimeOf(d: DownloadEntity): String =
        MimeTypes.forFileName(d.fileName) ?: d.mimeType?.takeIf { it != MimeTypes.OCTET_STREAM } ?: "*/*"

    fun open(context: Context, container: AppContainer, d: DownloadEntity, chooser: Boolean = false) {
        val location = d.savedLocation
        if (location == null || !container.fileSaver.exists(location)) {
            Toast.makeText(context, "الملف غير موجود (تم نقله أو حذفه)", Toast.LENGTH_SHORT).show()
            return
        }
        val mime = mimeOf(d)
        if (mime == MimeTypes.APK && !context.packageManager.canRequestPackageInstalls()) {
            // يطلب Android الإذن مرة واحدة لكل تطبيق مصدر.
            // انتقل إلى إعداد السماح لـ XDM بتثبيت التطبيقات، ثم اضغط على فتح مرة أخرى.
            Toast.makeText(context, "اسمح لتطبيق XDM بتثبيت التطبيقات، ثم اضغط على فتح مجدداً", Toast.LENGTH_LONG).show()
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(settings)
                return
            } catch (_: ActivityNotFoundException) {
                // بعض الأنظمة تخفي صفحة الإعدادات الخاصة بالتطبيق؛ دع أداة التثبيت تعرض رسالة التوضيح.
            }
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(container.fileSaver.uriFor(location), mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(if (chooser) Intent.createChooser(intent, "فتح باستخدام").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) else intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "لا يوجد تطبيق يمكنه فتح هذا الملف", Toast.LENGTH_SHORT).show()
        }
    }

    /** «فتح باستخدام…»: يعرض دائماً قائمة التطبيقات للاختيار بدلاً من استخدام التطبيق الافتراضي. */
    fun openWith(context: Context, container: AppContainer, d: DownloadEntity) = open(context, container, d, chooser = true)

    fun share(context: Context, container: AppContainer, d: DownloadEntity) {
        val location = d.savedLocation
        if (location == null || !container.fileSaver.exists(location)) {
            Toast.makeText(context, "الملف غير موجود (تم نقله أو حذفه)", Toast.LENGTH_SHORT).show()
            return
        }
        val send = Intent(Intent.ACTION_SEND)
            .setType(mimeOf(d))
            .putExtra(Intent.EXTRA_STREAM, container.fileSaver.uriFor(location))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "مشاركة ${d.fileName}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** يشارك جميع الملفات المكتملة الموجودة ضمن [items]، ويُرجع عدد الملفات التي تمت مشاركتها. */
    fun shareMany(context: Context, container: AppContainer, items: List<DownloadEntity>): Int {
        val uris = items.mapNotNull { d ->
            d.savedLocation?.takeIf { container.fileSaver.exists(it) }?.let { container.fileSaver.uriFor(it) }
        }
        if (uris.isEmpty()) {
            Toast.makeText(context, "لا توجد ملفات للمشاركة (تم نقلها أو حذفها)", Toast.LENGTH_SHORT).show()
            return 0
        }
        val send = Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType("*/*")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "مشاركة ${uris.size} ملفات").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return uris.size
    }

    fun copyLink(context: Context, url: String) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("URL", url))
        Toast.makeText(context, "تم نسخ الرابط", Toast.LENGTH_SHORT).show()
    }
}
