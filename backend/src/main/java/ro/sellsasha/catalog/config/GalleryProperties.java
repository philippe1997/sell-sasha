package ro.sellsasha.catalog.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gallery")
public record GalleryProperties(List<String> categories, int pageSize) {
}
