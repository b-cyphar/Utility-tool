package com.example.utilitytool

import android.content.ContentValues
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    private val pickForPdf = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { if (it.isNotEmpty()) makePdf(it) }

    private val pickForCompress = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { if (it.isNotEmpty()) compress(it) }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        layout.addView(Button(this).apply {
            text = "Image to PDF"
            setOnClickListener { pickForPdf.launch("image/*") }
        })
        layout.addView(Button(this).apply {
            text = "Compress Images"
            setOnClickListener { pickForCompress.launch("image/*") }
        })
        setContentView(layout)
    }

    private fun bitmap(uri: Uri): Bitmap? =
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }

    private fun makePdf(uris: List<Uri>) {
        val pdf = PdfDocument()
        val w = 595; val h = 842
        uris.forEachIndexed { i, u ->
            val bmp = bitmap(u) ?: return@forEachIndexed
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(w, h, i + 1).create())
            val scale = minOf(w.toFloat() / bmp.width, h.toFloat() / bmp.height)
            val nw = bmp.width * scale; val nh = bmp.height * scale
            val dst = RectF((w - nw) / 2, (h - nh) / 2, (w + nw) / 2, (h + nh) / 2)
            page.canvas.drawBitmap(bmp, null, dst, null)
            pdf.finishPage(page)
        }
        save("PDF_${System.currentTimeMillis()}.pdf", "application/pdf",
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, Environment.DIRECTORY_DOWNLOADS) {
            pdf.writeTo(it)
        }
        pdf.close()
        toast("PDF saved in Downloads")
    }

    private fun compress(uris: List<Uri>) {
        var n = 0
        uris.forEach { u ->
            val bmp = bitmap(u) ?: return@forEach
            save("IMG_${System.currentTimeMillis()}_$n.jpg", "image/jpeg",
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                Environment.DIRECTORY_PICTURES + "/Compressed") {
                bmp.compress(Bitmap.CompressFormat.JPEG, 60, it)
            }
            n++
        }
        toast("$n images compressed")
    }

    private fun save(
        name: String, mime: String, collection: Uri, path: String,
        write: (OutputStream) -> Unit
    ) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, path)
        }
        val uri = contentResolver.insert(collection, values) ?: return
        contentResolver.openOutputStream(uri)?.use(write)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
