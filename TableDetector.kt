package com.example.pbl2.access_ocr

import android.graphics.Rect

// OCR이 읽은 결과와 위치
data class OCRResult(
    val text: String,
    val rect: Rect
)

object TableDetector {
    fun isTable(results: List<OCRResult>): Boolean {
        if (results.isEmpty()) return false

        val numberRegex = Regex("""[\d,.]+""")
        val numericElements = results.count { numberRegex.containsMatchIn(it.text) } // 숫자가 포함된 개수
        val numerics = numericElements.toFloat() / results.size // 포함된 숫자의 비율

        if (numerics > 0.3) return true // 숫자가 전체의 30%를 넘음

        val toY = 20 // Y축 허용 오차 범위: 20픽셀
        val rows = results.groupBy { it.rect.centerY() / toY }  // Y축 허용 오차 범위로 같은 행의 요소 묶기

        var tableRows = 0
        val xStarts = mutableListOf<Int>()

        rows.forEach { (_, elementsRow) ->
            if (elementsRow.size >= 3) {  // 같은 행에 3개의 요소가 있는 경우 = 테이블 행으로 계산
                tableRows++
                elementsRow.forEach { xStarts.add(it.rect.left / 15) } // X축 허용 오차 범위: 15픽셀
            }
        }

        val verticalCount = xStarts.groupBy { it }.filter { it.value.size >= 2 }.size   // 비슷한 x축 위치가 2개 이상 = 열

        return tableRows >= 2 || verticalCount >= 3 // 행이 2개 이상, 열이 3개 이상
    }
}