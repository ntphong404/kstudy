package vn.edu.actvn.kstudy.util

import vn.edu.actvn.kstudy.data.model.AttendanceStatus
import vn.edu.actvn.kstudy.data.model.ClassAttendanceData
import vn.edu.actvn.kstudy.data.model.CourseClass
import vn.edu.actvn.kstudy.data.model.Role

// Builds the text sent to the AI model. The model only knows what is written here,
// so every number it may quote has to come from Firestore through AttendanceAnalyzer.
object AiPromptBuilder {

    private const val EXCUSED_RULE =
        "\"Có phép\" là buổi vắng có đơn xin nghỉ đã được giáo viên duyệt: không tính là đi học, " +
            "nhưng cũng không tính vào số buổi \"vắng\" dùng để xét cảnh báo và cấm thi."

    fun chatSystemInstruction(role: Role, userName: String, today: String, data: String): String {
        val roleLabel = when (role) {
            Role.ADMIN -> "quản trị viên"
            Role.TEACHER -> "giáo viên"
            Role.STUDENT -> "học sinh"
        }
        return """
Bạn là "Trợ lý Kstudy", trợ lý AI trong ứng dụng điểm danh Kstudy.
Người đang trò chuyện với bạn là $roleLabel ${userName.ifBlank { "(chưa rõ tên)" }}. Hôm nay là $today.

QUY TẮC:
- Chỉ trả lời dựa trên phần DỮ LIỆU ĐIỂM DANH bên dưới. Nếu dữ liệu không có thông tin cần thiết thì nói rõ là chưa có dữ liệu, tuyệt đối không tự bịa số liệu.
- Trả lời bằng tiếng Việt, ngắn gọn, thân thiện và nêu con số cụ thể khi có.
- Chỉ dùng văn bản thuần, không dùng Markdown (không dùng dấu **, dấu #, bảng). Khi cần liệt kê thì gạch đầu dòng bằng dấu "-".
- Quy định chuyên cần: vắng quá ${AttendanceAnalyzer.BAN_THRESHOLD_PERCENT}% số buổi đã học của một lớp thì có nguy cơ bị cấm thi; vắng quá ${AttendanceAnalyzer.WARNING_THRESHOLD_PERCENT}% là mức cảnh báo. Đi muộn vẫn được tính là có đi học.
- $EXCUSED_RULE
- Số liệu chỉ tính các buổi đã chốt (phiên điểm danh đã đóng).
- Nếu câu hỏi không liên quan đến việc học, lịch học hay điểm danh thì lịch sự từ chối và gợi ý người dùng hỏi về chuyên cần.

DỮ LIỆU ĐIỂM DANH:
$data
""".trim()
    }

    // Only this student's own records are included, never the classmates'
    fun studentData(studentId: String, classes: List<ClassAttendanceData>): String {
        if (classes.isEmpty()) return "Học sinh chưa được xếp vào lớp học nào."
        val stats = AttendanceAnalyzer.analyzeStudent(studentId, classes)
        val sb = StringBuilder()
        sb.appendLine("Số lớp đang học: ${stats.size}")
        for (stat in stats) {
            sb.appendLine(classHeading(stat.courseClass))
            sb.appendLine("- ${classInfo(stat.courseClass, includeTeacher = true)}")
            if (stat.history.isEmpty()) {
                sb.appendLine("- Chưa có buổi điểm danh nào đã chốt.")
                continue
            }
            sb.appendLine(
                "- Số buổi đã chốt: ${stat.count.total}. ${countText(stat.count)}. " +
                    "Đánh giá: ${AttendanceAnalyzer.riskLabel(stat.risk)}."
            )
            val details = stat.history.joinToString("; ") { (session, status) ->
                "${session.date}: ${AttendanceStatus.label(status)}"
            }
            sb.appendLine("- Chi tiết từng buổi: $details")
        }
        return sb.toString().trim()
    }

    fun teacherData(classes: List<ClassAttendanceData>): String {
        if (classes.isEmpty()) return "Giáo viên chưa được phân công lớp học nào."
        val sb = StringBuilder()
        sb.appendLine("Số lớp đang dạy: ${classes.size}")
        for (data in classes.sortedBy { it.courseClass.name }) {
            sb.appendLine(classDetail(AttendanceAnalyzer.analyzeClass(data), includeTeacher = false))
        }
        return sb.toString().trim()
    }

    fun adminData(classes: List<ClassAttendanceData>): String {
        if (classes.isEmpty()) return "Hệ thống chưa có lớp học nào."
        val reports = classes.sortedBy { it.courseClass.name }.map { AttendanceAnalyzer.analyzeClass(it) }
        val overall = reports.fold(StatusCount()) { acc, report -> acc + report.overall }
        val sb = StringBuilder()
        sb.appendLine("Toàn hệ thống: ${reports.size} lớp học. ${countText(overall)}.")
        for (report in reports) {
            sb.appendLine(classHeading(report.courseClass))
            sb.appendLine(
                "- ${classInfo(report.courseClass, includeTeacher = true)}; " +
                    "Sĩ số: ${report.students.size}; Số buổi đã chốt: ${report.sessions.size}"
            )
            sb.appendLine("- Tổng hợp: ${countText(report.overall)}.")
            sb.appendLine("- Học sinh cần lưu ý: ${atRiskText(report)}")
        }
        return sb.toString().trim()
    }

    fun classAnalysisInstruction(): String = """
Bạn là trợ lý phân tích chuyên cần của ứng dụng điểm danh Kstudy, hỗ trợ giáo viên.
- Chỉ dùng số liệu trong dữ liệu được cung cấp, không tự bịa số liệu.
- Viết bằng tiếng Việt, văn bản thuần, không dùng Markdown (không dùng dấu **, dấu #, bảng).
- Quy định: vắng quá ${AttendanceAnalyzer.BAN_THRESHOLD_PERCENT}% số buổi thì có nguy cơ bị cấm thi; vắng quá ${AttendanceAnalyzer.WARNING_THRESHOLD_PERCENT}% là mức cảnh báo. Đi muộn vẫn tính là có đi học.
- $EXCUSED_RULE
""".trim()

    fun classAnalysisPrompt(report: ClassReport): String = """
Hãy nhận xét tình hình chuyên cần của lớp dưới đây, tối đa khoảng 150 từ, gồm đúng 3 phần:
1. Tổng quan: tỷ lệ đi học chung và xu hướng qua các buổi.
2. Học sinh cần lưu ý: nêu tên, mã và số buổi vắng (nếu không có thì nói là không có).
3. Đề xuất: 2 đến 3 việc giáo viên nên làm.

${classDetail(report, includeTeacher = false)}
""".trim()

    // The chat bubbles are plain TextViews, so strip the Markdown a model may still produce
    fun cleanReply(text: String): String {
        return text
            .replace("**", "")
            .replace("__", "")
            .replace("`", "")
            .replace(Regex("(?m)^[ \\t]{0,3}#{1,6}[ \\t]*"), "")
            .replace(Regex("(?m)^([ \\t]*)[*•][ \\t]+"), "$1- ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun classDetail(report: ClassReport, includeTeacher: Boolean): String {
        val sb = StringBuilder()
        sb.appendLine(classHeading(report.courseClass))
        sb.appendLine(
            "- ${classInfo(report.courseClass, includeTeacher)}; " +
                "Sĩ số: ${report.students.size}; Số buổi đã chốt: ${report.sessions.size}"
        )
        if (report.sessions.isEmpty()) {
            sb.appendLine("- Chưa có buổi điểm danh nào đã chốt.")
            return sb.toString().trim()
        }
        sb.appendLine("- Tổng hợp cả lớp: ${countText(report.overall)}.")
        sb.appendLine("- Từng buổi:")
        for (stat in report.sessions) {
            val absent = namesWithStatus(report, stat.session.id, AttendanceStatus.ABSENT)
            val late = namesWithStatus(report, stat.session.id, AttendanceStatus.LATE)
            sb.appendLine("  + ${stat.session.date}: ${countText(stat.count)}. Vắng: $absent. Muộn: $late.")
        }
        sb.appendLine("- Từng học sinh:")
        for (stat in report.students) {
            sb.appendLine(
                "  + ${stat.student.studentCode} ${stat.student.name}: ${countText(stat.count)}; " +
                    "${AttendanceAnalyzer.riskLabel(stat.risk)}"
            )
        }
        return sb.toString().trim()
    }

    private fun classHeading(courseClass: CourseClass): String {
        val year = if (courseClass.academicYear.isBlank()) "" else " (năm học ${courseClass.academicYear})"
        return "Lớp \"${courseClass.name}\"$year"
    }

    private fun classInfo(courseClass: CourseClass, includeTeacher: Boolean): String {
        val parts = ArrayList<String>()
        if (includeTeacher) parts.add("Giáo viên: ${courseClass.teacherName.ifBlank { "chưa rõ" }}")
        parts.add("Phòng: ${courseClass.room.ifBlank { "chưa rõ" }}")
        parts.add("Lịch học: ${courseClass.schedule.ifBlank { "chưa có" }}")
        return parts.joinToString("; ")
    }

    private fun countText(count: StatusCount): String {
        val rate = count.attendanceRate?.let { "$it%" } ?: "chưa có dữ liệu"
        val excused = if (count.excused > 0) ", có phép ${count.excused}" else ""
        return "có mặt ${count.present}, muộn ${count.late}, vắng ${count.absent}$excused, tỷ lệ đi học $rate"
    }

    private fun namesWithStatus(report: ClassReport, sessionId: String, status: String): String {
        val names = report.students
            .filter { report.statusOf(it.student.id, sessionId) == status }
            .map { it.student.name }
        return if (names.isEmpty()) "không có" else names.joinToString(", ")
    }

    private fun atRiskText(report: ClassReport): String {
        val atRisk = report.atRisk
        if (atRisk.isEmpty()) return "không có."
        return atRisk.joinToString("; ") { stat ->
            "${stat.student.name} (${stat.student.studentCode}) " +
                "${AttendanceAnalyzer.absenceSummary(stat.count)} - ${AttendanceAnalyzer.riskLabel(stat.risk)}"
        }
    }
}
