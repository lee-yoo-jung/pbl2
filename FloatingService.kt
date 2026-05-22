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
import com.example.pbl2.access_ocr.OverlayView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import org.json.JSONObject
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.widget.RelativeLayout
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
import android.text.SpannableStringBuilder
import android.widget.Button
import android.text.style.ForegroundColorSpan
import android.text.Spannable
import android.text.style.StyleSpan
import android.graphics.Typeface


class FloatingService : Service() {
    private var overlayView: View? = null
    private var selectionRect: Rect?=null
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    private lateinit var windowManager: WindowManager
    private lateinit var buttonView: View
    private lateinit var buttonParams: WindowManager.LayoutParams

    private lateinit var popupView: View
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
    private var lastTargetPackage: String = "" // 마지막으로 감지된 외부 앱 패키지명

    data class ClickRange(var start: Pair<Float, Float> = Pair(0f,0f),var end: Pair<Float, Float> = Pair(0f,0f))
    val clicks= ClickRange()


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
                        Log.d("HIGHLIGHT_COORDS", "좌표: $coords") // ex. 좌표: {"x":500, "y":874}
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
                else -> "화면요약"
            }
        summarySection.text =
            when (lang) {
                "English" -> "Crop"
                "日本語" -> "部分翻訳"
                "中文" -> "部分翻译"
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

        val askMenu = menuLayout.getChildAt(2) as? TextView
        val recommendMenu = menuLayout.getChildAt(3) as? TextView
        askMenu?.text =
            when(lang) {
                "English" -> "Ask Directly"
                "日本語" -> "直接質問"
                "中文" -> "直接提问"
                else -> "직접질문"
            }
        recommendMenu?.text =
            when(lang) {
                "English" -> "Recommend"
                "日本語" -> "おすすめ質問"
                "中文" -> "推荐提问"
                else -> "추천질문"
            }

        // Recommend 버튼 글자 크기 조정
        if (lang == "English") {recommendMenu?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
            // askMenu(Ask Directly)는 크기 조절을 하지 않아 기본 크기 유지
        } else {
            recommendMenu?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
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

        for (i in 0 until menuLayout.childCount) {
            val child = menuLayout.getChildAt(i)
            if (child.id == R.id.summaryAll || child.id == R.id.summarySection) continue

            child.setOnClickListener {
                val mode = when (i) {
                    2 -> "ASK"
                    3 -> "RECOMMEND"
                    else -> "TEXT"
                }
                showTextBox(mode = mode, shouldCloseMenu = true)
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

        menuView!!.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) { closeMenu(); true } else false
        }

        // 하단 앱으로 돌아가기 버튼
        returnAppView = LayoutInflater.from(this).inflate(R.layout.layout_return_app, null)
        val returnBtnText = when (lang) {
            "English" -> "Return to App"
            "日本語" -> "アプリに戻る"
            "中文" -> "返回应用"
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
    }

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

        val lang = getCurrentLanguage()
        btnhighlight.text = when(lang) {
            "English" -> "Highlight"
            "日本語" -> "強調"
            "中文" -> "突出显示"
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
                else -> "질문 하시겠습니까?"
            }
        btnHistory.text =
            when(lang) {
                "English" -> "View history"
                "日本語" -> "以前の質問を見る"
                "中文" -> "查看历史记录"
                else -> "이전 질문을 보겠습니까?"
            }

        if (mode == "RECOMMEND") {
            recommendContainer.visibility = View.VISIBLE
            val pkg = MyAccessibilityService.instance?.rootInActiveWindow?.packageName?.toString() ?: ""

            if (pkg == "com.sampleapp" || pkg == "com.fineapp.yogiyo") {
                q1.text =
                    when(lang) {
                        "English" -> "How to search?"
                        "日本語" -> "検索方法は？"
                        "中文" -> "如何搜索？"
                        else -> "음식이나 가게를 어떻게 검색하나요?"
                    }
                q2.text =
                    when(lang) {
                        "English" -> "Where is my cart?"
                        "日本語" -> "カートはどこ？"
                        "中文" -> "购物车在哪？"
                        else -> "담은 상품들이 어디에 있나요?"
                    }
                q3.text =
                    when(lang) {
                        "English" -> "How to use coupons?"
                        "日本語" -> "クーポンの使い方は？"
                        "中文" -> "如何使用优惠券？"
                        else -> "어떻게 쿠폰을 써서 결제하나요?"
                    }
                q4.text =
                    when(lang) {
                        "English" -> "Cancel order"
                        "日本語" -> "注文をキャンセル"
                        "中文" -> "取消订单"
                        else -> "주문을 취소하고 싶어요."
                    }
                q4.visibility = View.VISIBLE
            } else if (pkg == "kr.go.minwon.m") {
                q1.text =
                    when(lang) {
                        "English" -> "Print/Download certificate"
                        "日本語" -> "証明書の印刷/ダウンロード方法"
                        "中文" -> "电子证书打印方法"
                        else -> "전자증명서 출력 방법 (종이/다운로드)"
                    }
                q2.text =
                    when(lang) {
                        "English" -> "Submit certificate"
                        "日本語" -> "証明書の提出方法"
                        "中文" -> "电子证书提交方法"
                        else -> "전자증명서 기관 제출 방법"
                    }
                q3.text =
                    when(lang) {
                        "English" -> "Cannot find application"
                        "日本語" -> "申請履歴が見つかりません"
                        "中文" -> "找不到申请记录"
                        else -> "민원 신청 내역이 확인되지 않아요."
                    }
                q4.text =
                    when(lang) {
                        "English" -> "Save order"
                        "日本語" -> "発行の保存順序"
                        "中文" -> "保存顺序"
                        else -> "발급민원 저장 순서"
                    }
                q4.visibility = View.VISIBLE
            } else if (pkg == "com.korail.talk" || pkg == "kr.co.tmoney.tia") {
                q1.text =
                    when(lang) {
                        "English" -> "Select date/time"
                        "日本語" -> "日付と時間の選択方法は？"
                        "中文" -> "在哪里选择日期和时间？"
                        else -> "예매 날짜와 시간은 어디서 고르나요?"
                    }
                q2.text =
                    when(lang) {
                        "English" -> "Select seat and book"
                        "日本語" -> "座席選択と予約方法は？"
                        "中文" -> "如何选座和预订？"
                        else -> "좌석 선택과 예매는 어떻게 하나요?"
                    }
                q3.text =
                    when(lang) {
                        "English" -> "View tickets"
                        "日本語" -> "予約したチケットを見たい"
                        "中文" -> "我想看预订的车票"
                        else -> "예매한 표를 보고 싶어요 (티켓함)"
                    }
                q4.visibility = View.GONE
            } else {
                q1.text =
                    when(lang) {
                        "English" -> "Main features?"
                        "日本語" -> "主な機能は？"
                        "中文" -> "主要功能？"
                        else -> "이 화면의 주요 기능을 알려줘"
                    }
                q2.text =
                    when(lang) {
                        "English" -> "What to tap?"
                        "日本語" -> "何を押せばいい？"
                        "中文" -> "该点什么？"
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

                val btnDirect = textBoxView!!.findViewById<View>(R.id.btn_ask_question)

                btnDirect.setOnClickListener {
                    val currentPkg = MyAccessibilityService.instance?.rootInActiveWindow?.packageName?.toString() ?: ""

                    if (currentPkg.isNotEmpty() && currentPkg != "com.example.pbl2") {
                        lastTargetPackage = currentPkg
                        Log.d("FloatingService", "Target App Saved: $lastTargetPackage")
                    }
                    showSearchBar()
                }
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
        boxParams.gravity = Gravity.CENTER

        // 버튼 클릭 이벤트 처리
        textBoxView!!.findViewById<View>(R.id.btn_close_text_box).setOnClickListener {
            closeTextBox()
        }

        textBoxView!!.findViewById<View>(R.id.btn_ask_question).setOnClickListener {
            // 검색창을 띄우기 전, 현재 화면의 패키지명을 미리 저장
            val currentPkg = MyAccessibilityService.instance?.rootInActiveWindow?.packageName?.toString() ?: ""
            if (currentPkg.isNotEmpty() && currentPkg != "com.example.pbl2") {
                lastTargetPackage = currentPkg
            }

            closeMenu()
            closeTextBox()
            showSearchBar()
        }

        val onRecommendClickListener = View.OnClickListener { view ->
            val clickedQuestion = (view as TextView).text.toString()
            Toast.makeText(this, "'$clickedQuestion' 질문을 전송합니다...", Toast.LENGTH_SHORT).show()
            closeTextBox()

            val pkg = MyAccessibilityService.instance?.rootInActiveWindow?.packageName?.toString() ?: ""

            // 기존 sendToServer 대신 캡처 실행!
            launchCaptureActivity(clickedQuestion, pkg)
        }


        q1.setOnClickListener(onRecommendClickListener)
        q2.setOnClickListener(onRecommendClickListener)
        q3.setOnClickListener(onRecommendClickListener)
        q4.setOnClickListener(onRecommendClickListener)


        windowManager.addView(textBoxView, boxParams)
        textBoxView!!.startAnimation(AlphaAnimation(0f, 1f).apply { duration = 300 })
    }


    private fun showSearchBar() {

        closeSearchBar()

        searchBarView = LayoutInflater.from(this)
            .inflate(R.layout.layout_search_bar, null)

        val editSearch =
            searchBarView!!.findViewById<EditText>(R.id.edit_search)

        val btnSendText =
            searchBarView!!.findViewById<ImageView>(R.id.btn_send_text)

        val btnMic =
            searchBarView!!.findViewById<ImageView>(R.id.btn_mic)

        val btnClose =
            searchBarView!!.findViewById<View>(R.id.btn_close_search)

        // 1. 돋보기(전송) 버튼 클릭 시
        btnSendText.setOnClickListener {
            val question = editSearch.text.toString()
            if (question.isNotBlank()) {
                Toast.makeText(this, "질문을 전송합니다...", Toast.LENGTH_SHORT).show()
                closeSearchBar()
                val pkg = MyAccessibilityService.instance?.rootInActiveWindow?.packageName?.toString() ?: ""

                // 캡처 실행
                launchCaptureActivity(question, pkg)
            }
        }



        // 2. 마이크 버튼 클릭 시: 음성 인식(STT) 시작
        btnMic.setOnClickListener {

            Toast.makeText(
                this,
                "말씀해 주세요...",
                Toast.LENGTH_SHORT
            ).show()

            startVoiceRecognition()
        }

        // 3. 닫기 버튼
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
    }


    // 음성 인식(STT)을 실행하고 서버로 보내는 함수
    private fun startVoiceRecognition() {

        val speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(this)

        // 1. 언어 코드 결정
        val lang = getCurrentLanguage()
        val sttLangCode = when (lang) {
            "English" -> "en-US"
            "日本語" -> "ja-JP"
            "中文" -> "zh-CN"
            else -> "ko-KR"
        }

        // 2. 인텐트 설정 (하나의 intent 객체에 모든 설정을 담아야 합니다)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)

            // 이 설정들을 모두 추가해야 엔진이 언어를 강제로 변경합니다.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, sttLangCode)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, sttLangCode)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true) // 현재 언어만 반환하도록 강제

            // 추가: 구글 엔진에게 현재 앱의 패키지명을 알려주어 우선순위를 높임
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }

        // 3. 리스너 설정
        speechRecognizer.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {}

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                Toast.makeText(
                    this@FloatingService,
                    "음성 인식 실패 (에러코드: $error)",
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val voiceText = matches[0]
                    // 결과 확인용 로그
                    Log.d("STT_RESULT", "언어($sttLangCode) 인식결과: $voiceText")
                    Toast.makeText(this@FloatingService, "인식됨: $voiceText", Toast.LENGTH_SHORT).show()
                    closeSearchBar()
                    val pkg = MyAccessibilityService.instance?.rootInActiveWindow?.packageName?.toString() ?: ""

                    // 캡처 실행하여 서버로 전송
                    launchCaptureActivity(voiceText, pkg)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer.startListening(intent)
    }

    private fun closeMenu() {
        if (isMenuOpen) {
            menuView?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
            returnAppView?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
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
        return START_STICKY // 시스템에 의해 종료되어도 다시 살아나게 함
    }

    // 캡쳐 (부분 화면) 요청
    private fun captureOnceCrop() {
        Log.d("FLOW", " captureOnceCrop 진입")
        val service = MyAccessibilityService.instance?:return   // 전역 instance 사용
        startOverlay{rect->
            serviceScope.launch(Dispatchers.Default){
                Log.d("FLOW", "OCR 실행")
                // 미디어프로덕션 - OCR
                if (rect == null) {
                    Log.e("FLOW_OCR", "X rect null → 실행 안함")
                    return@launch
                }
                Log.d("FLOW_OCR", "rect 정상 = $rect")
                val intent = Intent(applicationContext, CaptureActivity::class.java).apply {
                    putExtra("mode", "CROP")
                    putExtra("target_rect", rect)
                    putExtra("lang", getCurrentLanguage())
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                Log.d("FLOW", "CaptureActivity 실행 직전")
                withContext(Dispatchers.Main) {
                    Log.d("FLOW", "startActivity 호출")
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

            // 1. 유효한 텍스트 필터링
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

            // 2. 텍스트 충분 여부 판단
            val hasEnoughText = count >= 3 && totalLength >= 100

            if (hasEnoughText) {
                // 3-1. 텍스트 충분 → 텍스트만 전송
                Log.d("FLOW", "접근성 텍스트 충분 -> 텍스트만 전송")

                val finalText = texts.joinToString("\n")

                sendToServer(finalText, "summary", null) { answer ->
                    Log.d("API_RESULT", answer)
                    sendResultToUI(answer)
                }

            } else {
                // 3-2. 텍스트 부족 → 화면 캡처 + OCR
                Log.d("FLOW", "접근성 텍스트 부족 -> 화면 캡처 및 이미지 전송 실행")
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
        if (overlayView != null) return // 중복 방지
        val overlay = OverlayView(this) // OverlayView 생성
        overlayView = overlay

        // 전체 화면을 덮는 투명 레이어
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY // 다른 앱 위에도 뜨는 레이어
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

    fun sendToServer(text: String, mode: String, bitmap: Bitmap?, packageName: String? = null, onResult: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val base64Image = bitmap?.let { bitmapToBase64(it) }
                val currentLang = getCurrentLanguage() // 추가됨

                // request 포장에 언어 추가
                val request = QuestionRequest(text, mode, base64Image, packageName, currentLang)
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
                putExtra("mode", "DIRECT") // 캡처 모드를 직접질문(DIRECT)으로 설정
                putExtra("question", question)
                putExtra("pkg", pkg)
                putExtra("lang", getCurrentLanguage())
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        }, 500)
    }

    private fun getCurrentLanguage(): String {// 1. 서비스가 시작될 때 전달받은 언어가 있다면 그것을 사용
        if (!targetLang.isNullOrEmpty()) return targetLang!!

        // 2. 없다면 설정값에서 가져옴
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        return prefs.getString("APP_LANG", "한국어") ?: "한국어"
    }


}

/*
val customButton = findViewById<Button>(R.id.btn_custom_action)

// 필요할 때 표시
customButton.visibility = View.VISIBLE

customButton.setOnClickListener {

    // 버튼 클릭 시 동작
    Toast.makeText(this, "버튼 클릭!", Toast.LENGTH_SHORT).show()
}
*/