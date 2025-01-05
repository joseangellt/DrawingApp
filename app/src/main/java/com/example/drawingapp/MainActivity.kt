package com.example.drawingapp

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private  var drawingView: DrawingView? = null

    private var mImageButtonCurrentPaint: ImageButton? = null

    private val openGalleryLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            result ->
            if(result.resultCode == RESULT_OK && result.data != null){
                val imageBackground: ImageView = findViewById(R.id.iv_background)
                imageBackground.setImageURI(result.data?.data)
            }
        }




    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val ib_gallery : ImageButton = findViewById(R.id.ib_gallery)
        ib_gallery.setOnClickListener{
            requestStoragePermission()
        }

        val ibUndo : ImageButton = findViewById(R.id.ib_undo)
        ibUndo.setOnClickListener{
            drawingView!!.onClickUndo()
        }

        val ibRedo : ImageButton = findViewById(R.id.ib_redo)
        ibRedo.setOnClickListener{
            drawingView!!.onClickRedo()
        }

        val ibSave : ImageButton = findViewById(R.id.ib_save)
        ibSave.setOnClickListener{

           if(isManageStorageAllowed()){
               lifecycleScope.launch {
                   val flDrawingView: FrameLayout = findViewById(R.id.fl_drawing_view_container)
                   saveBitmap(flDrawingView)
               }
           }

        }


        drawingView = findViewById(R.id.drawing_view)
        drawingView?.setSizeBrush(20.toFloat())

        val linearLayoutPaintColors = findViewById<LinearLayout>(R.id.ll_paint_colors)

        mImageButtonCurrentPaint = linearLayoutPaintColors[1] as ImageButton
        mImageButtonCurrentPaint!!.setImageDrawable(
            ContextCompat.getDrawable(this, R.drawable.pallet_pressed)
        )

        val ib_brush : ImageButton = findViewById(R.id.ib_brush)
        ib_brush.setOnClickListener{
            showBrushSizeChooserDialog()
        }

    }

    private fun isManageStorageAllowed(): Boolean{
        val result = ContextCompat.checkSelfPermission(this, Manifest.permission.MANAGE_EXTERNAL_STORAGE)
        return result == PackageManager.PERMISSION_GRANTED
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestStoragePermission(){
        if(Environment.isExternalStorageManager()){
            val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            openGalleryLauncher.launch(pickIntent)
        }else{
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            val uri = Uri.fromParts("package",packageName, null)
            intent.data = uri
            startActivity(intent)
            Toast.makeText(this, "Please grant permission to access external storage",
                Toast.LENGTH_LONG).show()
        }
    }



    private fun showBrushSizeChooserDialog(){
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brus_size)
        brushDialog.setTitle("Brush Size: ")
        brushDialog.show()
        val smallBtn : ImageButton = brushDialog.findViewById(R.id.ib_small_brush)
        smallBtn.setOnClickListener{
            drawingView?.setSizeBrush(10.toFloat())
            brushDialog.dismiss()
        }

        val mediumBtn : ImageButton = brushDialog.findViewById(R.id.ib_medium_brush)
        mediumBtn.setOnClickListener{
            drawingView?.setSizeBrush(20.toFloat())
            brushDialog.dismiss()
        }

        val largeBtn : ImageButton = brushDialog.findViewById(R.id.ib_large_brush)
        largeBtn.setOnClickListener{
            drawingView?.setSizeBrush(30.toFloat())
            brushDialog.dismiss()
        }
    }

    fun paintClicked(view: View){
        if(view !== mImageButtonCurrentPaint){
           val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()
            drawingView?.setColor(colorTag)

            imageButton.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_pressed)
            )

            mImageButtonCurrentPaint?.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.palette_normal)
            )

            mImageButtonCurrentPaint = view
        }
    }

    private fun getBitMapFromView(view: View) : Bitmap{
        val returnedBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background
        if(bgDrawable != null){
            bgDrawable.draw(canvas)
        }else{
            canvas.drawColor(Color.WHITE)
        }

        view.draw(canvas)
        return returnedBitmap
    }



    private suspend fun saveBitmapFile(mBitmap: Bitmap?): String{
        var  result = ""
        withContext(Dispatchers.IO){
            if(mBitmap !=null){
                try {
                    val bytes = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)

                    val fileLocation = File(externalCacheDir?.absoluteFile.toString()
                            + File.separator + "Drawing App" + System.currentTimeMillis()/1000 + ".png" )

                    val fileOutput = FileOutputStream(fileLocation)
                    fileOutput.write(bytes.toByteArray())
                    fileOutput.close()

                    result = fileLocation.absolutePath

                     runOnUiThread{
                         if (result.isNotEmpty()){
                             Toast.makeText(this@MainActivity,"File saved successfully at : $result",
                                 Toast.LENGTH_LONG).show()
                         }else{
                             Toast.makeText(this@MainActivity,"Something went wrong while " +
                                     "saving the file",
                                 Toast.LENGTH_LONG).show()
                         }
                     }
                }catch (e:Exception){
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }


    private fun saveBitmap(view : View) {
        lifecycleScope.launch {
            val bitmap = getBitMapFromView(view) // replace with your view

            // save bitmap to gallery based on Android version
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = applicationContext.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "DrawingApp_${System.currentTimeMillis() / 1000}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                    put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
                }

                var imageUri: Uri? = null
                resolver.run {
                    imageUri = insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    imageUri?.let {
                        openOutputStream(it)?.use { outputStream ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 90, outputStream)
                        }
                    }
                }

                if (imageUri != null) {
                    Toast.makeText(this@MainActivity,
                        "File saved successfully: $imageUri", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity,
                        "Something went wrong while saving the file", Toast.LENGTH_SHORT).show()
                }

            } else {
                val filePath = saveBitmapFile(bitmap)
                if (filePath.isNotEmpty()) {
                    val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                    val file = File(filePath)
                    val contentUri = Uri.fromFile(file)
                    mediaScanIntent.data = contentUri
                    sendBroadcast(mediaScanIntent)
                    Toast.makeText(this@MainActivity,
                        "File saved successfully: $filePath", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity,
                        "Something went wrong while saving the file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}