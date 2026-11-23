package app.drugs;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/**
 * 허가정보 API의 문서 필드(EE_DOC_DATA·UD_DOC_DATA)는 {@code <DOC><SECTION><ARTICLE><PARAGRAPH>} 형태의 XML 문자열이다.
 * 화면에 보일 수 있게 제목과 문단을 한 줄씩 평문으로 바꾼다.
 */
public final class DrugDocText {

    private DrugDocText() {
    }

    /** XML 문서 문자열을 평문으로 바꾼다. 비어 있으면 빈 문자열, XML이 아니면 태그만 걷어 낸다. */
    public static String toText(String xml) {
        if (xml == null || xml.isBlank()) return "";
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            // 외부 엔티티·DTD를 읽지 않는다
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setExpandEntityReferences(false);
            var builder = f.newDocumentBuilder();
            // 형식 오류를 표준 오류에 찍지 않고 예외로만 받는다
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override
                public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException {
                    throw e;
                }
            });
            Element root = builder.parse(new InputSource(new StringReader(xml))).getDocumentElement();
            List<String> lines = new ArrayList<>();
            walk(root, lines);
            return String.join("\n", lines);
        } catch (Exception e) {
            return clean(xml.replaceAll("<[^>]+>", "\n")).replaceAll("\n{2,}", "\n").strip();
        }
    }

    /** SECTION·ARTICLE 제목과 PARAGRAPH 본문을 차례로 모은다. */
    private static void walk(Element e, List<String> lines) {
        String tag = e.getTagName();
        if (tag.equals("PARAGRAPH")) {
            add(lines, e.getTextContent());
            return;
        }
        if (tag.equals("SECTION") || tag.equals("ARTICLE")) add(lines, e.getAttribute("title"));
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element c) walk(c, lines);
        }
    }

    private static void add(List<String> lines, String raw) {
        String s = clean(raw);
        if (!s.isEmpty()) lines.add(s);
    }

    /** 문단 안에 남은 HTML 태그·엔티티와 겹친 공백을 정리한다. */
    static String clean(String s) {
        if (s == null) return "";
        return s.replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
                .replace(' ', ' ')
                .replaceAll("[ \\t]+", " ")
                .strip();
    }
}
