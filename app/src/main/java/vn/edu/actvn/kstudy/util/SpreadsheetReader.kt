package vn.edu.actvn.kstudy.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

// The message is written for the admin and can be shown as is
class ImportFileException(message: String) : Exception(message)

// Turns a file the admin picked into rows of cells, whatever the kind of spreadsheet. The kind
// is told from the content: the name and the type a file manager reports are not reliable.
object SpreadsheetReader {

    const val MAX_FILE_BYTES = 5 * 1024 * 1024

    const val EMPTY_FILE = "File không có nội dung."
    const val TOO_LARGE = "File quá lớn (tối đa 5 MB)."
    const val OLD_EXCEL = "File .xls đời cũ hoặc file có mật khẩu chưa được hỗ trợ. " +
        "Hãy mở file và lưu lại dạng .xlsx hoặc .csv."
    const val BROKEN_EXCEL = "Không đọc được file Excel. File có thể đã bị hỏng."
    const val NOT_A_SPREADSHEET = "File không phải Excel (.xlsx) hay CSV."

    fun read(input: InputStream): List<List<String>> = read(readLimited(input))

    fun read(bytes: ByteArray): List<List<String>> = when {
        bytes.isEmpty() -> throw ImportFileException(EMPTY_FILE)
        bytes.startsWith(0x50, 0x4B, 0x03, 0x04) -> try {
            XlsxReader.read(ByteArrayInputStream(bytes))
        } catch (e: Exception) {
            throw ImportFileException(BROKEN_EXCEL)
        }
        // The container of Excel 97-2003 workbooks; an .xlsx with a password is wrapped in one too
        bytes.startsWith(0xD0, 0xCF, 0x11, 0xE0) -> throw ImportFileException(OLD_EXCEL)
        else -> {
            val text = decode(bytes)
            // Text never holds a NUL character; a picture or a PDF does
            if (text.contains('\u0000')) throw ImportFileException(NOT_A_SPREADSHEET)
            CsvParser.parse(text)
        }
    }

    // Excel saves "CSV UTF-8" with a byte order mark and "Unicode text" as UTF-16. Its plain
    // "CSV" uses the code page of Windows, which for Vietnamese is windows-1258.
    internal fun decode(bytes: ByteArray): String = when {
        bytes.startsWith(0xEF, 0xBB, 0xBF) -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        bytes.startsWith(0xFF, 0xFE) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        bytes.startsWith(0xFE, 0xFF) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        else -> try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            String(bytes, windowsVietnamese())
        }
    }

    private fun windowsVietnamese(): Charset = try {
        Charset.forName("windows-1258")
    } catch (e: Exception) {
        Charsets.ISO_8859_1
    }

    private fun readLimited(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            out.write(buffer, 0, count)
            if (out.size() > MAX_FILE_BYTES) throw ImportFileException(TOO_LARGE)
        }
        return out.toByteArray()
    }

    private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it].toByte() }
}
