package ro.sellsasha.catalog.photo;

import java.util.List;

public record Photo(
        String id,
        String publicId,
        String folder,
        String format,
        int width,
        int height,
        long bytes,
        String createdAt,
        String thumbnailUrl,
        String displayUrl,
        List<String> tags) {
}
