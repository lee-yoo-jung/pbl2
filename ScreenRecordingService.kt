package com.example.pbl2.access_ocr

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcelable
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.parcelize.Parcelize
import kotlin.getValue
import kotlinx.coroutines.Dispatchers
import kotlin.jvm.java
import kotlin.to
import com.example.pbl2.backend.RetrofitClient
import kotlinx.coroutines.launch
import android.util.Base64
import java.io.ByteArrayOutputStream
import com.example.pbl2.backend.QuestionRequest
import android.content.Context
import com.example.pbl2.backend.UserSession

@Parcelize
data class ScreenRecordConfig(
    val resultCode:Int,
    val data: Intent
): Parcelable

class ScreenRecordService: Service() {
    private var recordingStartTime = 0L
    private val initialDelay = 4000L

    private var mode: String? = null
    private var questionText: String? = null
    private var targetPkgName: String? = null

    private var hasCaptured = false
    private var mediaProjection: MediaProjection? = null  // 화면을 캡처할 권한 및 실제 캡처 엔진
    private var virtualDisplay: VirtualDisplay? = null    // 화면을 복제해서 MediaRecorder로 보내는 가상 디스플레이
    private var imageReader: ImageReader? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())  // 백그라운드 작업용
    private val screenData by lazy { getScreenSize() }    // 화면 크기 데이터
    private var selectedOCR = false
    private var targetRect: Rect? = null
    private var pendingCapture = false
    private var pendingRect: Rect? = null
    private var listenerSet: Boolean = false
    private var ignoreFirstFrame: Boolean = true
    private var hasProcessedOCR = false
    private var targetLang: String? = null

    // 화면 캡쳐를 시작하기 위한 관리자
    private val mediaProjectionManager by lazy {
        getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    // 화면 캡쳐의 상태를 실시간으로 감시
    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        // 시스템이 녹화를 강제로 종료할 때 호출됨
        override fun onStop() {
            super.onStop()
            releaseResources()  // 리소스 정리
            stopService()       // 서비스 종료
        }
    }

    // 서비스에 명령을 내릴 때마다 호출되는 함수
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            mode = it.getStringExtra("mode")
            questionText = it.getStringExtra("question")
            targetPkgName = it.getStringExtra("pkg")
            targetLang = it.getStringExtra("lang") // 전달받은 언어 저장
        }
        intent?.getStringExtra("mode")?.let {
            mode = it
            intent?.getStringExtra("question")?.let { questionText = it }
            intent?.getStringExtra("pkg")?.let { targetPkgName = it }

        }
        Log.d("OnlyCapture", "받은 mode = $mode")

        Log.d("FLOW", "Service onStartCommand action=${intent?.action}")
        // 전달받은 rect 좌표 데이터 확인
        intent?.let {
            if (it.action == "ACTION_CAPTURE") {
                targetRect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    it.getParcelableExtra("target_rect", Rect::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    it.getParcelableExtra<Rect>("target_rect")
                }
                Log.d("FLOW_OCR", "Service 받은 rect = $targetRect")
            }
        }

        // 명령별로 하는 일
        when (intent?.action) {
            START_RECORDING -> {
                val notification =
                    NotificationHelper.createNotification(applicationContext) // 알림 생성
                NotificationHelper.createNotificationChannel(applicationContext)    // 상단바 알림 띄우기

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        1,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    )
                } else {
                    startForeground(
                        1,
                        notification
                    )
                }
                _isServiceRunning.value = true    // 서비스 실행 여부
                hasCaptured = true    // 캡쳐 기능 닫아두기
                if (mediaProjection == null) {
                    pendingCapture = true
                    pendingRect = targetRect
                    startRecording(intent)
                }
            }

            ACTION_CAPTURE -> {
                val rect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra("target_rect", Rect::class.java)
                } else {
                    intent.getParcelableExtra<Rect>("target_rect")
                }
                if (rect == null) {
                    return START_NOT_STICKY
                }
                selectedOCR = true
                hasProcessedOCR = false
                hasCaptured = true

                targetRect = rect
            }

        }
        return START_STICKY
    }

    // 캡쳐 준비
    private fun startRecording(intent: Intent) {
        if (mediaProjection != null) return
        recordingStartTime = System.currentTimeMillis()
        ignoreFirstFrame = true

        val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // 권한 설정 데이터 복원
            intent.getParcelableExtra(
                KEY_RECORDING_CONFIG,
                ScreenRecordConfig::class.java
            )
        } else {
            intent.getParcelableExtra(KEY_RECORDING_CONFIG,)
        }

        if (config == null) return

        // 화면 캡쳐 시작
        mediaProjection = mediaProjectionManager.getMediaProjection(
            config.resultCode,  // 팝업 창에서 '허용'을 눌렀는지 확인
            config.data         // 허용한 권한의 데이터
        )

        mediaProjection?.registerCallback(mediaProjectionCallback, null)

        val screenWidth = screenData.first
        val screenHeight = screenData.second

        // ImageReader 생성
        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight,
            PixelFormat.RGBA_8888,
            2   // 버퍼 크기
        )
        // 실제 녹화 시작
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "Screen",
            screenWidth, screenHeight,
            resources.displayMetrics.densityDpi,    // 화면의 선명도
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,    // 화면을 실시간으로 복제
            imageReader?.surface,
            null,
            null
        )
        Log.d("FLOW", "VirtualDisplay 생성됨")
        if (!listenerSet) {
            setupImageListener()
            listenerSet = true
        }
        setupImageListener()
    }

    // 화면이 들어오는 순간 자동으로 처리 시작
    private fun setupImageListener() {
        // 새로운 화면이 입력되면 실행됨
        imageReader?.setOnImageAvailableListener({ reader ->
            if (mediaProjection == null) {
                return@setOnImageAvailableListener
            }
            if (virtualDisplay == null) {
                return@setOnImageAvailableListener
            }

            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener

            image.use {
                if (ignoreFirstFrame) {
                    ignoreFirstFrame = false
                    return@setOnImageAvailableListener
                }

                if (System.currentTimeMillis() - recordingStartTime < 800) {
                    return@setOnImageAvailableListener
                }
                val currentPackage =
                    MyAccessibilityService.instance?.rootInActiveWindow?.packageName?.toString()

                Log.d("FLOW", "현재 패키지=$currentPackage")

                if (currentPackage == "com.example.pbl2") {
                    return@setOnImageAvailableListener
                }

                // "DIRECT" (직접 질문/추천 질문) 모드 실행
                if (mode == "DIRECT" || mode =="HighLight" && hasCaptured  ) {

                    if (currentPackage == null || currentPackage == "com.example.pbl2") {
                        return@setOnImageAvailableListener
                    }

                    val fixedPackage = currentPackage

                    val now = System.currentTimeMillis()
                    if (now - recordingStartTime < initialDelay) return@setOnImageAvailableListener

                    val bitmap = imageToBitmap(it)  // 화면 캡쳐본
                    stopRecording()

                    val qText = questionText ?: ""

                    if(mode=="HighLight"){
                        sendToServer(qText, "guide", bitmap, fixedPackage) { coordsJson ->
                            Log.d("DIRECT_RESULT", "받은 좌표: $coordsJson")
                            sendResultToUI(null, coordsJson)    // DB 저장 안되도록 NULL
                        }
                        stopService()
                        return@setOnImageAvailableListener
                    }

                    sendToServer(qText, "direct", bitmap, fixedPackage) { answer ->
                        Log.d("DIRECT_RESULT", "받은 답변: $answer")

                        // 정규식을 써서 답변 중에 "1. ~~~~" 문장만 떼어내기
                        val step1Regex = Regex("""1\.\s*(.*?)(?=\n|2\.|$)""")
                        val step1 = step1Regex.find(answer)?.value ?: answer

                        Log.d("DIRECT_RESULT", "추출된 1단계: $step1")

                        // 떼어낸 문장 + 캡쳐 이미지를 다시 보내서 좌표 요청
                        sendToServer(step1, "guide", bitmap, fixedPackage) { coordsJson ->
                            Log.d("DIRECT_RESULT", "받은 좌표: $coordsJson")
                            // 답변 내용과 좌표를 UI(FloatingService)로
                            sendResultToUI(answer, coordsJson)
                        }
                    }
                    stopService()
                    return@setOnImageAvailableListener
                }

                if (selectedOCR && !hasProcessedOCR) {
                    hasProcessedOCR = true

                    val rect = targetRect ?: return@setOnImageAvailableListener
                    selectedOCR = false

                    Log.d("FLOW_OCR", "부분 OCR 실행 rect=$rect")
                    processSelectedImage(it, rect)

                    stopRecording()
                    stopService()
                    return@setOnImageAvailableListener
                }
                processLongImage(it)
            }
        }, Handler(Looper.getMainLooper()))
    }

    // 비트맵 변환 및 스크롤 기반 OCR 실행
    private fun processLongImage(image: Image) {
        val now = System.currentTimeMillis()

        if (now - recordingStartTime < initialDelay) return

        val bitmap = imageToBitmap(image) ?: return
        val finalBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)

        OCR.process(finalBitmap, targetRect) { ocrText, errorBitmap ->
            sendToServer(ocrText, "summary", errorBitmap) { answer ->
                Log.d("API_RESULT", answer)
                sendResultToUI(answer)
            }
            stopRecording()
        }
    }

    // <부분 화면> 비트맵 변환 및 캡쳐 화면 미리보기 및 부분 영역 OCR 실행
    private fun processSelectedImage(image: Image, targetRect: Rect) {
        if (targetRect == null) {
            return
        }
        Log.d("FLOW_OCR", "OCR 직전 rect = $targetRect")
        selectedOCR = false

        // 캡처용 비트맵
        val bitmap = imageToBitmap(image) ?: return
        val safeConfig = bitmap.config ?: Bitmap.Config.ARGB_8888

        val finalBitmap = bitmap.copy(safeConfig, false)

        OCR.process(finalBitmap, targetRect) { ocrText, errorBitmap ->

            sendToServer(ocrText, "crop", errorBitmap) { answer ->
                Log.d("API_RESULT", answer)

                // 여기서 UI로 보내야 함
                sendResultToUI(answer)
            }
            stopRecording()
        }
        return
    }

    // ImageReader에서 받은 이미지를 Bitmap으로 변환하는 함수
    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer

        val width = image.width
        val height = image.height
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        // 실제 필요한 영역만 잘라서 반환
        return Bitmap.createBitmap(bitmap, 0, 0, width, height)
    }

    // 녹화 종료
    private fun stopRecording() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null

            imageReader?.close()
            imageReader = null

            mediaProjection?.stop()
            mediaProjection = null

        } catch (e: Exception) {
            Log.e("STOP", "error: ${e.message}")
        }
    }

    // 서비스 완전 종료
    private fun stopService() {
        _isServiceRunning.value = false   // 종료 상태
        stopForeground(STOP_FOREGROUND_REMOVE)  // 상단바 알림 삭제
    }

    // 화면 크기 측정
    private fun getScreenSize(): Pair<Int, Int> {
        val displayMetrics = resources.displayMetrics   // 화면 가로, 세로
        return displayMetrics.widthPixels to displayMetrics.heightPixels
    }

    // 서비스가 완전히 사라지기 직전에 호출
    override fun onDestroy() {
        super.onDestroy()
        _isServiceRunning.value = false   // 종료 상태
        serviceScope.coroutineContext.cancelChildren()  // 백그라운드 작업 즉시 중단
        releaseResources()
    }

    // 메모리 누수 방지
    private fun releaseResources() {
        try {
            imageReader?.setOnImageAvailableListener(null, null)
        } catch (e: Exception) {Log.e("ImageReaderError", "${e}") }

        try {
            virtualDisplay?.release()
        } catch (e: Exception) {Log.e("VirtualDisplayError", "${e}") }

        try {
            mediaProjection?.unregisterCallback(mediaProjectionCallback)
        } catch (e: Exception) {Log.e("MediaProjectionError", "${e}") }
        mediaProjection = null
        virtualDisplay = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // 서비스 명령어 목록
    companion object {
        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()
        // 작업 이름
        const val ACTION_CAPTURE = "ACTION_CAPTURE"
        const val START_RECORDING = "START_RECORDING"
        const val KEY_RECORDING_CONFIG = "KEY_RECORDING_CONFIG"
    }

    // 서버 전송 함수
    fun sendToServer(text: String, mode: String, bitmap: Bitmap?, packageName: String? = null, onResult: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val base64Image = bitmap?.let { bitmapToBase64(it) }
                val currentLang = getCurrentLanguage()
                val request = QuestionRequest(UserSession.userId, text, mode, base64Image, packageName, currentLang)
                val response = RetrofitClient.api.askQuestion(request)

                if (response.isSuccessful) {
                    val answer = response.body()?.answer ?: ""
                    onResult(answer)
                }
            } catch (e: Exception) {
                Log.e("API_ERROR", e.toString())
            }
        }
    }

    // 이미지를 텍스트로 압축 변환
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()

        bitmap.compress(
            Bitmap.CompressFormat.JPEG,
            70,
            outputStream
        )

        val byteArray = outputStream.toByteArray()

        return Base64.encodeToString(
            byteArray,
            Base64.NO_WRAP
        )
    }

    fun sendResultToUI(answer: String?, coordsJson: String? = null) {
        val intent = Intent("OCR_RESULT_ACTION")
        intent.setPackage(applicationContext.packageName)
        intent.putExtra("result", answer)
        if (coordsJson != null) {
            intent.putExtra("coords", coordsJson)
        }
        sendBroadcast(intent)
    }

    // 서비스가 시작될 때 전달받은 언어가 있다면 그것을 사용, 없다면 설정값에서 가져옴
    private fun getCurrentLanguage(): String {
        if (!targetLang.isNullOrEmpty()) return targetLang!!

        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        return prefs.getString("APP_LANG", "한국어") ?: "한국어"
    }
}