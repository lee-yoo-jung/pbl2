package com.example.pbl2.access_ocr

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

private var mode: String? = null
private var targetRect: Rect? = null

class CaptureActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("FLOW", "CaptureActivity onCreate")
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra("mode")

        targetRect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("target_rect", Rect::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("target_rect")
        }
        startScreenCapture()
    }

    // 화면 녹화 권한 요청 함수
    fun startScreenCapture() {
        val mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent =
            mediaProjectionManager.createScreenCaptureIntent()    // 시스템이 제공하는 화면 녹화 허용 팝업
        screenRecord.launch(captureIntent)  // 결과를 기다림
    }

    // 결과 받기
    private val screenRecord = registerForActivityResult( // 대답 처리 준비
        ActivityResultContracts.StartActivityForResult()    // 허용/거부 결과 받아오기
    ) { result ->
        Log.d("FLOW", " 권한 결과 받음 resultCode=${result.resultCode}")
        if (result.resultCode == RESULT_OK && result.data != null) { // 사용자가 허용 눌렀을 때
            Log.d("FLOW", " 권한 허용됨")
            val config =
                ScreenRecordConfig(result.resultCode, result.data!!)    // MediaProjection 권한 정보 저장
            val serviceIntent = Intent(applicationContext, ScreenRecordService::class.java).apply {
                // 서비스에 데이터 전달
                action = ScreenRecordService.START_RECORDING // Service에게 녹화 시작 알림
                putExtra(ScreenRecordService.KEY_RECORDING_CONFIG, config)  // 사용자가 허용한 화면 녹화 권한 정보
                putExtra("target_rect", targetRect)
                putExtra("mode", mode)
                putExtra("question", intent.getStringExtra("question"))
                putExtra("pkg", intent.getStringExtra("pkg"))
                putExtra("lang", intent.getStringExtra("lang"))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d("FLOW", "START_RECORDING 서비스 실행")
                startForegroundService(serviceIntent)
            } else {
                Log.d("FLOW", "START_RECORDING 서비스 실행")
                startService(serviceIntent)
            }
            finish()
            moveTaskToBack(true)

            Handler(Looper.getMainLooper()).postDelayed({
                val actionIntent = Intent(applicationContext, ScreenRecordService::class.java).apply {
                    action = ScreenRecordService.ACTION_CAPTURE
                    putExtra("target_rect", targetRect)
                    putExtra("mode", mode)
                    putExtra("question", intent.getStringExtra("question"))
                    putExtra("pkg", intent.getStringExtra("pkg"))
                    putExtra("lang", intent.getStringExtra("lang"))
                }
                startService(actionIntent)
            }, 2500)
        }else{
            Log.d("FLOW", "권한 거부됨")
            finish()
            moveTaskToBack(true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}