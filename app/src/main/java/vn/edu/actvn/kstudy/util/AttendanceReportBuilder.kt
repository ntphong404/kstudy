package vn.edu.actvn.kstudy.util

import vn.edu.actvn.kstudy.data.model.AttendanceStatus
import java.text.Normalizer

// Turns a ClassReport into the sheets of the Excel file a teacher can export
object AttendanceReportBuilder {

    const val MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    fun build(report: ClassReport, exportedAt: String): List<XlsxSheet> =
        listOf(rosterSheet(report, exportedAt), sessionSheet(report))

    // e.g. "DiemDanh_Toan-cao-cap-A1_20261011_1530.xlsx"; ASCII only so every app can open the shared file
    fun fileName(className: String, timestamp: String): String {
        val slug = Normalizer.normalize(className, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
            .replace(Regex("[^A-Za-z0-9]+"), "-")
            .trim('-')
            .take(40)
            .trim('-')
            .ifEmpty { "Lop" }
        return "DiemDanh_${slug}_$timestamp.xlsx"
    }

    private fun rosterSheet(report: ClassReport, exportedAt: String): XlsxSheet {
        val courseClass = report.courseClass
        val rows = ArrayList<List<XlsxCell>>()

        rows.add(listOf(XlsxCell("BẢNG ĐIỂM DANH - ${courseClass.name}", style = XlsxStyle.TITLE)))
        rows.add(
            listOf(
                XlsxCell(
                    "Năm học: ${courseClass.academicYear.ifBlank { "-" }}   |   " +
                        "Phòng: ${courseClass.room.ifBlank { "-" }}   |   " +
                        "Lịch học: ${courseClass.schedule.ifBlank { "-" }}"
                )
            )
        )
        val teacher = if (courseClass.teacherName.isBlank()) "" else "Giáo viên: ${courseClass.teacherName}   |   "
        rows.add(
            listOf(
                XlsxCell(
                    teacher +
                        "Sĩ số: ${report.students.size}   |   " +
                        "Số buổi đã chốt: ${report.sessions.size}   |   " +
                        "Ngày xuất: $exportedAt   |   " +
                        "Điểm chuyên cần: thang ${AttendanceScore.format(AttendanceScore.MAX)}, " +
                        AttendanceScore.rule(courseClass.absencePenalty).replaceFirstChar { it.lowercase() }
                )
            )
        )
        rows.add(emptyList())

        val header = ArrayList<XlsxCell>()
        header.add(XlsxCell.text("STT", XlsxStyle.HEADER))
        header.add(XlsxCell.text("Mã SV", XlsxStyle.HEADER))
        header.add(XlsxCell.text("Họ và tên", XlsxStyle.HEADER))
        for (stat in report.sessions) {
            header.add(XlsxCell.text(SessionDates.dateOnly(stat.session.date), XlsxStyle.HEADER))
        }
        header.add(XlsxCell.text("Có mặt", XlsxStyle.HEADER))
        header.add(XlsxCell.text("Muộn", XlsxStyle.HEADER))
        header.add(XlsxCell.text("Vắng", XlsxStyle.HEADER))
        header.add(XlsxCell.text("Có phép", XlsxStyle.HEADER))
        header.add(XlsxCell.text("Chuyên cần (%)", XlsxStyle.HEADER))
        header.add(XlsxCell.text("Điểm chuyên cần", XlsxStyle.HEADER))
        header.add(XlsxCell.text("Ghi chú", XlsxStyle.HEADER))
        rows.add(header)

        report.students.forEachIndexed { index, stat ->
            val row = ArrayList<XlsxCell>()
            row.add(XlsxCell.number(index + 1))
            row.add(XlsxCell.text(stat.student.studentCode, XlsxStyle.BODY_CENTER))
            row.add(XlsxCell.text(stat.student.name))
            for (sessionStat in report.sessions) {
                row.add(statusCell(report.statusOf(stat.student.id, sessionStat.session.id)))
            }
            row.add(XlsxCell.number(stat.count.present))
            row.add(XlsxCell.number(stat.count.late))
            row.add(XlsxCell.number(stat.count.absent))
            row.add(XlsxCell.number(stat.count.excused))
            row.add(rateCell(stat.count))
            // A number, so the column can be averaged or sorted in the spreadsheet
            row.add(XlsxCell(number = report.scoreOf(stat), style = XlsxStyle.BODY_CENTER))
            row.add(
                when (stat.risk) {
                    RiskLevel.DANGER -> XlsxCell.text("Nguy cơ cấm thi", XlsxStyle.DANGER)
                    RiskLevel.WARNING -> XlsxCell.text("Cảnh báo", XlsxStyle.WARNING)
                    RiskLevel.SAFE -> XlsxCell.text("", XlsxStyle.BODY_CENTER)
                }
            )
            rows.add(row)
        }

        if (report.sessions.isNotEmpty()) {
            val footer = ArrayList<XlsxCell>()
            footer.add(XlsxCell.text("", XlsxStyle.HEADER))
            footer.add(XlsxCell.text("", XlsxStyle.HEADER))
            footer.add(XlsxCell.text("Tỷ lệ đi học theo buổi (%)", XlsxStyle.HEADER))
            for (sessionStat in report.sessions) {
                footer.add(rateCell(sessionStat.count))
            }
            rows.add(footer)
        }

        val widths = listOf(6, 12, 28) + report.sessions.map { 13 } + listOf(9, 9, 9, 9, 15, 16, 18)
        return XlsxSheet("Điểm danh", rows, widths)
    }

    private fun sessionSheet(report: ClassReport): XlsxSheet {
        val rows = ArrayList<List<XlsxCell>>()
        rows.add(listOf(XlsxCell("TỔNG HỢP THEO BUỔI - ${report.courseClass.name}", style = XlsxStyle.TITLE)))
        rows.add(emptyList())
        rows.add(
            listOf("STT", "Ngày điểm danh", "Có mặt", "Muộn", "Vắng", "Có phép", "Tỷ lệ đi học (%)")
                .map { XlsxCell.text(it, XlsxStyle.HEADER) }
        )
        report.sessions.forEachIndexed { index, stat ->
            rows.add(
                listOf(
                    XlsxCell.number(index + 1),
                    XlsxCell.text(stat.session.date, XlsxStyle.BODY_CENTER),
                    XlsxCell.number(stat.count.present),
                    XlsxCell.number(stat.count.late),
                    XlsxCell.number(stat.count.absent),
                    XlsxCell.number(stat.count.excused),
                    rateCell(stat.count)
                )
            )
        }
        return XlsxSheet("Theo buổi", rows, listOf(6, 20, 10, 10, 10, 10, 18))
    }

    private fun statusCell(status: String?): XlsxCell = when (status) {
        AttendanceStatus.PRESENT -> XlsxCell.text(AttendanceStatus.label(status), XlsxStyle.PRESENT)
        AttendanceStatus.LATE -> XlsxCell.text(AttendanceStatus.label(status), XlsxStyle.LATE)
        AttendanceStatus.ABSENT -> XlsxCell.text(AttendanceStatus.label(status), XlsxStyle.ABSENT)
        AttendanceStatus.EXCUSED -> XlsxCell.text(AttendanceStatus.label(status), XlsxStyle.EXCUSED)
        // The student joined the class after this session, so there is no record
        else -> XlsxCell.text("-", XlsxStyle.BODY_CENTER)
    }

    private fun rateCell(count: StatusCount): XlsxCell {
        val rate = count.attendanceRate
        return if (rate != null) XlsxCell.number(rate) else XlsxCell.text("-", XlsxStyle.BODY_CENTER)
    }
}
