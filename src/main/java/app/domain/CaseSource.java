package app.domain;

/**
 * 케이스 출처 (D20, D24, D25).
 * FIXED: 사람이 검토해 저장한 고정 케이스(PC-01), AI_GENERATED: 실습 시작 때 AI가 만든 케이스,
 * DEMO: PC-01 검토 전 화면 개발용 케이스(채점 정답·만점 테스트에 쓰지 않음).
 */
public enum CaseSource {
    FIXED,
    AI_GENERATED,
    DEMO
}
