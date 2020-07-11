package com.luza.basicvideocutbardemo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main2.*

class MainActivity2 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)
        initView()
    }

    private fun initView() {
        val path = intent.getStringExtra(MainActivity.EXTRA_PATH) ?: ""
        videoCutBar?.setVideoPath(path, true)
    }

    override fun onBackPressed() {
        videoCutBar?.clearHistoryBitmap()
        super.onBackPressed()
    }
}