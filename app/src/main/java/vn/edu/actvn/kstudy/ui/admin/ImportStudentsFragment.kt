package vn.edu.actvn.kstudy.ui.admin

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.data.model.CourseClass
import vn.edu.actvn.kstudy.ui.adapter.ImportRowAdapter
import vn.edu.actvn.kstudy.ui.common.FormFields
import vn.edu.actvn.kstudy.ui.common.ReportSharer
import vn.edu.actvn.kstudy.util.ImportPreview
import vn.edu.actvn.kstudy.viewmodel.ExportedReport
import vn.edu.actvn.kstudy.viewmodel.ImportResult
import vn.edu.actvn.kstudy.viewmodel.ImportState
import vn.edu.actvn.kstudy.viewmodel.ImportStudentsViewModel
import vn.edu.actvn.kstudy.viewmodel.KstudyViewModelFactory

// Creates many student accounts at once: the admin picks an Excel or CSV file, checks the
// preview of every row and confirms
class ImportStudentsFragment : Fragment() {

    private lateinit var viewModel: ImportStudentsViewModel

    private lateinit var btnConfirm: Button
    private lateinit var actvClass: AutoCompleteTextView

    private var classes = listOf<CourseClass>()
    // The class to put the students into; null for none
    private var selectedClass: CourseClass? = null

    // The created accounts with their first passwords, while on screen
    private var accountsDialog: AlertDialog? = null

    // The system file picker needs no storage permission
    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) openFile(uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        saved: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_import_students, container, false)

        viewModel = ViewModelProvider(this, KstudyViewModelFactory)[ImportStudentsViewModel::class.java]

        val layoutChoose = view.findViewById<View>(R.id.layoutImportChoose)
        val layoutPreview = view.findViewById<View>(R.id.layoutImportPreview)
        val pbReading = view.findViewById<View>(R.id.pbImportReading)
        val tvError = view.findViewById<TextView>(R.id.tvImportError)
        val tvFileName = view.findViewById<TextView>(R.id.tvImportFileName)
        val tvSummary = view.findViewById<TextView>(R.id.tvImportSummary)
        val tilClass = view.findViewById<View>(R.id.tilImportClass)
        val btnOtherFile = view.findViewById<Button>(R.id.btnImportOtherFile)
        btnConfirm = view.findViewById(R.id.btnImportConfirm)
        actvClass = view.findViewById(R.id.actvImportClass)

        val rv = view.findViewById<RecyclerView>(R.id.rvImportRows)
        rv.layoutManager = LinearLayoutManager(requireContext())
        val adapter = ImportRowAdapter(emptyList())
        rv.adapter = adapter
        // The preview the list is showing: the state is published again while it is being saved
        var shownPreview: ImportPreview? = null

        view.findViewById<View>(R.id.btnBackImport).setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        view.findViewById<View>(R.id.btnChooseImportFile).setOnClickListener {
            pickFile.launch(FILE_TYPES)
        }
        view.findViewById<View>(R.id.btnImportTemplate).setOnClickListener {
            viewModel.exportTemplate()
        }
        btnOtherFile.setOnClickListener {
            viewModel.chooseAnotherFile()
        }
        btnConfirm.setOnClickListener {
            viewModel.importStudents(selectedClass?.id)
        }
        actvClass.setOnItemClickListener { _, _, position, _ ->
            // The first choice is "no class"
            selectedClass = classes.getOrNull(position - 1)
            showConfirmButton()
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            layoutChoose.visibility = if (state is ImportState.ChooseFile) View.VISIBLE else View.GONE
            layoutPreview.visibility = if (state is ImportState.Preview) View.VISIBLE else View.GONE
            pbReading.visibility = if (state is ImportState.Reading) View.VISIBLE else View.GONE
            when (state) {
                is ImportState.ChooseFile -> {
                    tvError.text = state.error
                    tvError.visibility = if (state.error == null) View.GONE else View.VISIBLE
                }
                is ImportState.Reading -> Unit
                is ImportState.Preview -> {
                    tvFileName.text = state.fileName
                    tvSummary.text = summary(state.preview)
                    if (state.preview !== shownPreview) {
                        shownPreview = state.preview
                        adapter.updateList(state.preview.rows)
                        // Another file starts at its first row, not where the last list was left
                        rv.scrollToPosition(0)
                    }
                    btnOtherFile.isEnabled = !state.saving
                    tilClass.isEnabled = !state.saving
                    showConfirmButton()
                }
            }
        }

        viewModel.classes.observe(viewLifecycleOwner) { list ->
            classes = list
            selectedClass = list.firstOrNull { it.id == selectedClass?.id }
            FormFields.setChoices(actvClass, listOf(NO_CLASS) + list.map { classLabel(it) })
            // Without filtering, otherwise the dropdown would only offer the choice it already shows
            actvClass.setText(selectedClass?.let { classLabel(it) } ?: NO_CLASS, false)
            showConfirmButton()
        }

        viewModel.finished.observe(viewLifecycleOwner) { result ->
            if (result == null) return@observe
            if (result.accounts.isEmpty()) {
                // Only students who already had an account were put into a class
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                parentFragmentManager.popBackStack()
            } else {
                showAccounts(result)
            }
        }

        viewModel.template.observe(viewLifecycleOwner) { template ->
            if (template != null) {
                viewModel.clearTemplate()
                showTemplateDialog(template)
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                viewModel.clearErrorMessage()
            }
        }

        viewModel.loadClasses()

        return view
    }

    private fun openFile(uri: Uri) {
        // The application's resolver, so that the ViewModel reading the file does not hold on to this screen
        val resolver = requireContext().applicationContext.contentResolver
        viewModel.openFile(fileName(resolver, uri)) { resolver.openInputStream(uri) }
    }

    private fun fileName(resolver: ContentResolver, uri: Uri): String {
        val name = try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
        return name ?: uri.lastPathSegment ?: "File đã chọn"
    }

    private fun summary(preview: ImportPreview): String {
        val parts = mutableListOf("${preview.rows.size} dòng", "${preview.newStudents.size} học sinh mới")
        if (preview.existingIds.isNotEmpty()) parts.add("${preview.existingIds.size} đã có")
        if (preview.invalidCount > 0) parts.add("${preview.invalidCount} lỗi")
        return parts.joinToString(" • ")
    }

    // What the button will do depends on the file and on the class chosen
    private fun showConfirmButton() {
        val state = viewModel.state.value as? ImportState.Preview ?: return
        val created = state.preview.newStudents.size
        val alreadyThere = state.preview.existingIds.size
        btnConfirm.text = when {
            state.saving -> "Đang nhập…"
            created == 0 && alreadyThere > 0 && selectedClass != null -> "Xếp $alreadyThere học sinh vào lớp"
            else -> "Nhập $created học sinh"
        }
        btnConfirm.isEnabled = !state.saving && (created > 0 || (alreadyThere > 0 && selectedClass != null))
    }

    private fun classLabel(courseClass: CourseClass): String =
        if (courseClass.academicYear.isBlank()) courseClass.name else "${courseClass.name} (${courseClass.academicYear})"

    // The first passwords exist nowhere else (the database keeps only their hash), so the dialog
    // cannot be dismissed by accident and sharing the file leaves it open
    private fun showAccounts(result: ImportResult) {
        accountsDialog?.dismiss()
        val context = requireContext()
        val accounts = result.accounts.joinToString("\n") { "${it.username}  •  ${it.password}" }
        val builder = MaterialAlertDialogBuilder(context)
            .setTitle("Đã tạo ${result.accounts.size} tài khoản")
            .setMessage(
                "${result.message}\n\n" +
                    "Tên đăng nhập và mật khẩu ban đầu (chỉ hiện một lần, hãy lưu lại để phát cho học sinh; " +
                    "học sinh phải đổi mật khẩu ở lần đăng nhập đầu tiên):\n\n$accounts"
            )
            .setCancelable(false)
            .setNegativeButton("Xong") { _, _ ->
                accountsDialog = null
                // The list of students loads again when it comes back to the front
                parentFragmentManager.popBackStack()
            }
        if (result.accountsFile != null) builder.setPositiveButton("Chia sẻ file Excel", null)
        val dialog = builder.show()
        accountsDialog = dialog
        val file = result.accountsFile ?: return
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                val uri = ReportSharer.save(context, file)
                ReportSharer.share(context, uri, "Danh sách tài khoản học sinh", "Chia sẻ danh sách tài khoản")
            } catch (e: Exception) {
                Toast.makeText(context, "Không lưu được file. Hãy chép lại danh sách trên màn hình.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        // A rotation shows the dialog again from the result kept by the ViewModel
        accountsDialog?.dismiss()
        accountsDialog = null
        super.onDestroyView()
    }

    private fun showTemplateDialog(template: ExportedReport) {
        val context = requireContext()
        val uri = try {
            ReportSharer.save(context, template)
        } catch (e: Exception) {
            Toast.makeText(context, "Không lưu được file mẫu!", Toast.LENGTH_LONG).show()
            return
        }
        MaterialAlertDialogBuilder(context)
            .setTitle("Đã tạo file mẫu")
            .setMessage("${template.fileName}\n\nGửi file sang máy tính, điền danh sách rồi chọn lại file tại đây.")
            .setPositiveButton("Chia sẻ") { _, _ ->
                ReportSharer.share(context, uri, "Mẫu danh sách học sinh", "Chia sẻ file mẫu")
            }
            .setNeutralButton("Mở file") { _, _ ->
                if (!ReportSharer.open(context, uri)) {
                    Toast.makeText(
                        context,
                        "Máy chưa có ứng dụng mở file Excel. Hãy dùng Chia sẻ để gửi file đi.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private companion object {
        const val NO_CLASS = "Không xếp lớp"

        // File managers do not agree on the type of a CSV file, so the plain ones are let through
        // as well; SpreadsheetReader tells what the file really is from its content
        val FILE_TYPES = arrayOf(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "text/csv",
            "text/comma-separated-values",
            "application/csv",
            "text/plain",
            "application/octet-stream"
        )
    }
}
