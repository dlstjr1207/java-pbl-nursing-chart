package app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

/**
 * 빌드 환경 스모크 테스트 (T-00).
 * 화면을 띄우지 않으므로 CI 같은 headless 환경에서도 실행된다. AI 키가 필요 없다.
 */
class BuildSmokeTest {

    @Test
    void runsOnJava21OrLater() {
        assertTrue(Runtime.version().feature() >= 21,
                "JDK 21 이상이 필요합니다. 현재: " + Runtime.version());
    }

    @Test
    void mainClassHasEntryPoint() throws Exception {
        Method main = Main.class.getMethod("main", String[].class);
        assertTrue(Modifier.isStatic(main.getModifiers()));
        assertTrue(Modifier.isPublic(main.getModifiers()));
    }

    @Test
    void gsonIsOnClasspathAndHandlesKorean() {
        record Sample(String id, String title) {}
        Gson gson = new Gson();
        Sample original = new Sample("SMOKE", "간호 차팅");
        Sample copy = gson.fromJson(gson.toJson(original), Sample.class);
        assertEquals(original, copy);
    }
}
