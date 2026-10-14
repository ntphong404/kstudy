package vn.edu.actvn.kstudy.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.edu.actvn.kstudy.data.model.CourseClass
import vn.edu.actvn.kstudy.data.repository.StudentImportStore
import vn.edu.actvn.kstudy.util.ImportFileException
import vn.edu.actvn.kstudy.util.ImportPreview
import vn.edu.actvn.kstudy.util.ImportedStudent
import vn.edu.actvn.kstudy.util.PasswordRules
import vn.edu.actvn.kstudy.util.SpreadsheetReader
import vn.edu.actvn.kstudy.util.StudentImport
import vn.edu.actvn.kstudy.util.XlsxWriter
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class ImportState {
    // No file yet; `error` says why the last one could not be used
    data class ChooseFile(val error: String? = null) : ImportState()
    object Reading : ImportState()
    // The rows of the file, checked; `saving` while the import is being written
    data class Preview(val fileName: String, val preview: ImportPreview, val saving: Boolean = false) : ImportState()
}

// What a saved import leaves to show once: the summary, and the first password of every
// account created, which nobody can read again afterwards
class ImportResult(
    val message: String,
    val accounts: List<ImportedStudent>,
    val accountsFile: ExportedReport? // the same accounts as an Excel file to share; null when none
)

// Creates many student accounts at once from an Excel or CSV file: read, check, show, save
class ImportStudentsViewModel(
    private val store: StudentImportStore,
    private val worker: CoroutineDispatcher = Dispatchers.Default,
    private val newPassword: () -> String = { PasswordRules.generate() },
    private val now: () -> Date = { Date() }
) : ViewModel() {

    private val _state = MutableLiveData<ImportState>(ImportState.ChooseFile())
    val state: LiveData<ImportState> = _state

    // The classes the new students can be put into straight away
    private val _classes = MutableLiveData<List<CourseClass>>(emptyList())
    val classes: LiveData<List<CourseClass>> = _classes

    // Set once the import has been saved; the screen shows it and then closes
    private val _finished = MutableLiveData<ImportResult?>()
    val finished: LiveData<ImportResult?> = _finished

    private val _template = MutableLiveData<ExportedReport?>()
    val template: LiveData<ExportedReport?> = _template

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun loadClasses() {
        viewModelScope.launch {
            try {
                _classes.value = store.getClasses().sortedBy { it.name }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The import still works, only without putting the students into a class
                _classes.value = emptyList()
            }
        }
    }

    // open: gives the content of the file the admin picked, null when it cannot be opened
    fun openFile(fileName: String, open: () -> InputStream?) {
        if (_state.value == ImportState.Reading) return
        _state.value = ImportState.Reading
        viewModelScope.launch {
            _state.value = try {
                val rows = withContext(worker) {
                    val input = open() ?: throw ImportFileException(CANNOT_OPEN)
                    input.use { SpreadsheetReader.read(it) }
                }
                val existing = try {
                    store.getExistingAccounts()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw ImportFileException(CANNOT_CHECK)
                }
                ImportState.Preview(fileName, withContext(worker) { StudentImport.preview(rows, existing) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImportFileException) {
                ImportState.ChooseFile(e.message)
            } catch (e: Exception) {
                ImportState.ChooseFile(CANNOT_OPEN)
            }
        }
    }

    fun chooseAnotherFile() {
        if ((_state.value as? ImportState.Preview)?.saving == true) return
        _state.value = ImportState.ChooseFile()
    }

    // classId: the class to put the students into, null for none. Students of the file who
    // already have an account are not created again, but they are put into the class as well.
    fun importStudents(classId: String?) {
        val current = _state.value as? ImportState.Preview ?: return
        if (current.saving) return
        // Made now and not in the preview: choosing another file must not show passwords that
        // never get saved
        val students = current.preview.newStudents.map {
            if (it.password.isEmpty()) it.copy(password = newPassword()) else it
        }
        val alsoEnrollIds = if (classId != null) current.preview.existingIds else emptyList()
        if (students.isEmpty() && alsoEnrollIds.isEmpty()) {
            _errorMessage.value = NOTHING_TO_IMPORT
            return
        }
        _state.value = current.copy(saving = true)
        viewModelScope.launch {
            try {
                store.importStudents(students, classId, alsoEnrollIds)
                _finished.value = ImportResult(
                    message = summary(students.size, classId, students.size + alsoEnrollIds.size),
                    accounts = students,
                    accountsFile = if (students.isEmpty()) null else accountsFile(students)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = current
                _errorMessage.value = IMPORT_FAILED
            }
        }
    }

    fun exportTemplate() {
        viewModelScope.launch {
            try {
                val bytes = withContext(worker) {
                    val out = ByteArrayOutputStream()
                    XlsxWriter.write(StudentImport.templateSheets(), out)
                    out.toByteArray()
                }
                _template.value = ExportedReport(StudentImport.TEMPLATE_FILE_NAME, bytes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = "Không tạo được file mẫu!"
            }
        }
    }

    // The screen calls these after handling a one-shot event, so it is not replayed on rotation
    fun clearTemplate() {
        _template.value = null
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    // The accounts are saved by now: a file that cannot be written must not turn that into a
    // failure, the passwords are still on screen
    private suspend fun accountsFile(accounts: List<ImportedStudent>): ExportedReport? = try {
        val date = now()
        val createdAt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(date)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(date)
        val bytes = withContext(worker) {
            val out = ByteArrayOutputStream()
            XlsxWriter.write(StudentImport.accountSheets(accounts, createdAt), out)
            out.toByteArray()
        }
        ExportedReport(StudentImport.accountsFileName(timestamp), bytes)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun summary(created: Int, classId: String?, enrolled: Int): String {
        val className = _classes.value.orEmpty().firstOrNull { it.id == classId }?.name
        val parts = mutableListOf<String>()
        if (created > 0) parts.add("Đã nhập $created học sinh.")
        if (classId != null && enrolled > 0) {
            parts.add("Đã xếp $enrolled học sinh vào lớp${className?.let { " $it" }.orEmpty()}.")
        }
        return parts.joinToString(" ")
    }

    companion object {
        const val CANNOT_OPEN = "Không mở được file đã chọn."
        const val CANNOT_CHECK = "Không kiểm tra được các tài khoản đã có. Vui lòng kiểm tra kết nối mạng!"
        const val NOTHING_TO_IMPORT = "Không có học sinh nào để nhập."
        const val IMPORT_FAILED = "Nhập thất bại. Vui lòng kiểm tra kết nối mạng và thử lại!"
    }
}
