package com.example.worker.agent.application.service;

/**
 * /pdca analysis 결과 텍스트에서 갭 분석 통과 여부를 판별한다.
 *
 * <p>stream-json result 텍스트에 포함된 키워드 기반으로 판단한다.
 * PASS 조건: matchRate 90% 이상 또는 명시적 PASS/통과 키워드 포함.
 * FAIL 조건: FAIL/실패/gap 키워드 포함 또는 matchRate 90% 미만.
 */
final class GapAnalysisChecker {

    private static final int PASS_THRESHOLD = 90;

    private GapAnalysisChecker() {}

    static boolean isPassed(String analysisOutput) {
        if (analysisOutput == null || analysisOutput.isBlank()) {
            return false;
        }
        String lower = analysisOutput.toLowerCase();

        // 명시적 실패 키워드 → 즉시 FAIL
        if (containsFailKeyword(lower)) {
            return false;
        }

        // matchRate 숫자 추출 → 임계값 비교
        int matchRate = extractMatchRate(analysisOutput);
        if (matchRate >= 0) {
            return matchRate >= PASS_THRESHOLD;
        }

        // 명시적 통과 키워드 → PASS
        return containsPassKeyword(lower);
    }

    private static boolean containsFailKeyword(String lower) {
        return lower.contains("fail")
                || lower.contains("실패")
                || lower.contains("gap found")
                || lower.contains("갭 발견")
                || lower.contains("미구현")
                || lower.contains("누락");
    }

    private static boolean containsPassKeyword(String lower) {
        return lower.contains("pass")
                || lower.contains("통과")
                || lower.contains("완료")
                || lower.contains("갭 없음")
                || lower.contains("gap: 0")
                || lower.contains("gap 없음");
    }

    /**
     * "matchRate: 87" 또는 "matchRate: 87%" 패턴에서 숫자를 추출한다.
     * 찾지 못하면 -1 반환.
     */
    static int extractMatchRate(String output) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("matchRate[:\\s]+([0-9]+)")
                .matcher(output);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return -1;
    }
}
