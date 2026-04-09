package com.example.tts
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import com.example.tts.R
import android.util.Log

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // TTS 객체 초기화
        //tts = TextToSpeech(this, this)
        tts = TextToSpeech(this, this, "com.google.android.tts")

        val btnSpeak = findViewById<Button>(R.id.btnSpeak)
        btnSpeak.setOnClickListener {
            //문장 바꿔서 확인 가능.
            speakOut("안녕하세요. TTS 확인")
        }
    }

    /*
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // 언어 설정 (한국어)
            val result = tts?.setLanguage(Locale.ENGLISH)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "해당 언어는 지원되지 않습니다.")
            }
        } else {
            Log.e("TTS", "초기화 실패")
        }
    }
    */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // 현재 사용 중인 엔진 이름 확인 (로그캣에서 확인 가능)
            val engineName = tts?.defaultEngine
            Log.d("TTS_TEST", "현재 사용 중인 엔진: $engineName")

            // 언어설정, KOREAN, ENGLISH, GERMAN 등등
            val result = tts?.setLanguage(Locale.KOREAN)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "한국어를 지원하지 않거나 데이터가 없습니다.")
            }
        } else {
            // 만약 Google TTS가 설치되어 있지 않으면 status가 ERROR로 올 수 있습니다.
            Log.e("TTS", "초기화 실패 (Google TTS 설치 여부 확인 필요)")
        }
    }

    private fun speakOut(text: String) {
        tts?.setPitch(1.0f)      // 음성 높낮이 (0.5 ~ 2.0)
        tts?.setSpeechRate(1.0f) // 재생 속도 (0.5 ~ 2.0)

        // QUEUE_FLUSH: 이전 음성을 멈추고 새 음성 출력
        // QUEUE_ADD: 이전 음성이 끝난 뒤 출력
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onDestroy() {
        // 메모리 누수 방지를 위한 자원 해제
        if (tts != null) {
            tts?.stop()
            tts?.shutdown()
        }
        super.onDestroy()
    }
}