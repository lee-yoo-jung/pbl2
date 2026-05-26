package com.example.pbl2

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.pbl2.access_ocr.MyAccessibilityService
import com.example.pbl2.access_ocr.ScreenRecordConfig
import com.example.pbl2.access_ocr.ScreenRecordService
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.BroadcastReceiver
import android.content.IntentFilter


// 추가----------------------------------------------------------------------------------------------
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.layout.safeDrawingPadding

// 추가----------------------------------------------------------------------------------------------


class MainActivity : ComponentActivity() {

    var answerText by mutableStateOf("")
    private var isServiceRunning = false  // 현재 녹화 상태

    // 최신 안드로이드 방식의 권한 요청 API
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGrand: Boolean ->
        if (isGrand) { // 권한 허용
            Toast.makeText(this, "권한이 허용되었습니다.", Toast.LENGTH_LONG).show()
        } else {       // 권한 거부
            Toast.makeText(this, "권한이 허용되지 않았습니다.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 이 Activity가 살아있는 동안에만 수행
        lifecycleScope.launch {
            ScreenRecordService.isServiceRunning.collect {  // companion object의 값 수집
                isServiceRunning = it   // 새로 들어온 상태 값 저장
            }
        }

        // 추가--------------------------------------------------------------------------------------
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        // 추가--------------------------------------------------------------------------------------

        super.onCreate(savedInstanceState)

        // 1. 기존 앱 UI의 언어 설정 로직 유지
        val savedLang = loadLanguage(this)
        setLocale(this, savedLang)

        // 2. 다른 앱 위에 그리기 권한 체크 (플로팅 버튼용)
        checkPermission()

        // 앱 실행 시 알림 권한 체크
        NotificationPermission()
        // 앱 실행 시 접근성 서비스 체크
        AccessibilityPermission()

        // 수정--------------------------------------------------------------------------------------
        setContent {                   // 3. 메인 UI 화면 호출
            MaterialTheme {

                Surface(
                    modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    SettingsScreen(answerText)
                }
            }
        }
        // 수정--------------------------------------------------------------------------------------
    }


    private fun checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                // 권한 요청 화면으로 이동
                startActivityForResult(intent, 100)
            }
        }
    }

    // 권한 허용 후 돌아왔을 때 처리
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100) {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "권한이 허용되었습니다.", Toast.LENGTH_SHORT).show()
                // 주의: 여기서 바로 서비스를 시작하지 마세요!
                // SettingsScreen의 스위치 상태에 따라 제어하는 것이 더 깔끔합니다.
            } else {
                Toast.makeText(this, "플로팅 버튼 기능을 위해 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 접근성 서비스 권한
    private fun AccessibilityPermission() {
        if (!isAccessibilityService(this)) {
            Toast.makeText(this, "접근성 서비스를 켜주세요", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        } else {
            Toast.makeText(this, "접근성 서비스를 켜져있습니다.", Toast.LENGTH_SHORT).show()
        }
    }



    // 접근성 서비스 활성화 여부 확인
    fun isAccessibilityService(context: Context): Boolean {
        val expectedComponentName = ComponentName(context, MyAccessibilityService::class.java)
        val Services = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return Services.contains(expectedComponentName.flattenToString())
    }

    // 알림 권한
    private fun NotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // 안드로이드 13 이상
            // 권한이 이미 허용됐는지 확인
            if (ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(this, "권한이 허용되어있습니다.", Toast.LENGTH_LONG).show()
            } else {  // 권한이 없으면 권한 요청
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            Toast.makeText(this, "권한이 허용되어있습니다.", Toast.LENGTH_LONG).show()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val result = intent?.getStringExtra("result")

            if (result != null) {
                updateUI(result)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                receiver,
                IntentFilter("OCR_RESULT_ACTION"),
                Context.RECEIVER_NOT_EXPORTED
            )
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }

    fun updateUI(text: String) {
        answerText = text
    }

}