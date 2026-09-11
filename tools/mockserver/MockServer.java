import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MockServer {

    public static void main(String[] args) throws IOException {
        int port = 8080;
        String portEnv = System.getenv("PORT");
        if (portEnv != null && !portEnv.isBlank()) {
            port = Integer.parseInt(portEnv.trim());
        }

        Db db = new Db();
        Handlers handlers = new Handlers(db);
        List<Route> routes = handlers.routes();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", new Router(routes));
        server.setExecutor(null);
        server.start();

        System.out.println("Mock server listening on http://127.0.0.1:" + port);
    }

    private MockServer() {
    }
}

interface RouteHandler {
    void handle(HttpExchange exchange, Matcher pathMatcher, Map<String, String> query) throws IOException;
}

final class Route {
    final String method;
    final Pattern pathPattern;
    final RouteHandler handler;

    Route(String method, String pathRegex, RouteHandler handler) {
        this.method = method;
        this.pathPattern = Pattern.compile(pathRegex);
        this.handler = handler;
    }
}

final class Router implements HttpHandler {
    private final List<Route> routes;

    Router(List<Route> routes) {
        this.routes = routes;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String rawPath = exchange.getRequestURI().getRawPath();
        String method = exchange.getRequestMethod();
        System.out.println(method + " " + exchange.getRequestURI());

        try {
            for (Route route : routes) {
                if (!route.method.equals(method)) {
                    continue;
                }
                Matcher matcher = route.pathPattern.matcher(rawPath);
                if (matcher.matches()) {
                    Map<String, String> query = HttpUtil.parseQuery(exchange.getRequestURI().getRawQuery());
                    route.handler.handle(exchange, matcher, query);
                    return;
                }
            }
            HttpUtil.sendJson(exchange, 404, Json.map("error", "not_found", "detail", "No route for " + method + " " + rawPath));
        } catch (Exception e) {
            e.printStackTrace();
            HttpUtil.sendJson(exchange, 500, Json.map("error", "internal_error", "detail", String.valueOf(e.getMessage())));
        } finally {
            exchange.close();
        }
    }
}

final class HttpUtil {

    static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> result = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return result;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            result.put(decode(key), decode(value));
        }
        return result;
    }

    static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    static byte[] readBody(HttpExchange exchange) throws IOException {
        InputStream in = exchange.getRequestBody();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int read;
        while ((read = in.read(buf)) != -1) {
            out.write(buf, 0, read);
        }
        return out.toByteArray();
    }

    static String readBodyAsString(HttpExchange exchange) throws IOException {
        return new String(readBody(exchange), StandardCharsets.UTF_8);
    }

    static Map<String, Object> readJsonBody(HttpExchange exchange) throws IOException {
        String body = readBodyAsString(exchange);
        if (body.isBlank()) {
            return new LinkedHashMap<>();
        }
        Object parsed = new Json.Parser(body).parseValue();
        if (parsed instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) parsed;
            return map;
        }
        return new LinkedHashMap<>();
    }

    static Map<String, String> parseFormEncoded(String body) {
        return parseQuery(body);
    }

    static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    static void sendHtml(HttpExchange exchange, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    static String htmlEscape(String s) {
        return s.replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }

    static Long parseLongOrNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Integer parseIntOrNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

final class Json {

    static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    static List<Object> list(Object... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            writeString(sb, (String) value);
        } else if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
            sb.append(value.toString());
        } else if (value instanceof Double || value instanceof Float) {
            sb.append(value.toString());
        } else if (value instanceof Map) {
            writeObject(sb, (Map<String, Object>) value);
        } else if (value instanceof List) {
            writeArray(sb, (List<Object>) value);
        } else {
            writeString(sb, value.toString());
        }
    }

    private static void writeObject(StringBuilder sb, Map<String, Object> map) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeString(sb, entry.getKey());
            sb.append(':');
            writeValue(sb, entry.getValue());
        }
        sb.append('}');
    }

    private static void writeArray(StringBuilder sb, List<Object> list) {
        sb.append('[');
        boolean first = true;
        for (Object value : list) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeValue(sb, value);
        }
        sb.append(']');
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        Object parseValue() {
            skipWhitespace();
            char c = peek();
            if (c == '{') {
                return parseObject();
            }
            if (c == '[') {
                return parseArray();
            }
            if (c == '"') {
                return parseString();
            }
            if (c == 't' || c == 'f') {
                return parseBoolean();
            }
            if (c == 'n') {
                pos += 4;
                return null;
            }
            return parseNumber();
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++;
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                pos++; // ':'
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                char c = peek();
                pos++;
                if (c == '}') {
                    break;
                }
            }
            return map;
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++;
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                Object value = parseValue();
                list.add(value);
                skipWhitespace();
                char c = peek();
                pos++;
                if (c == ']') {
                    break;
                }
            }
            return list;
        }

        private String parseString() {
            StringBuilder sb = new StringBuilder();
            pos++; // opening quote
            while (true) {
                char c = s.charAt(pos++);
                if (c == '"') {
                    break;
                }
                if (c == '\\') {
                    char escaped = s.charAt(pos++);
                    switch (escaped) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            String hex = s.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                        }
                        default -> sb.append(escaped);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private Boolean parseBoolean() {
            if (s.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            pos += 5;
            return Boolean.FALSE;
        }

        private Object parseNumber() {
            int start = pos;
            boolean isDouble = false;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == '-' || c == '+' || Character.isDigit(c)) {
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E') {
                    isDouble = true;
                    pos++;
                } else {
                    break;
                }
            }
            String numStr = s.substring(start, pos);
            if (isDouble) {
                return Double.parseDouble(numStr);
            }
            try {
                return Long.parseLong(numStr);
            } catch (NumberFormatException e) {
                return Double.parseDouble(numStr);
            }
        }

        private void skipWhitespace() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        private char peek() {
            return s.charAt(pos);
        }
    }

    private Json() {
    }
}

final class MultipartPart {
    final String name;
    final String filename;
    final String contentType;
    final byte[] bytes;

    MultipartPart(String name, String filename, String contentType, byte[] bytes) {
        this.name = name;
        this.filename = filename;
        this.contentType = contentType;
        this.bytes = bytes;
    }

    String asText() {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}

final class MultipartParser {

    static List<MultipartPart> parse(byte[] body, String boundary) {
        List<MultipartPart> parts = new ArrayList<>();
        byte[] delimiter = ("--" + boundary).getBytes(StandardCharsets.UTF_8);
        List<Integer> markers = findAll(body, delimiter);

        for (int i = 0; i < markers.size() - 1; i++) {
            int start = markers.get(i) + delimiter.length;
            int end = markers.get(i + 1);
            if (start >= body.length || start >= end) {
                continue;
            }
            byte[] section = Arrays.copyOfRange(body, start, end);
            MultipartPart part = parseSection(section);
            if (part != null) {
                parts.add(part);
            }
        }
        return parts;
    }

    private static MultipartPart parseSection(byte[] section) {
        String headerBlock;
        int headerEnd = indexOf(section, "\r\n\r\n".getBytes(StandardCharsets.UTF_8), 0);
        int bodyStart;
        if (headerEnd < 0) {
            return null;
        }
        headerBlock = new String(section, 0, headerEnd, StandardCharsets.UTF_8);
        bodyStart = headerEnd + 4;

        int bodyEnd = section.length;
        while (bodyEnd > bodyStart && (section[bodyEnd - 1] == '\n' || section[bodyEnd - 1] == '\r' || section[bodyEnd - 1] == '-')) {
            bodyEnd--;
        }
        byte[] contentBytes = Arrays.copyOfRange(section, bodyStart, Math.max(bodyStart, bodyEnd));

        String name = null;
        String filename = null;
        String contentType = "application/octet-stream";
        for (String line : headerBlock.split("\r\n")) {
            String lower = line.toLowerCase();
            if (lower.startsWith("content-disposition")) {
                name = extractParam(line, "name");
                filename = extractParam(line, "filename");
            } else if (lower.startsWith("content-type")) {
                int idx = line.indexOf(':');
                contentType = line.substring(idx + 1).trim();
            }
        }
        if (name == null) {
            return null;
        }
        return new MultipartPart(name, filename, contentType, contentBytes);
    }

    private static String extractParam(String headerLine, String paramName) {
        Pattern pattern = Pattern.compile(paramName + "=\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(headerLine);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static List<Integer> findAll(byte[] haystack, byte[] needle) {
        List<Integer> results = new ArrayList<>();
        int from = 0;
        while (true) {
            int idx = indexOf(haystack, needle, from);
            if (idx < 0) {
                break;
            }
            results.add(idx);
            from = idx + needle.length;
        }
        return results;
    }

    private static int indexOf(byte[] haystack, byte[] needle, int from) {
        outer:
        for (int i = from; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    static String extractBoundary(String contentType) {
        Matcher matcher = Pattern.compile("boundary=\"?([^\";]+)\"?").matcher(contentType);
        return matcher.find() ? matcher.group(1) : null;
    }

    private MultipartParser() {
    }
}

final class Db {

    final Map<String, Object> warehouse = Json.map(
        "id", 1L,
        "name", "DC-1",
        "enforceBins", true
    );

    final List<Map<String, Object>> purchaseOrders = new ArrayList<>();
    final List<Map<String, Object>> bins = new ArrayList<>();
    final List<Map<String, Object>> receipts = new ArrayList<>();
    final List<Map<String, Object>> varianceReasons = new ArrayList<>();

    final Map<String, Map<String, Object>> idempotencyStore = new LinkedHashMap<>();

    final AtomicLong receiptIdSeq = new AtomicLong(100);
    final AtomicLong photoIdSeq = new AtomicLong(1000);

    Db() {
        seedPurchaseOrders();
        seedBins();
        seedReceipts();
        seedVarianceReasons();
    }

    private void seedPurchaseOrders() {
        purchaseOrders.add(Json.map(
            "id", 1L,
            "number", "PO-1001",
            "vendorName", "Acme Supply Co",
            "warehouseRef", warehouse,
            "blindCount", true,
            "status", "OPEN",
            "expectedDeliveryDate", "2026-09-08",
            "lines", Json.list(
                line(101L, "SKU-1001", "Widget A - 12pk", Json.map("upc", "012345678905"), 0, false, 60),
                line(102L, "SKU-1002", "Widget B - 6pk", Json.map("ean", "4006381333931"), 0, false, 60),
                line(103L, "SKU-1003", "Widget C - Single", Json.map("gtin", "00012345678905"), 0, true, 60)
            )
        ));

        purchaseOrders.add(Json.map(
            "id", 2L,
            "number", "PO-1002",
            "vendorName", "Blue Ridge Distributors",
            "warehouseRef", warehouse,
            "blindCount", false,
            "status", "PARTIALLY_RECEIVED",
            "expectedDeliveryDate", "2026-09-06",
            "lines", Json.list(
                line(201L, "SKU-2001", "Case of Bolts - M8", Json.map("upc", "099999999999"), 20, false, 50),
                line(202L, "SKU-2002", "Case of Nuts - M8", Json.map(), 0, false, 40)
            )
        ));

        purchaseOrders.add(Json.map(
            "id", 3L,
            "number", "PO-1003",
            "vendorName", "Cascade Hardware",
            "warehouseRef", warehouse,
            "blindCount", true,
            "status", "OPEN",
            "expectedDeliveryDate", "2026-09-10",
            "lines", Json.list(
                line(301L, "SKU-3001", "Serialized Tool Kit", Json.map("fnsku", "X001ABC123"), 0, true, 20),
                line(302L, "SKU-3002", "Tool Kit Accessory Pack", Json.map("upc", "011111111111"), 0, false, 20)
            )
        ));

        for (Map<String, Object> po : purchaseOrders) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> lines = (List<Map<String, Object>>) po.get("lines");
            int total = 0;
            for (Map<String, Object> l : lines) {
                total += (Integer) l.get("quantityExpectedInternal");
            }
            po.put("lineCount", lines.size());
            po.put("totalUnitsExpected", total);
        }
    }

    private Map<String, Object> line(long itemId, String sku, String name, Map<String, Object> identifiers,
                                      int quantityAlreadyReceived, boolean requiresSerial, int quantityExpectedInternal) {
        return Json.map(
            "purchaseOrderItemId", itemId,
            "sku", sku,
            "name", name,
            "identifiers", identifiers,
            "quantityAlreadyReceived", quantityAlreadyReceived,
            "requiresSerialNumber", requiresSerial,
            "quantityExpectedInternal", quantityExpectedInternal
        );
    }

    private void seedBins() {
        bins.add(Json.map("id", 1L, "label", "A-01", "warehouseId", 1L, "warehouseName", "DC-1", "isDefaultReceivingBin", true));
        bins.add(Json.map("id", 2L, "label", "A-02", "warehouseId", 1L, "warehouseName", "DC-1", "isDefaultReceivingBin", false));
        bins.add(Json.map("id", 3L, "label", "B-01", "warehouseId", 1L, "warehouseName", "DC-1", "isDefaultReceivingBin", false));
    }

    private void seedReceipts() {
        Instant now = Instant.now();
        Instant outsideReceivedAt = now.minus(2, ChronoUnit.HOURS);
        Instant insideReceivedAt = now.minus(2, ChronoUnit.MINUTES);

        receipts.add(Json.map(
            "id", 1L,
            "purchaseOrderNumber", "PO-0900",
            "vendorName", "Blue Ridge Distributors",
            "lineCount", 2,
            "totalUnits", 90,
            "receivedAt", outsideReceivedAt.toString(),
            "isVoided", false,
            "hasVariance", false,
            "voidAvailableUntil", outsideReceivedAt.plus(15, ChronoUnit.MINUTES).toString()
        ));

        receipts.add(Json.map(
            "id", 2L,
            "purchaseOrderNumber", "PO-0901",
            "vendorName", "Acme Supply Co",
            "lineCount", 3,
            "totalUnits", 180,
            "receivedAt", insideReceivedAt.toString(),
            "isVoided", false,
            "hasVariance", true,
            "voidAvailableUntil", insideReceivedAt.plus(15, ChronoUnit.MINUTES).toString()
        ));
    }

    private void seedVarianceReasons() {
        varianceReasons.add(Json.map("id", 1L, "label", "Damaged in transit"));
        varianceReasons.add(Json.map("id", 2L, "label", "Short shipment"));
        varianceReasons.add(Json.map("id", 3L, "label", "Vendor substitution"));
    }

    Map<String, Object> findPurchaseOrder(long id) {
        for (Map<String, Object> po : purchaseOrders) {
            if (((Number) po.get("id")).longValue() == id) {
                return po;
            }
        }
        return null;
    }

    Map<String, Object> findReceipt(long id) {
        for (Map<String, Object> r : receipts) {
            if (((Number) r.get("id")).longValue() == id) {
                return r;
            }
        }
        return null;
    }
}

final class Handlers {
    private final Db db;

    Handlers(Db db) {
        this.db = db;
    }

    List<Route> routes() {
        List<Route> routes = new ArrayList<>();
        routes.add(new Route("GET", "^/protocol/openid-connect/auth$", this::authPage));
        routes.add(new Route("POST", "^/protocol/openid-connect/token$", this::token));
        routes.add(new Route("GET", "^/api/me/$", this::me));
        routes.add(new Route("GET", "^/api/mobile/receiving/purchase-orders/$", this::workQueue));
        routes.add(new Route("GET", "^/api/mobile/receiving/purchase-orders/(?<id>\\d+)/$", this::purchaseOrderDetail));
        routes.add(new Route("POST", "^/api/mobile/receiving/purchase-orders/(?<id>\\d+)/resolve-scan/$", this::resolveScan));
        routes.add(new Route("POST", "^/api/mobile/receiving/purchase-orders/(?<id>\\d+)/receive/$", this::receive));
        routes.add(new Route("POST", "^/api/mobile/receiving/purchase-orders/(?<id>\\d+)/reconcile/$", this::reconcile));
        routes.add(new Route("POST", "^/api/mobile/receiving/receipts/(?<id>\\d+)/photos/$", this::uploadPhoto));
        routes.add(new Route("GET", "^/api/mobile/receiving/bins/$", this::bins));
        routes.add(new Route("GET", "^/api/mobile/receiving/receipts/$", this::receipts));
        routes.add(new Route("POST", "^/api/mobile/receiving/receipts/(?<id>\\d+)/void/$", this::voidReceipt));
        return routes;
    }

    private void authPage(HttpExchange exchange, Matcher pathMatcher, Map<String, String> query) throws IOException {
        String redirectUri = query.getOrDefault("redirect_uri", "");
        String state = query.getOrDefault("state", "");
        String href = redirectUri + "?code=dummy-auth-code&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8);
        String html = "<!doctype html><html><head><title>Dummy Sign In</title></head><body>"
            + "<h1>Dummy OAuth Provider</h1>"
            + "<p>This is a local mock sign-in page for development.</p>"
            + "<a href=\"" + HttpUtil.htmlEscape(href) + "\">Continue as Test User</a>"
            + "</body></html>";
        HttpUtil.sendHtml(exchange, 200, html);
    }

    private void token(HttpExchange exchange, Matcher pathMatcher, Map<String, String> query) throws IOException {
        HttpUtil.readBodyAsString(exchange); // form-encoded, not validated
        Map<String, Object> body = Json.map(
            "access_token", "dummy-access-token",
            "refresh_token", "dummy-refresh-token",
            "token_type", "Bearer",
            "expires_in", 3600
        );
        HttpUtil.sendJson(exchange, 200, body);
    }

    private void me(HttpExchange exchange, Matcher pathMatcher, Map<String, String> query) throws IOException {
        Map<String, Object> meWarehouse = Json.map(
            "id", 1L,
            "name", "DC-1",
            "is_default", true,
            "enforce_bins", true
        );
        Map<String, Object> permissions = Json.map(
            "can_receive", true,
            "can_over_receive", true
        );
        Map<String, Object> company = Json.map(
            "external_id", "test-co",
            "name", "Test Co",
            "is_default", true,
            "warehouses", Json.list(meWarehouse),
            "permissions", permissions
        );
        Map<String, Object> user = Json.map(
            "id", 1L,
            "email", "test.user@example.com",
            "display_name", "Test User"
        );
        Map<String, Object> body = Json.map(
            "user", user,
            "companies", Json.list(company)
        );
        HttpUtil.sendJson(exchange, 200, body);
    }

    private void workQueue(HttpExchange exchange, Matcher pathMatcher, Map<String, String> query) throws IOException {
        Long warehouseId = HttpUtil.parseLongOrNull(query.get("warehouse"));
        String search = query.get("search");
        Integer limit = HttpUtil.parseIntOrNull(query.get("limit"));
        Integer offset = HttpUtil.parseIntOrNull(query.get("offset"));

        List<Map<String, Object>> matches = new ArrayList<>();
        for (Map<String, Object> po : db.purchaseOrders) {
            if (warehouseId != null && ((Number) db.warehouse.get("id")).longValue() != warehouseId) {
                continue;
            }
            if (search != null && !search.isBlank()) {
                String needle = search.toLowerCase();
                String number = ((String) po.get("number")).toLowerCase();
                String vendor = ((String) po.get("vendorName")).toLowerCase();
                if (!number.contains(needle) && !vendor.contains(needle)) {
                    continue;
                }
            }
            matches.add(po);
        }

        List<Object> results = new ArrayList<>();
        int start = offset != null ? Math.max(0, offset) : 0;
        int end = limit != null ? Math.min(matches.size(), start + Math.max(0, limit)) : matches.size();
        for (int i = start; i < end && i < matches.size(); i++) {
            results.add(toSummaryJson(matches.get(i)));
        }

        Map<String, Object> body = Json.map(
            "count", matches.size(),
            "next", null,
            "previous", null,
            "results", results
        );
        HttpUtil.sendJson(exchange, 200, body);
    }

    private Map<String, Object> toSummaryJson(Map<String, Object> po) {
        return Json.map(
            "id", po.get("id"),
            "number", po.get("number"),
            "vendor_name", po.get("vendorName"),
            "warehouse", warehouseJson(),
            "line_count", po.get("lineCount"),
            "total_units_expected", po.get("totalUnitsExpected"),
            "status", po.get("status"),
            "expected_delivery_date", po.get("expectedDeliveryDate")
        );
    }

    private Map<String, Object> warehouseJson() {
        return Json.map(
            "id", db.warehouse.get("id"),
            "name", db.warehouse.get("name"),
            "enforce_bins", db.warehouse.get("enforceBins")
        );
    }

    private void purchaseOrderDetail(HttpExchange exchange, Matcher pathMatcher, Map<String, String> query) throws IOException {
        long id = Long.parseLong(pathMatcher.group("id"));
        Map<String, Object> po = db.findPurchaseOrder(id);
        if (po == null) {
            HttpUtil.sendJson(exchange, 404, Json.map("error", "not_found", "detail", "No purchase order with id " + id));
            return;
        }

        List<Object> lines = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> internalLines = (List<Map<String, Object>>) po.get("lines");
        for (Map<String, Object> line : internalLines) {
            lines.add(Json.map(
                "purchase_order_item_id", line.get("purchaseOrderItemId"),
                "sku", line.get("sku"),
                "name", line.get("name"),
                "identifiers", identifiersJson((Map<String, Object>) line.get("identifiers")),
                "quantity_already_received", line.get("quantityAlreadyReceived"),
                "requires_serial_number", line.get("requiresSerialNumber")
            ));
        }

        Map<String, Object> body = Json.map(
            "id", po.get("id"),
            "number", po.get("number"),
            "vendor_name", po.get("vendorName"),
            "warehouse", warehouseJson(),
            "blind_count", po.get("blindCount"),
            "lines", lines,
            "total_units_expected", po.get("totalUnitsExpected"),
            "status", po.get("status")
        );
        HttpUtil.sendJson(exchange, 200, body);
    }

    private Map<String, Object> identifiersJson(Map<String, Object> identifiers) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (identifiers.containsKey("upc")) out.put("upc", identifiers.get("upc"));
        if (identifiers.containsKey("ean")) out.put("ean", identifiers.get("ean"));
        if (identifiers.containsKey("gtin")) out.put("gtin", identifiers.get("gtin"));
        if (identifiers.containsKey("fnsku")) out.put("fnsku", identifiers.get("fnsku"));
        return out;
    }

    private void resolveScan(HttpExchange exchange, Matcher pathMatcher, Map<String, String> query) throws IOException {
        long id = Long.parseLong(pathMatcher.group("id"));
        Map<String, Object> requestBody = HttpUtil.readJsonBody(exchange);
        String code = String.valueOf(requestBody.getOrDefault("code", ""));

        Map<String, Object> po = db.findPurchaseOrder(id);
        if (po == null) {
            HttpUtil.sendJson(exchange, 200, Json.map("outcome", "unknown_code", "code", code));
            return;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> internalLines = (List<Map<String, Object>>) po.get("lines");
        Map<String, Object> firstLine = internalLines.get(0);

        Map<String, Object> lineJson = Json.map(
            "purchase_order_item_id", firstLine.get("purchaseOrderItemId"),
            "sku", firstLine.get("sku"),
            "name", firstLine.get("name"),
            "quantity_already_received", firstLine.get("quantityAlreadyReceived"),
            "requires_serial_number", firstLine.get("requiresSerialNumber"),
            "fully_received", false
        );

        Map<String, Object> body = Json.map(
            "outcome", "matched",
            "matched_field", "sku",
            "line", lineJson
        );
        HttpUtil.sendJson(exchange, 200, body);
    }

    private void receive(HttpExchange exchange, Matcher pathMatcher, Map<String, String> query) throws IOException {
        long purchaseOrderId = Long.parseLong(pathMatcher.group("id"));
        Map<String, Object> requestBody = HttpUtil.readJsonBody(exchange);
        String idempotencyKey = String.valueOf(requestBody.getOrDefault("idempotency_key", ""));

        Map<String, Object> existing = db.idempotencyStore.get(idempotencyKey);
        if (existing != null) {
            Map<String, Object> replayed = new LinkedHashMap<>(existing);
            replayed.put("replayed", true);
            HttpUtil.sendJson(exchange, 200, replayed);
            return;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> requestLines = (List<Map<String, Object>>) requestBody.getOrDefault("lines", new ArrayList<>());

        List<Object> updated = new ArrayList<>();
        long totalUnits = 0;
        boolean hasVariance = false;
        for (Map<String, Object> line : requestLines) {
            updated.add(line.get("purchase_order_item_id"));
            Number qtyReceived = (Number) line.getOrDefault("quantity_received", 0L);
            Number qtyDamaged = (Number) line.getOrDefault("quantity_damaged", 0L);
            Number qtyMissing = (Number) line.getOrDefault("quantity_missing", 0L);
            totalUnits += qtyReceived.longValue();
            if (qtyDamaged.longValue() > 0 || qtyMissing.longValue() > 0) {
                hasVariance = true;
            }
        }

        long receiptId = db.receiptIdSeq.incrementAndGet();
        Map<String, Object> po = db.findPurchaseOrder(purchaseOrderId);

        Instant now = Instant.now();
        Map<String, Object> receipt = Json.map(
            "id", receiptId,
            "purchaseOrderNumber", po != null ? po.get("number") : "PO-UNKNOWN",
            "vendorName", po != null ? po.get("vendorName") : "Unknown Vendor",
            "lineCount", requestLines.size(),
            "totalUnits", (int) totalUnits,
            "receivedAt", now.toString(),
            "isVoided", false,
            "hasVariance", hasVariance,
            "voidAvailableUntil", now.plus(15, ChronoUnit.MINUTES).toString()
        );
        db.receipts.add(receipt);

        Map<String, Object> responseBody = Json.map(
            "receipt_id", receiptId,
            "replayed", false,
            "updated", updated,
            "failed", Json.list()
        );
        db.idempotencyStore.put(idempotencyKey, responseBody);

        HttpUtil.sendJson(exchange, 200, responseBody);
    }

    private void reconcile(HttpExchange exchange, Matcher pathMatcher, Map<String, String> query) throws IOException {
        long purchaseOrderId = Long.parseLong(pathMatcher.group("id"));
        Map<String, Object> po = db.findPurchaseOrder(purchaseOrderId);
        if (po == null) {
            HttpUtil.sendJson(exchange, 404, Json.map("error", "not_found", "detail", "No purchase order with id " + purchaseOrderId));
            return;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> internalLines = (List<Map<String, Object>>) po.get("lines");
        Map<String, Object> requestBody = HttpUtil.readJsonBody(exchange);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> requestLines = (List<Map<String, Object>>) requestBody.getOrDefault("lines", new ArrayList<>());

        List<Object> responseLines = new ArrayList<>();
        for (Map<String, Object> requestLine : requestLines) {
            long itemId = ((Number) requestLine.get("purchase_order_item_id")).longValue();
            long counted = ((Number) requestLine.getOrDefault("quantity_counted", 0L)).longValue();

            Map<String, Object> internalLine = findInternalLine(internalLines, itemId);
            if (internalLine == null) {
                continue;
            }
            int expected = (Integer) internalLine.get("quantityExpectedInternal");
            long delta = counted - expected;

            responseLines.add(Json.map(
                "purchase_order_item_id", itemId,
                "sku", internalLine.get("sku"),
                "name", internalLine.get("name"),
                "quantity_expected", expected,
                "quantity_counted", (int) counted,
                "delta", (int) delta,
                "is_over_receipt", delta > 0,
                "over_receipt_permitted", true
            ));
        }

        List<Object> reasons = new ArrayList<>();
        for (Map<String, Object> reason : db.varianceReasons) {
            reasons.add(Json.map("id", reason.get("id"), "label", reason.get("label")));
        }

        Map<String, Object> body = Json.map(
            "lines", responseLines,
            "variance_reasons", reasons
        );
        HttpUtil.sendJson(exchange, 200, body);
    }

    private Map<String, Object> findInternalLine(List<Map<String, Object>> lines, long itemId) {
        for (Map<String, Object> line : lines) {
            if (((Number) line.get("purchaseOrderItemId")).longValue() == itemId) {
                return line;
            }
        }
        return null;
    }

    private void uploadPhoto(HttpExchange exchange, Matcher pathMatcher, Map<String, String> query) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        byte[] body = HttpUtil.readBody(exchange);
        String boundary = contentType != null ? MultipartParser.extractBoundary(contentType) : null;

        String originalFilename = "upload.bin";
        String partContentType = "application/octet-stream";
        long fileSize = 0;

        if (boundary != null) {
            List<MultipartPart> parts = MultipartParser.parse(body, boundary);
            for (MultipartPart part : parts) {
                if ("file".equals(part.name)) {
                    if (part.filename != null) {
                        originalFilename = part.filename;
                    }
                    partContentType = part.contentType;
                    fileSize = part.bytes.length;
                }
            }
        }

        long photoId = db.photoIdSeq.incrementAndGet();
        Map<String, Object> responseBody = Json.map(
            "id", photoId,
            "original_filename", originalFilename,
            "file_size", fileSize,
            "content_type", partContentType
        );
        HttpUtil.sendJson(exchange, 200, responseBody);
    }

    private void bins(HttpExchange exchange, Matcher pathMatcher, Map<String, String> query) throws IOException {
        Long warehouseId = HttpUtil.parseLongOrNull(query.get("warehouse"));
        String search = query.get("search");

        List<Object> results = new ArrayList<>();
        for (Map<String, Object> bin : db.bins) {
            if (warehouseId != null && ((Number) bin.get("warehouseId")).longValue() != warehouseId) {
                continue;
            }
            if (search != null && !search.isBlank()) {
                String label = ((String) bin.get("label")).toLowerCase();
                if (!label.contains(search.toLowerCase())) {
                    continue;
                }
            }
            results.add(Json.map(
                "id", bin.get("id"),
                "label", bin.get("label"),
                "warehouse_id", bin.get("warehouseId"),
                "warehouse_name", bin.get("warehouseName"),
                "is_default_receiving_bin", bin.get("isDefaultReceivingBin")
            ));
        }

        Map<String, Object> body = Json.map(
            "count", results.size(),
            "next", null,
            "previous", null,
            "results", results
        );
        HttpUtil.sendJson(exchange, 200, body);
    }

    private void receipts(HttpExchange exchange, Matcher pathMatcher, Map<String, String> query) throws IOException {
        List<Object> results = new ArrayList<>();
        for (Map<String, Object> receipt : db.receipts) {
            results.add(Json.map(
                "id", receipt.get("id"),
                "purchase_order_number", receipt.get("purchaseOrderNumber"),
                "vendor_name", receipt.get("vendorName"),
                "line_count", receipt.get("lineCount"),
                "total_units", receipt.get("totalUnits"),
                "received_at", receipt.get("receivedAt"),
                "is_voided", receipt.get("isVoided"),
                "has_variance", receipt.get("hasVariance"),
                "void_available_until", receipt.get("voidAvailableUntil")
            ));
        }

        Map<String, Object> body = Json.map(
            "count", results.size(),
            "next", null,
            "previous", null,
            "results", results
        );
        HttpUtil.sendJson(exchange, 200, body);
    }

    private void voidReceipt(HttpExchange exchange, Matcher pathMatcher, Map<String, String> query) throws IOException {
        long receiptId = Long.parseLong(pathMatcher.group("id"));
        HttpUtil.readJsonBody(exchange); // {"reason": "..."}, not validated

        Map<String, Object> receipt = db.findReceipt(receiptId);
        if (receipt != null) {
            receipt.put("isVoided", true);
        }

        Map<String, Object> body = Json.map(
            "id", receiptId,
            "is_voided", true
        );
        HttpUtil.sendJson(exchange, 200, body);
    }
}
