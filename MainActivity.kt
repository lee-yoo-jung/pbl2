package com.example.pbl2

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import com.example.pbl2.access_ocr.ScreenRecordService
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.core.app.ActivityCompat

class MainActivity : ComponentActivity() {
    private lateinit var language: String

    var answerText by mutableStateOf("")
    private var isServiceRunning = false

    // 최신 안드로이드 방식의 권한 요청 API
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGrand: Boolean ->
        if (isGrand) { // 권한 허용
            var local_Toast=when(language){
                "en" -> "Authorization allowed."
                "ja" -> "権限が許可されました。"
                "zh" -> "权限被允许了。"
                "vi"-> "Quyền hạn đã được cho phép."
                else -> "권한이 허용되었습니다."
            }
            Toast.makeText(this, local_Toast, Toast.LENGTH_SHORT).show()
        } else {       // 권한 거부
            var local_Toast=when(language){
                "en" -> "Authorization not allowed"
                "ja" -> "権限が許可されていません。"
                "zh" -> "不允许授权。"
                "vi"-> "Quyền hạn không được cho phép."
                else -> "권한이 허용되지 않았습니다."
            }
            Toast.makeText(this, local_Toast, Toast.LENGTH_SHORT).show()
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val locale = resources.configuration.locales[0]
        language = locale.language

        // 이 Activity가 살아있는 동안에만 수행
        lifecycleScope.launch {
            val locale = resources.configuration.locales[0]

            Log.d("LANG", locale.language)        // ko, en, ja, zh, vi
            ScreenRecordService.isServiceRunning.collect {  // companion object의 값 수집
                isServiceRunning = it   // 새로 들어온 상태 값 저장
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                1000
            )
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        super.onCreate(savedInstanceState)

        // 기존 앱 UI의 언어 설정 로직 유지
        val savedLang = loadLanguage(this)
        setLocale(this, savedLang)

        checkPermission()   // 다른 앱 위에 그리기 권한 체크 (플로팅 버튼용)
        NotificationPermission()     // 앱 실행 시 알림 권한 체크
        AccessibilityPermission()   // 앱 실행 시 접근성 서비스 체크

        // 메인 UI 화면 호출
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen(answerText)
                }
            }
        }
    }

    fun checkPermission() {
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

    // 접근성 서비스 권한
    private fun AccessibilityPermission() {
        if (!isAccessibilityService(this)) {
            var local_Toast=when(language){
                "en" -> "Please enable the accessibility service."
                "ja" -> "アクセシビリティサービスを有効にしてください。"
                "zh" -> "请启用无障碍服务。"
                "vi" -> "Vui lòng bật dịch vụ trợ năng."
                else -> "접근성 서비스를 켜주세요."
            }
            Toast.makeText(this, local_Toast, Toast.LENGTH_SHORT).show()
            Toast.makeText(this, "접근성 서비스를 켜주세요.", Toast.LENGTH_SHORT).show()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
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

    // 권한 허용 후 돌아왔을 때 처리
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100) {
            if (Settings.canDrawOverlays(this)) {
                var local_Toast=when(language){
                    "en" -> "Authorization allowed."
                    "ja" -> "権限が許可されました。"
                    "zh" -> "权限被允许了。"
                    "vi"-> "Quyền hạn đã được cho phép."
                    else -> "권한이 허용되었습니다."
                }
                Toast.makeText(this, local_Toast, Toast.LENGTH_SHORT).show()
            } else {
                var local_Toast=when(language){
                    "en" -> "Please grant permission to use the guidance feature."
                    "ja" -> "案内機能を使用するには権限を許可してください。"
                    "zh" -> "请授予权限以使用引导功能。"
                    "vi" -> "Vui lòng cấp quyền để sử dụng tính năng hướng dẫn."
                    else -> "안내 기능을 사용하려면 권한을 허용해주세요."
                }
                Toast.makeText(this, local_Toast, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 알림 권한
    private fun NotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // 안드로이드 13 이상
            // 권한이 이미 허용됐는지 확인
            if (ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                var local_Toast=when(language){
                    "en" -> "Authorization allowed."
                    "ja" -> "権限が許可されました。"
                    "zh" -> "权限被允许了。"
                    "vi"-> "Quyền hạn đã được cho phép."
                    else -> "권한이 허용되었습니다."
                }
                Toast.makeText(this, local_Toast, Toast.LENGTH_SHORT).show()
            } else {  // 권한이 없으면 권한 요청
                var local_Toast=when(language){
                    "en" -> "Please grant permission to use the guidance feature."
                    "ja" -> "案内機能を使用するには権限を許可してください。"
                    "zh" -> "请授予权限以使用引导功能。"
                    "vi" -> "Vui lòng cấp quyền để sử dụng tính năng hướng dẫn."
                    else -> "안내 기능을 사용하려면 권한을 허용해주세요."
                }
                Toast.makeText(this, local_Toast, Toast.LENGTH_SHORT).show()
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
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