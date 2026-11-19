package ro.sellsasha.catalog.photo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import ro.sellsasha.catalog.config.GalleryProperties;

@Service
public class SupabasePhotoService {
    private static final Logger logger = LoggerFactory.getLogger(SupabasePhotoService.class);
    private static final int MAX_BATCH_SIZE = 50;
    private static final long MAX_FILE_SIZE = 200_000L;
    private static final String WEBP_CONTENT_TYPE = "image/webp";
    private static final String PHOTOS_TABLE = "photos";
    private static final String DEFAULT_BUCKET = "gallery-images";
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    private final RestClient restClient;
    private final String projectUrl;
    private final String apiKey;
    private final String bucket;
    private final GalleryProperties properties;
    private final ObjectMapper objectMapper;

    public SupabasePhotoService(
            RestClient.Builder restClientBuilder,
            GalleryProperties properties,
            ObjectMapper objectMapper,
            @Value("${supabase.url:}") String projectUrl,
            @Value("${supabase.service-role-key:}") String apiKey,
            @Value("${supabase.bucket:" + DEFAULT_BUCKET + "}") String bucket) {
        this.restClient = restClientBuilder.build();
        this.projectUrl = projectUrl == null ? "" : projectUrl.replaceAll("/+$", "");
        this.apiKey = apiKey;
        this.bucket = bucket;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public List<String> categories() {
        if (properties.categories() == null) {
            return List.of();
        }
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

    public PhotoPage getPhotos(String requestedCategory, String cursor, int pageSize) {
        requireConfiguration();
        String category = normalizeCategory(requestedCategory, false);
        if (pageSize < 1 || pageSize > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "limit must be between 1 and 100.");
        }
        Cursor last = decodeCursor(cursor);
        try {
            ResponseEntity<JsonNode> response = restClient.get()
                    .uri(photoListUri(category, last, pageSize + 1))
                    .headers(headers -> {
                        addAuthHeaders(headers);
                        headers.set("Prefer", "count=exact");
                    })
                    .retrieve()
                    .toEntity(JsonNode.class);
            JsonNode rows = response.getBody();
            if (rows == null || !rows.isArray()) {
                throw new IllegalStateException("Supabase returned an invalid photo list.");
            }
            List<JsonNode> pageRows = new ArrayList<>();
            rows.forEach(pageRows::add);
            boolean hasNextPage = pageRows.size() > pageSize;
            if (hasNextPage) {
                pageRows.remove(pageRows.size() - 1);
            }
            List<Photo> photos = pageRows.stream().map(this::toPhoto).toList();
            String nextCursor = hasNextPage
                    ? encodeCursor(cursorFor(pageRows.get(pageRows.size() - 1)))
                    : null;
            int totalCount = parsePhotoCount(
                    response.getHeaders().getFirst("Content-Range"));
            return new PhotoPage(photos, nextCursor, category, pageSize, totalCount);
        } catch (RestClientException exception) {
            throw supabaseUnavailable("list category images", exception);
        }
    }

    public List<Photo> savePhotos(String requestedCategory, List<MultipartFile> files) {
        requireConfiguration();
        String category = normalizeCategory(requestedCategory, true);
        List<byte[]> contents = validateFiles(files);
        List<Photo> savedPhotos = new ArrayList<>(files.size());
        for (int index = 0; index < files.size(); index++) {
            MultipartFile file = files.get(index);
            String id = UUID.randomUUID().toString();
            String storagePath = id + ".webp";
            String filename = safeFilename(file.getOriginalFilename());
            Instant createdAt = Instant.now();
            byte[] content = contents.get(index);
            try {
                uploadImage(storagePath, content);
                Map<String, Object> row = Map.of(
                        "id", id,
                        "category", category,
                        "filename", filename,
                        "content_type", WEBP_CONTENT_TYPE,
                        "storage_path", storagePath,
                        "byte_size", content.length,
                        "created_at", createdAt.toString());
                byte[] rowJson;
                try {
                    rowJson = objectMapper.writeValueAsBytes(row);
                } catch (IOException exception) {
                    removeUploadedImage(storagePath);
                    throw new IllegalStateException(
                            "Could not encode the Supabase photo metadata.", exception);
                }
                JsonNode inserted;
                try {
                    inserted = restClient.post()
                            .uri(tableUri())
                            .headers(headers -> {
                                addAuthHeaders(headers);
                                headers.setContentType(MediaType.APPLICATION_JSON);
                                headers.set("Prefer", "return=representation");
                            })
                            .body(rowJson)
                            .retrieve()
                            .body(JsonNode.class);
                } catch (RestClientException exception) {
                    removeUploadedImage(storagePath);
                    throw exception;
                }
                if (inserted == null || !inserted.isArray() || inserted.isEmpty()) {
                    removeUploadedImage(storagePath);
                    throw new IllegalStateException("Supabase did not return the saved photo.");
                }
                savedPhotos.add(toPhoto(inserted.get(0)));
            } catch (RestClientException exception) {
                throw supabaseUnavailable("save category image", exception);
            }
        }
        return List.copyOf(savedPhotos);
    }

    public StoredPhoto getPhoto(String requestedCategory, String id) {
        requireConfiguration();
        String category = normalizeCategory(requestedCategory, true);
        UUID photoId;
        try {
            photoId = UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found.");
        }
        try {
            JsonNode response = restClient.get()
                    .uri(photoByIdUri(photoId, category))
                    .headers(this::addAuthHeaders)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.isArray() || response.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found.");
            }
            JsonNode row = response.get(0);
            String storagePath = requiredText(row, "storage_path");
            if (!storagePath.matches("[0-9a-fA-F-]{36}\\.webp")) {
                throw new IllegalStateException("Supabase returned an invalid image path.");
            }
            byte[] content = downloadImage(storagePath);
            return new StoredPhoto(
                    content,
                    requiredText(row, "content_type"),
                    safeFilename(text(row, "filename")));
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found.");
            }
            throw supabaseUnavailable("retrieve category image", exception);
        } catch (RestClientException exception) {
            throw supabaseUnavailable("retrieve category image", exception);
        }
    }

    private static int parsePhotoCount(String contentRange) {
        if (contentRange == null || !contentRange.contains("/")) {
            throw new IllegalStateException("Supabase did not return an exact photo count.");
        }
        String count = contentRange.substring(contentRange.lastIndexOf('/') + 1);
        try {
            int parsedCount = Integer.parseInt(count);
            if (parsedCount < 0) {
                throw new NumberFormatException("Negative photo count.");
            }
            return parsedCount;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Supabase returned an invalid photo count.", exception);
        }
    }

    private void uploadImage(String storagePath, byte[] content) {
        restClient.post()
                .uri(storageUri(storagePath))
                .headers(headers -> {
                    addAuthHeaders(headers);
                    headers.setContentType(MediaType.parseMediaType(WEBP_CONTENT_TYPE));
                    headers.set("x-upsert", "false");
                })
                .body(new ByteArrayResource(content))
                .retrieve()
                .toBodilessEntity();
    }

    private byte[] downloadImage(String storagePath) {
        byte[] content = restClient.get()
                .uri(storageUri(storagePath))
                .headers(this::addAuthHeaders)
                .retrieve()
                .body(byte[].class);
        if (content == null || content.length == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image data was not found.");
        }
        return content;
    }

    private void removeUploadedImage(String storagePath) {
        try {
            restClient.delete()
                    .uri(storageUri(storagePath))
                    .headers(this::addAuthHeaders)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException cleanupException) {
            logger.error("Supabase could not remove an orphaned uploaded image.", cleanupException);
        }
    }

    private java.net.URI tableUri() {
        return UriComponentsBuilder.fromUriString(projectUrl)
                .path("/rest/v1/{table}")
                .queryParam("select", "*")
                .buildAndExpand(PHOTOS_TABLE)
                .encode()
                .toUri();
    }

    private java.net.URI photoListUri(String category, Cursor cursor, int limit) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(projectUrl)
                .path("/rest/v1/{table}")
                .queryParam(
                        "select",
                        "id,category,filename,content_type,storage_path,byte_size,created_at")
                .queryParam("order", "created_at.desc,id.desc")
                .queryParam("limit", limit);
        addCategoryFilter(builder, category);
        if (cursor != null) {
            String instant = cursor.createdAt().toString();
            builder.queryParam(
                    "or",
                    "(created_at.lt." + instant
                            + ",and(created_at.eq." + instant + ",id.lt." + cursor.id() + "))");
        }
        return builder.buildAndExpand(PHOTOS_TABLE).encode().toUri();
    }

    private java.net.URI photoByIdUri(UUID id, String category) {
        return UriComponentsBuilder.fromUriString(projectUrl)
                .path("/rest/v1/{table}")
                .queryParam("select", "id,category,filename,content_type,storage_path,byte_size,created_at")
                .queryParam("id", "eq." + id)
                .queryParam("category", "eq." + category)
                .queryParam("limit", 1)
                .buildAndExpand(PHOTOS_TABLE)
                .encode()
                .toUri();
    }

    private java.net.URI storageUri(String storagePath) {
        return UriComponentsBuilder.fromUriString(projectUrl)
                .path("/storage/v1/object")
                .pathSegment(bucket, storagePath)
                .build()
                .encode()
                .toUri();
    }

    private void addAuthHeaders(HttpHeaders headers) {
        headers.set("apikey", apiKey);
        headers.setBearerAuth(apiKey);
    }

    private void addCategoryFilter(UriComponentsBuilder builder, String category) {
        if (category != null) {
            builder.queryParam("category", "eq." + category);
        }
    }

    private Photo toPhoto(JsonNode row) {
        String id = requiredText(row, "id");
        String category = storedCategory(requiredText(row, "category"));
        String contentType = requiredText(row, "content_type");
        String filename = safeFilename(text(row, "filename"));
        Instant createdAt = Instant.parse(requiredText(row, "created_at"));
        long bytes = row.path("byte_size").asLong(-1);
        if (category == null || !WEBP_CONTENT_TYPE.equals(contentType)
                || bytes < 1 || bytes > MAX_FILE_SIZE) {
            throw new IllegalStateException("Supabase returned invalid photo metadata.");
        }
        String encodedCategory = UriUtils.encodePathSegment(category, StandardCharsets.UTF_8);
        String encodedId = UriUtils.encodePathSegment(id, StandardCharsets.UTF_8);
        String imageUrl = "/api/stored-photos/" + encodedCategory + "/" + encodedId;
        return new Photo(
                category + ":" + id,
                filename,
                category,
                "webp",
                0,
                0,
                bytes,
                DATE_FORMAT.format(createdAt),
                imageUrl,
                imageUrl,
                List.of(category));
    }

    private String storedCategory(String storedCategory) {
        String normalized = normalizeName(storedCategory);
        return categories().stream()
                .filter(category -> normalizeName(category).equals(normalized))
                .findFirst()
                .orElse(null);
    }

    private String normalizeCategory(String requestedCategory, boolean required) {
        if (requestedCategory == null || requestedCategory.isBlank()) {
            if (required) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "A category is required.");
            }
            return null;
        }
        String normalized = normalizeName(requestedCategory);
        return categories().stream()
                .filter(category -> normalizeName(category).equals(normalized))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Unknown photo category."));
    }

    private void requireConfiguration() {
        if (projectUrl.isBlank() || apiKey == null || apiKey.isBlank()
                || bucket == null || bucket.isBlank()) {
            logger.error("Supabase URL, service-role key, and storage bucket must be configured.");
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Supabase is not configured.");
        }
        if (!projectUrl.startsWith("https://")) {
            logger.error("SUPABASE_URL must use HTTPS.");
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Supabase configuration is invalid.");
        }
    }

    private Cursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        if (cursor.length() > 4096) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "cursor must not exceed 4096 characters.");
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            Map<String, String> values = objectMapper.readValue(
                    decoded, new TypeReference<>() {
                    });
            if (values.size() != 2 || !values.containsKey("createdAt") || !values.containsKey("id")) {
                throw new IllegalArgumentException("Invalid cursor fields.");
            }
            return new Cursor(
                    Instant.parse(values.get("createdAt")),
                    UUID.fromString(values.get("id")));
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor.");
        }
    }

    private String encodeCursor(Cursor cursor) {
        try {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(Map.of(
                            "createdAt", cursor.createdAt().toString(),
                            "id", cursor.id().toString())));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not encode the Supabase photo cursor.", exception);
        }
    }

    private static Cursor cursorFor(JsonNode row) {
        return new Cursor(
                Instant.parse(requiredText(row, "created_at")),
                UUID.fromString(requiredText(row, "id")));
    }

    private static String text(JsonNode row, String field) {
        JsonNode value = row.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private static String requiredText(JsonNode row, String field) {
        String value = text(row, field);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Supabase returned a photo without " + field + ".");
        }
        return value;
    }

    private static String normalizeName(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }

    private static List<byte[]> validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty() || files.size() > MAX_BATCH_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Choose between 1 and 50 images.");
        }
        List<byte[]> contents = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            String contentType = file.getContentType();
            if (file.isEmpty()
                    || file.getSize() > MAX_FILE_SIZE
                    || contentType == null
                    || !WEBP_CONTENT_TYPE.equals(contentType.toLowerCase(Locale.ROOT))) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Images must be optimized WebP files no larger than 200 KB.");
            }
            try {
                byte[] content = file.getBytes();
                if (content.length < 12
                        || content[0] != 'R' || content[1] != 'I'
                        || content[2] != 'F' || content[3] != 'F'
                        || content[8] != 'W' || content[9] != 'E'
                        || content[10] != 'B' || content[11] != 'P') {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "The uploaded file is not a valid WebP image.");
                }
                contents.add(content);
            } catch (IOException exception) {
                logger.warn("Could not read uploaded image data.", exception);
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "The uploaded image could not be read.", exception);
            }
        }
        return contents;
    }

    private static String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "image.webp";
        }
        String normalized = filename.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "_");
    }

    private static ResponseStatusException supabaseUnavailable(
            String operation, RestClientException exception) {
        logger.warn("Supabase could not {}.", operation, exception);
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Supabase is temporarily unavailable. Please try again.",
                exception);
    }

    public record StoredPhoto(byte[] content, String contentType, String filename) {
    }

    private record Cursor(Instant createdAt, UUID id) {
    }
}
