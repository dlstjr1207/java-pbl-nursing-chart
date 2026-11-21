package app.cases;

import java.util.List;

/** 케이스 파일을 읽지 못했을 때의 오류. 원인(문제 목록)을 함께 담는다. */
public class CaseLoadException extends RuntimeException {

    private final List<String> problems;

    public CaseLoadException(String message, List<String> problems) {
        super(problems.isEmpty() ? message : message + ": " + String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    public CaseLoadException(String message, Throwable cause) {
        super(message + (cause.getMessage() == null ? "" : " (" + cause.getMessage() + ")"), cause);
        this.problems = List.of();
    }

    /** 발견된 문제 목록 (없으면 빈 목록). */
    public List<String> problems() {
        return problems;
    }
}
