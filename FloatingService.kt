package com.example.pbl2

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.*
import android.view.animation.*
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.pbl2.access_ocr.CaptureActivity
import com.example.pbl2.access_ocr.MyAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import org.json.JSONObject
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.widget.ImageView
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import java.io.ByteArrayOutputStream
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.EditText
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import com.example.pbl2.access_ocr.highlight_Controller
import com.example.pbl2.backend.AudioRecorder
import com.example.pbl2.backend.LlmLogStreaming
import com.example.pbl2.backend.LlmlogResponse
import com.example.pbl2.backend.LogList
import com.example.pbl2.backend.QuestionRequest
import com.example.pbl2.backend.RetrofitClient
import com.example.pbl2.backend.UserSession
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import kotlin.collections.toMutableList


class FloatingService : Service() {
    private var currentAudioFile: File? = null
    private val recorder by lazy { AudioRecorder(cacheDir) }

    private var overlayView: View? = null
    private var selectionRect: Rect?=null
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    private lateinit var windowManager: WindowManager
    private lateinit var buttonView: View
    private lateinit var buttonParams: WindowManager.LayoutParams

    private lateinit var btnhighlight: Button
    private var menuView: View? = null
    private var returnAppView: View? = null
    private var textBoxView: View? = null
    private var searchBarView: View? = null
    private var targetLang: String? = null

    private lateinit var summaryAll: TextView
    private lateinit var summarySection: TextView

    private var isMenuOpen = false
    private var screenWidth = 0
    private var screenHeight = 0
    private var lastCloseTime = 0L

    private var mode: String? = null
    private var questionText: String? = null
    private var targetPkgName: String? = null

    private var tts: TextToSpeech? = null
    private var lastTargetPackage: String = ""

    data class ClickRange(var start: Pair<Float, Float> = Pair(0f,0f),var end: Pair<Float, Float> = Pair(0f,0f))
    val clicks= ClickRange()

    private var currentHistoryView: View? = null
    private var currentListContainer: LinearLayout? = null
    private var currentNonLog: TextView? = null

    private var currentMenuX = 0
    private var currentMenuY = 0
    private var currentMenuHeight = 0

    private lateinit var streamManager: LlmLogStreaming

    private val ocrResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val result = intent?.getStringExtra("result")
            val coords = intent?.getStringExtra("coords") // 좌표값 문자열

            if (result != null) {
                Handler(Looper.getMainLooper()).post {
                    var x: Float? = null
                    var y: Float? = null

                    // UI에는 답변 텍스트만 띄움
                    showTextBox(mode = "TEXT", shouldCloseMenu = true, message = result)

                    // 좌표값은 화면에 띄우지 않고 로그로만 남김
                    if (coords != null) {
                        Log.d("HIGHLIGHT_COORDS", "좌표: $coords")
                        // 좌표 파싱
                        val json = JSONObject(coords)
                        x = json.getDouble("x").toFloat()
                        y = json.getDouble("y").toFloat()
                    }
                    showTextBox(
                        mode = "TEXT",
                        shouldCloseMenu = true,
                        message = result,
                        x = x,
                        y = y
                    )
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()

        // 데이터 스트리밍
        streamManager= LlmLogStreaming{ newLogs->
            Handler(Looper.getMainLooper()).post {
                Log.d("LOG_DEBUG", "신규 데이터 스트리밍 수신 성공! 개수: ${newLogs.size}")

                val combined = (newLogs + LogList.responses.value).distinctBy { it.question }
                LogList.responses.value = combined

                updateLogs()
            }
        }
        streamManager.startSteaming(UserSession.userId)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        updateScreenSize()

        buttonView = LayoutInflater.from(this).inflate(R.layout.layout_floating_button_only, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        buttonParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        buttonParams.gravity = Gravity.TOP or Gravity.START
        buttonParams.x = 0
        buttonParams.y = 500

        windowManager.addView(buttonView, buttonParams)

        buttonView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var startTime = 0L
            private var isDragging = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = buttonParams.x
                        initialY = buttonParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        startTime = System.currentTimeMillis()
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = abs(event.rawX - initialTouchX)
                        val dy = abs(event.rawY - initialTouchY)
                        if (dx > 15 || dy > 15) {
                            if (isMenuOpen) closeMenu()
                            isDragging = true
                        }
                        if (isDragging) {
                            buttonParams.x = initialX + (event.rawX - initialTouchX).toInt()
                            var newY = initialY + (event.rawY - initialTouchY).toInt()
                            if (newY < 120) newY = 120
                            if (newY > screenHeight - v.height - 250) newY = screenHeight - v.height - 250
                            buttonParams.y = newY
                            windowManager.updateViewLayout(buttonView, buttonParams)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val duration = System.currentTimeMillis() - startTime
                        val distance = abs(event.rawX - initialTouchX) + abs(event.rawY - initialTouchY)
                        if (isDragging) {
                            buttonParams.x = if (buttonParams.x + v.width / 2 < screenWidth / 2) 0 else screenWidth - v.width
                            windowManager.updateViewLayout(buttonView, buttonParams)
                        } else {
                            if (duration < 300 && distance < 25) {
                                toggleMenu()
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })

        ContextCompat.registerReceiver(
            this,
            ocrResultReceiver,
            IntentFilter("OCR_RESULT_ACTION"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // TTS 초기화 및 한국어 설정
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val lang = getCurrentLanguage()
                tts?.language = when (lang) {
                    "English" -> Locale.ENGLISH
                    "日本語" -> Locale.JAPANESE
                    "中文" -> Locale.CHINESE
                    "Tiếng Việt"-> Locale("vi", "VN")
                    else -> Locale.KOREAN
                }
                tts?.setSpeechRate(0.8f)
            }
        }

    }

    private fun toggleMenu() {
        val currentTime = System.currentTimeMillis()
        if (isMenuOpen) closeMenu()
        else if (currentTime - lastCloseTime > 300) openMenu()
    }

    private fun openMenu() {
        updateScreenSize()
        closeTextBox()
        closeSearchBar()
        isMenuOpen = true

        menuView = LayoutInflater.from(this).inflate(R.layout.layout_floating_menu_only, null)
        val menuLayout = menuView!!.findViewById<LinearLayout>(R.id.menu_layout)
        val isButtonOnRight = (buttonParams.x + buttonView.width / 2) > screenWidth / 2

        summaryAll = menuView!!.findViewById(R.id.summaryAll)
        summarySection = menuView!!.findViewById(R.id.summarySection)

        val lang = getCurrentLanguage()
        summaryAll.text =
            when (lang) {
                "English" -> "Summarize"
                "日本語" -> "全体要約"
                "中文" -> "全部摘要"
                "Tiếng Việt"-> "Tóm tắt toàn bộ"
                else -> "화면요약"
            }
        summarySection.text =
            when (lang) {
                "English" -> "Crop"
                "日本語" -> "部分翻訳"
                "中文" -> "部分翻译"
                "Tiếng Việt"-> "Biên dịch một phần"
                else -> "부분해석"
            }

        // 영어일 때만 글자 크기 조정
        if (lang == "English") {
            // Summarize 버튼만 작게
            summaryAll.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
            // summarySection(Crop)은 코드에서 제외하여 기본 크기 유지
        } else {
            summaryAll.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
        }

        val askMenu = menuView!!.findViewById<TextView>(R.id.askMenu)
        val recentMenu = menuView!!.findViewById<TextView>(R.id.recentQuestion)

        recentMenu.text =
            when(lang) {
                "English" -> "Recent"
                "日本語" -> "最近の質問"
                "中文" -> "最近提问"
                "Tiếng Việt" -> "Gần đây"
                else -> "최근질문"
            }

        recentMenu.setOnClickListener {
            closeTextBox()

            showTextBox(
                mode = "TEXT",
                shouldCloseMenu = true,
                message = "최근 질문 결과",
                x = 500f,
                y = 1000f
            )
        }

        askMenu?.text =
            when(lang) {
                "English" -> "Ask Directly"
                "日本語" -> "直接質問"
                "中文" -> "直接提问"
                "Tiếng Việt"-> "Câu hỏi trực tiếp"
                else -> "직접질문"
            }

        askMenu?.setOnClickListener {
            closeTextBox()

            showTextBox(
                mode = "ASK",
                shouldCloseMenu = true
            )
        }


        // 녹화 (전체 화면) 버튼
        summaryAll.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // 안드로이드 13 이상
                // 권한이 이미 허용됐는지 확인
                if (ContextCompat.checkSelfPermission(
                        this, android.Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    closeMenu()
                    captureOnceAll()

                } else {  // 권한이 없으면 요청 메시지
                    Toast.makeText(this, "권한을 허용해주세요.", Toast.LENGTH_LONG).show()
                }
            } else {
                closeMenu()
                captureOnceAll()
            }
        }

        // 녹화 (부분 화면) 버튼
        summarySection.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // 안드로이드 13 이상
                // 권한이 이미 허용됐는지 확인
                if (ContextCompat.checkSelfPermission(
                        this, android.Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    closeMenu()
                    captureOnceCrop()

                } else {  // 권한이 없으면 요청 메시지
                    Toast.makeText(this, "권한을 허용해주세요.", Toast.LENGTH_LONG).show()
                }
            } else {
                closeMenu()
                captureOnceCrop()
            }
        }



        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE

        val menuParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        menuParams.gravity = Gravity.TOP or Gravity.START
        menuView!!.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        menuParams.x = if (isButtonOnRight) buttonParams.x - menuView!!.measuredWidth else buttonParams.x + buttonView.width
        menuParams.y = (buttonParams.y + buttonView.height / 2 - menuView!!.measuredHeight / 2).coerceIn(120, screenHeight - menuView!!.measuredHeight - 250)

        currentMenuX = menuParams.x
        currentMenuY = menuParams.y
        currentMenuHeight = menuView!!.measuredHeight

        menuView!!.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) { closeMenu(); true } else false
        }

        // 하단 앱으로 돌아가기 버튼
        returnAppView = LayoutInflater.from(this).inflate(R.layout.layout_return_app, null)
        val returnBtnText = when (lang) {
            "English" -> "Return to App"
            "日本語" -> "アプリに戻る"
            "中文" -> "返回应用"
            "Tiếng Việt"-> "Trở lại với ứng dụng"
            else -> "앱으로 돌아가기"
        }
        // 레이아웃 내의 텍스트뷰(btn_return_app)를 찾아 글자 변경
        returnAppView!!.findViewById<TextView>(R.id.btn_return_app).text = returnBtnText

        val returnParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        )
        returnParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        returnParams.y = 200

        returnAppView!!.findViewById<TextView>(R.id.btn_return_app).setOnClickListener {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            closeMenu()
        }

        windowManager.addView(menuView, menuParams)
        windowManager.addView(returnAppView, returnParams)

        Handler(Looper.getMainLooper()).post { startMenuAnimation(menuLayout, isButtonOnRight) }

        showTextBox(
            mode = "RECOMMEND",
            shouldCloseMenu = false
        )
    }

    // LLM 응답 메시지
    private fun showTextBox(
        mode: String,
        shouldCloseMenu: Boolean,
        message: String? = null,
        x: Float? = null,
        y: Float? = null
    ) {
        if (shouldCloseMenu) closeMenu()
        closeTextBox()

        textBoxView = LayoutInflater.from(this).inflate(R.layout.layout_text_box, null)

        btnhighlight = textBoxView!!.findViewById<Button>(R.id.btn_highlight)
        val textContentLayout = textBoxView!!.findViewById<LinearLayout>(R.id.text_content_layout)
        val contentText = textBoxView!!.findViewById<TextView>(R.id.content_text)
        val btnTtsPlay = textBoxView!!.findViewById<ImageView>(R.id.btn_tts_play)
        val buttonContainer = textBoxView!!.findViewById<LinearLayout>(R.id.button_container)
        val recommendContainer = textBoxView!!.findViewById<LinearLayout>(R.id.recommend_container)

        // 추천 질문 텍스트뷰 연결
        val q1 = textBoxView!!.findViewById<TextView>(R.id.rec_q1)
        val q2 = textBoxView!!.findViewById<TextView>(R.id.rec_q2)
        val q3 = textBoxView!!.findViewById<TextView>(R.id.rec_q3)
        val q4 = textBoxView!!.findViewById<TextView>(R.id.rec_q4)

        // 글자크기 조정 기능
        var currentFontSize = 20f // 초기 크기

        val btnIncrease = textBoxView!!.findViewById<TextView>(R.id.btn_font_increase)
        val btnDecrease = textBoxView!!.findViewById<TextView>(R.id.btn_font_decrease)

        val tvFontSizeLabel = textBoxView!!.findViewById<TextView>(R.id.tv_font_size_label)
        val lang = getCurrentLanguage()
        val isKorean = (lang == "Korean" || lang == "한국어")

        tvFontSizeLabel?.text = if (isKorean) "글자 크기" else "Text Size"

        btnIncrease.setOnClickListener {
            if (currentFontSize < 30f) { // 최대 크기 제한
                currentFontSize += 2f
                contentText.textSize = currentFontSize
            }
        }

        btnDecrease.setOnClickListener {
            if (currentFontSize > 18f) { // 최소 크기 제한
                currentFontSize -= 2f
                contentText.textSize = currentFontSize
            }
        }

        // 텍스트 채우기 및 TTS 재생
        if (message != null) {
            contentText.text = message.trim()
        }

        btnTtsPlay?.setOnClickListener {
            if (message != null) {
                tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "TTS_ID")
            }
        }

        btnhighlight.visibility = View.VISIBLE
        Log.d("HIGHLIGHT_COORDS", btnhighlight.visibility.toString())

        btnhighlight.text = when(lang) {
            "English" -> "Highlight"
            "日本語" -> "強調"
            "中文" -> "突出显示"
            "Tiếng Việt"-> "Nhấn mạnh"
            else -> "하이라이트"
        }

        if (x != null && y != null) {
            btnhighlight.visibility = View.VISIBLE

            btnhighlight.setOnClickListener {
                closeMenu()
                closeTextBox()
                highlight_Controller(x, y, this@FloatingService)
            }
        } else {
            btnhighlight.visibility = View.GONE
        }

        // 추천 질문(RECOMMEND) 내용 세팅 (다국어 적용)
        val btnAsk = textBoxView!!.findViewById<TextView>(R.id.btn_ask_question)
        val btnHistory = textBoxView!!.findViewById<TextView>(R.id.btn_view_history)

        btnAsk.text =
            when(lang) {
                "English" -> "Ask a question"
                "日本語" -> "質問しますか？"
                "中文" -> "要提问吗？"
                "Tiếng Việt"-> "Bạn có muốn đặt câu hỏi không?"
                else -> "질문 하시겠습니까?"
            }
        btnAsk.textSize = 18f
        btnHistory.text =
            when(lang) {
                "English" -> "View history"
                "日本語" -> "以前の質問を見る"
                "中文" -> "查看历史记录"
                "Tiếng Việt"-> "Xem câu hỏi trước đây"
                else -> "이전 질문을 보겠습니까?"
            }
        btnHistory.textSize = 18f

        if (mode == "RECOMMEND") {
            recommendContainer.visibility = View.VISIBLE
            val pkg = MyAccessibilityService.instance?.rootInActiveWindow?.packageName?.toString() ?: ""

            q1.textSize=18f
            q2.textSize=18f
            q3.textSize=18f
            q4.textSize=18f

            if (pkg == "com.sampleapp" || pkg == "com.fineapp.yogiyo") {
                q1.text =
                    when(lang) {
                        "English" -> "How to search?"
                        "日本語" -> "検索方法は？"
                        "中文" -> "如何搜索？"
                        "Tiếng Việt"-> "Phương pháp tìm kiếm là gì?"
                        else -> "음식이나 가게를 어떻게 검색하나요?"
                    }
                q2.text =
                    when(lang) {
                        "English" -> "Where is my cart?"
                        "日本語" -> "カートはどこ？"
                        "中文" -> "购物车在哪？"
                        "Tiếng Việt"-> "Xe đẩy đâu?"
                        else -> "담은 상품들이 어디에 있나요?"
                    }
                q3.text =
                    when(lang) {
                        "English" -> "How to use coupons?"
                        "日本語" -> "クーポンの使い方は？"
                        "中文" -> "如何使用优惠券？"
                        "Tiếng Việt"-> "Cách sử dụng phiếu giảm giá?"
                        else -> "어떻게 쿠폰을 써서 결제하나요?"
                    }
                q4.text =
                    when(lang) {
                        "English" -> "Cancel order"
                        "日本語" -> "注文をキャンセル"
                        "中文" -> "取消订单"
                        "Tiếng Việt"-> "Hủy đặt hàng"
                        else -> "주문을 취소하고 싶어요."
                    }
                q4.visibility = View.VISIBLE
            } else if (pkg == "kr.go.minwon.m") {
                q1.text =
                    when(lang) {
                        "English" -> "Print/Download certificate"
                        "日本語" -> "証明書の印刷/ダウンロード方法"
                        "中文" -> "电子证书打印方法"
                        "Tiếng Việt"-> "Cách in / tải chứng chỉ"
                        else -> "전자증명서 출력 방법 (종이/다운로드)"
                    }
                q2.text =
                    when(lang) {
                        "English" -> "Submit certificate"
                        "日本語" -> "証明書の提出方法"
                        "中文" -> "电子证书提交方法"
                        "Tiếng Việt"-> "Phương pháp nộp giấy chứng nhận"
                        else -> "전자증명서 기관 제출 방법"
                    }
                q3.text =
                    when(lang) {
                        "English" -> "Cannot find application"
                        "日本語" -> "申請履歴が見つかりません"
                        "中文" -> "找不到申请记录"
                        "Tiếng Việt"-> "Không tìm thấy lịch sử đăng ký"
                        else -> "민원 신청 내역이 확인되지 않아요."
                    }
                q4.text =
                    when(lang) {
                        "English" -> "Save order"
                        "日本語" -> "発行の保存順序"
                        "中文" -> "保存顺序"
                        "Tiếng Việt"-> "Trình tự bảo tồn phát hành"
                        else -> "발급민원 저장 순서"
                    }
                q4.visibility = View.VISIBLE
            } else if (pkg == "com.korail.talk" || pkg == "kr.co.tmoney.tia" || pkg =="com.ebcard.bustago") {
                q1.text =
                    when(lang) {
                        "English" -> "Select date/time"
                        "日本語" -> "日付と時間の選択方法は？"
                        "中文" -> "在哪里选择日期和时间？"
                        "Tiếng Việt"-> "Ngày và giờ làm cách nào?"
                        else -> "예매 날짜와 시간은 어디서 고르나요?"
                    }
                q2.text =
                    when(lang) {
                        "English" -> "Select seat and book"
                        "日本語" -> "座席選択と予約方法は？"
                        "中文" -> "如何选座和预订？"
                        "Tiếng Việt"-> "Chọn chỗ ngồi và đặt chỗ như thế nào?"
                        else -> "좌석 선택과 예매는 어떻게 하나요?"
                    }
                q3.text =
                    when(lang) {
                        "English" -> "View tickets"
                        "日本語" -> "予約したチケットを見たい"
                        "中文" -> "我想看预订的车票"
                        "Tiếng Việt"-> "Muốn xem vé đã đặt"
                        else -> "예매한 표를 보고 싶어요 (티켓함)"
                    }
                q4.visibility = View.GONE
            } else {
                q1.text =
                    when(lang) {
                        "English" -> "Main features?"
                        "日本語" -> "主な機能は？"
                        "中文" -> "主要功能？"
                        "Tiếng Việt"-> "Chức năng chính?"
                        else -> "이 화면의 주요 기능을 알려줘"
                    }
                q2.text =
                    when(lang) {
                        "English" -> "What to tap?"
                        "日本語" -> "何を押せばいい？"
                        "中文" -> "该点什么？"
                        "Tiếng Việt"-> "Nhấn cái nào thì được nhỉ?"
                        else -> "무엇을 누르면 되나요?"
                    }
                q3.visibility = View.GONE
                q4.visibility = View.GONE
            }
        }


        // 모드(mode)에 따른 뷰 보이기/숨기기
        when (mode) {
            "ASK" -> {
                textContentLayout.visibility = View.GONE
                buttonContainer.visibility = View.VISIBLE
                recommendContainer?.visibility = View.GONE

            }
            "RECOMMEND" -> {
                textContentLayout.visibility = View.GONE
                buttonContainer.visibility = View.GONE
                recommendContainer?.visibility = View.VISIBLE
            }
            else -> { // "TEXT" 모드 (결과를 띄울 때)
                textContentLayout.visibility = View.VISIBLE
                buttonContainer.visibility = View.GONE
                recommendContainer?.visibility = View.GONE

                if (message != null) contentText.text = message.trim()
            }
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val boxParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        if (mode == "RECOMMEND") {

            boxParams.gravity = Gravity.TOP or Gravity.START

            boxParams.x = currentMenuX

            boxParams.y =
                currentMenuY +
                        currentMenuHeight +
                        20

        } else {

            boxParams.gravity = Gravity.CENTER

        }


        val btnClose = textBoxView!!.findViewById<View>(R.id.btn_close_text_box)

        if (mode == "RECOMMEND") {
            btnClose.visibility = View.GONE
        }

        btnClose.setOnClickListener {
            closeTextBox()
        }

        // 직접 질문하기 버튼 (ASK 모드 및 RECOMMEND 모드 하단에 있을 경우)
        textBoxView?.findViewById<View>(R.id.btn_ask_question)?.setOnClickListener {
            val currentPkg = MyAccessibilityService.instance?.rootInActiveWindow?.packageName?.toString() ?: ""
            if (currentPkg.isNotEmpty() && currentPkg != "com.example.pbl2") {
                lastTargetPackage = currentPkg
            }
            closeMenu()
            closeTextBox()
            showSearchBar()
        }

        // 이전 질문 보기 버튼
        val btnViewHistory = textBoxView?.findViewById<TextView>(R.id.btn_view_history)
        btnViewHistory?.setOnClickListener {
            closeTextBox()
            showHistoryBox()
        }

        // 추천 질문 리스트 (q1~q4) 클릭 리스너
        val onRecommendClickListener = View.OnClickListener { view ->
            val clickedQuestion = (view as TextView).text.toString()
            Toast.makeText(this, "'$clickedQuestion' 질문을 전송합니다...", Toast.LENGTH_SHORT).show()
            closeTextBox()
            val pkg = MyAccessibilityService.instance?.rootInActiveWindow?.packageName?.toString() ?: ""
            launchCaptureActivity(clickedQuestion, pkg)
        }

        q1.setOnClickListener(onRecommendClickListener)
        q2.setOnClickListener(onRecommendClickListener)
        q3.setOnClickListener(onRecommendClickListener)
        q4.setOnClickListener(onRecommendClickListener)

        // 뷰 추가 및 애니메이션
        windowManager.addView(textBoxView, boxParams)
        textBoxView?.startAnimation(AlphaAnimation(0f, 1f).apply { duration = 300 })
    }

    // 이전 기록 보기
    private fun showHistoryBox() {
        lastTargetPackage = MyAccessibilityService.instance?.rootInActiveWindow?.packageName?.toString() ?: ""

        currentHistoryView?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
        val contextThemeWrapper = ContextThemeWrapper(this, R.style.Theme_PBL2)
        val historyView = LayoutInflater.from(contextThemeWrapper).inflate(R.layout.layout_history_box, null)

        currentHistoryView = historyView
        currentListContainer  = historyView.findViewById<LinearLayout>(R.id.history_list_container)
        currentNonLog = historyView.findViewById<TextView>(R.id.non_log)
        val btnClose = historyView.findViewById<View>(R.id.btn_close_history)

        // 닫기 버튼 설정
        btnClose.setOnClickListener {
            try { windowManager.removeView(historyView) } catch (e: Exception) {}
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        windowManager.addView(historyView, params)
        historyView.startAnimation(AlphaAnimation(0f, 1f).apply { duration = 300 })

        updateLogs()

        if(::streamManager.isInitialized){
            Log.d("LOG_DEBUG", "이전 질문 다시보기 창 열림")
            streamManager.startSteaming(UserSession.userId)
        }
    }

    private fun showHistoryDetailBox(question: String, answer: String) {
        val contextThemeWrapper = ContextThemeWrapper(this, R.style.Theme_PBL2)
        val detailView = LayoutInflater.from(contextThemeWrapper).inflate(R.layout.layout_history_detail, null)

        val tvQuestion = detailView.findViewById<TextView>(R.id.tv_detail_question)
        val tvAnswer = detailView.findViewById<TextView>(R.id.tv_detail_answer)
        val btnClose = detailView.findViewById<View>(R.id.btn_close_detail)

        // 데이터 세팅
        tvQuestion.text = question
        tvAnswer.text = answer

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        // 닫기 버튼 리스너
        btnClose.setOnClickListener {
            try { windowManager.removeView(detailView) } catch (e: Exception) {}
        }

        windowManager.addView(detailView, params)
        detailView.startAnimation(AlphaAnimation(0f, 1f).apply { duration = 300 })
    }

    // 로그 기록 불러오기
    private fun updateLogs() {
        val historyView = currentHistoryView ?: return
        historyView.post {
            val container = historyView.findViewById<LinearLayout>(R.id.history_list_container) ?: return@post
            val nonLogView = historyView.findViewById<TextView>(R.id.non_log) ?: return@post

            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val response = RetrofitClient.api.getLogs(UserSession.userId)

                    if (response.isSuccessful && response.body() != null) {
                        val LogData = response.body()!!

                        LogList.responses.value = LogData

                        val logs = LogData.toMutableList()
                        val currentPkg = if (lastTargetPackage.isNotBlank()) lastTargetPackage
                        else MyAccessibilityService.instance?.rootInActiveWindow?.packageName?.toString() ?: ""

                        val currentLogs = logs.filter { it.forgroundApp == currentPkg }.reversed()

                        container.removeAllViews()

                        if (currentLogs.isEmpty()) {
                            nonLogView.visibility = View.VISIBLE

                            val lang = getCurrentLanguage()
                            nonLogView.text=when(lang) {
                                "English" -> "No questions have been asked yet."
                                "日本語" -> "まだ質問履歴がありません。"
                                "中文" -> "暂无提问记录。"
                                "Tiếng Việt" -> "Chưa có câu hỏi nào được ghi lại."
                                else -> "이 앱에서 질문한 기록이 \n 없습니다."
                            }
                        } else {
                            nonLogView.visibility = View.GONE

                            // 리스트 아이템 추가
                            currentLogs.forEach { (question, answer, _) ->
                                val itemView = TextView(this@FloatingService).apply {
                                    text = question
                                    textSize = 18f
                                    setTextColor(Color.BLACK)
                                    gravity = Gravity.CENTER
                                    setBackgroundResource(R.drawable.bg_recommend_btn)

                                    val params = LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT
                                    )
                                    params.setMargins(0, 0, 0, 25)
                                    layoutParams = params
                                    setPadding(40, 45, 40, 45)

                                    setOnClickListener {
                                        try {
                                            windowManager.removeView(historyView)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                        showHistoryDetailBox(question, answer)
                                    }
                                }
                                container.addView(itemView)
                            }
                            container.requestLayout()
                            container.invalidate()
                        }
                    } else {
                        Log.e("LOG_DEBUG", "${response.code()}")
                    }
                } catch (e: Exception) {
                    Log.e("LOG_DEBUG", "${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    private fun showSearchBar() {
        searchBarView = LayoutInflater.from(this)
            .inflate(R.layout.layout_search_bar, null)

        val editSearch = searchBarView!!.findViewById<EditText>(R.id.edit_search)
        val btnSendText = searchBarView!!.findViewById<TextView>(R.id.btn_send_text)
        val btnMic = searchBarView!!.findViewById<View>(R.id.btn_mic)
        val btnClose = searchBarView!!.findViewById<TextView>(R.id.btn_close_search)

        // --- 언어 설정 확인 (한국어가 아니면 모두 영어 처리) ---
        val lang = getCurrentLanguage()
        val isKorean = (lang == "Korean" || lang == "한국어")

        // 1. 닫기 버튼
        btnClose.text = if (isKorean) "닫기" else "Close"

        // 2. 검색창 힌트
        editSearch.hint = if (isKorean) "질문을 입력하세요..." else "Enter your question..."

        // 3. 검색 버튼
        btnSendText.text = if (isKorean) "검색" else "Search"
        // ----------------------------------------------

        // 검색 버튼 클릭 시
        btnSendText.setOnClickListener {
            val question = editSearch.text.toString()
            if (question.isNotBlank()) {
                val toastMsg = if (isKorean) "질문을 전송합니다..." else "Sending question..."
                Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()

                closeSearchBar()
                val pkg = MyAccessibilityService.instance?.rootInActiveWindow?.packageName?.toString() ?: ""
                launchCaptureActivity(question, pkg)
            }
        }

        btnMic.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val downMsg = if (isKorean) "누른 채로 말씀해 주세요..." else "Speak while holding down..."
                    Toast.makeText(this, downMsg, Toast.LENGTH_SHORT).show()

                    btnMic.isPressed = true
                    try {
                        currentAudioFile = recorder.startRecording()
                    } catch (e: Exception) {
                        Log.e("VOICE_RESPONSE", "${e.message}", e)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val upMsg = if (isKorean) "음성을 분석 중입니다..." else "Analyzing voice..."
                    Toast.makeText(this, upMsg, Toast.LENGTH_SHORT).show()

                    btnMic.isPressed = false
                    try {
                        recorder.stopRecording()
                    } catch (e: Exception) {
                        Log.e("VOICE_RESPONSE", "${e.message}", e)
                    }

                    val audioFile = currentAudioFile
                    val pkg = MyAccessibilityService.instance?.rootInActiveWindow?.packageName?.toString() ?: ""

                    if (audioFile != null && audioFile.exists()) {
                        sendVoiceToServer(audioFile, pkg)
                    }
                    closeSearchBar()
                    view.performClick()
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    btnMic.isPressed = false
                    recorder.stopRecording()
                    closeSearchBar()
                    true
                }
                else -> false
            }
        }

        btnClose.setOnClickListener {
            closeSearchBar()
        }

        val searchParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        searchParams.gravity = Gravity.TOP
        searchParams.y = 150

        windowManager.addView(searchBarView, searchParams)

        editSearch.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        editSearch.postDelayed({
            imm.showSoftInput(editSearch, InputMethodManager.SHOW_IMPLICIT)
        }, 100)
    }

    private fun sendVoiceToServer(audioFile: File, pkgName: String) {
        val requestFile = audioFile.asRequestBody("audio/mp3".toMediaTypeOrNull())
        val audioPart = MultipartBody.Part.createFormData("audioFile", audioFile.name, requestFile)

        val pkgPart = pkgName.toRequestBody("text/plain".toMediaTypeOrNull())

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val response = RetrofitClient.api.uploadVoice(audioPart, pkgPart)
                if (response.isSuccessful && response.body() != null) {
                    val geminiResult = response.body()
                    val voiceText = geminiResult?.question ?: ""

                    val pkg = MyAccessibilityService.instance?.rootInActiveWindow?.packageName?.toString() ?: ""
                    Log.d("VOICE_RESPONSE", "인식된 질문: $voiceText")
                    // 캡처 실행하여 서버로 전송
                    launchCaptureActivity(voiceText, pkg)
                } else {
                    Log.e("VOICE_RESPONSE", "서버 에러 발생")
                }
            } catch (e: Exception) {
                Log.e("VOICE_RESPONSE", "${e.message}", e)
            }
        }
    }

    private fun closeMenu() {
        if (isMenuOpen) {
            menuView?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
            returnAppView?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
            closeTextBox()
            closeSearchBar()
            menuView = null
            returnAppView = null
            isMenuOpen = false
            lastCloseTime = System.currentTimeMillis()
        }
    }

    private fun closeTextBox() {
        tts?.stop() // 창을 닫으면 읽어주던 음성도 멈춤
        textBoxView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e("LLM_MESSAGE", "${e.message}", e)
            }
            textBoxView = null
        }
    }

    private fun closeSearchBar() {
        searchBarView?.let { try { windowManager.removeView(it) } catch (e: Exception) {}; searchBarView = null }
    }

    private fun startMenuAnimation(view: View, buttonOnRight: Boolean) {
        val pivotX = if (buttonOnRight) 1.0f else 0.0f
        val animSet = AnimationSet(true).apply {
            addAnimation(ScaleAnimation(0f, 1f, 0f, 1f, Animation.RELATIVE_TO_SELF, pivotX, Animation.RELATIVE_TO_SELF, 0.5f))
            addAnimation(AlphaAnimation(0f, 1f))
            duration = 250
            interpolator = OvershootInterpolator(1.2f)
        }
        view.startAnimation(animSet)
    }

    private fun updateScreenSize() {
        val size = Point()
        windowManager.defaultDisplay.getRealSize(size)
        screenWidth = size.x
        screenHeight = size.y
    }

    override fun onDestroy() {
        super.onDestroy()

        if (::buttonView.isInitialized) {
            try {
                windowManager.removeView(buttonView)
            } catch (e: Exception) {
            }
        }

        closeMenu()
        closeTextBox()
        closeSearchBar()
        streamManager.stopStreaming()

        try {
            unregisterReceiver(ocrResultReceiver)
        } catch (e: Exception) {
        }

        tts?.stop()
        tts?.shutdown()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            mode = it.getStringExtra("mode")
            questionText = it.getStringExtra("question")
            targetPkgName = it.getStringExtra("pkg")
            targetLang = it.getStringExtra("lang") // 전달받은 언어 저장
        }
        return START_STICKY
    }

    // 캡쳐 (부분 화면) 요청
    private fun captureOnceCrop() {

        startOverlay{rect->
            serviceScope.launch(Dispatchers.Default){
                Log.d("FLOW", "OCR 실행")
                // 미디어프로덕션 - OCR
                if (rect == null) {
                    return@launch
                }
                Log.d("FLOW_OCR", "rect 정상 = $rect")
                val intent = Intent(applicationContext, CaptureActivity::class.java).apply {
                    putExtra("mode", "CROP")
                    putExtra("target_rect", rect)
                    putExtra("lang", getCurrentLanguage())
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                withContext(Dispatchers.Main) {
                    startActivity(intent)
                }
            }
        }
    }

    // 캡쳐 (전체 화면) 요청
    private fun captureOnceAll() {
        val service = MyAccessibilityService.instance
        // 접근성 서비스 확인
        if (service == null) {
            Toast.makeText(this, "접근성 서비스가 꺼져있어요", Toast.LENGTH_SHORT).show()
            return
        }
        Handler(Looper.getMainLooper()).postDelayed({
            val texts = service.getAllTextNow()

            // 유효한 텍스트 필터링
            val validTexts = texts.filter {
                val text = it.trim()

                text.length > 5 &&
                        text.contains(" ") &&
                        !text.contains("http") &&
                        !text.contains(".kr") &&
                        !text.contains(".com") &&
                        !text.contains("/") &&
                        !text.contains("secure", ignoreCase = true)
            }
            val totalLength = validTexts.sumOf { it.length }
            val count = validTexts.size

            // 텍스트 충분 여부 판단
            val hasEnoughText = count >= 3 && totalLength >= 100

            if (hasEnoughText) {
                // 텍스트 충분 → 텍스트만 전송
                val finalText = texts.joinToString("\n")

                sendToServer(finalText, "summary", null) { answer ->
                    Log.d("API_RESULT", answer)
                    sendResultToUI(answer)
                }
            } else {
                // 텍스트 부족 → 화면 캡처 + OCR
                val intent = Intent(this, CaptureActivity::class.java).apply {
                    putExtra("mode", "ALL")
                    putExtra("lang", getCurrentLanguage())
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            }

        }, 300)
    }

    // 오버레이를 실제로 화면 위에 띄우는 함수
    private fun startOverlay(onSelected: (Rect) -> Unit) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "오버레이 권한 필요", Toast.LENGTH_SHORT).show()
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            startActivity(intent)
            return
        }
        if (overlayView != null) return
        val overlay = OverlayView(this)
        overlayView = overlay

        // 전체 화면을 덮는 투명 레이어
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        overlay.setOnTouchListener { v, event ->
            when (event.action) {
                // 드래그 시작
                MotionEvent.ACTION_DOWN -> {
                    clicks.start = Pair(event.rawX, event.rawY)
                }
                // 드래그 종료
                MotionEvent.ACTION_UP -> {
                    Log.d("FLOW", "Overlay 선택 완료")
                    clicks.end = Pair(event.rawX, event.rawY)
                    selectionRect = Rect(
                        Math.min(clicks.start.first, clicks.end.first).toInt(),
                        Math.min(clicks.start.second, clicks.end.second).toInt(),
                        Math.max(clicks.start.first, clicks.end.first).toInt(),
                        Math.max(clicks.start.second, clicks.end.second).toInt()
                    )
                    Log.d("FLOW", "rect=$selectionRect")
                    onSelected(selectionRect!!)
                    windowManager.removeView(overlay)
                    overlayView = null
                }
            }
            true
        }
        windowManager.addView(overlay, params)
    }

    // 화면 위 레이어로 터치 감지
    class OverlayView(context: Context) : View(context)

    fun sendToServer(text: String, mode: String, bitmap: Bitmap?, packageName: String? = null, onResult: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val base64Image = bitmap?.let { bitmapToBase64(it) }
                val currentLang = getCurrentLanguage()

                val request = QuestionRequest(
                    UserSession.userId,
                    text,
                    mode,
                    base64Image,
                    packageName,
                    currentLang
                )
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

    // 이미지를 문자열(Base64)로 변환하는 함수
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

    private fun sendResultToUI(text: String) {
        val intent = Intent("OCR_RESULT_ACTION")
        intent.setPackage(packageName)
        intent.putExtra("result", text)
        sendBroadcast(intent)
    }

    // 캡처 액티비티를 실행하는 전용 함수
    private fun launchCaptureActivity(question: String, pkg: String) {
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, CaptureActivity::class.java).apply {
                putExtra("mode", "DIRECT")
                putExtra("question", question)
                putExtra("pkg", pkg)
                putExtra("lang", getCurrentLanguage())
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        }, 500)
    }

    // 서비스가 시작될 때 전달받은 언어가 있다면 그것을 사용, 없다면 설정값에서 가져옴
    private fun getCurrentLanguage(): String {
        if (!targetLang.isNullOrEmpty()) return targetLang!!

        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        return prefs.getString("APP_LANG", "한국어") ?: "한국어"
    }
}