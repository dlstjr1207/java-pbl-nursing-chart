package app.storage;

import app.domain.DomainJson;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 파일 저장 모드(D27)의 공통 입출력. JSON 파일 하나를 통째로 읽고 쓴다.
 * 쓰기는 임시 파일에 먼저 쓴 뒤 바꿔치기해서, 쓰는 도중 꺼져도 기존 파일이 깨지지 않게 한다.
 */
final class JsonFileStore {

    private JsonFileStore() {
    }

    /** 파일이 있으면 읽어 객체로 돌려주고, 없으면 null. */
    static <T> T read(Path file, Type type) {
        if (!Files.isRegularFile(file)) return null;
        try {
            return DomainJson.gson().fromJson(Files.readString(file, StandardCharsets.UTF_8), type);
        } catch (IOException e) {
            throw new UncheckedIOException("저장 파일을 읽지 못했습니다: " + file, e);
        }
    }

    /** 객체를 JSON으로 파일에 쓴다. 폴더가 없으면 만든다. */
    static void write(Path file, Object value) {
        try {
            Path dir = file.toAbsolutePath().getParent();
            if (dir != null) Files.createDirectories(dir);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, DomainJson.toJson(value), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("저장 파일을 쓰지 못했습니다: " + file, e);
        }
    }
}
