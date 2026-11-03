package ro.sellsasha.catalog.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloudinary")
public record CloudinaryProperties(
        String apiBaseUrl,
        String cloudName,
        String apiKey,
        String apiSecret,
        List<String> categories,
        String folderField,
        String frontendOrigin,
        int pageSize) {
}
