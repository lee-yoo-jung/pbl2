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
        private String language;
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }

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
                finalPrompt = "다음 첨부된 화면 일부 이미지와 OCR 텍스트를 함께 분석하여, 화면의 구성내용을 파악하여 요약해주세요.\n" +
                        "1.서술식 요약을 사용합니다. 핵심 내용을 한 줄의 자연스러운 문장으로 먼저 적으세요.\n" +
                        "서술식 요약 출력 예시: '이 화면은 배달의민족 음식 검색 화면이며, 원하는 가게 이름을 입력하여 찾을 수 있습니다.\n" +
                        "2.개조식 상세(구체적인 사용 단계, 버튼 기능 설명, 목록 안내 시 사용))를 사용합니다. 요약에 포함되지 않은 세부 기능이나 정보는 항목별로 나누어 최대 3줄 이내로 설명하세요. 정보를 항목별로 나누어 명확하게 전달합니다. 불필요한 수식어를 빼고 '항목: 내용' 혹은 '숫자 리스트' 혹은 명사형으로 문장을 표현합니다\n" +
                        "개조식 상세 출력 예시: \n'버튼 이름: [기능 설명] \n 단계 1: [행동 지침]'\n" +
                        "서술식 요약과 개조식 상세 내용을 2줄 띄워서 구분해주세요. 개조식 상세 내용을 각 문장마다 한 줄씩 띄워서 출력해주세요.\n" +
                        "(OCR 텍스트: " + request.getQuestion() + ")\n" +
                        "주의사항:\n" +
                        "설명을 받는 대상은 디지털 취약계층입니다. 노인, 외국인, 어린이 등이 이해하기 쉬운 단어를 사용하세요.\n" +
                        "별표(*)나 우물 정(#) 같은 특수문자를 사용하여 단어를 강조하지 마세요. 대괄호([, ])를 사용하여 단어를 강조하고, 나머지 문장은 마크다운 기호 없이 평문으로만 대답하세요.\n" +
                        "출력에 말머리로 '서술식 요약'과 '개조식 상세'라는 단어를 사용하지마세요.\n" +
                        "이미지 속에 표가 존재한다면 OCR 텍스트는 정확하지 않을 수 있으니 이미지를 기준으로 설명해주세요.\n" +
                        "위험 분석: OCR 텍스트에서 보이스피싱, 금융사기, 개인정보 요구 등 위험이 감지되면 마지막에 반드시 다음 문구를 추가하세요: “주의: 사기 또는 위험 상황일 수 있으니 확인 후 진행해주세요.”";
            } else {
                finalPrompt = "다음 첨부된 OCR 텍스트를 사용하여, 화면의 구성내용을 파악하여 요약해주세요.\n" +
                        "1.서술식 요약을 사용합니다. 핵심 내용을 한 줄의 자연스러운 문장으로 먼저 적으세요.\n" +
                        "서술식 요약 출력 예시: '이 화면은 배달의민족 음식 검색 화면이며, 원하는 가게 이름을 입력하여 찾을 수 있습니다.\n" +
                        "2.개조식 상세(구체적인 사용 단계, 버튼 기능 설명, 목록 안내 시 사용))를 사용합니다. 요약에 포함되지 않은 세부 기능이나 정보는 항목별로 나누어 최대 3줄 이내로 설명하세요. 정보를 항목별로 나누어 명확하게 전달합니다. 불필요한 수식어를 빼고 '항목: 내용' 혹은 '숫자 리스트' 혹은 명사형으로 문장을 표현합니다\n" +
                        "개조식 상세 출력 예시: \n'버튼 이름: [기능 설명] \n 단계 : [행동 지침]'\n" +
                        "서술식 요약과 개조식 상세 내용을 2줄 띄워서 구분해주세요. 개조식 상세 내용을 각 문장마다 한 줄씩 띄워서 출력해주세요.\n" +
                        "(OCR 텍스트: " + request.getQuestion() + ")\n" +
                        "주의사항:\n" +
                        "설명을 받는 대상은 디지털 취약계층입니다. 노인, 외국인, 어린이 등이 이해하기 쉬운 단어를 사용하세요.\n" +
                        "별표(*)나 우물 정(#) 같은 특수문자를 사용하여 단어를 강조하지 마세요. 대괄호([, ])를 사용하여 단어를 강조하고, 나머지 문장은 마크다운 기호 없이 평문으로만 대답하세요.\n" +
                        "출력에 말머리로 '서술식 요약'과 '개조식 상세'라는 단어를 사용하지마세요.\n" +
                        "위험 분석: 보이스피싱, 사기 등이 의심될 경우 마지막에 반드시 다음 문구를 추가하세요: “주의: 사기 또는 위험 상황일 수 있으니 확인 후 진행해주세요.”";
            }
        } else if ("direct".equals(request.getMode())) {
            String appHint = "";
            if (request.getPackageName() != null && !request.getPackageName().isEmpty()) {
                appHint = "\n현재 사용자가 실행 중인 앱의 패키지명은 '" + request.getPackageName() + "' 입니다.\n" +
                        "이 앱과 사용자의 질문이 관련있는 경우 참고하되, 관련 없으면 무시하세요. 앱 이름은 언급하지 마세요.\n";
            }

            finalPrompt = "사용자의 질문: '" + request.getQuestion() + "'\n" +
                    appHint +
                    "형식 지침:\n" +
                    "1.첫 줄(서술식을 사용합니다): 사용자의 질문을 간결하게 정리한 한 문장을 적으세요. (예시: ~를 도와드릴게요.)\n" +
                    "2. 단계별 안내(개조식 서술을 사용합니다): 반드시 숫자를 사용한 개조식으로 5단계 이내로 설명하세요.\n" +
                    "개조식 상세 출력 예시: \n'버튼 이름: [기능 설명] \n 단계 1: [행동 지침]'\n" +
                    "첫 단계는 무조건 '1. [버튼 이름] 버튼을 누르세요.'처럼 현재 화면에서 즉시 해야 할 행동이어야 합니다.\n" +
                    "서술식 요약과 개조식 상세 내용을 2줄 띄워서 구분해주세요. 개조식 상세 내용을 각 문장마다 한 줄씩 띄워서 출력해주세요.\n" +
                    "주의사항:\n" +
                    "설명을 받는 대상은 디지털 취약계층입니다. 노인, 외국인, 어린이 등이 이해하기 쉬운 단어를 사용하세요.\n" +
                    "별표(*)나 우물 정(#) 같은 특수문자를 사용하여 단어를 강조하지 마세요. 대괄호([, ])를 사용하여 단어를 강조하고, 나머지 문장은 마크다운 기호 없이 평문으로만 대답하세요.\n";
        } else if ("guide".equals(request.getMode())) {
            // guide 모드는 좌표 전송용이므로 개조식/서술식 구분이 필요 없으므로 기존 유지
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
                finalPrompt = "다음 첨부된 OCR 텍스트와 화면 이미지를 사용하여, 화면을 전체적으로 분석하세요.\n" +
                        "1.서술식 요약을 사용합니다. 현재 화면이 무엇인지 '~앱의 ~한 화면입니다.' 형태로 한 줄로 요약하세요.\n" +
                        "2.개조식 상세를 사용합니다. 화면의 주요 기능이나 긴 글(뉴스, 공지)의 핵심 내용 또는 화면의 내용을 항목별로 나누어 최대 3줄 이내로 설명하세요.\n" +
                        "개조식 상세 출력 예시: \n'버튼 이름: [기능 설명] \n 단계 1: [행동 지침]'\n" +
                        "서술식 요약과 개조식 상세 내용을 2줄 띄워서 구분해주세요. 개조식 상세 내용을 각 문장마다 한 줄씩 띄워서 출력해주세요.\n" +
                        "(OCR 텍스트: " + request.getQuestion() + ")\n" +
                        "주의사항:\n" +
                        "설명을 받는 대상은 디지털 취약계층입니다. 노인, 외국인, 어린이 등이 이해하기 쉬운 단어를 사용하세요.\n" +
                        "별표(*)나 우물 정(#) 같은 특수문자를 사용하여 단어를 강조하지 마세요. 대괄호([, ])를 사용하여 단어를 강조하고, 나머지 문장은 마크다운 기호 없이 평문으로만 대답하세요.\n" +
                        "출력에 말머리로 '서술식 요약'과 '개조식 상세'라는 단어를 사용하지마세요.\n" +
                        "이미지 속에 표가 존재한다면 OCR 텍스트는 정확하지 않을 수 있으니 이미지를 기준으로 설명해주세요.\n" +
                        "위험 분석: 보이스피싱, 금융사기 등 위험이 판단될 경우 마지막에 반드시 다음 문구를 추가하세요: “주의: 사기 또는 위험 상황일 수 있으니 확인 후 진행해주세요.”";
            } else {
                finalPrompt = "다음 첨부된 OCR 텍스트를 사용하여, 화면을 전체적으로 분석하세요.\n" +
                        "1.서술식 요약을 사용합니다. 현재 화면이 무엇인지 '~앱의 ~한 화면입니다.' 형태로 한 줄로 요약하세요.\n" +
                        "2.개조식 상세를 사용합니다. 화면의 주요 기능이나 정보들(글 또는 화면의 내용)을 항목별로 나누어 최대 3줄 이내로 설명하세요.\n" +
                        "개조식 상세 출력 예시: \n'버튼 이름: [기능 설명] \n 단계 1: [행동 지침]'\n" +
                        "서술식 요약과 개조식 상세 내용을 2줄 띄워서 구분해주세요. 개조식 상세 내용을 각 문장마다 한 줄씩 띄워서 출력해주세요.\n" +
                        "(OCR 텍스트: " + request.getQuestion() + ")\n" +
                        "주의사항:\n" +
                        "설명을 받는 대상은 디지털 취약계층입니다. 노인, 외국인, 어린이 등이 이해하기 쉬운 단어를 사용하세요.\n" +
                        "출력에 말머리로 '서술식 요약'과 '개조식 상세'라는 단어를 사용하지마세요.\n" +
                        "별표(*)나 우물 정(#) 같은 특수문자를 사용하여 단어를 강조하지 마세요. 대괄호([, ])를 사용하여 단어를 강조하고, 나머지 문장은 마크다운 기호 없이 평문으로만 대답하세요.\n" +
                        "위험 분석: 보이스피싱, 금융사기 등 위험이 판단될 경우 마지막에 반드시 다음 문구를 추가하세요: “주의: 사기 또는 위험 상황일 수 있으니 확인 후 진행해주세요.”";
            }
        }

        // 선택된 언어를 확인하고, 프롬프트 맨 끝에 해당 언어로만 대답하도록 강제 지시문 추가
        String targetLang = (request.getLanguage() != null && !request.getLanguage().isEmpty()) ? request.getLanguage() : "한국어";

        // AI가 더 잘 이해할 수 있도록 명칭을 보강
        String instruction = "";
        if (targetLang.equals("English")) instruction = "English";
        else if (targetLang.equals("日本語")) instruction = "Japanese";
        else if (targetLang.equals("中文")) instruction = "Chinese";
        else instruction = "Korean";

        finalPrompt = finalPrompt + "\n\n" +
                "[SYSTEM RULE: You must provide the entire response in " + instruction + " ONLY. " +
                "Do not use any other languages. This is a strict requirement.]";

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
