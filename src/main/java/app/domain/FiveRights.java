package app.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 투약 5원칙(5 Rights) 확인 (D38, B2). 투약 기록을 저장하기 전에 약물·용량·대상자·경로·시간을 확인했는지 체크한다.
 * 다섯 개를 모두 체크해야 저장할 수 있고, 점수에는 넣지 않는다.
 *
 * @param drug    약물 확인
 * @param dose    용량 확인
 * @param patient 대상자 확인
 * @param route   경로 확인
 * @param time    시간 확인
 */
public record FiveRights(boolean drug, boolean dose, boolean patient, boolean route, boolean time) {

    /** 화면에 보이는 순서의 이름 */
    public static final List<String> LABELS = List.of("약물", "용량", "대상자", "경로", "시간");

    /** 다섯 개를 모두 체크한 값 */
    public static final FiveRights ALL = new FiveRights(true, true, true, true, true);

    /** 다섯 개를 모두 체크했는지 */
    public boolean allChecked() {
        return drug && dose && patient && route && time;
    }

    /** 체크 상태를 LABELS 순서로 */
    public List<Boolean> values() {
        return List.of(drug, dose, patient, route, time);
    }

    /** 체크하지 않은 항목 이름 */
    public List<String> unchecked() {
        List<String> out = new ArrayList<>();
        List<Boolean> v = values();
        for (int i = 0; i < LABELS.size(); i++) if (!v.get(i)) out.add(LABELS.get(i));
        return out;
    }

    /** 결과·다시 보기 표시: "5 Rights: 약물 ✓ · 용량 ✓ · 대상자 ✓ · 경로 ✓ · 시간 ✓". 기록이 없으면(예전 기록) 안내 문구. */
    public static String text(FiveRights r) {
        if (r == null) return "5 Rights: 기록 없음 (이 기능 전에 저장한 기록)";
        StringBuilder sb = new StringBuilder("5 Rights: ");
        List<Boolean> v = r.values();
        for (int i = 0; i < LABELS.size(); i++) {
            if (i > 0) sb.append(" · ");
            sb.append(LABELS.get(i)).append(v.get(i) ? " ✓" : " ✗");
        }
        return sb.toString();
    }
}
