package app.auth;

/**
 * 저장된 사용자 계정. 비밀번호는 해시와 솔트만 가진다 (평문 없음, D23).
 *
 * @param id           DB가 부여한 번호 (메모리 구현은 순번)
 * @param loginId      아이디
 * @param name         이름
 * @param passwordHash PBKDF2 해시 (Base64)
 * @param salt         사용자별 솔트 (Base64)
 */
public record User(long id, String loginId, String name, String passwordHash, String salt) {

    /** 화면·로그에 비밀번호 관련 값이 나가지 않도록 toString을 제한한다. */
    @Override
    public String toString() {
        return "User[id=" + id + ", loginId=" + loginId + ", name=" + name + "]";
    }
}
