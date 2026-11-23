package app.domain;

import java.util.List;

/** 모델 내부에서 쓰는 목록 도우미. JSON에 목록이 없으면(null) 빈 목록으로 바꾸고, 바꿀 수 없는 목록으로 보관한다. */
final class Lists {

    private Lists() {
    }

    /** null이면 빈 목록, 아니면 변경 불가 복사본을 돌려준다. */
    static <T> List<T> copy(List<T> list) {
        return list == null ? List.of() : List.copyOf(list);
    }
}
