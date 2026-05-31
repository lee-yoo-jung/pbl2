package com.example.server;
import com.example.server.database.LlmService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@CrossOrigin(origins = "*")
@RestController
public class AskController {

    private final LlmService llmService;

    @Value("${llm.api.key}")
    private String apiKey;

    public AskController(LlmService llmService) {
        this.llmService = llmService;
    }

    // 1. 안드로이드에서 보낸 데이터를 받을 그릇(클래스) 만들기
    public static class QuestionRequest {
        private String userId;
        private String question;
        private String mode;
        private String imageBase64;
        private String packageName;
        private String language;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public String getQuestion() {
            return question;
        }

        public void setQuestion(String question) {
            this.question = question;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getImageBase64() {
            return imageBase64;
        }

        public void setImageBase64(String imageBase64) {
            this.imageBase64 = imageBase64;
        }

        public String getPackageName() {
            return packageName;
        }

        public void setPackageName(String packageName) {
            this.packageName = packageName;
        }
    }

    RestTemplate restTemplate = new RestTemplate();

    // 2. GET에서 POST 방식으로 변경하고, @RequestBody로 데이터 받기
    @PostMapping("/ask")
    public Map<String, String> ask(@RequestBody QuestionRequest request) {

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=" + apiKey;

        String finalPrompt;

        // 이미지가 같이 온 경우 프롬프트를 약간 수정하여 이미지도 참고하라고 지시
        boolean hasImage = request.getImageBase64() != null && !request.getImageBase64().isEmpty();

        String InitLang = "Korean";
        // 선택된 언어를 확인하고, 프롬프트 맨 끝에 해당 언어로만 대답하도록 강제 지시문 추가
        String targetLang = (request.getLanguage() != null && !request.getLanguage().isEmpty()) ? request.getLanguage() : "한국어";

        // AI가 더 잘 이해할 수 있도록 명칭을 보강
        String instruction = "";
        if (targetLang.equals("English")) instruction = "English";
        else if (targetLang.equals("日本語")) instruction = "Japanese";
        else if (targetLang.equals("中文")) instruction = "Chinese";
        else if (targetLang.equals("Tiếng Việt")) instruction = "Vietnamese";
        else instruction = "Korean";

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
                        "별표(*)나 우물 정(#) 같은 특수문자를 사용하여 단어를 강조하지 마세요. 대괄호([, ])를 사용하여 인식된 한국어 단어를 강조하고, 나머지 문장은 마크다운 기호 없이 평문으로만 대답하세요.\n " +
                        "만약 원본 입력 언어가 한국어가 아니라면, 대괄호 안에는 강조하려는 언어인 [" + InitLang + "] "+ InitLang +"단어를 적고, 그 바로 뒤 괄호 (" + instruction + ") 안에는 입력받았던 원본 언어로 번역된" + instruction +"언어 를 적으세요."+
                        "[출력 예시 (Target: Korean)] {\"question\": \"[예매]버튼을 누르세요.\"}\n\n" +
                        "[출력 예시 (Target: Chinese)] {\"question\": \"请按 [예매](预售) 按钮。.\"}\n\n" +
                        "[출력 예시 (Target: Japanese)] {\"question\": \"[예매](予約) ボタンを押してください。\"}\n\n" +
                        "[출력 예시 (Target: Vietnamese)] {\"question\": \"[예매](Đặt vé) Hãy nhấn nút.\"}\n\n" +
                        "[출력 예시 (Target: English)] {\"question\": \"Press the [예매](Reservation) button.\"}"+
                        "[한국어 단어](그 한국어 단어를 타겟 외국어로 번역한 단어)-> 예시: 한국어 단어가 '날씨'이고 타겟 언어가 영어라면 [날씨](Weather) 가 되어야 합니다. [Weather](날씨)가 아닙니다. 반드시 대괄호 안이 한국어여야 합니다."+
                        "출력에 말머리로 '서술식 요약'과 '개조식 상세'라는 단어를 사용하지마세요.\n" +
                        "이미지 속에 표가 존재한다면 OCR 텍스트는 정확하지 않을 수 있으니 이미지를 기준으로 설명해주세요.\n" +
                        "위험 분석: OCR 텍스트에서 보이스피싱, 금융사기, 개인정보 요구 등 위험이 감지되면 맨 앞에 반드시 다음 문구를 추가하세요: \"🚨 주의 🚨 : 사기 또는 위험 상황일 수 있으니 확인 후 진행해주세요. \"\n" +
                        "결제, 결제하기, 구매, 구매하기송금, 이체, 보내기,충전, 충전하기, 후원, 후원하기, 기부, 간편결제, 원클릭 결제 등 결제와 관련된 단어가 감지되면 맨 앞에 반드시 다음 문구를 추가하세요. “ \"🚨 주의 🚨 : 의도치 않은 결제일 수 있으니 확인 후 진행해주세요. \"\n"+
                        "인증, 인증하기, 본인인증비밀번호 입력, PIN 번호, 패스워드지문 인식, Face ID, 생체 인증 간편결제, 원클릭 결제 등 인증 및 간편 결제 관련된 단어가 감지되면 맨 앞에 반드시 다음 문구를 추가하세요. “  \"🚨 주의 🚨 : 핵심정보 노출일 수 있으니 확인 후 진행해주세요. \"\n"+
                        "만약 감지된 사용자의 언어가 한국어가 아니라면(예: 영어, 베트남어 등), 위의 '한국어 기준 기본 문구'를 감지된 사용자의 언어로 정확하게 번역하여 문장 맨 앞에 결합하세요."+
                        "예시: 영어인 경우 \"🚨 Caution 🚨 : This could be an actual monetary transaction, please verify before proceeding. \" 와 같이 해당 언어에 맞게 자연스럽게 번역해야 합니다."+
                        "English: \"🚨 Caution 🚨 : This could be an actual monetary transaction, please verify before proceeding. "+
                        "Japanese: \"🚨 注意 🚨 : 実際の決済が発生する可能性があるため、確認の上進めてください。 "+
                        "Chinese: \"🚨 注意 🚨 : 这 可能是 实际 扣款 情况，请 确认 后 再 继续。"+
                        "Vietnamese: \"🚨 Chú ý 🚨 : Đây có thể là tình huống thanh toán thực tế, vui lòng kiểm tra kỹ trước khi tiếp tục. ";
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
                        "만약 원본 입력 언어가 한국어가 아니라면, 대괄호 안에는 강조하려는 언어인 [" + InitLang + "] "+ InitLang +"단어를 적고, 그 바로 뒤 괄호 (" + instruction + ") 안에는 입력받았던 원본 언어로 번역된" + instruction +"언어 를 적으세요."+
                        "[출력 예시 (Target: Korean)] {\"question\": \"[예매]버튼을 누르세요.\"}\n\n" +
                        "[출력 예시 (Target: Chinese)] {\"question\": \"请按 [예매](预售) 按钮。.\"}\n\n" +
                        "[출력 예시 (Target: Japanese)] {\"question\": \"[예매](予約) ボタンを押してください。\"}\n\n" +
                        "[출력 예시 (Target: Vietnamese)] {\"question\": \"[예매](Đặt vé) Hãy nhấn nút.\"}\n\n" +
                        "[출력 예시 (Target: English)] {\"question\": \"Press the [예매](Reservation) button.\"}"+
                        "[한국어 단어](그 한국어 단어를 타겟 외국어로 번역한 단어)-> 예시: 한국어 단어가 '날씨'이고 타겟 언어가 영어라면 [날씨](Weather) 가 되어야 합니다. [Weather](날씨)가 아닙니다. 반드시 대괄호 안이 한국어여야 합니다."+
                        "출력에 말머리로 '서술식 요약'과 '개조식 상세'라는 단어를 사용하지마세요.\n" +
                        "위험 분석: OCR 텍스트에서 보이스피싱, 금융사기, 개인정보 요구 등 위험이 감지되면 맨 앞에 반드시 다음 문구를 추가하세요: \"🚨 주의 🚨 : 사기 또는 위험 상황일 수 있으니 확인 후 진행해주세요. \"\n" +
                        "결제, 결제하기, 구매, 구매하기송금, 이체, 보내기,충전, 충전하기, 후원, 후원하기, 기부, 간편결제, 원클릭 결제 등 결제와 관련된 단어가 감지되면 맨 앞에 반드시 다음 문구를 추가하세요. “ \"🚨 주의 🚨 : 의도치 않은 결제일 수 있으니 확인 후 진행해주세요. \"\n"+
                        "인증, 인증하기, 본인인증비밀번호 입력, PIN 번호, 패스워드지문 인식, Face ID, 생체 인증 간편결제, 원클릭 결제 등 인증 및 간편 결제 관련된 단어가 감지되면 맨 앞에 반드시 다음 문구를 추가하세요. “  \"🚨 주의 🚨 : 핵심정보 노출일 수 있으니 확인 후 진행해주세요. \"\n"+
                        "만약 감지된 사용자의 언어가 한국어가 아니라면(예: 영어, 베트남어 등), 위의 '한국어 기준 기본 문구'를 감지된 사용자의 언어로 정확하게 번역하여 문장 맨 앞에 결합하세요."+
                        "예시: 영어인 경우 \"🚨 Caution 🚨 : This could be an actual monetary transaction, please verify before proceeding. \" 와 같이 해당 언어에 맞게 자연스럽게 번역해야 합니다."+
                        "English: \"🚨 Caution 🚨 : This could be an actual monetary transaction, please verify before proceeding. "+
                        "Japanese: \"🚨 注意 🚨 : 実際の決済が発生する可能性があるため、確認の上進めてください。 "+
                        "Chinese: \"🚨 注意 🚨 : 这 可能是 实际 扣款 情况，请 确认 后 再 继续。"+
                        "Vietnamese: \"🚨 Chú ý 🚨 : Đây có thể là tình huống thanh toán thực tế, vui lòng kiểm tra kỹ trước khi tiếp tục. ";
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
                    "별표(*)나 우물 정(#) 같은 특수문자를 사용하여 단어를 강조하지 마세요. 대괄호([, ])를 사용하여 단어를 강조하고, 나머지 문장은 마크다운 기호 없이 평문으로만 대답하세요.\n"+
                    "만약 원본 입력 언어가 한국어가 아니라면, 대괄호 안에는 강조하려는 언어인 [" + InitLang + "] "+ InitLang +"단어를 적고, 그 바로 뒤 괄호 (" + instruction + ") 안에는 입력받았던 원본 언어로 번역된" + instruction +"언어 를 적으세요."+
                    "[출력 예시 (Target: Korean)] {\"question\": \"[예매]버튼을 누르세요.\"}\n\n" +
                    "[출력 예시 (Target: Chinese)] {\"question\": \"请按 [예매](预售) 按钮。.\"}\n\n" +
                    "[출력 예시 (Target: Japanese)] {\"question\": \"[예매](予約) ボタンを押してください。\"}\n\n" +
                    "[출력 예시 (Target: Vietnamese)] {\"question\": \"[예매](Đặt vé) Hãy nhấn nút.\"}\n\n" +
                    "[출력 예시 (Target: English)] {\"question\": \"Press the [예매](Reservation) button.\"}"+
                    "[한국어 단어](그 한국어 단어를 타겟 외국어로 번역한 단어)-> 예시: 한국어 단어가 '날씨'이고 타겟 언어가 영어라면 [날씨](Weather) 가 되어야 합니다. [Weather](날씨)가 아닙니다. 반드시 대괄호 안이 한국어여야 합니다."+
                    "출력에 말머리로 '서술식 요약'과 '개조식 상세'라는 단어를 사용하지마세요.\n" +
                    "이미지 속에 표가 존재한다면 OCR 텍스트는 정확하지 않을 수 있으니 이미지를 기준으로 설명해주세요.\n" ;
        } else if ("guide".equals(request.getMode())) {
            finalPrompt = "첨부된 현재 모바일 앱 화면 이미지와 아래 지시사항을 함께 분석하라.\n" +

                    "지시사항은 두 가지 형태일 수 있다.\n" +
                    "1. 단일 행동 문장 (예: '[회원가입] 버튼을 누르세요')\n" +
                    "2. 여러 단계로 이루어진 전체 안내문\n\n" +

                    "만약 지시사항이 단일 행동 문장이라면:\n" +
                    "- 해당 문장을 그대로 수행하기 위해 필요한 UI 요소를 찾으라.\n" +
                    "- 현재 화면에서 그 행동을 수행할 수 있는 버튼, 메뉴, 입력창 등을 선택하라.\n" +
                    "- 이미 문장에 버튼 이름이 포함되어 있다면 해당 버튼을 우선적으로 찾으라.\n" +
                    "- 현재 화면에서 수행 가능한 가장 직접적인 UI 요소 하나만 선택하라.\n\n" +
                    "- 선택한 UI 요소의 정중앙 좌표(x, y)를 반환하라.\n\n" +

                    "만약 지시사항이 여러 단계로 이루어진 전체 안내문이라면:\n" +
                    "- 현재 화면 상태를 분석하라.\n" +
                    "- 전체 안내문 중 현재 화면에서 수행해야 하는 단계가 무엇인지 판단하라.\n" +
                    "- 이미 완료된 단계는 건너뛰어라.\n" +
                    "- 아직 수행되지 않은 단계 중 현재 화면에서 수행 가능한 가장 적절한 다음 단계를 선택하라.\n" +
                    "- 선택한 단계를 수행하기 위해 터치해야 하는 UI 요소를 찾으라.\n\n" +
                    "- 선택한 UI 요소의 정중앙 좌표(x, y)를 반환하라.\n\n" +

                    "만약 현재 화면이 전체 안내문과 전혀 맞지 않는다면:\n" +
                    "- 사용자가 해당 안내를 진행하기 위해 이동해야 하는 화면 또는 앱을 추론하라.\n" +
                    "- 현재 화면에서 그 앱, 메뉴, 검색창, 홈 버튼 또는 이동 버튼에 해당하는 가장 적절한 UI 요소를 선택하라.\n" +
                    "- 예를 들어 은행 앱 사용 안내인데 현재 홈 화면이라면 해당 은행 앱 아이콘을 선택할 수 있다.\n" +
                    "- 쇼핑 앱 주문 안내인데 현재 다른 메뉴라면 주문내역 또는 홈 탭으로 이동하는 버튼을 선택할 수 있다.\n\n" +
                    "- 선택한 UI 요소의 정중앙 좌표(x, y)를 반환하라.\n\n" +

                    "매우 중요:\n" +
                    "- 반드시 첨부된 실제 이미지의 UI 위치를 기준으로 판단해라.\n" +
                    "- 현재 화면에 실제로 보이는 UI만 선택해라.\n" +
                    "- 여러 후보가 있으면 가장 가능성이 높은 하나만 선택해라.\n" +
                    "- 버튼 전체 영역의 정중앙 좌표를 반환해라.\n" +
                    "- 화면 전체 기준 좌표를 사용해라.\n" +
                    "- 원본 이미지 해상도 기준 좌표를 사용해라.\n" +
                    "- 상태바와 네비게이션바를 포함한 전체 이미지 기준이다.\n" +
                    "- 설명, 분석, 이유를 출력하지 마라.\n" +
                    "- 반드시 JSON만 출력해라.\n\n" +

                    "반환 형식:\n" +
                    "{\"x\":420,\"y\":1630}\n\n" +

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
                        "만약 원본 입력 언어가 한국어가 아니라면, 대괄호 안에는 강조하려는 언어인 [" + InitLang + "] "+ InitLang +"단어를 적고, 그 바로 뒤 괄호 (" + instruction + ") 안에는 입력받았던 원본 언어로 번역된" + instruction +"언어 를 적으세요."+
                        "[출력 예시 (Target: Korean)] {\"question\": \"[예매]버튼을 누르세요.\"}\n\n" +
                        "[출력 예시 (Target: Chinese)] {\"question\": \"请按 [예매](预售) 按钮。.\"}\n\n" +
                        "[출력 예시 (Target: Japanese)] {\"question\": \"[예매](予約) ボタンを押してください。\"}\n\n" +
                        "[출력 예시 (Target: Vietnamese)] {\"question\": \"[예매](Đặt vé) Hãy nhấn nút.\"}\n\n" +
                        "[출력 예시 (Target: English)] {\"question\": \"Press the [예매](Reservation) button.\"}"+
                        "[한국어 단어](그 한국어 단어를 타겟 외국어로 번역한 단어)-> 예시: 한국어 단어가 '날씨'이고 타겟 언어가 영어라면 [날씨](Weather) 가 되어야 합니다. [Weather](날씨)가 아닙니다. 반드시 대괄호 안이 한국어여야 합니다."+
                        "출력에 말머리로 '서술식 요약'과 '개조식 상세'라는 단어를 사용하지마세요.\n" +
                        "이미지 속에 표가 존재한다면 OCR 텍스트는 정확하지 않을 수 있으니 이미지를 기준으로 설명해주세요.\n" +
                        "위험 분석: OCR 텍스트에서 보이스피싱, 금융사기, 개인정보 요구 등 위험이 감지되면 맨 앞에 반드시 다음 문구를 추가하세요: \"🚨 주의 🚨 : 사기 또는 위험 상황일 수 있으니 확인 후 진행해주세요. \"\n" +
                        "결제, 결제하기, 구매, 구매하기송금, 이체, 보내기,충전, 충전하기, 후원, 후원하기, 기부, 간편결제, 원클릭 결제 등 결제와 관련된 단어가 감지되면 맨 앞에 반드시 다음 문구를 추가하세요. “ \"🚨 주의 🚨 : 의도치 않은 결제일 수 있으니 확인 후 진행해주세요. \"\n"+
                        "인증, 인증하기, 본인인증비밀번호 입력, PIN 번호, 패스워드지문 인식, Face ID, 생체 인증 간편결제, 원클릭 결제 등 인증 및 간편 결제 관련된 단어가 감지되면 맨 앞에 반드시 다음 문구를 추가하세요. “  \"🚨 주의 🚨 : 핵심정보 노출일 수 있으니 확인 후 진행해주세요. \"\n"+
                        "만약 감지된 사용자의 언어가 한국어가 아니라면(예: 영어, 베트남어 등), 위의 '한국어 기준 기본 문구'를 감지된 사용자의 언어로 정확하게 번역하여 문장 맨 앞에 결합하세요."+
                        "예시: 영어인 경우 \"🚨 Caution 🚨 : This could be an actual monetary transaction, please verify before proceeding. \" 와 같이 해당 언어에 맞게 자연스럽게 번역해야 합니다."+
                        "English: \"🚨 Caution 🚨 : This could be an actual monetary transaction, please verify before proceeding. "+
                        "Japanese: \"🚨 注意 🚨 : 実際の決済が発生する可能性があるため、確認の上進めてください。 "+
                        "Chinese: \"🚨 注意 🚨 : 这 可能是 实际 扣款 情况，请 确认 后 再 继续。"+
                        "Vietnamese: \"🚨 Chú ý 🚨 : Đây có thể là tình huống thanh toán thực tế, vui lòng kiểm tra kỹ trước khi tiếp tục. ";

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
                        "만약 원본 입력 언어가 한국어가 아니라면, 대괄호 안에는 강조하려는 언어인 [" + InitLang + "] "+ InitLang +"단어를 적고, 그 바로 뒤 괄호 (" + instruction + ") 안에는 입력받았던 원본 언어로 번역된" + instruction +"언어 를 적으세요."+
                        "[출력 예시 (Target: Korean)] {\"question\": \"[예매]버튼을 누르세요.\"}\n\n" +
                        "[출력 예시 (Target: Chinese)] {\"question\": \"请按 [예매](预售) 按钮。.\"}\n\n" +
                        "[출력 예시 (Target: Japanese)] {\"question\": \"[예매](予約) ボタンを押してください。\"}\n\n" +
                        "[출력 예시 (Target: Vietnamese)] {\"question\": \"[예매](Đặt vé) Hãy nhấn nút.\"}\n\n" +
                        "[출력 예시 (Target: English)] {\"question\": \"Press the [예매](Reservation) button.\"}"+
                        "[한국어 단어](그 한국어 단어를 타겟 외국어로 번역한 단어)-> 예시: 한국어 단어가 '날씨'이고 타겟 언어가 영어라면 [날씨](Weather) 가 되어야 합니다. [Weather](날씨)가 아닙니다. 반드시 대괄호 안이 한국어여야 합니다."+
                        "출력에 말머리로 '서술식 요약'과 '개조식 상세'라는 단어를 사용하지마세요.\n" +
                        "이미지 속에 표가 존재한다면 OCR 텍스트는 정확하지 않을 수 있으니 이미지를 기준으로 설명해주세요.\n" +
                        "위험 분석: OCR 텍스트에서 보이스피싱, 금융사기, 개인정보 요구 등 위험이 감지되면 맨 앞에 반드시 다음 문구를 추가하세요: \"🚨 주의 🚨 : 사기 또는 위험 상황일 수 있으니 확인 후 진행해주세요. \"\n" +
                        "결제, 결제하기, 구매, 구매하기송금, 이체, 보내기,충전, 충전하기, 후원, 후원하기, 기부, 간편결제, 원클릭 결제 등 결제와 관련된 단어가 감지되면 맨 앞에 반드시 다음 문구를 추가하세요. “ \"🚨 주의 🚨 : 의도치 않은 결제일 수 있으니 확인 후 진행해주세요. \"\n"+
                        "인증, 인증하기, 본인인증비밀번호 입력, PIN 번호, 패스워드지문 인식, Face ID, 생체 인증 간편결제, 원클릭 결제 등 인증 및 간편 결제 관련된 단어가 감지되면 맨 앞에 반드시 다음 문구를 추가하세요. “  \"🚨 주의 🚨 : 핵심정보 노출일 수 있으니 확인 후 진행해주세요. \"\n"+
                        "만약 감지된 사용자의 언어가 한국어가 아니라면(예: 영어, 베트남어 등), 위의 '한국어 기준 기본 문구'를 감지된 사용자의 언어로 정확하게 번역하여 문장 맨 앞에 결합하세요."+
                        "예시: 영어인 경우 \"🚨 Caution 🚨 : This could be an actual monetary transaction, please verify before proceeding. \" 와 같이 해당 언어에 맞게 자연스럽게 번역해야 합니다."+
                        "English: \"🚨 Caution 🚨 : This could be an actual monetary transaction, please verify before proceeding. "+
                        "Japanese: \"🚨 注意 🚨 : 実際の決済が発生する可能性があるため、確認の上進めてください。 "+
                        "Chinese: \"🚨 注意 🚨 : 这 可能是 实际 扣款 情况，请 确认 后 再 继续。"+
                        "Vietnamese: \"🚨 Chú ý 🚨 : Đây có thể là tình huống thanh toán thực tế, vui lòng kiểm tra kỹ trước khi tiếp tục. ";
            }
        }

        List<String> foreign = List.of("English", "Japanese","Chinese","Vietnamese");

        finalPrompt = finalPrompt + "\n\n" +
                "[SYSTEM RULE: You must provide the entire response in " + instruction + " ONLY. " +
                "Do not use any other languages. This is a strict requirement.] +" +
                "You MUST translate and write the 'question' value ONLY in \" + instruction + \".\\n\" "+
                "Content inside square brackets `[...]` MUST ONLY BE WRITTRN IN KOREAN.\n"+
                "Content inside square brackets `[...]` MUST ONLY be written in"+InitLang +".\n"+
                "NEVER WRITE NON-KOREAN characters inside square brackets `[...]`.\n"+
                "Content inside parentheses `(...)` MUST ONLY be written in the "+foreign +".\n" +
                "Strictly adhere to the format: ["+InitLang +"]("+foreign +"). Never swap or invert them."+
                "[Example for rule compliance]\n" +
                "If " + InitLang + " is '날씨' and target language is '" + instruction + "', you must output: [날씨](Translated Word)\n" +
                "Never output: (Translated Word)[날씨] or [Translated Word](날씨).";

        try {
            //JSON 만들기
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode rootNode = mapper.createObjectNode();
            ArrayNode contentsNode = rootNode.putArray("contents");
            ObjectNode contentItemNode = contentsNode.addObject();
            ArrayNode partsNode = contentItemNode.putArray("parts");

            //  텍스트 프롬프트 넣기
            ObjectNode textPart = partsNode.addObject();
            textPart.put("text", finalPrompt);

            // 이미지가 있다면 이미지 데이터(Base64) 추가하기
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

            // 제미나이 API 호출
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            JsonNode root = mapper.readTree(response.getBody());

            // 응답 텍스트 추출
            String text = root
                    .path("candidates").get(0)
                    .path("content")
                    .path("parts").get(0)
                    .path("text")
                    .asText();

            // 6. DB 저장
            if (text != null && !text.trim().isEmpty()) {
                llmService.saveLog(
                        request.getUserId(),
                        request.getPackageName(),
                        request.getQuestion(),
                        text,
                        request.getMode()
                );
            }
            return Map.of("answer", text);

        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("answer", "서버 에러 발생: " + e.getMessage());
        }
    }


    // 음성인식 STT
    @PostMapping("/api/gemini/analyze-voice")
    public ResponseEntity<?> analyzeVoice(
            @RequestParam("audioFile") MultipartFile audioFile,
            @RequestParam("currentAppPkg") String currentAppPkg) {

        if (audioFile == null || audioFile.isEmpty()) {
            return ResponseEntity.status(400).body("오디오 파일이 유효하지 않습니다.");
        }
        try {
            String myRealKey = apiKey;
            String voiceUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=" + myRealKey;
            RestTemplate voiceRestTemplate = new RestTemplate();

            byte[] audioBytes = audioFile.getBytes();
            String base64Audio = Base64.getEncoder().encodeToString(audioBytes);

            // 목소리 입력 LLM 프롬포트
            String promptText = """ 
            사용자의 음성을 인식하세요.

            - 지원언어:
            English
            日本語
            中文
            Tiếng Việt
            한국어


            규칙:
            1. 오디오에서 사용자가 말한 언어를 자동으로 감지하세요.
            2. 'question' 필드에는 음성에서 인식한 내용을 사용자가 말한 언어의 고유 문자(예: 영어면 알파벳, 베트남어면 베트남 문자 등) 원문 그대로 작성하세요.
            3. 절대 들리는 소리를 한글(한국어)로 받아적거나 음차(음독)하지 마세요. (예: 'Hello'를 '헬로우'나 '안녕하세요'로 적으면 절대 안 됩니다. 반드시 'Hello'로 적으세요.)
            4. 절대 다른 언어로 번역하지 마세요.
            5. 마크다운 기호 없이 오직 JSON만 반환하세요.

            출력 형식"{\\n" + "  \\\"question\\\": \\\"음성에서 인식한 다국어 원문\\\",\\n" + "}
            """;

            // JSON 맵 데이터 구축
            Map<String, Object> inlineData = new HashMap<>();
            String mimeType = audioFile.getContentType() != null ? audioFile.getContentType() : "audio/mp3";
            inlineData.put("mimeType", mimeType);
            inlineData.put("data", base64Audio);

            Map<String, Object> audioPart = new HashMap<>();
            audioPart.put("inlineData", inlineData);

            Map<String, Object> textPart = new HashMap<>();
            textPart.put("text", promptText);

            List<Map<String, Object>> partsList = new ArrayList<>();
            partsList.add(audioPart);
            partsList.add(textPart);

            Map<String, Object> contentMap = new HashMap<>();
            contentMap.put("parts", partsList);

            List<Map<String, Object>> contentsList = new ArrayList<>();
            contentsList.add(contentMap);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", contentsList);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = voiceRestTemplate.postForEntity(voiceUrl, entity, String.class);

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode rootNode = mapper.readTree(response.getBody());

            String textResult = rootNode.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text")
                    .asText();

            textResult = textResult.replaceAll("(?s)^```(?:json)?\\s*|\\s*```$", "").trim();

            JsonNode json = mapper.readTree(textResult);
            String question = json.path("question").asText();

            Map<String, String> finalResponse = new HashMap<>();
            finalResponse.put("question", question);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(finalResponse);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("서버 음성 가이드 분석 실패: " + e.getMessage());
        }
    }
}
