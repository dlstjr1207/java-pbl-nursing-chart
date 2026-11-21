package app.domain;

/** 처방 구분. 정규·PRN·과거 처방을 구분해 R7 비교 대상을 정한다 (기획안 R7). */
public enum OrderType {
    REGULAR,
    PRN,
    PAST
}
