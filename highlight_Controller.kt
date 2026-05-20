package com.example.pbl2

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager

// 화면을 어둡게, 특정 부분만 밝게하기
class SpotlightView(context: Context) : View(context) {
    var SpotClick: (() -> Unit)? = null
    var spots: List<Triple<Float, Float, Float>> = emptyList()  // 특정 부분 정보

    // 검은색 반투명
    private val dimPaint = Paint().apply {
        color = Color.BLACK
        alpha = 100
    }
    // 검은색 반투명에 하얀 스팟
    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    // 화면에 그려질 때 실행
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 전체 화면 어둡게
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

        // 각 좌표에 원 그리기
        for (spot in spots) {
            canvas.drawCircle(spot.first, spot.second, spot.third, clearPaint)
        }
    }

    // 원 내의 터치이벤트 감지
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            // 사용자가 누른 좌표
            val touchX = event.x
            val touchY = event.y

            for (spot in spots) {
                val centerX = spot.first
                val centerY = spot.second
                val radius = spot.third

                // 터치한 위치와 원 중심 사이 거리 계산
                val distance = Math.sqrt(
                    ((touchX - centerX) * (touchX - centerX) +
                            (touchY - centerY) * (touchY - centerY)).toDouble()
                )
                // 원 내부 클릭 = 터치한 위치와 원 중심 거리 <= 반지름
                if (distance <= radius) {
                    SpotClick?.invoke() // 등록된 함수 실행 remove()
                    return true
                }
            }
        }
        return true
    }
}

// X 버튼 + 터치 감지
class CloseButtonView(context: Context) : View(context) {
    var onClose: (() -> Unit)? = null
    // X 글자를 그리기 위한 설정
    private val paint = Paint().apply {
        color = Color.WHITE
        textSize = 60f
        isAntiAlias = true  // 글자 부드럽게
    }
    // 그리기
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawText("X", 20f, 80f, paint)
    }
    // 터치 이벤트 처리
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 눌린 순간, 오버레이 제거
        if (event.action == MotionEvent.ACTION_DOWN) {
            onClose?.invoke()
            return true
        }
        return false
    }
}

// Spotlight+Close 버튼 제어
class OverlayManager(private val context: Context) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager   // 시스템 레벨 화면
    // 두 개의 View
    private val spotlightView = SpotlightView(context)
    private val closeView = CloseButtonView(context)

    private var isAdded = false // 이미 화면에 띄워져 있는지

    fun show(spots: List<Triple<Float, Float, Float>>) {
        spotlightView.spots = spots
        spotlightView.invalidate()

        if (!isAdded) {
            val spotlightParams = WindowManager.LayoutParams(
                MATCH_PARENT,
                MATCH_PARENT,   // 화면 전체 덮기
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,    // 다른 앱 위에 띄우는 오버레이
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,  // 터치 이벤트 통과
                PixelFormat.TRANSLUCENT // 투명도
            )

            windowManager.addView(spotlightView, spotlightParams)

            val closeParams = WindowManager.LayoutParams(
                150,
                150,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )

            closeParams.gravity = Gravity.TOP or Gravity.END    // 오른쪽 위

            // X 누르면, 제거
            closeView.onClose = {
                remove()
            }

            // 원 내 공간을 누르면, 제거
            spotlightView.SpotClick = {
                remove()
            }

            windowManager.addView(closeView, closeParams)

            isAdded = true
        }
    }
    // 오버레이 제거
    fun remove() {
        if (isAdded) {
            windowManager.removeView(spotlightView)
            windowManager.removeView(closeView)
            isAdded = false
        }
    }
}

// Spotlight 띄우기
class highlight_Controller(x: Float, y: Float, private val context: Context) {
    private val overlay = OverlayManager(context)   // spotlight
    init {
        val spots = listOf(
            // 좌표 변환 부분 = (x,y)원 중심, 반지름
            Triple(x,y,80f)
        )
        overlay.show(spots)
    }
}