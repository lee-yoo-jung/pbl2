package com.example.pbl2.access_ocr

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions

object OCR{
    fun process(
        fullBitmap: Bitmap,
        rect: Rect? = null,
        onComplete: (String, Bitmap?) -> Unit
    ) {
        val callId = System.currentTimeMillis()
        Log.d("FLOW_OCR", "[$callId] OCR 호출 rect=$rect")

        val targetedBitmap = if (rect != null) {
            Log.d("FLOW_OCR", "[$callId] 부분 OCR rect = $rect")
            captureRegion(fullBitmap,rect)
        } else {
            Log.d("FLOW_OCR", "[$callId] 전체 OCR (rect 없음)")
            fullBitmap
        }
        var image= InputImage.fromBitmap(targetedBitmap,0)      // ML가 이해할 수 있는 형태로 변환 (Bitmap -> InputImage)
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())   // OCR 처리기 엔진 생성
        val textConfidenceArray=ArrayList<Float>() // 분석한 단어의 신뢰도

        // OCR 수행 (비동기 처리)
        recognizer.process(image)
            // OCR 성공 시
            .addOnSuccessListener { visionText ->
                // 0. 표 여부 판단
                val results = mutableListOf<OCRResult>()
                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        val lineRect = line.boundingBox ?: continue
                        results.add(OCRResult(line.text,lineRect))
                    }
                }
                val isTable = TableDetector.isTable(results)
                Log.d("OCR_TABLE", "표 판별 결과: $isTable")

                if(isTable) {
                    // TODO: 이미지로 LLM 입력
                    return@addOnSuccessListener
                }

                val text = visionText.text

                // 1. 부분/전체 상관없이 단어 신뢰도 추출
                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        for (element in line.elements) {

                            val confidence = element.confidence
                            Log.d("ML", "단어: ${element.text}, 신뢰도: $confidence")

                            textConfidenceArray.add(confidence)
                        }
                    }
                }

                // 2. 텍스트 정제 및 빈 문자열 처리
                val resultText = if (text.trim().isEmpty()) {
                    "인식된 단어가 없습니다."
                } else {
                    refineText(text)
                }

                Log.d("OCR_RESULT", "추출된 텍스트: $resultText")

                // 3. 신뢰도 판단 (평균 0.7 기준)
                val isLowConfidence =
                    textConfidenceArray.isNotEmpty() &&
                            textConfidenceArray.average() < 0.7

                // 4. 결과 반환
                if (resultText == "인식된 단어가 없습니다." || isLowConfidence) {

                    Log.d("OCR_RESULT", "신뢰도 낮음/글자 없음 -> 이미지도 같이 반환")

                    // 텍스트 + 이미지 전송
                    onComplete(resultText, targetedBitmap)

                } else {

                    Log.d("OCR_RESULT", "정상 인식 -> 텍스트만 반환")

                    // 텍스트만 전송
                    onComplete(resultText, null)
                }
            }
            // OCR 실패 시 = 이미지 입력
            .addOnFailureListener {
                Log.e("OCR_ERROR", it.message ?: "Error")   // OCR 실패 메시지
                onComplete("OCR 실패", targetedBitmap)
            }
    }

    fun refineText(input: String): String {
        return input.split(Regex("[\\n\\r]+"))
            .map { it.trim() }
            .filter { line ->
                line.isNotEmpty() &&
                        // 시간대/재생시간 패턴 제거
                        !line.matches(Regex(".*\\d{1,2}:\\d{2}.*"))
            }
            .distinct() // 중복 문장 즉시 제거
            .joinToString("\n") // 하나의 문단으로 병합
            .replace(Regex("[ ]+"), " ") // 공백 정리
            .trim()
    }

    fun captureRegion(full: Bitmap, rect: Rect): Bitmap {

        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        val screenHeight = Resources.getSystem().displayMetrics.heightPixels

        val scaleX = full.width.toFloat() / screenWidth
        val scaleY = full.height.toFloat() / screenHeight

        val scaledRect = Rect(
            (rect.left * scaleX).toInt(),
            (rect.top * scaleY).toInt(),
            (rect.right * scaleX).toInt(),
            (rect.bottom * scaleY).toInt()
        )

        return Bitmap.createBitmap(
            full,
            scaledRect.left.coerceAtLeast(0),
            scaledRect.top.coerceAtLeast(0),
            scaledRect.width().coerceAtMost(full.width),
            scaledRect.height().coerceAtMost(full.height)
        )

    }
}