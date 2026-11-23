package app.drugs;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * 식약처 의약품 제품 허가정보 API에서 허용 약물 4종의 효능효과·용법용량을 받아 drug-info.json으로 저장하는 별도 명령 (단계 7).
 * 프로그램 실행과 무관하게 사람이 필요할 때만 돌린다. 키는 config/ai.properties의 drug.apiKey에서 읽고 출력하지 않는다.
 *
 * <p>실행: {@code ./mvnw -q compile exec:java -Dmain.class=app.drugs.DrugInfoUpdater}
 */
public final class DrugInfoUpdater {

    /** 상세 조회 주소 (data.go.kr 15095677, DrugPrdtPrmsnInfoService08). */
    static final String DETAIL_URL = "https://apis.data.go.kr/1471000/DrugPrdtPrmsnInfoService08/getDrugPrdtPrmsnDtlInq08";
    static final Path DEFAULT_OUT = Path.of("src", "main", "resources", "drugs", "drug-info.json");
    static final String SOURCE = "식품의약품안전처_의약품 제품 허가정보 (공공데이터포털 15095677)";

    /**
     * 받을 약과 대표 제품. 근거표(E-20·E-21·E-28·E-29)에서 확인한 제품 계열로 고정한다.
     *
     * @param drug       허용 약물 이름
     * @param searchWord API 검색어 (item_name)
     * @param itemSeq    대표 제품 품목기준코드
     */
    public record Source(String drug, String searchWord, String itemSeq) {
    }

    public static final List<Source> SOURCES = List.of(
            new Source("Aripiprazole", "아리피졸정10밀리그램", "201102477"),   // 환인 아리피졸정 (E-20 허가보고서 계열)
            new Source("Olanzapine", "산도스올란자핀정10밀리그램", "200903065"), // E-28
            new Source("Lithium", "명인탄산리튬정300밀리그램", "198500638"),    // 명인탄산리튬정 (E-21 계열, PC-01 300mg)
            new Source("Lorazepam", "스리반정1밀리그램", "198501816"));        // E-29

    /** 검색어로 상세 응답 JSON을 받아 오는 부분. 테스트에서는 저장된 샘플로 바꾼다. */
    public interface Fetcher {
        String fetch(String searchWord) throws IOException;
    }

    private DrugInfoUpdater() {
    }

    /** 모든 약을 받아 정보 목록을 만든다. 하나라도 실패하면 이유와 함께 IOException. */
    public static List<DrugInfo> collect(Fetcher fetcher) throws IOException {
        List<DrugInfo> out = new ArrayList<>();
        for (Source s : SOURCES) {
            try {
                out.add(DrugInfoParser.parse(fetcher.fetch(s.searchWord()), s.drug(), s.itemSeq()));
            } catch (IllegalArgumentException e) {
                throw new IOException(s.drug() + ": " + e.getMessage());
            }
        }
        return out;
    }

    /** 저장할 JSON 문자열. */
    public static String toJson(List<DrugInfo> drugs, LocalDate fetchedAt) {
        var file = new DrugInfoCatalog.FileFormat(SOURCE, fetchedAt.toString(), drugs);
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(file) + "\n";
    }

    public static void main(String[] args) throws Exception {
        Path out = args.length > 0 ? Path.of(args[0]) : DEFAULT_OUT;
        String key = readKey(Path.of("config", "ai.properties"));
        if (key.isEmpty()) {
            System.out.println("config/ai.properties에 drug.apiKey가 없어 갱신하지 않습니다.");
            return;
        }
        List<DrugInfo> drugs = collect(httpFetcher(key));
        Files.createDirectories(out.getParent());
        Files.writeString(out, toJson(drugs, LocalDate.now()), StandardCharsets.UTF_8);
        for (DrugInfo d : drugs) {
            System.out.println(d.drug() + " → " + d.itemName() + " (변경일 " + d.changeDate() + ")");
        }
        System.out.println("저장: " + out);
    }

    private static String readKey(Path file) throws IOException {
        if (!Files.exists(file)) return "";
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        }
        return p.getProperty("drug.apiKey", "").trim();
    }

    /** 실제 API 호출. 오류 메시지에 주소(키 포함)를 넣지 않는다. */
    static Fetcher httpFetcher(String key) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return word -> {
            String url = DETAIL_URL + "?serviceKey=" + URLEncoder.encode(key, StandardCharsets.UTF_8)
                    + "&type=json&pageNo=1&numOfRows=100&item_name=" + URLEncoder.encode(word, StandardCharsets.UTF_8);
            try {
                HttpResponse<String> res = client.send(HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(30)).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                return res.body() == null ? "" : res.body().replace(key, "****");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("요청이 중단되었습니다.");
            }
        };
    }
}
