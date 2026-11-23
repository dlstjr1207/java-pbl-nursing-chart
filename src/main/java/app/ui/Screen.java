package app.ui;

/** 화면 종류. needsCase 화면은 실습 중인 케이스가 있어야 들어갈 수 있다. */
public enum Screen {
    LOGIN("로그인", false, false),
    SIGNUP("회원가입", false, false),
    HOME("개인 메인", true, false),
    PATIENT("환자 정보", true, true),
    OBSERVATION("활력징후·관찰", true, true),
    MEDICATION("투약 점검", true, true),
    SBAR("SBAR", true, true);

    private final String title;
    private final boolean needsLogin;
    private final boolean needsCase;

    Screen(String title, boolean needsLogin, boolean needsCase) {
        this.title = title;
        this.needsLogin = needsLogin;
        this.needsCase = needsCase;
    }

    /** 메뉴·제목에 표시하는 이름. */
    public String title() { return title; }

    /** 로그인해야 들어갈 수 있는 화면인지. */
    public boolean needsLogin() { return needsLogin; }

    /** 실습 중인 케이스가 있어야 들어갈 수 있는 화면인지. */
    public boolean needsCase() { return needsCase; }
}
