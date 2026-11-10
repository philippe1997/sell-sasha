package ro.sellsasha.catalog.photo;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.StreamSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import ro.sellsasha.catalog.config.CloudinaryProperties;

@Service
public class CloudinaryPhotoService {
    private static final Logger logger = LoggerFactory.getLogger(CloudinaryPhotoService.class);

    private final RestClient cloudinary;
    private final CloudinaryProperties properties;

    public CloudinaryPhotoService(RestClient cloudinary, CloudinaryProperties properties) {
        this.cloudinary = cloudinary;
        this.properties = properties;
    }

    public List<String> categories() {
        return properties.categories().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(category -> !category.isEmpty())
                .distinct()
                .toList();
    }

    public int defaultPageSize() {
        return properties.pageSize();
    }

    @Cacheable(
            cacheNames = "photoPages",
            key = "T(java.util.Objects).toString(#category, 'all') + ':' + T(java.util.Objects).toString(#cursor, 'first') + ':' + #pageSize")
    public PhotoPage getPhotos(String category, String cursor, int pageSize) {
        String selectedCategory = normalizeCategory(category);
        requireCredentials();

        try {
            JsonNode response = searchImages(selectedCategory, cursor, pageSize);
            if (response == null
                    || !response.path("resources").isArray()
                    || !response.path("total_count").isIntegralNumber()
                    || response.path("total_count").asInt() < 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Cloudinary returned an invalid photo page.");
            }

            List<Photo> photos = StreamSupport.stream(
                            response.path("resources").spliterator(), false)
                    .map(this::toPhoto)
                    .toList();
            String nextCursor = response.path("next_cursor").asText(null);
            int totalCount = response.path("total_count").asInt();

            return new PhotoPage(
                    photos, nextCursor, selectedCategory, pageSize, totalCount);
        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Cloudinary rejected the photo request (HTTP "
                            + exception.getStatusCode().value() + "). Check the API credentials and folder configuration.");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Cloudinary is temporarily unavailable. Please try again.");
        }
    }

    private JsonNode searchImages(String category, String cursor, int pageSize) {
        String expression = category == null
                ? "resource_type:image"
                : folderExpression(category);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("expression", expression);
        request.put(
                "fields",
                "asset_id,public_id,asset_folder,created_at,resource_type,format,width,height,bytes,secure_url,tags");
        request.put("max_results", pageSize);
        if (cursor != null && !cursor.isBlank()) {
            request.put("next_cursor", cursor);
        }

        long startedAt = System.nanoTime();
        try {
            JsonNode response = cloudinary.post()
                    .uri(uriBuilder -> uriBuilder
                            .pathSegment(properties.cloudName(), "resources", "search")
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            logger.info(
                    "Cloudinary photo search completed: category={}, pageSize={}, hasCursor={}, durationMs={}",
                    category == null ? "all" : category,
                    pageSize,
                    cursor != null && !cursor.isBlank(),
                    elapsedMillis(startedAt));
            return response;
        } catch (RestClientException exception) {
            logger.warn(
                    "Cloudinary photo search failed: category={}, pageSize={}, hasCursor={}, durationMs={}, error={}",
                    category == null ? "all" : category,
                    pageSize,
                    cursor != null && !cursor.isBlank(),
                    elapsedMillis(startedAt),
                    exception.getClass().getSimpleName());
            throw exception;
        }
    }

    private String folderExpression(String category) {
        String folderField = properties.folderField();
        if (!"asset_folder".equals(folderField) && !"folder".equals(folderField)) {
            throw new IllegalStateException(
                    "CLOUDINARY_FOLDER_FIELD must be either asset_folder or folder.");
        }
        String escapedFolder = category.replace("\\", "\\\\").replace("\"", "\\\"");
        return "resource_type:image AND " + folderField + ":\"" + escapedFolder + "\"/*";
    }

    private String normalizeCategory(String requestedCategory) {
        if (requestedCategory == null || requestedCategory.isBlank()) {
            return null;
        }

        String category = categories().stream()
                .filter(value -> value.equalsIgnoreCase(requestedCategory.trim()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Unknown photo category."));

        return category;
    }

    private void requireCredentials() {
        if (isBlank(properties.cloudName())
                || isBlank(properties.apiKey())
                || isBlank(properties.apiSecret())) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Cloudinary is not configured. Set CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, and CLOUDINARY_API_SECRET.");
        }
    }

    private Photo toPhoto(JsonNode resource) {
        String secureUrl = resource.path("secure_url").asText(null);

        return new Photo(
                resource.path("asset_id").asText(resource.path("public_id").asText()),
                resource.path("public_id").asText(),
                resource.path("asset_folder").asText(""),
                resource.path("format").asText(""),
                resource.path("width").asInt(),
                resource.path("height").asInt(),
                resource.path("bytes").asLong(),
                resource.path("created_at").asText(""),
                transform(secureUrl, 640),
                transform(secureUrl, 1600),
                StreamSupport.stream(resource.path("tags").spliterator(), false)
                        .map(JsonNode::asText)
                        .toList());
    }

    private static String transform(String secureUrl, int width) {
        if (secureUrl == null) {
            return null;
        }

        int uploadIndex = secureUrl.indexOf("/upload/");
        if (uploadIndex < 0) {
            return secureUrl;
        }

        int transformationStart = uploadIndex + "/upload/".length();
        return secureUrl.substring(0, transformationStart)
                + "f_auto,q_auto,w_" + width + ",c_limit/"
                + secureUrl.substring(transformationStart);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static long elapsedMillis(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedAt);
    }
}
