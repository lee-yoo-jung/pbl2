package com.example.riskdetection

import android.util.Log

object RiskDetector {

    private val dangerWords = listOf(
        "결제", "인증", "카드", "비밀번호", "계좌",
        "송금", "입금", "출금", "결제요청",
        "주민등록번호", "OTP", "인증번호",
        "긴급", "즉시", "링크클릭", "당첨"
    )

    fun checkRisk(text: String) {
        for (word in dangerWords) {
            if (text.contains(word)) {
                Log.d("RISK", "⚠️ 위험단어가 포함되어 있습니다: $word")
                return
            }
        }
        Log.d("RISK", "안전한 텍스트입니다.")
    }
}