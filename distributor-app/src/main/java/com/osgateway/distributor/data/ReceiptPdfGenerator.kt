package com.osgateway.distributor.data

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.osgateway.shared.model.TransactionDto
import com.osgateway.shared.util.MoneyFormat
import com.osgateway.shared.util.TransactionStatusFr
import java.io.File
import java.io.FileOutputStream

/**
 * Simple PDF receipt using Android PdfDocument (sans commissions).
 */
object ReceiptPdfGenerator {

    fun generate(context: Context, tx: TransactionDto): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val title = Paint().apply {
            textSize = 22f
            isFakeBoldText = true
        }
        val body = Paint().apply { textSize = 14f }
        var y = 60f
        fun line(text: String, paint: Paint = body) {
            canvas.drawText(text, 40f, y, paint)
            y += paint.textSize + 10f
        }
        line("OS Gateway — Reçu", title)
        y += 10f
        line("Référence: ${tx.reference ?: tx.id ?: "-"}")
        line("Type: ${tx.type ?: "-"}")
        line("Opérateur: ${tx.operator ?: "-"}")
        line("Bénéficiaire: ${tx.beneficiaryPhone ?: "-"}")
        line("Montant: ${MoneyFormat.formatXof(tx.amount)}")
        line("Statut: ${TransactionStatusFr.label(tx.status)}")
        line("Date: ${tx.createdAt ?: "-"}")
        y += 20f
        line("Document généré localement.")
        document.finishPage(page)

        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(dir, "receipt_${tx.reference ?: tx.id ?: System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    fun asShareText(tx: TransactionDto): String = buildString {
        appendLine("OS Gateway — Reçu")
        appendLine("Référence: ${tx.reference ?: tx.id ?: "-"}")
        appendLine("Type: ${tx.type ?: "-"}")
        appendLine("Opérateur: ${tx.operator ?: "-"}")
        appendLine("Bénéficiaire: ${tx.beneficiaryPhone ?: "-"}")
        appendLine("Montant: ${MoneyFormat.formatXof(tx.amount)}")
        appendLine("Statut: ${TransactionStatusFr.label(tx.status)}")
        appendLine("Date: ${tx.createdAt ?: "-"}")
    }
}
