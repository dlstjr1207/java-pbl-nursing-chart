-- 간호 차팅 연습 프로그램 MySQL 스키마 (D21, T-19)
-- 기준: docs/기획안.md '데이터와 저장', '저장 방식 (D21)'의 테이블 초안
-- 환자 케이스 원본(PC-01)은 DB가 아니라 src/main/resources/cases/ 의 JSON으로 관리한다.
-- 접속한 DB에 테이블을 만든다. CREATE DATABASE는 하지 않는다 (DB 생성은 사람이 root로 실행).
-- 여러 번 실행해도 안전하도록 CREATE TABLE IF NOT EXISTS를 쓴다.
-- 주의: SchemaInitializer가 세미콜론으로 문장을 나누므로 주석 안에 세미콜론을 쓰지 않는다.
-- 시각 컬럼(*_time)은 케이스 기준 시각을 입력 그대로 보존하기 위해 문자열로 둔다. 형식은 T-01에서 확정한다.

-- 사용자 계정 (F-13). 비밀번호는 PBKDF2 해시와 사용자별 솔트만 저장한다
CREATE TABLE IF NOT EXISTS users (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    login_id       VARCHAR(50)  NOT NULL,
    name           VARCHAR(50)  NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    salt           VARCHAR(255) NOT NULL,
    affiliation    VARCHAR(100) NULL COMMENT '소속',
    career_stage   VARCHAR(30)  NULL COMMENT '경력 단계 (가입 항목은 D-14에서 확정)',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_login_id (login_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 실습 1회차 (F-01, F-09, F-11). 고정 케이스와 AI 생성 케이스를 같은 형식으로 받는다 (D20)
CREATE TABLE IF NOT EXISTS attempts (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    user_id              BIGINT       NOT NULL,
    case_id              VARCHAR(50)  NOT NULL COMMENT '예: PC-01',
    case_source          VARCHAR(20)  NOT NULL DEFAULT 'FIXED' COMMENT 'FIXED 또는 AI_GENERATED',
    generated_case_json  LONGTEXT     NULL COMMENT 'AI 생성 케이스 원문 (과거 기록 재현용)',
    started_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at         DATETIME     NULL,
    score                DECIMAL(6,2) NULL COMMENT '미산출이면 NULL',
    ai_feedback_status   VARCHAR(20)  NOT NULL DEFAULT 'NONE' COMMENT 'NONE, SUCCESS, FAILED 등',
    ai_feedback_json     LONGTEXT     NULL COMMENT 'strengths, improvements, example_rewrite. 점수에 반영하지 않음',
    PRIMARY KEY (id),
    KEY idx_attempts_user (user_id, submitted_at),
    CONSTRAINT fk_attempts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 학생이 작성한 관찰 기록 (F-04). 기존 관찰 로그는 케이스 JSON에 있고 여기에는 저장하지 않는다
CREATE TABLE IF NOT EXISTS observation_records (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    attempt_id     BIGINT       NOT NULL,
    observed_time  VARCHAR(20)  NOT NULL COMMENT '관찰 시각 (입력 그대로)',
    shift          VARCHAR(20)  NOT NULL COMMENT '근무 (Day, Evening, Night)',
    tags           VARCHAR(500) NOT NULL COMMENT '태그 목록 (JSON 배열 문자열)',
    narrative      TEXT         NOT NULL COMMENT '서술',
    intervention   TEXT         NULL COMMENT '처치',
    vitals_json    TEXT         NULL COMMENT '선택적 활력징후 (JSON 문자열)',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_observation_attempt (attempt_id),
    CONSTRAINT fk_observation_attempt FOREIGN KEY (attempt_id) REFERENCES attempts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 학생이 작성한 투약 기록 (F-05). 용량은 처방과 달라도 저장하고 R7에서 지적한다
CREATE TABLE IF NOT EXISTS medication_records (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    attempt_id         BIGINT       NOT NULL,
    order_id           VARCHAR(50)  NOT NULL COMMENT '케이스 처방 식별자 (정규, PRN 구분 포함)',
    drug_name          VARCHAR(100) NOT NULL,
    dose               VARCHAR(30)  NOT NULL COMMENT '학생 입력 용량 (입력 그대로)',
    unit               VARCHAR(20)  NOT NULL,
    route              VARCHAR(30)  NOT NULL,
    administered_time  VARCHAR(20)  NOT NULL,
    result             VARCHAR(20)  NOT NULL COMMENT '투여, 거부, 보류 등',
    reason             TEXT         NULL COMMENT 'PRN, 거부, 보류는 필수 (R6, 앱에서 검증)',
    order_basis        VARCHAR(200) NOT NULL COMMENT '처방 근거',
    oral_check         TINYINT(1)   NULL COMMENT '구강 확인 여부',
    reassessment_plan  TEXT         NULL,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_medication_attempt (attempt_id),
    CONSTRAINT fk_medication_attempt FOREIGN KEY (attempt_id) REFERENCES attempts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- SBAR 인계 (F-06). 실습 1회차에 1건
CREATE TABLE IF NOT EXISTS sbar_reports (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    attempt_id  BIGINT   NOT NULL,
    situation   TEXT     NOT NULL,
    background  TEXT     NOT NULL,
    assessment  TEXT     NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sbar_attempt (attempt_id),
    CONSTRAINT fk_sbar_attempt FOREIGN KEY (attempt_id) REFERENCES attempts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- SBAR의 R: 시각과 중재를 가진 행 목록 (R9)
CREATE TABLE IF NOT EXISTS sbar_recommendations (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    sbar_id       BIGINT      NOT NULL,
    seq           INT         NOT NULL COMMENT '행 순서',
    rec_time      VARCHAR(20) NOT NULL,
    intervention  TEXT        NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sbar_rec_seq (sbar_id, seq),
    CONSTRAINT fk_sbar_rec_sbar FOREIGN KEY (sbar_id) REFERENCES sbar_reports (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 정신상태 평가와 투약 5원칙 확인 (D38). 예전 DB에도 새 표만 만들면 되도록 따로 둔다 (ALTER 없음)
-- extras_json: mentalStatus(MSE)와 medicationRights(투약 기록 순서대로 5원칙 체크) JSON
CREATE TABLE IF NOT EXISTS attempt_extras (
    attempt_id   BIGINT    NOT NULL,
    extras_json  LONGTEXT  NOT NULL,
    PRIMARY KEY (attempt_id),
    CONSTRAINT fk_extras_attempt FOREIGN KEY (attempt_id) REFERENCES attempts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 규칙 점검 결과 (F-08, F-09). 제출 당시 결과를 그대로 보존한다
CREATE TABLE IF NOT EXISTS check_results (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    attempt_id  BIGINT       NOT NULL,
    rule_id     VARCHAR(10)  NOT NULL COMMENT 'R1, R2, R4~R9',
    target      VARCHAR(20)  NOT NULL COMMENT 'OBSERVATION, MEDICATION, SBAR',
    target_ref  VARCHAR(100) NULL COMMENT '입력 위치 (기록 ID, 필드, 문장 위치)',
    status      VARCHAR(20)  NOT NULL COMMENT 'PASS, MISSING, MISMATCH, NOT_SCORED 등',
    message     TEXT         NULL,
    points      DECIMAL(6,2) NULL COMMENT '가중치 미확정이면 NULL',
    PRIMARY KEY (id),
    KEY idx_check_attempt (attempt_id),
    CONSTRAINT fk_check_attempt FOREIGN KEY (attempt_id) REFERENCES attempts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
