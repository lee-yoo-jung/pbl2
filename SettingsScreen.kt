package com.example.pbl2

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.provider.Settings
import java.util.*

@Composable
fun SettingsScreen(answerText: String) {

    val context = LocalContext.current
    // 초기값을 false로 두어 앱을 켰을 때 바로 버튼이 생기지 않게 조절할 수 있습니다.
    var floatingOn by remember { mutableStateOf(false) }

    // floatingOn 스위치를 누를 때마다 이 블록이 실행됩니다.
    LaunchedEffect(floatingOn) {
        val intent = Intent(context, FloatingService::class.java)
        if (floatingOn) {
            // "다른 앱 위에 그리기" 권한이 있는지 확인 후 서비스 시작
            if (Settings.canDrawOverlays(context)) {
                context.startService(intent)
            }
        } else {
            context.stopService(intent) // 스위치를 끄면 서비스 종료
        }
    }

    var helpOn by remember { mutableStateOf(false) }
    var optionOn by remember { mutableStateOf(true) }

    var expanded by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf("한국어") }

    val languages = listOf("ko" to "한국어", "en" to "English", "ja" to "日本語", "zh" to "中文")

    val purple = Color(0xFF6A5ACD)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {

        // 상단
        Box(modifier = Modifier.fillMaxWidth()) {

            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .background(purple, RoundedCornerShape(16.dp))
                    .clickable { }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Help, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.help), color = Color.White)
            }

            Text(
                text = stringResource(R.string.app_name),
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.titleLarge
            )

            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 언어 선택
        Box(modifier = Modifier.width(200.dp)) {

            val currentLang = loadLanguage(context)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    languages.find { it.first == currentLang }?.second ?: "한국어"
                )
                Text("▼")
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                languages.forEach { lang ->
                    DropdownMenuItem(
                        text = { Text(lang.second) },
                        onClick = {
                            saveLanguage(context, lang.first)
                            setLocale(context, lang.first)

                            // FloatingService에서 읽을 수 있게 언어 이름 저장 추가
                            val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                            prefs.edit().putString("APP_LANG", lang.second).apply()

                            (context as? Activity)?.recreate()

                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 설정
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.settings), fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        SettingToggle(stringResource(R.string.floating_button), floatingOn, purple) { floatingOn = it }
        SettingToggle(stringResource(R.string.help_message), helpOn, purple) { helpOn = it }
        SettingToggle(stringResource(R.string.option3), optionOn, purple) { optionOn = it }

        Spacer(modifier = Modifier.height(24.dp))

        // 최근 질문
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Chat, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.recent_questions), fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        val questionList = listOf(
            "질문 1" to "답변 1",
            "질문 2" to "답변 2",
            "질문 3" to "답변 3",
            "질문 4" to "답변 4",
            "질문 5" to "답변 5",
            "질문 6" to "답변 6",
            "질문 7" to "답변 7",
            "질문 8" to "답변 8"
        )

        questionList.forEach { (q, a) ->
            QuestionCard(q, a)
        }
    }
}

@Composable
fun SettingToggle(
    title: String,
    checked: Boolean,
    purple: Color,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title)

        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = purple
            )
        )
    }
}

@Composable
fun QuestionCard(question: String, answer: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(1.dp, Color.Gray, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(question, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Divider()
        Spacer(modifier = Modifier.height(6.dp))
        Text(answer)
    }
}

fun saveLanguage(context: Context, language: String) {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    prefs.edit().putString("language", language).apply()
}

fun loadLanguage(context: Context): String {
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    return prefs.getString("language", "ko") ?: "ko"  // 기본값 한국어
}

// 언어 변경 함수
fun setLocale(context: Context, language: String) {
    val locale = Locale(language)
    Locale.setDefault(locale)

    val config = context.resources.configuration
    config.setLocale(locale)

    context.resources.updateConfiguration(config, context.resources.displayMetrics)
}