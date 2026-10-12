package vn.edu.actvn.kstudy.ui.common

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import vn.edu.actvn.kstudy.util.AttendanceReportBuilder
import vn.edu.actvn.kstudy.viewmodel.ExportedReport
import java.io.File

// Saves an exported Excel report in the app cache and hands it to other apps through FileProvider,
// which needs no storage permission.
object ReportSharer {

    fun save(context: Context, report: ExportedReport): Uri {
        val directory = File(context.cacheDir, "reports")
        directory.mkdirs()
        val file = File(directory, report.fileName)
        file.writeBytes(report.bytes)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    // Returns false when no app on the device can open .xlsx files
    fun open(context: Context, uri: Uri): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, AttendanceReportBuilder.MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    fun share(context: Context, uri: Uri, subject: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = AttendanceReportBuilder.MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            // The chooser preview needs the grant through ClipData as well
            clipData = ClipData.newRawUri(subject, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Chia sẻ báo cáo điểm danh"))
    }
}
