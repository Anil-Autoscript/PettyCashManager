package com.pettycash.manager.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.pettycash.manager.data.model.Transaction
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream

object ExcelExporter {

    fun exportTransactions(
        context: Context,
        transactions: List<Transaction>,
        companyName: String,
        startDate: String,
        endDate: String
    ): File {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Transactions")

        // Styles
        val headerFont = workbook.createFont().apply {
            bold = true
            fontHeightInPoints = 12
            color = IndexedColors.WHITE.index
        }
        val headerStyle = workbook.createCellStyle().apply {
            setFont(headerFont)
            fillForegroundColor = IndexedColors.DARK_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.CENTER
            borderBottom = BorderStyle.THIN
        }
        val dateStyle = workbook.createCellStyle().apply {
            dataFormat = workbook.createDataFormat().getFormat("yyyy-mm-dd")
        }
        val currencyStyle = workbook.createCellStyle().apply {
            dataFormat = workbook.createDataFormat().getFormat("#,##0.00")
        }
        val cashInStyle = workbook.createCellStyle().apply {
            font = workbook.createFont().apply { color = IndexedColors.GREEN.index }
        }
        val expenseStyle = workbook.createCellStyle().apply {
            font = workbook.createFont().apply { color = IndexedColors.RED.index }
        }

        // Title Row
        val titleRow = sheet.createRow(0)
        titleRow.createCell(0).setCellValue("$companyName - Petty Cash Report")
        titleRow.createCell(6).setCellValue("Period: $startDate to $endDate")

        // Empty row
        sheet.createRow(1)

        // Header Row
        val headers = listOf("Date", "Type", "Category", "Description", "Cash In", "Expense", "Balance", "Added By", "Status")
        val headerRow = sheet.createRow(2)
        headers.forEachIndexed { idx, header ->
            headerRow.createCell(idx).apply {
                setCellValue(header)
                cellStyle = headerStyle
            }
        }

        // Data Rows
        var runningBalance = 0.0
        transactions.sortedBy { it.date }.forEachIndexed { idx, tx ->
            val row = sheet.createRow(idx + 3)
            row.createCell(0).setCellValue(DateUtils.formatForDisplay(tx.date))
            row.createCell(1).apply {
                setCellValue(tx.type.name)
                cellStyle = if (tx.type.name == "CASH_IN") cashInStyle else expenseStyle
            }
            row.createCell(2).setCellValue(tx.category)
            row.createCell(3).setCellValue(tx.description)

            if (tx.type.name == "CASH_IN") {
                runningBalance += tx.amount
                row.createCell(4).apply { setCellValue(tx.amount); cellStyle = currencyStyle }
                row.createCell(5).setCellValue("")
            } else {
                runningBalance -= tx.amount
                row.createCell(4).setCellValue("")
                row.createCell(5).apply { setCellValue(tx.amount); cellStyle = currencyStyle }
            }
            row.createCell(6).apply { setCellValue(runningBalance); cellStyle = currencyStyle }
            row.createCell(7).setCellValue(tx.addedBy)
            row.createCell(8).setCellValue(tx.approvalStatus.name)
        }

        // Summary Section
        val summaryRow = sheet.createRow(transactions.size + 4)
        summaryRow.createCell(0).setCellValue("SUMMARY")

        val totalIn = transactions.filter { it.type.name == "CASH_IN" }.sumOf { it.amount }
        val totalOut = transactions.filter { it.type.name == "EXPENSE" }.sumOf { it.amount }

        sheet.createRow(transactions.size + 5).apply {
            createCell(3).setCellValue("Total Cash In:")
            createCell(4).apply { setCellValue(totalIn); cellStyle = currencyStyle }
        }
        sheet.createRow(transactions.size + 6).apply {
            createCell(3).setCellValue("Total Expense:")
            createCell(5).apply { setCellValue(totalOut); cellStyle = currencyStyle }
        }
        sheet.createRow(transactions.size + 7).apply {
            createCell(3).setCellValue("Net Balance:")
            createCell(6).apply { setCellValue(totalIn - totalOut); cellStyle = currencyStyle }
        }

        // Auto-size columns
        headers.indices.forEach { sheet.autoSizeColumn(it) }

        // Save file
        val fileName = "PettyCash_${companyName.replace(" ", "_")}_${startDate}_${endDate}.xlsx"
        val file = File(context.getExternalFilesDir(null), fileName)
        FileOutputStream(file).use { workbook.write(it) }
        workbook.close()
        return file
    }

    fun shareExcelFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Report"))
    }
}
