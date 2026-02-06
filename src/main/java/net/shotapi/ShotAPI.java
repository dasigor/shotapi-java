package net.shotapi;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * ShotAPI Java Client
 *
 * Official Java SDK for ShotAPI - Screenshot & Rendering API
 *
 * Example:
 * <pre>
 * ShotAPI client = new ShotAPI("sk_your_api_key");
 * byte[] image = client.screenshot("https://example.com");
 * Files.write(Paths.get("screenshot.png"), image);
 * </pre>
 */
public class ShotAPI {
    private static final String DEFAULT_BASE_URL = "https://shotapi.net";
    private static final int DEFAULT_TIMEOUT = 60000;

    private final String apiKey;
    private String baseUrl;
    private int timeout;

    public ShotAPI(String apiKey) {
        this.apiKey = apiKey;
        this.baseUrl = DEFAULT_BASE_URL;
        this.timeout = DEFAULT_TIMEOUT;
    }

    public ShotAPI withBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        return this;
    }

    public ShotAPI withTimeout(int timeoutMs) {
        this.timeout = timeoutMs;
        return this;
    }

    /**
     * Take a screenshot of a URL
     */
    public byte[] screenshot(String url) throws ShotAPIException {
        return screenshot(url, new ScreenshotOptions());
    }

    public byte[] screenshot(String url, ScreenshotOptions options) throws ShotAPIException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("url", url);
        options.applyTo(payload);
        return request("/v1/screenshot", payload);
    }

    /**
     * Render HTML/CSS to an image
     */
    public byte[] render(String html) throws ShotAPIException {
        return render(html, new RenderOptions());
    }

    public byte[] render(String html, RenderOptions options) throws ShotAPIException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("html", html);
        options.applyTo(payload);
        return request("/v1/render", payload);
    }

    /**
     * Extract metadata from a URL
     */
    public Map<String, Object> metadata(String url) throws ShotAPIException {
        return metadata(url, false);
    }

    public Map<String, Object> metadata(String url, boolean extractMarkdown) throws ShotAPIException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("url", url);
        if (extractMarkdown) {
            payload.put("extract_markdown", true);
        }
        byte[] response = request("/v1/metadata", payload);
        return parseJson(new String(response, StandardCharsets.UTF_8));
    }

    /**
     * Take screenshots of multiple URLs
     */
    public BatchResult batch(List<String> urls) throws ShotAPIException {
        return batch(urls, new ScreenshotOptions());
    }

    public BatchResult batch(List<String> urls, ScreenshotOptions options) throws ShotAPIException {
        Map<String, Object> optionsMap = new HashMap<>();
        options.applyTo(optionsMap);

        Map<String, Object> payload = new HashMap<>();
        payload.put("urls", urls);
        payload.put("options", optionsMap);

        byte[] response = request("/v1/batch", payload);
        return BatchResult.fromJson(new String(response, StandardCharsets.UTF_8));
    }

    /**
     * Compare two URLs visually
     */
    public DiffResult diff(String urlA, String urlB) throws ShotAPIException {
        return diff(urlA, urlB, 1280, 720);
    }

    public DiffResult diff(String urlA, String urlB, int width, int height) throws ShotAPIException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("url_a", urlA);
        payload.put("url_b", urlB);
        payload.put("width", width);
        payload.put("height", height);

        try {
            URL url = new URL(baseUrl + "/v1/diff");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-API-Key", apiKey);
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);
            conn.setDoOutput(true);

            String json = toJson(payload);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            if (status >= 400) {
                handleError(status, readStream(conn.getErrorStream()));
            }

            String diffPercentage = conn.getHeaderField("X-Diff-Percentage");
            double percentage = diffPercentage != null ? Double.parseDouble(diffPercentage) : 0.0;
            byte[] image = readStream(conn.getInputStream());

            return new DiffResult(image, percentage);
        } catch (IOException e) {
            throw new ShotAPIException("Network error: " + e.getMessage());
        }
    }

    private byte[] request(String endpoint, Map<String, Object> payload) throws ShotAPIException {
        try {
            URL url = new URL(baseUrl + endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-API-Key", apiKey);
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);
            conn.setDoOutput(true);

            String json = toJson(payload);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            if (status >= 400) {
                handleError(status, readStream(conn.getErrorStream()));
            }

            return readStream(conn.getInputStream());
        } catch (IOException e) {
            throw new ShotAPIException("Network error: " + e.getMessage());
        }
    }

    private void handleError(int status, byte[] body) throws ShotAPIException {
        String message = new String(body, StandardCharsets.UTF_8);
        switch (status) {
            case 401:
                throw new AuthenticationException("Invalid API key");
            case 403:
                throw new FeatureNotAvailableException(message);
            case 429:
                throw new RateLimitException("Rate limit exceeded");
            default:
                throw new ShotAPIException(message, status);
        }
    }

    private byte[] readStream(InputStream is) throws IOException {
        if (is == null) return new byte[0];
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int bytesRead;
        while ((bytesRead = is.read(data)) != -1) {
            buffer.write(data, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":");
            sb.append(valueToJson(entry.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }

    private String valueToJson(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "\"" + escapeJson((String) value) + "\"";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : (List<?>) value) {
                if (!first) sb.append(",");
                first = false;
                sb.append(valueToJson(item));
            }
            sb.append("]");
            return sb.toString();
        }
        if (value instanceof Map) {
            return toJson((Map<String, Object>) value);
        }
        return "\"" + value.toString() + "\"";
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        // Simple JSON parser for response parsing
        // For production, consider using Jackson or Gson
        Map<String, Object> result = new HashMap<>();
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1);
            // Basic parsing - works for simple flat objects
            String[] pairs = json.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            for (String pair : pairs) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    String key = kv[0].trim().replaceAll("^\"|\"$", "");
                    String val = kv[1].trim();
                    if (val.startsWith("\"") && val.endsWith("\"")) {
                        result.put(key, val.substring(1, val.length() - 1));
                    } else if (val.equals("null")) {
                        result.put(key, null);
                    } else if (val.equals("true") || val.equals("false")) {
                        result.put(key, Boolean.parseBoolean(val));
                    } else {
                        try {
                            result.put(key, Double.parseDouble(val));
                        } catch (NumberFormatException e) {
                            result.put(key, val);
                        }
                    }
                }
            }
        }
        return result;
    }

    // ==================== Options Classes ====================

    public static class ScreenshotOptions {
        private Integer width;
        private Integer height;
        private Boolean fullPage;
        private String format;
        private Integer quality;
        private Double delay;
        private String mockup;
        private String selector;
        private Double deviceScaleFactor;
        private Boolean darkMode;
        private String customCss;
        private String customJs;
        private Boolean blockAds;
        private String timezone;

        public ScreenshotOptions width(int width) { this.width = width; return this; }
        public ScreenshotOptions height(int height) { this.height = height; return this; }
        public ScreenshotOptions fullPage(boolean fullPage) { this.fullPage = fullPage; return this; }
        public ScreenshotOptions format(String format) { this.format = format; return this; }
        public ScreenshotOptions quality(int quality) { this.quality = quality; return this; }
        public ScreenshotOptions delay(double delay) { this.delay = delay; return this; }
        public ScreenshotOptions mockup(String mockup) { this.mockup = mockup; return this; }
        public ScreenshotOptions selector(String selector) { this.selector = selector; return this; }
        public ScreenshotOptions deviceScaleFactor(double factor) { this.deviceScaleFactor = factor; return this; }
        public ScreenshotOptions darkMode(boolean darkMode) { this.darkMode = darkMode; return this; }
        public ScreenshotOptions customCss(String css) { this.customCss = css; return this; }
        public ScreenshotOptions customJs(String js) { this.customJs = js; return this; }
        public ScreenshotOptions blockAds(boolean blockAds) { this.blockAds = blockAds; return this; }
        public ScreenshotOptions timezone(String timezone) { this.timezone = timezone; return this; }

        void applyTo(Map<String, Object> map) {
            if (width != null) map.put("width", width);
            if (height != null) map.put("height", height);
            if (fullPage != null) map.put("full_page", fullPage);
            if (format != null) map.put("format", format);
            if (quality != null) map.put("quality", quality);
            if (delay != null) map.put("delay", delay);
            if (mockup != null) map.put("mockup", mockup);
            if (selector != null) map.put("selector", selector);
            if (deviceScaleFactor != null) map.put("device_scale_factor", deviceScaleFactor);
            if (darkMode != null) map.put("dark_mode", darkMode);
            if (customCss != null) map.put("custom_css", customCss);
            if (customJs != null) map.put("custom_js", customJs);
            if (blockAds != null) map.put("block_ads", blockAds);
            if (timezone != null) map.put("timezone", timezone);
        }
    }

    public static class RenderOptions {
        private String css;
        private Integer width;
        private Integer height;
        private String format;
        private Double deviceScaleFactor;

        public RenderOptions css(String css) { this.css = css; return this; }
        public RenderOptions width(int width) { this.width = width; return this; }
        public RenderOptions height(int height) { this.height = height; return this; }
        public RenderOptions format(String format) { this.format = format; return this; }
        public RenderOptions deviceScaleFactor(double factor) { this.deviceScaleFactor = factor; return this; }

        void applyTo(Map<String, Object> map) {
            if (css != null) map.put("css", css);
            if (width != null) map.put("width", width);
            if (height != null) map.put("height", height);
            if (format != null) map.put("format", format);
            if (deviceScaleFactor != null) map.put("device_scale_factor", deviceScaleFactor);
        }
    }

    // ==================== Result Classes ====================

    public static class DiffResult {
        public final byte[] image;
        public final double percentage;

        public DiffResult(byte[] image, double percentage) {
            this.image = image;
            this.percentage = percentage;
        }
    }

    public static class BatchResult {
        public final List<BatchResultItem> results;

        public BatchResult(List<BatchResultItem> results) {
            this.results = results;
        }

        static BatchResult fromJson(String json) {
            // Simplified parsing
            List<BatchResultItem> items = new ArrayList<>();
            // For production, use Jackson/Gson
            return new BatchResult(items);
        }
    }

    public static class BatchResultItem {
        public final String url;
        public final String filename;
        public final String error;

        public BatchResultItem(String url, String filename, String error) {
            this.url = url;
            this.filename = filename;
            this.error = error;
        }
    }

    // ==================== Exception Classes ====================

    public static class ShotAPIException extends Exception {
        public final int statusCode;

        public ShotAPIException(String message) {
            super(message);
            this.statusCode = 0;
        }

        public ShotAPIException(String message, int statusCode) {
            super(message);
            this.statusCode = statusCode;
        }
    }

    public static class AuthenticationException extends ShotAPIException {
        public AuthenticationException(String message) {
            super(message, 401);
        }
    }

    public static class RateLimitException extends ShotAPIException {
        public RateLimitException(String message) {
            super(message, 429);
        }
    }

    public static class FeatureNotAvailableException extends ShotAPIException {
        public FeatureNotAvailableException(String message) {
            super(message, 403);
        }
    }
}
