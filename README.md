# ShotAPI Java SDK

Official Java SDK for [ShotAPI](https://shotapi.net) - The Screenshot & Rendering API.

## Requirements

- Java 11 or higher

## Installation

### Maven

```xml
<dependency>
    <groupId>net.shotapi</groupId>
    <artifactId>shotapi-java</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'net.shotapi:shotapi-java:1.0.0'
```

## Quick Start

```java
import net.shotapi.ShotAPI;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Example {
    public static void main(String[] args) throws Exception {
        ShotAPI client = new ShotAPI("sk_your_api_key");

        // Take a screenshot
        byte[] image = client.screenshot("https://example.com");
        Files.write(Paths.get("screenshot.png"), image);
    }
}
```

## Usage Examples

### Basic Screenshot

```java
ShotAPI client = new ShotAPI("sk_your_api_key");

// Simple screenshot
byte[] image = client.screenshot("https://stripe.com");

// Full-page screenshot with options
byte[] fullPage = client.screenshot("https://github.com",
    new ShotAPI.ScreenshotOptions()
        .fullPage(true)
        .format("png")
        .width(1920)
        .height(1080)
);

// Dark mode with retina
byte[] dark = client.screenshot("https://example.com",
    new ShotAPI.ScreenshotOptions()
        .darkMode(true)
        .deviceScaleFactor(2)
        .blockAds(true)
);
```

### Device Mockups

```java
// iPhone mockup
byte[] iphone = client.screenshot("https://example.com",
    new ShotAPI.ScreenshotOptions().mockup("iphone")
);

// MacBook mockup
byte[] macbook = client.screenshot("https://example.com",
    new ShotAPI.ScreenshotOptions().mockup("macbook")
);
```

### HTML to Image

```java
String html = """
    <div style="padding: 40px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);">
        <h1 style="color: white; font-family: sans-serif;">Hello World!</h1>
    </div>
    """;

byte[] image = client.render(html,
    new ShotAPI.RenderOptions().width(800).height(400)
);
```

### Metadata Extraction

```java
// Get page metadata
Map<String, Object> meta = client.metadata("https://github.com");
System.out.println(meta.get("title"));
System.out.println(meta.get("description"));
System.out.println(meta.get("og_image"));

// With markdown content
Map<String, Object> metaWithMarkdown = client.metadata("https://example.com", true);
System.out.println(metaWithMarkdown.get("markdown"));
```

### Batch Screenshots

```java
List<String> urls = Arrays.asList(
    "https://google.com",
    "https://github.com",
    "https://stripe.com"
);

ShotAPI.BatchResult result = client.batch(urls,
    new ShotAPI.ScreenshotOptions().format("png").fullPage(true)
);

for (ShotAPI.BatchResultItem item : result.results) {
    System.out.println(item.url + " -> " + item.filename);
}
```

### Visual Diff

```java
ShotAPI.DiffResult result = client.diff(
    "https://example.com",
    "https://example.org",
    1280, 720
);

System.out.println("Pages differ by " + result.percentage + "%");
Files.write(Paths.get("diff.png"), result.image);
```

## Error Handling

```java
try {
    byte[] image = client.screenshot("https://example.com");
} catch (ShotAPI.AuthenticationException e) {
    System.out.println("Invalid API key");
} catch (ShotAPI.RateLimitException e) {
    System.out.println("Rate limit exceeded");
} catch (ShotAPI.FeatureNotAvailableException e) {
    System.out.println("Feature not available on your plan");
} catch (ShotAPI.ShotAPIException e) {
    System.out.println("API error: " + e.getMessage());
}
```

## Configuration

```java
ShotAPI client = new ShotAPI("sk_your_api_key")
    .withBaseUrl("https://shotapi.net")
    .withTimeout(120000); // milliseconds
```

## Spring Boot Integration

```java
@Configuration
public class ShotAPIConfig {
    @Value("${shotapi.key}")
    private String apiKey;

    @Bean
    public ShotAPI shotAPI() {
        return new ShotAPI(apiKey);
    }
}

@RestController
public class ScreenshotController {
    @Autowired
    private ShotAPI shotAPI;

    @GetMapping("/screenshot")
    public ResponseEntity<byte[]> screenshot(@RequestParam String url) throws Exception {
        byte[] image = shotAPI.screenshot(url);
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .body(image);
    }
}
```

## Links

- [Documentation](https://shotapi.net/docs-page)
- [Pricing](https://shotapi.net/pricing)
- [Dashboard](https://shotapi.net/dashboard)

## License

MIT License
