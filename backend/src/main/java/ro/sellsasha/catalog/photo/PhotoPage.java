package ro.sellsasha.catalog.photo;

import java.util.List;

public record PhotoPage(
        List<Photo> photos,
        String nextCursor,
        String category,
        int pageSize,
        int totalCount) {
}
