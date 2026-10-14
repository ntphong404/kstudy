package vn.edu.actvn.kstudy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvParserTest {

    @Test
    fun parse_splitsLinesIntoCells() {
        val rows = CsvParser.parse("Họ và tên,Mã sinh viên,Lớp\nNguyễn Văn An,AT180101,AT18A\n")

        assertEquals(
            listOf(
                listOf("Họ và tên", "Mã sinh viên", "Lớp"),
                listOf("Nguyễn Văn An", "AT180101", "AT18A")
            ),
            rows
        )
    }

    @Test
    fun parse_acceptsEveryKindOfLineBreak() {
        val expected = listOf(listOf("a", "b"), listOf("c", "d"), listOf("e", "f"))

        assertEquals("Windows", expected, CsvParser.parse("a,b\r\nc,d\r\ne,f\r\n"))
        assertEquals("Unix", expected, CsvParser.parse("a,b\nc,d\ne,f"))
        assertEquals("old Mac", expected, CsvParser.parse("a,b\rc,d\re,f"))
    }

    @Test
    fun parse_semicolonFile_asExcelWritesItInVietnam() {
        val rows = CsvParser.parse("Họ và tên;Mã sinh viên;Lớp\nTrần Thị Bình;AT180102;AT18A")

        assertEquals(listOf("Trần Thị Bình", "AT180102", "AT18A"), rows[1])
    }

    @Test
    fun parse_tabSeparatedText_asCopiedFromASpreadsheet() {
        val rows = CsvParser.parse("Họ và tên\tMã sinh viên\nLê Văn Cường\tAT180103")

        assertEquals(listOf("Lê Văn Cường", "AT180103"), rows[1])
    }

    @Test
    fun parse_quotedCell_mayHoldTheDelimiterQuotesAndLineBreaks() {
        val rows = CsvParser.parse("name,address\n\"Phạm \"\"Tí\"\" Dũng\",\"Số 1, ngõ 2\nHà Đông, Hà Nội\"\n")

        assertEquals(listOf("Phạm \"Tí\" Dũng", "Số 1, ngõ 2\nHà Đông, Hà Nội"), rows[1])
        assertEquals(2, rows.size)
    }

    @Test
    fun parse_delimiterInsideQuotes_doesNotDecideTheDelimiter() {
        val rows = CsvParser.parse("\"a,b,c,d\";x\n1;2")

        assertEquals(listOf(listOf("a,b,c,d", "x"), listOf("1", "2")), rows)
    }

    @Test
    fun parse_quoteInTheMiddleOfACell_isPlainText() {
        assertEquals(listOf(listOf("5\" màn hình", "x")), CsvParser.parse("5\" màn hình,x"))
    }

    @Test
    fun parse_keepsEmptyCellsAndEmptyLines_soRowsKeepTheirLineNumbers() {
        val rows = CsvParser.parse("a,,c\n\n,,\nd,e,")

        assertEquals(
            listOf(listOf("a", "", "c"), listOf(""), listOf("", "", ""), listOf("d", "e", "")),
            rows
        )
    }

    @Test
    fun parse_dropsTheByteOrderMark() {
        assertEquals(listOf(listOf("Họ và tên", "Lớp")), CsvParser.parse("﻿Họ và tên,Lớp"))
    }

    @Test
    fun parse_emptyText_hasNoRows() {
        assertTrue(CsvParser.parse("").isEmpty())
        assertTrue(CsvParser.parse("﻿").isEmpty())
    }

    @Test
    fun detectDelimiter_looksPastLinesWithoutAny() {
        assertEquals(';', CsvParser.detectDelimiter("DANH SÁCH LỚP AT18A\n\nHọ và tên;Lớp"))
        assertEquals("a single column", ',', CsvParser.detectDelimiter("Họ và tên\nAn"))
    }
}
