package com.example.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

@RestController
public class AskController {

    @Value("${gemini.api.key}")
    private String apiKey;

    // 1. 안드로이드에서 보낸 데이터를 받을 그릇(클래스) 만들기
    public static class QuestionRequest {
        private String question;
        private String mode;
        private String imageBase64;
        private String packageName;

        public String getQuestion() { return question; }
        public void setQuestion(String question) { this.question = question; }
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public String getImageBase64() { return imageBase64; }
        public void setImageBase64(String imageBase64) { this.imageBase64 = imageBase64; }
        public String getPackageName() { return packageName; }
        public void setPackageName(String packageName) { this.packageName = packageName; }
    }

    // 2. GET에서 POST 방식으로 변경하고, @RequestBody로 데이터 받기
    @PostMapping("/ask")
    public Map<String, String> ask(@RequestBody QuestionRequest request) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=" + apiKey;
        RestTemplate restTemplate = new RestTemplate();

        String finalPrompt;

        // 이미지가 같이 온 경우 프롬프트를 약간 수정하여 이미지도 참고하라고 지시
        boolean hasImage = request.getImageBase64() != null && !request.getImageBase64().isEmpty();

        if ("crop".equals(request.getMode())) {
            if (hasImage) {
                finalPrompt = "다음 첨부된 화면 일부 이미지와 OCR 텍스트를 함께 분석해서, 핵심만 간단히 3줄로 설명해줘. (특수문자는 제거) (OCR 텍스트: " + request.getQuestion() + ")";
            } else {
                finalPrompt = "다음 OCR 결과를 보고 핵심만 간단히 3줄로 설명해줘(특수문자는 제거):\n" + request.getQuestion();
            }
        } else if ("direct".equals(request.getMode())) {
            // 패키지명이 비어있지 않다면, AI에게 힌트를 줄 문구를 만듭니다.
            String appHint = "";
            if (request.getPackageName() != null && !request.getPackageName().isEmpty()) {
                appHint = "\n현재 사용자가 실행 중인 앱의 패키지명은 '" + request.getPackageName() + "' 입니다. 이 앱과 관련된 내용이라면 참고해서 답변해 주세요. 이 앱이 무엇인지는 따로 언급하지마세요.";
            }

            // 최종 프롬프트 합치기
            finalPrompt = appHint + "사용자가 다음 질문을 했습니다: '" + request.getQuestion() + "'\n" +
                    "반드시 첨부된 '현재 화면 이미지'의 상황에서 출발하여, 목표를 달성하기 위한 방법을 1, 2, 3 단계로 설명해줘. " +
                    "무조건 '1. [버튼 이름] 버튼을 누르세요.' 형태로 현재 화면에서 당장 조작해야 할 행동이 1단계에 와야 해.";
        } else if ("guide".equals(request.getMode())) {
            finalPrompt = "첨부된 현재 모바일 앱 화면 이미지를 분석해서," +
                    "아래 \"지시사항\"을 수행하기 위해 사용자가 실제로 터치해야 하는 UI 요소의 중심 좌표(x, y)를 찾아라.\n" +
                    "매우 중요:\n" +
                    "- 반드시 첨부된 실제 이미지의 UI 위치를 기준으로 판단해라.\n" +
                    "- 현재 화면에 실제로 보이는 버튼만 선택해라.\n" +
                    "- 여러 후보가 있으면 가장 가능성이 높은 버튼 하나만 선택해라.\n" +
                    "- 버튼 전체 영역의 \"정중앙 좌표\"를 반환해라.\n" +
                    "- 화면 전체 기준 좌표를 사용해라.\n" +
                    "- 첨부된 원본 이미지 해상도 기준 좌표를 사용해라.\n" +
                    "- 상태바/네비게이션바를 포함한 전체 이미지 기준이다.\n" +
                    "- 설명, 분석, 문장, 마크다운을 절대 출력하지 마라.\n" +
                    "- 반드시 JSON만 출력해라.\n" +
                    "반환 형식:\n" +
                    "{\"x\":420,\"y\":1630}\n" +
                    "지시사항:\n" +
                    request.getQuestion();
        } else {
            if (hasImage) {
                finalPrompt = "다음 첨부된 전체 화면 이미지와 OCR 텍스트를 함께 분석해서, 3줄로 전체 요약해줘.(특수문자는 제거) (OCR 텍스트: " + request.getQuestion() + ")";
            } else {
                finalPrompt = "다음 화면 내용을 3줄로 전체 요약해줘(특수문자는 제거):\n" + request.getQuestion();
            }
        }

        try {
            // 3. 제미나이에게 보낼 JSON을 안전하게 만들기 (ObjectMapper 사용)
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            ArrayNode contentsNode = rootNode.putArray("contents");
            ObjectNode contentItemNode = contentsNode.addObject();
            ArrayNode partsNode = contentItemNode.putArray("parts");

            // 첫 번째 파트: 텍스트 프롬프트 넣기
            ObjectNode textPart = partsNode.addObject();
            textPart.put("text", finalPrompt);

            // 두 번째 파트: 이미지가 있다면 이미지 데이터(Base64) 추가하기
            if (hasImage) {
                ObjectNode imagePart = partsNode.addObject();
                ObjectNode inlineData = imagePart.putObject("inline_data");
                inlineData.put("mime_type", "image/jpeg");
                inlineData.put("data", request.getImageBase64());
            }

            String requestBody = mapper.writeValueAsString(rootNode);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            // 4. 제미나이 API 호출
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            JsonNode root = mapper.readTree(response.getBody());

            // 5. 응답 텍스트 추출
            String text = root
                    .path("candidates").get(0)
                    .path("content")
                    .path("parts").get(0)
                    .path("text")
                    .asText();

            return Map.of("answer", text);

        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("answer", "서버 에러 발생: " + e.getMessage());
        }
    }
}
