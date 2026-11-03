package ro.sellsasha.catalog.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import ro.sellsasha.catalog.config.CloudinaryProperties;

class CloudinaryPhotoServiceTest {
    private HttpServer server;
    private AtomicReference<String> requestPath;
    private AtomicReference<String> requestMethod;
    private AtomicReference<String> requestBody;
    private CloudinaryPhotoService service;

    @BeforeEach
    void setUp() throws IOException {
        requestPath = new AtomicReference<>();
        requestMethod = new AtomicReference<>();
        requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().toString());
            requestMethod.set(exchange.getRequestMethod());
            requestBody.set(new String(
                    exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = """
                    {
                      "total_count": 101,
                      "resources": [{
                        "asset_id": "asset-1",
                        "public_id": "catalog/kitchen",
                        "asset_folder": "Bucatarie",
                        "format": "jpg",
                        "width": 1200,
                        "height": 800,
                        "bytes": 120000,
                        "created_at": "2025-01-01T00:00:00Z",
                        "secure_url": "https://res.cloudinary.com/demo/image/upload/v1/catalog/kitchen.jpg",
                        "tags": ["Bucatarie"]
                      }],
                      "next_cursor": "next-page"
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        var properties = new CloudinaryProperties(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "demo",
                "key",
                "secret",
                java.util.List.of("Bucatarie", "Living"),
                "asset_folder",
                "http://localhost:5173",
                40);
        service = new CloudinaryPhotoService(
                RestClient.builder().baseUrl(properties.apiBaseUrl()).build(), properties);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void categoryRequestsUseCloudinaryFolderAndCursorPagination() {
        PhotoPage page = service.getPhotos("bucatarie", "cursor value", 20);

        assertThat(requestPath.get()).isEqualTo("/demo/resources/search");
        assertThat(requestMethod.get()).isEqualTo("POST");
        assertThat(requestBody.get())
                .contains("asset_folder:\\\"Bucatarie\\\"/*")
                .contains("\"next_cursor\":\"cursor value\"")
                .contains("\"max_results\":20");
        assertThat(page.category()).isEqualTo("Bucatarie");
        assertThat(page.nextCursor()).isEqualTo("next-page");
        assertThat(page.photos()).hasSize(1);
        assertThat(page.totalCount()).isEqualTo(101);
        assertThat(page.photos().get(0).thumbnailUrl())
                .contains("/upload/f_auto,q_auto,w_640,c_limit/v1/");
        assertThat(page.photos().get(0).displayUrl())
                .contains("/upload/f_auto,q_auto,w_1600,c_limit/v1/");
    }

    @Test
    void rejectsUnconfiguredCategoriesWithoutCallingCloudinary() {
        assertThatThrownBy(() -> service.getPhotos("Unknown", null, 40))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown photo category");
        assertThat(requestPath.get()).isNull();
    }

    @Test
    void allPhotosUseTheImageSearchAndDefaultPageSize() {
        PhotoPage page = service.getPhotos(null, null, service.defaultPageSize());

        assertThat(requestMethod.get()).isEqualTo("POST");
        assertThat(requestBody.get())
                .contains("\"expression\":\"resource_type:image\"")
                .contains("\"max_results\":40")
                .doesNotContain("next_cursor");
        assertThat(page.category()).isNull();
        assertThat(page.pageSize()).isEqualTo(40);
        assertThat(page.totalCount()).isEqualTo(101);
    }

    @Test
    void legacyFolderModeQueriesTheLegacyFolderField() {
        var properties = new CloudinaryProperties(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "demo",
                "key",
                "secret",
                java.util.List.of("Bucatarie"),
                "folder",
                "http://localhost:5173",
                40);
        var legacyService = new CloudinaryPhotoService(
                RestClient.builder().baseUrl(properties.apiBaseUrl()).build(), properties);

        legacyService.getPhotos("Bucatarie", null, 40);

        assertThat(requestBody.get())
                .contains("folder:\\\"Bucatarie\\\"/*");
    }
}
