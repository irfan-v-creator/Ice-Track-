package com.example.service

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

sealed class XlsxCell {
  data class Text(val value: String, val isBold: Boolean = false) : XlsxCell()
  data class Currency(val paise: Long, val isBold: Boolean = false) : XlsxCell()
  data class WholeNumber(val value: Long, val isBold: Boolean = false) : XlsxCell()
}

data class XlsxSheet(
  val name: String,
  val titleBanner: String,
  val subtitleBanner: String,
  val headers: List<String>,
  val rows: List<List<XlsxCell>>,
  val footerRows: List<List<XlsxCell>> = emptyList()
)

/**
 * Pure Kotlin Office Open XML (.xlsx) Workbook generator using java.util.zip.ZipOutputStream.
 * Generates valid Excel / Google Sheets compatible spreadsheets with styling and multiple sheets.
 */
object XlsxWorkbookWriter {

  fun writeWorkbook(outputFile: File, sheets: List<XlsxSheet>) {
    require(sheets.isNotEmpty()) { "Workbook must contain at least one sheet" }
    outputFile.parentFile?.mkdirs()

    val sanitizedSheets = sheets.mapIndexed { idx, sheet ->
      val cleanName = sanitizeSheetName(sheet.name, idx)
      sheet.copy(name = cleanName)
    }

    ZipOutputStream(FileOutputStream(outputFile)).use { zip ->
      writeEntry(zip, "[Content_Types].xml", buildContentTypesXml(sanitizedSheets.size))
      writeEntry(zip, "_rels/.rels", buildRootRelsXml())
      writeEntry(zip, "xl/workbook.xml", buildWorkbookXml(sanitizedSheets))
      writeEntry(zip, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml(sanitizedSheets.size))
      writeEntry(zip, "xl/styles.xml", buildStylesXml())

      sanitizedSheets.forEachIndexed { index, sheet ->
        writeEntry(zip, "xl/worksheets/sheet${index + 1}.xml", buildWorksheetXml(sheet))
      }
    }
  }

  private fun writeEntry(zip: ZipOutputStream, entryName: String, content: String) {
    zip.putNextEntry(ZipEntry(entryName))
    zip.write(content.toByteArray(Charsets.UTF_8))
    zip.closeEntry()
  }

  private fun sanitizeSheetName(raw: String, index: Int): String {
    val cleaned = raw.replace(Regex("[\\\\/?*\\[\\]:]"), " ").trim()
    val nonEmpty = cleaned.ifEmpty { "Sheet${index + 1}" }
    return if (nonEmpty.length > 31) nonEmpty.take(31) else nonEmpty
  }

  private fun columnLetter(colIndexZeroBased: Int): String {
    var idx = colIndexZeroBased
    val sb = StringBuilder()
    while (idx >= 0) {
      val rem = idx % 26
      sb.append(('A'.code + rem).toChar())
      idx = (idx / 26) - 1
    }
    return sb.reverse().toString()
  }

  private fun escapeXml(input: String): String {
    val sb = StringBuilder(input.length)
    for (ch in input) {
      val code = ch.code
      // Skip illegal XML 1.0 control characters
      if (code < 0x20 && code != 0x09 && code != 0x0A && code != 0x0D) continue
      when (ch) {
        '&' -> sb.append("&amp;")
        '<' -> sb.append("&lt;")
        '>' -> sb.append("&gt;")
        '"' -> sb.append("&quot;")
        '\'' -> sb.append("&apos;")
        else -> sb.append(ch)
      }
    }
    return sb.toString()
  }

  private fun buildContentTypesXml(sheetCount: Int): String = buildString {
    append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
    append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
    append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
    append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
    append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
    append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
    for (i in 1..sheetCount) {
      append("<Override PartName=\"/xl/worksheets/sheet$i.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
    }
    append("</Types>")
  }

  private fun buildRootRelsXml(): String = buildString {
    append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
    append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
    append("<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>")
    append("</Relationships>")
  }

  private fun buildWorkbookXml(sheets: List<XlsxSheet>): String = buildString {
    append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
    append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ")
    append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
    append("<sheets>")
    sheets.forEachIndexed { idx, sheet ->
      val id = idx + 1
      append("<sheet name=\"${escapeXml(sheet.name)}\" sheetId=\"$id\" r:id=\"rId$id\"/>")
    }
    append("</sheets>")
    append("</workbook>")
  }

  private fun buildWorkbookRelsXml(sheetCount: Int): String = buildString {
    append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
    append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
    for (i in 1..sheetCount) {
      append("<Relationship Id=\"rId$i\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet$i.xml\"/>")
    }
    val styleRelId = sheetCount + 1
    append("<Relationship Id=\"rId$styleRelId\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>")
    append("</Relationships>")
  }

  /**
   * Styles index mapping:
   * 0 = Default body text
   * 1 = Title banner (Bold Navy #10283F, size 13)
   * 2 = Table header (Bold White text on Navy #10283F fill)
   * 3 = Currency format (#,##0.00)
   * 4 = Bold text / Footer label (Soft Teal #EAF3F1 fill)
   * 5 = Bold Currency (#,##0.00 on Soft Teal #EAF3F1 fill)
   * 6 = Subtitle muted (#667787)
   * 7 = Whole number (#,##0)
   */
  private fun buildStylesXml(): String = """
    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
    <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
      <numFmts count="2">
        <numFmt numFmtId="164" formatCode="#,##0.00"/>
        <numFmt numFmtId="165" formatCode="#,##0"/>
      </numFmts>
      <fonts count="5">
        <font><sz val="10"/><color rgb="FF152D43"/><name val="Calibri"/></font>
        <font><b/><sz val="13"/><color rgb="FF10283F"/><name val="Calibri"/></font>
        <font><b/><sz val="10"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
        <font><b/><sz val="10"/><color rgb="FF10283F"/><name val="Calibri"/></font>
        <font><sz val="9"/><color rgb="FF667787"/><name val="Calibri"/></font>
      </fonts>
      <fills count="4">
        <fill><patternFill patternType="none"/></fill>
        <fill><patternFill patternType="gray125"/></fill>
        <fill><patternFill patternType="solid"><fgColor rgb="FF10283F"/></patternFill></fill>
        <fill><patternFill patternType="solid"><fgColor rgb="FFEAF3F1"/></patternFill></fill>
      </fills>
      <borders count="2">
        <border><left/><right/><top/><bottom/><diagonal/></border>
        <border>
          <left style="thin"><color rgb="FFE3EAED"/></left>
          <right style="thin"><color rgb="FFE3EAED"/></right>
          <top style="thin"><color rgb="FFE3EAED"/></top>
          <bottom style="thin"><color rgb="FFE3EAED"/></bottom>
          <diagonal/>
        </border>
      </borders>
      <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
      <cellXfs count="8">
        <xf numFmtId="0" fontId="0" fillId="0" borderId="1" applyBorder="1"/>
        <xf numFmtId="0" fontId="1" fillId="0" borderId="0" applyFont="1"/>
        <xf numFmtId="0" fontId="2" fillId="2" borderId="1" applyFont="1" applyFill="1" applyBorder="1"/>
        <xf numFmtId="164" fontId="0" fillId="0" borderId="1" applyNumberFormat="1" applyBorder="1"/>
        <xf numFmtId="0" fontId="3" fillId="3" borderId="1" applyFont="1" applyFill="1" applyBorder="1"/>
        <xf numFmtId="164" fontId="3" fillId="3" borderId="1" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1"/>
        <xf numFmtId="0" fontId="4" fillId="0" borderId="0" applyFont="1"/>
        <xf numFmtId="165" fontId="0" fillId="0" borderId="1" applyNumberFormat="1" applyBorder="1"/>
      </cellXfs>
    </styleSheet>
  """.trimIndent()

  private fun buildWorksheetXml(sheet: XlsxSheet): String = buildString {
    val colCount = maxOf(
      sheet.headers.size,
      sheet.rows.maxOfOrNull { it.size } ?: 0,
      sheet.footerRows.maxOfOrNull { it.size } ?: 0,
      1
    )

    append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
    append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")

    // Compute clean column widths based on header and sample cell lengths
    append("<cols>")
    for (c in 0 until colCount) {
      val headerLen = sheet.headers.getOrNull(c)?.length ?: 10
      val maxRowLen = sheet.rows.take(100).maxOfOrNull { row ->
        when (val cell = row.getOrNull(c)) {
          is XlsxCell.Text -> cell.value.length
          is XlsxCell.Currency -> 14
          is XlsxCell.WholeNumber -> 10
          null -> 0
        }
      } ?: 0
      val width = (maxOf(headerLen, maxRowLen) + 4).coerceIn(12, 42)
      append("<col min=\"${c + 1}\" max=\"${c + 1}\" width=\"$width\" customWidth=\"1\"/>")
    }
    append("</cols>")

    append("<sheetData>")

    var rowNum = 1

    // Row 1: Title Banner
    append("<row r=\"$rowNum\">")
    appendInlineTextCell(0, rowNum, sheet.titleBanner, style = 1)
    append("</row>")
    rowNum++

    // Row 2: Subtitle Banner
    append("<row r=\"$rowNum\">")
    appendInlineTextCell(0, rowNum, sheet.subtitleBanner, style = 6)
    append("</row>")
    rowNum++

    // Row 3: Blank spacer
    rowNum++

    // Row 4: Table Headers
    if (sheet.headers.isNotEmpty()) {
      append("<row r=\"$rowNum\">")
      sheet.headers.forEachIndexed { colIdx, header ->
        appendInlineTextCell(colIdx, rowNum, header, style = 2)
      }
      append("</row>")
      rowNum++
    }

    // Data Rows
    for (row in sheet.rows) {
      append("<row r=\"$rowNum\">")
      row.forEachIndexed { colIdx, cell ->
        appendCell(colIdx, rowNum, cell, forceFooterStyle = false)
      }
      append("</row>")
      rowNum++
    }

    // Footer / Totals Rows
    for (row in sheet.footerRows) {
      append("<row r=\"$rowNum\">")
      row.forEachIndexed { colIdx, cell ->
        appendCell(colIdx, rowNum, cell, forceFooterStyle = true)
      }
      append("</row>")
      rowNum++
    }

    append("</sheetData>")
    append("</worksheet>")
  }

  private fun StringBuilder.appendInlineTextCell(colIdx: Int, rowNum: Int, text: String, style: Int) {
    val ref = "${columnLetter(colIdx)}$rowNum"
    append("<c r=\"$ref\" t=\"inlineStr\" s=\"$style\"><is><t xml:space=\"preserve\">${escapeXml(text)}</t></is></c>")
  }

  private fun StringBuilder.appendCell(colIdx: Int, rowNum: Int, cell: XlsxCell, forceFooterStyle: Boolean) {
    val ref = "${columnLetter(colIdx)}$rowNum"
    when (cell) {
      is XlsxCell.Text -> {
        val style = if (forceFooterStyle || cell.isBold) 4 else 0
        append("<c r=\"$ref\" t=\"inlineStr\" s=\"$style\"><is><t xml:space=\"preserve\">${escapeXml(cell.value)}</t></is></c>")
      }
      is XlsxCell.Currency -> {
        val style = if (forceFooterStyle || cell.isBold) 5 else 3
        val rupees = cell.paise.toDouble() / 100.0
        val numStr = String.format(java.util.Locale.US, "%.2f", rupees)
        append("<c r=\"$ref\" s=\"$style\"><v>$numStr</v></c>")
      }
      is XlsxCell.WholeNumber -> {
        val style = if (forceFooterStyle || cell.isBold) 4 else 7
        append("<c r=\"$ref\" s=\"$style\"><v>${cell.value}</v></c>")
      }
    }
  }
}
