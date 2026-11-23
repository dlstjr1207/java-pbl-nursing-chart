package app.cases;

import java.io.InputStream;
import java.net.URL;

/** PC-01.json이 없는 클래스패스를 흉내 내는 클래스로더 (PC-01이 없을 때 DEMO로 대체되는지 확인용). */
public final class WithoutPc01 extends ClassLoader {
    private static final String HIDDEN = "cases/PC-01.json";

    public WithoutPc01() {
        super(WithoutPc01.class.getClassLoader());
    }

    /** PC-01을 숨긴 케이스 로더. */
    public static CaseLoader loader() {
        return new CaseLoader(new WithoutPc01());
    }

    @Override
    public URL getResource(String name) {
        return HIDDEN.equals(name) ? null : super.getResource(name);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return HIDDEN.equals(name) ? null : super.getResourceAsStream(name);
    }
}
