package com.example.riskdetection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun SettingsScreen() {

    var floatingOn by remember { mutableStateOf(true) }
    var helpOn by remember { mutableStateOf(false) }
    var optionOn by remember { mutableStateOf(true) }

    var expanded by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf("한국어") }

    val languages = listOf("한국어", "English", "中文", "日本語")

    val purple = Color(0xFF6A5ACD)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {

        Box(modifier = Modifier.fillMaxWidth()) {

            // 사용법 버튼
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .background(purple, RoundedCornerShape(16.dp))
                    .clickable { }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Help,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    "사용법",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                text = "APP 이름",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.headlineMedium
            )

            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "설정",
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 언어 선택
        Box(
            modifier = Modifier.width(200.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(selectedLanguage)
                Text("▼")
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                languages.forEach { lang ->
                    DropdownMenuItem(
                        text = { Text(lang) },
                        onClick = {
                            selectedLanguage = lang
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "설정",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        SettingToggle("플로팅 버튼", floatingOn, purple) { floatingOn = it }
        SettingToggle("도움 제공 멘트", helpOn, purple) { helpOn = it }
        SettingToggle("옵션 3", optionOn, purple) { optionOn = it }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Chat, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "최근 질문(로그)",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 임시 데이터
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

        questionList.forEach { (question, answer) ->
            QuestionCard(question, answer)
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

        Row(verticalAlignment = Alignment.CenterVertically) {

            Text("Off", color = Color.Gray)

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = purple
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text("On", color = Color.Gray)
        }
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