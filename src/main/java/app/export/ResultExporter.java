package app.export;

import app.domain.Attempt;
import app.ui.ResultView;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

/**
 * 선택한 실습 1건의 입력과 점검 결과를 UTF-8 텍스트 파일로 저장한다 (T-26, D22 파일 입출력).
 * 강의에서 배운 FileWriter를 BufferedWriter로 감싸 한 줄씩 쓴다.
 * 저장 실패는 예외 대신 안내 문구로 돌려 화면이 그대로 보여 줄 수 있게 한다.
 */
public class ResultExporter {
    private static final DateTimeFormatter FILE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    /** 파일에 쓸 내용. 화면의 상세 문장과 같은 형식에 머리글을 붙인다. */
    public static String content(Attempt a) {
        return "간호 기록 실습 결과\n"
                + "교육용 예시 · 임상 검수 전\n"
                + "============================\n"
                + ResultView.detailText(a);
    }

    /** 저장 파일 이름 제안 (예: practice-DEMO-20261122-0930.txt). */
    public static String suggestedFileName(Attempt a) {
        String when = a.submittedAt() == null ? "draft" : a.submittedAt().format(FILE_FMT);
        String id = a.caseId() == null ? "case" : a.caseId().replaceAll("[^A-Za-z0-9_-]", "_");
        return "practice-" + id + "-" + when + ".txt";
    }

    /**
     * 파일로 저장한다. 성공이면 null, 실패면 사람이 읽을 안내 문구를 돌려준다.
     * 이미 있는 파일은 덮어쓴다(저장 대화상자에서 사용자가 고른 경로).
     */
    public String export(Attempt a, Path target) {
        if (a == null) return "내보낼 실습 기록을 먼저 선택해 주세요.";
        if (target == null) return "저장할 위치를 선택해 주세요.";
        try {
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null && !Files.isDirectory(parent)) return "저장할 폴더가 없습니다: " + parent;
            // try-with-resources로 파일을 반드시 닫는다. 한글이 깨지지 않도록 UTF-8을 지정한다.
            try (BufferedWriter out = new BufferedWriter(new FileWriter(target.toFile(), StandardCharsets.UTF_8))) {
                for (String line : content(a).split("\n", -1)) {
                    out.write(line);
                    out.newLine();
                }
            }
            return null;
        } catch (IOException | UncheckedIOException | SecurityException e) {
            return "파일을 저장하지 못했습니다. 위치와 권한을 확인해 주세요. (" + e.getClass().getSimpleName() + ")";
        }
    }
}
