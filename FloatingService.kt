//FloatingService.kt 수정사항
//-onMenu()함수 for문
//-showTextBox()함수

package com.example.pbl2

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
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
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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

class FloatingService : Service() {
    private var overlayView: View? = null
    private var selectionRect: Rect?=null
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    private lateinit var windowManager: WindowManager
    private lateinit var buttonView: View
    private lateinit var buttonParams: WindowManager.LayoutParams

    private var menuView: View? = null
    private var returnAppView: View? = null
    private var textBoxView: View? = null
    private var searchBarView: View? = null

    private lateinit var summaryAll: TextView
    private lateinit var summarySection: TextView

    private var isMenuOpen = false
    private var screenWidth = 0
    private var screenHeight = 0
    private var lastCloseTime = 0L

    data class ClickRange(var start: Pair<Float, Float> = Pair(0f,0f),var end: Pair<Float, Float> = Pair(0f,0f))
    val clicks= ClickRange()
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

            if (child.id == R.id.summaryAll||child.id==R.id.summarySection) continue

            //------------------------수정한 부분----------------------------
            child.setOnClickListener {
                when (i) {
                    2 -> { // '직접 질문' 메뉴
                        showTextBox(mode = "ASK", shouldCloseMenu = false)
                    }
                    3 -> { // '추천 질문' 메뉴 (새로 추가됨)
                        showTextBox(mode = "RECOMMEND", shouldCloseMenu = false)
                    }
                    else -> { // '부분 해석', '화면 요약' 등 일반 메뉴
                        showTextBox(mode = "TEXT", shouldCloseMenu = true)
                    }
                }
            }
            //------------------------수정한 부분----------------------------


            /* 기존코드
            menuLayout.getChildAt(i).setOnClickListener {
                showTextBox(true, false)
            }
            */
        }

        /* 기존 코드
        for (i in 0 until menuLayout.childCount) {
            menuLayout.getChildAt(i).setOnClickListener {
                if (i == 2) {
                    // [3번째 메뉴 클릭] 메뉴 유지 + 버튼 2개 텍스트박스
                    showTextBox(showButtons = true, shouldCloseMenu = false)
                } else {
                    // [1, 2번째 메뉴 클릭] 메뉴 닫기 + 일반 텍스트박스
                    showTextBox(showButtons = false, shouldCloseMenu = true)
                }
            }
        }*/

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


    //--------------------------------------------------------------------------
    //private fun showTextBox(...)에서 'showButtons: Boolean'을  'mode: String'으로 변경
    private fun showTextBox(mode: String, shouldCloseMenu: Boolean) {
        if (shouldCloseMenu) closeMenu()
        closeTextBox()

        textBoxView = LayoutInflater.from(this).inflate(R.layout.layout_text_box, null)

        val contentText = textBoxView!!.findViewById<TextView>(R.id.content_text)
        val buttonContainer = textBoxView!!.findViewById<LinearLayout>(R.id.button_container)

        // --------------코드 추가 : recommendContainer 변수, if문을 when(mode)로 변경---------------------------------------
        val recommendContainer = textBoxView!!.findViewById<LinearLayout>(R.id.recommend_container)

        when (mode) {
            "ASK" -> {
                contentText.visibility = View.GONE
                buttonContainer.visibility = View.VISIBLE
                if (recommendContainer != null) recommendContainer.visibility = View.GONE
            }
            "RECOMMEND" -> {
                contentText.visibility = View.GONE
                buttonContainer.visibility = View.GONE
                if (recommendContainer != null) recommendContainer.visibility = View.VISIBLE
            }
            else -> {
                contentText.visibility = View.VISIBLE
                buttonContainer.visibility = View.GONE
                if (recommendContainer != null) recommendContainer.visibility = View.GONE
            }
        }
        // --------------------------------------------------------------------




        /* 기존 코드
        if (showButtons) {
            // 버튼 2개 모드
            contentText.visibility = View.GONE
            buttonContainer.visibility = View.VISIBLE
        } else {
            // 일반 텍스트 모드
            contentText.visibility = View.VISIBLE
            buttonContainer.visibility = View.GONE
        }
         */

        val boxParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        boxParams.gravity = Gravity.CENTER

        // X 버튼: 박스만 닫기
        textBoxView!!.findViewById<View>(R.id.btn_close_text_box).setOnClickListener {
            closeTextBox()
        }

        // 질문 하시겠습니까? 클릭 시
        textBoxView!!.findViewById<View>(R.id.btn_ask_question).setOnClickListener {
            closeMenu() // 질문 클릭 시는 메뉴도 함께 닫음
            closeTextBox()
            showSearchBar()
        }

        windowManager.addView(textBoxView, boxParams)
        textBoxView!!.startAnimation(AlphaAnimation(0f, 1f).apply { duration = 300 })
    }

    private fun showSearchBar() {
        closeSearchBar()
        searchBarView = LayoutInflater.from(this).inflate(R.layout.layout_search_bar, null)

        val searchParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, // 키보드 입력을 위해 Focusable 상태 유지
            PixelFormat.TRANSLUCENT
        )
        searchParams.gravity = Gravity.TOP
        searchParams.y = 150

        // 닫기 버튼 로직
        searchBarView!!.findViewById<View>(R.id.btn_close_search).setOnClickListener {
            closeSearchBar()
        }

        windowManager.addView(searchBarView, searchParams)
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
        textBoxView?.let { try { windowManager.removeView(it) } catch (e: Exception) {}; textBoxView = null }
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
        if (::buttonView.isInitialized) try { windowManager.removeView(buttonView) } catch (e: Exception) {}
        closeMenu()
        closeTextBox()
        closeSearchBar()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // 시스템에 의해 종료되어도 다시 살아나게 함
    }

    // 캡쳐 (부분 화면) 요청
    private fun captureOnceCrop() {
        Log.d("FLOW", " captureOnceCrop 진입")
        val service = MyAccessibilityService.instance?:return   // 전역 instance 사용
        startOverlay{rect->
            serviceScope.launch(Dispatchers.Default){
                /* 접근성서비스 - 텍스트 추출
                val texts=service.getDragTextNow(rect)
                if(texts.isNotEmpty()){
                    texts.forEach { Log.d("OCR_RESULT", it) }
                }*/
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
        val service = MyAccessibilityService.instance   // 전역 instance 사용
        if (service == null) {  // 서비스 안 켜져 있으면 종료
            Toast.makeText(this, "접근성 서비스가 꺼져있어요", Toast.LENGTH_SHORT).show()
            return
        }
        // 화면 안정화 위해 딜레이
        Handler(Looper.getMainLooper()).postDelayed({
            val texts = service.getAllTextNow() // AccessibilityService에서 전체 텍스트 가져오기
            val validTexts = texts.filter {
                val text = it.trim()
                text.length > 5 && text.contains(" ") && !text.contains("http") && !text.contains(".kr") && !text.contains(".com") && !text.contains("/") && !text.contains("secure", true)
            }
            val totalLength = validTexts.sumOf { it.length }
            val count = validTexts.size

            val shouldCapture = count >= 3 && totalLength >= 100

            if (!shouldCapture) {    // 접근성 서비스로 얻은 텍스트 출력
                Log.d("FLOW", "접근성 사용")

                texts.forEach { Log.d("OCR_RESULT", texts.joinToString("\n")) } // 텍스트 출력
                return@postDelayed
            }else{  // 접근성 서비스로 얻은 텍스트 부족시, MediaProjection 실행
                Log.d("FLOW", "OCR 실행")
                val intent = Intent(this, CaptureActivity::class.java).apply {
                    putExtra("mode", "ALL")
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
}