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
                finalPrompt = "다음 첨부된 화면 일부 이미지와 OCR 텍스트를 함께 분석하여, 화면의 구성내용을 파악하여 요약해주세요. 1줄 요약 후, 요약에 포함되지 않은 세부적인 설명을 최대 3줄 이내로 추가로 설명해주세요.\n" +
                        "(OCR 텍스트: " + request.getQuestion() + ")\n" +
                        "주의사항:\n" +
                        "설명을 받는 대상은 디지털 취약계층입니다. 노인, 외국인, 어린이 등이 이해하기 쉬운 단어를 사용하세요.\n" +
                        "별표(*)나 우물 정(#) 같은 특수문자를 사용하여 단어를 강조하지 마세요. 마크다운 기호 없이 평문으로만 대답하세요.\n" +
                        "OCR로 추출된 텍스트 내용을 분석하여 보이스피싱, 금융사기, 계정 탈취, 원격제어 유도, 개인정보 요구 등 위험 가능성이 있다고 판단될 경우, 응답 마지막에 다음과 같은 짧은 경고 문구를 추가하세요:\n" +
                        "“주의: 사기 또는 위험 상황일 수 있으니 확인 후 진행해주세요.”\n" +
                        "위험 가능성이 낮거나 일반적인 내용일 경우에는 경고 문구를 추가하지 마세요.";
            } else {
                finalPrompt = "다음 첨부된 OCR 텍스트를 사용하여, 화면의 구성내용을 파악하여 요약해주세요. 1줄 요약 후, 요약에 포함되지 않은 세부적인 설명을 최대 3줄 이내로 추가로 설명해주세요.\n" +
                        "(OCR 텍스트: " + request.getQuestion() + ")\n" +
                        "주의사항:\n" +
                        "설명을 받는 대상은 디지털 취약계층입니다. 노인, 외국인, 어린이 등이 이해하기 쉬운 단어를 사용하세요.\n" +
                        "별표(*)나 우물 정(#) 같은 특수문자를 사용하여 단어를 강조하지 마세요. 마크다운 기호 없이 평문으로만 대답하세요.\n" +
                        "OCR로 추출된 텍스트 내용을 분석하여 보이스피싱, 금융사기, 계정 탈취, 원격제어 유도, 개인정보 요구 등 위험 가능성이 있다고 판단될 경우, 응답 마지막에 다음과 같은 짧은 경고 문구를 추가하세요:\n" +
                        "“주의: 사기 또는 위험 상황일 수 있으니 확인 후 진행해주세요.”\n" +
                        "위험 가능성이 낮거나 일반적인 내용일 경우에는 경고 문구를 추가하지 마세요.";
            }
        } else if ("direct".equals(request.getMode())) {
            // 패키지명이 비어있지 않다면, AI에게 힌트를 줄 문구를 만듭니다.
            String appHint = "";
            if (request.getPackageName() != null && !request.getPackageName().isEmpty()) {
                appHint = "\n현재 사용자가 실행 중인 앱의 패키지명은 '" + request.getPackageName() + "' 입니다.\n" +
                        "이 앱과 사용자의 질문이 관련있는 경우, 참고해서 답변해 주세요. 관련없는 경우, 앱 패키지명은 무시하세요. 이 앱이 무엇인지는 따로 언급하지마세요.\n";
            }

            // 최종 프롬프트 합치기
            finalPrompt = "사용자의 질문: '" + request.getQuestion() + "'\n" +
                    appHint +
                    "가장 첫 줄은 사용자의 질문을 간결하게 정리하여 한 문장으로 (예시: ~하는 방법) 적어주세요.\n" +
                    "첨부된 이미지는 사용자의 현재 화면 이미지입니다. 반드시 첨부된 '현재 화면 이미지'의 상황에서 출발하여, 사용자의 질문에 맞는 목표를 달성하기 위한 방법을 5단계 이내로 설명해주세요.\n" +
                    "무조건 '1. [버튼 이름] 버튼을 누르세요.' 형태로 현재 화면에서 당장 조작해야 할 행동이 1단계에 와야합니다.\n" +
                    "주의사항:\n" +
                    "설명을 받는 대상은 디지털 취약계층입니다. 노인, 외국인, 어린이 등이 이해하기 쉬운 단어를 사용하세요.\n" +
                    "별표(*)나 우물 정(#) 같은 특수문자를 사용하여 단어를 강조하지 마세요. 마크다운 기호 없이 평문으로만 대답하세요.\n";
        } else if ("guide".equals(request.getMode())) {
            finalPrompt = "첨부된 현재 모바일 앱 화면 이미지를 분석해서," +
                    "아래 \"지시사항\"을 수행하기 위해 사용자가 실제로 터치해야 하는 UI 요소의 중심 좌표(x, y)를 찾으세요.\n" +
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
                finalPrompt = "다음 첨부된 OCR 텍스트와 화면 이미지를 사용하여, 화면의 구성내용을 파악하여 요약해주세요.\n" +
                        "1줄 요약 후, 요약에 포함되지 않은 세부적인 설명을 최대 3줄 이내로 추가 설명해주세요.\n" +
                        "1줄 요약 예시: '~앱의 ~한 화면입니다.'\n" +
                        "세부 설명(개조식 서술): 현재 화면에서 수행 가능한 주요 기능 설명, 뉴스나 공지 사항 같은 긴 줄글이 포함된 경우에는 내용 요약" +
                        "(OCR 텍스트: " + request.getQuestion() + ")\n" +
                        "주의사항:\n" +
                        "설명을 받는 대상은 디지털 취약계층입니다. 노인, 외국인, 어린이 등이 이해하기 쉬운 단어를 사용하세요.\n" +
                        "별표(*)나 우물 정(#) 같은 특수문자를 사용하여 단어를 강조하지 마세요. 마크다운 기호 없이 평문으로만 대답하세요.\n" +
                        "OCR로 추출된 텍스트 내용을 분석하여 보이스피싱, 금융사기, 계정 탈취, 원격제어 유도, 개인정보 요구 등 위험 가능성이 있다고 판단될 경우, 응답 마지막에 다음과 같은 짧은 경고 문구를 추가하세요:\n" +
                        "“주의: 사기 또는 위험 상황일 수 있으니 확인 후 진행해주세요.”\n" +
                        "위험 가능성이 낮거나 일반적인 내용일 경우에는 경고 문구를 추가하지 마세요.";
            } else {
                finalPrompt = "다음 첨부된 OCR 텍스트를 사용하여, 화면의 구성내용을 파악하여 요약해주세요.\n" +
                        "1줄 요약 후, 요약에 포함되지 않은 세부적인 설명을 최대 3줄 이내로 추가 설명해주세요.\n" +
                        "1줄 요약 예시: '~앱의 ~한 화면입니다.'\n" +
                        "세부 설명(개조식 서술): 현재 화면에서 수행 가능한 주요 기능 설명, 뉴스나 공지 사항 같은 긴 줄글이 포함된 경우에는 내용 요약" +
                        "(OCR 텍스트: " + request.getQuestion() + ")\n" +
                        "주의사항:\n" +
                        "설명을 받는 대상은 디지털 취약계층입니다. 노인, 외국인, 어린이 등이 이해하기 쉬운 단어를 사용하세요.\n" +
                        "별표(*)나 우물 정(#) 같은 특수문자를 사용하여 단어를 강조하지 마세요. 마크다운 기호 없이 평문으로만 대답하세요.\n" +
                        "OCR로 추출된 텍스트 내용을 분석하여 보이스피싱, 금융사기, 계정 탈취, 원격제어 유도, 개인정보 요구 등 위험 가능성이 있다고 판단될 경우, 응답 마지막에 다음과 같은 짧은 경고 문구를 추가하세요:\n" +
                        "“주의: 사기 또는 위험 상황일 수 있으니 확인 후 진행해주세요.”\n" +
                        "위험 가능성이 낮거나 일반적인 내용일 경우에는 경고 문구를 추가하지 마세요.";
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
