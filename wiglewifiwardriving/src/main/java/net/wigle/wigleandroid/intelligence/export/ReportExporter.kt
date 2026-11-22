package net.wigle.wigleandroid.intelligence.export

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import net.wigle.wigleandroid.intelligence.model.Finding
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Exports intelligence reports in CSV and PDF formats.
 * Strictly passive and offline.
 */
object ReportExporter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun exportToCsv(context: Context, findings: List<Finding>, periodName: String = "Report"): File {
        val fileName = "Intelligence_${periodName}_${fileDateFormat.format(Date())}.csv"
        val outDir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(outDir, fileName)

        file.printWriter().use { out ->
            out.println("ID,Analyzer,Severity,Title,Summary,BSSID,SSID,Latitude,Longitude,Score,Timestamp,Details")
            for (f in findings) {
                val csvLine = listOf(
                    f.id.toString(),
                    f.analyzerType.name,
                    f.severity.name,
                    "\"${f.title.replace("\"", "\"\"")}\"",
                    "\"${f.summary.replace("\"", "\"\"")}\"",
                    f.bssid ?: "",
                    "\"${(f.ssid ?: "").replace("\"", "\"\"")}\"",
                    f.latitude.toString(),
                    f.longitude.toString(),
                    f.score.toString(),
                    dateFormat.format(Date(f.timestamp)),
                    "\"${f.details.replace("\"", "\"\"")}\""
                ).joinToString(",")
                out.println(csvLine)
            }
        }
        return file
    }

    fun exportToPdf(context: Context, findings: List<Finding>, periodName: String = "Daily"): File {
        val fileName = "Intelligence_${periodName}_${fileDateFormat.format(Date())}.pdf"
        val outDir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(outDir, fileName)

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint().apply {
            isAntiAlias = true
        }

        // Dark HUD Header
        paint.color = Color.rgb(10, 16, 28)
        canvas.drawRect(0f, 0f, 595f, 842f, paint)

        paint.color = Color.rgb(0, 255, 242) // Cyan
        paint.textSize = 18f
        paint.isFakeBoldText = true
        canvas.drawText("GHOSTH // INTELLIGENCE $periodName REPORT", 40f, 50f, paint)

        paint.color = Color.rgb(180, 200, 220)
        paint.textSize = 10f
        paint.isFakeBoldText = false
        canvas.drawText("Generated: ${dateFormat.format(Date())} | Total Findings: ${findings.size}", 40f, 70f, paint)

        // Divider
        paint.color = Color.rgb(0, 255, 242)
        paint.strokeWidth = 1f
        canvas.drawLine(40f, 80f, 555f, 80f, paint)

        var y = 110f
        paint.textSize = 10f

        for ((index, finding) in findings.take(20).withIndex()) {
            if (y > 800f) break

            // Severity Tag Color
            val tagColor = when (finding.severity.name) {
                "CRITICAL" -> Color.rgb(255, 31, 143) // Cyber Pink
                "HIGH" -> Color.rgb(255, 110, 0)
                "MEDIUM" -> Color.rgb(255, 215, 0)
                else -> Color.rgb(0, 255, 242)
            }

            paint.color = tagColor
            paint.isFakeBoldText = true
            canvas.drawText("[${finding.severity.name}] ${finding.title}", 40f, y, paint)

            paint.color = Color.rgb(220, 230, 240)
            paint.isFakeBoldText = false
            canvas.drawText(finding.summary, 40f, y + 14f, paint)

            paint.color = Color.rgb(120, 140, 160)
            paint.textSize = 8f
            canvas.drawText("Loc: ${finding.latitude}, ${finding.longitude} | Score: ${"%.2f".format(finding.score)} | Time: ${dateFormat.format(Date(finding.timestamp))}", 40f, y + 26f, paint)

            paint.textSize = 10f
            y += 42f
        }

        document.finishPage(page)
        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()
        return file
    }
}

