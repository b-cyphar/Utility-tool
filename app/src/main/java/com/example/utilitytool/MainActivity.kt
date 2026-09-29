package com.example.utilitytool

import android.app.AlertDialog
import android.content.ContentValues
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    private val pages = mutableListOf<Bitmap>()

    // launches the crop screen (has its own rotate / drag-corner UI)
    private val cropLauncher = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val uri = result.uriContent ?: return@registerForActivityResult
            val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            if (bmp != null) showPreview(bmp)
        }
    }

    private var modeIsCompress = false

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            cropLauncher.launch(
                CropImageContractOptions(
                    uri, CropImageOptions(
                        guidelines = com.canhub.cropper.CropImageView.Guidelines.ON
                    )
                )
            )
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        layout.addView(Button(this).apply {
            text = "Scan Page for PDF"
            setOnClickListener {
                modeIsCompress = false
                pickImage.launch("image/*")
            }
        })
        layout.addView(Button(this).apply {
            text = "Compress a Photo"
            setOnClickListener {
                modeIsCompress = true
                pickImage.launch("image/*")
            }
        })
        layout.addView(Button(this).apply {
            text = "Finish & Create PDF (${'$'}{pages.size} pages)"
            setOnClickListener { finishPdf() }
        })
        setContentView(layout)
    }

    // ---- Preview after crop, nothing saved yet ----
    private fun showPreview(bmp: Bitmap) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        val iv = ImageView(this).apply {
            setImageBitmap(bmp)
            adjustViewBounds = true
        }
        container.addView(iv)

        var quality = 80
        val qualityLabel = TextView(this).apply { text = "Quality: $quality" }
        val seek = SeekBar(this).apply {
            max = 100
            progress = quality
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) {
                    quality = p
                    qualityLabel.text = "Quality: $quality"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        if (modeIsCompress) {
            container.addView(qualityLabel)
            container.addView(seek)
        }

        AlertDialog.Builder(this)
            .setTitle(if (modeIsCompress) "Preview - confirm to save" else "Preview - add this page?")
            .setView(container)
            .setPositiveButton(if (modeIsCompress) "Save" else "Add Page") { d, _ ->
                if (modeIsCompress) {
                    confirmAndSaveCompressed(bmp, quality)
                } else {
                    pages.add(bmp)
                    toast("Page added (${pages.size} total)")
                    recreate() // refresh page count on button
                }
                d.dismiss()
            }
            .setNegativeButton("Redo Crop") { d, _ -> d.dismiss() }
            .setNeutralButton("Cancel") { d, _ -> d.dismiss() }
            .show()
    }

    private fun confirmAndSaveCompressed(bmp: Bitmap, quality: Int) {
        AlertDialog.Builder(this)
            .setTitle("Save compressed image?")
            .setMessage("This will save to Pictures/Compressed. Continue?")
            .setPositiveButton("Save") { _, _ ->
                save("IMG_${System.currentTimeMillis()}.jpg", "image/jpeg",
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    Environment.DIRECTORY_PICTURES + "/Compressed") {
                    bmp.compress(Bitmap.CompressFormat.JPEG, quality, it)
                }
                toast("Saved to Pictures/Compressed")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun finishPdf() {
        if (pages.isEmpty()) { toast("No pages added yet"); return }
        AlertDialog.Builder(this)
            .setTitle("Create PDF?")
            .setMessage("${pages.size} page(s) will be saved to Downloads. Continue?")
            .setPositiveButton("Save PDF") { _, _ ->
                val pdf = PdfDocument()
                val w = 595; val h = 842
                pages.forEachIndexed { i, bmp ->
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
                pages.clear()
                toast("PDF saved in Downloads")
                recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
