package app.domain;

import java.util.List;

/**
 * 알레르기와 약물 부작용(ADR). C-08에 따라 두 가지를 구분해 보관한다.
 *
 * @param allergies        알레르기 목록
 * @param adverseReactions 약물 부작용 이력 목록
 */
public record Adr(List<String> allergies, List<String> adverseReactions) {

    public Adr {
        allergies = Lists.copy(allergies);
        adverseReactions = Lists.copy(adverseReactions);
    }
}
