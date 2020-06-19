package com.luza.basicvideocutbardemo

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.luza.videocutbar.VideoCutBar
import com.luza.videocutbar.eLog
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File


class MainActivity : AppCompatActivity(), VideoCutBar.ILoadingListener,
    VideoCutBar.OnCutRangeChangeListener {

    companion object {
        const val REQUEST_VIDEO = 100
        const val REQUEST_PERMISSION = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        videoCutBar.loadingListener = this
        videoCutBar.rangeChangeListener = this
    }

    override fun onLoadingStart() {
        llLoading.visibility = View.VISIBLE
    }

    override fun onLoadingComplete() {
        llLoading.visibility = View.GONE
    }

    override fun onLoadingError() {
        llLoading.visibility = View.GONE
    }

    override fun onRangeChanged(
        videoCutBar: VideoCutBar?,
        minValue: Long,
        maxValue: Long,
        thumbIndex: Int
    ) {

    }

    override fun onRangeChanging(
        videoCutBar: VideoCutBar?,
        minValue: Long,
        maxValue: Long,
        thumbIndex: Int
    ) {

    }

    fun loadVideo(view: View) {
        doRequestPermission(
            arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ), {
                val intent = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                startActivityForResult(intent, REQUEST_VIDEO)
            }, {

            }
        )

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_VIDEO -> {
                if (resultCode == Activity.RESULT_OK) {
                    data?.let {
                        val path = getPath(it.data!!)
                        if (path != null) {
                            eLog("Path Video: $path")
                            if (File(path).exists()) {
                                videoCutBar.videoPath = path
                            } else
                                Toast.makeText(
                                    this,
                                    "File is not exists",
                                    Toast.LENGTH_SHORT
                                ).show()
                        } else {
                            Toast.makeText(this, "Can't find path by uri", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("Recycle")
    fun getPath(uri: Uri): String? {
        val projection = arrayOf(MediaStore.Video.Media.DATA)
        val cursor = contentResolver.query(uri, projection, null, null, null)
        return if (cursor != null) {
            val column_index = cursor
                .getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            cursor.moveToFirst()
            cursor.getString(column_index)
        } else
            null
    }


    private var onAllow: (() -> Unit)? = null
    private var onDenied: (() -> Unit)? = null
    fun checkPermission(permissions: Array<String>): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            permissions.forEach {
                if (checkSelfPermission(it) ==
                    PackageManager.PERMISSION_DENIED
                ) {
                    return false
                }
            }
        }
        return true
    }

    private fun doRequestPermission(
        permissions: Array<String>,
        onAllow: () -> Unit = {}, onDenied: () -> Unit = {}
    ) {
        this.onAllow = onAllow
        this.onDenied = onDenied
        if (checkPermission(permissions)) {
            onAllow()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(permissions, REQUEST_PERMISSION)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (checkPermission(permissions)) {
            onAllow?.invoke()
        } else {
            onDenied?.invoke()
        }
    }
}