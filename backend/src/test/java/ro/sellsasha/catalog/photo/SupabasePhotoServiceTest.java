package ro.sellsasha.catalog.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ro.sellsasha.catalog.config.GalleryProperties;

class SupabasePhotoServiceTest {
    private static final String SUPABASE_URL = "https://project.supabase.co";
    private static final String SERVICE_ROLE_KEY = "test-service-role-key";
    private static final String WEBP_JSON =
            "\"content_type\":\"image/webp\"";

    private MockRestServiceServer server;
    private SupabasePhotoService photoService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        photoService = new SupabasePhotoService(
                builder,
                new GalleryProperties(List.of("living", "bucătărie"), 40),
                new ObjectMapper(),
                SUPABASE_URL,
                SERVICE_ROLE_KEY,
                "gallery-images");
    }

    @Test
    void categoriesComeFromConfiguredGalleryProperties() {
        assertThat(photoService.categories()).containsExactly("living", "bucătărie");
        assertThat(photoService.defaultPageSize()).isEqualTo(40);
    }

    @Test
    void listsSupabasePhotoRowsAndReturnsAnImageProxyUrl() {
        String id = "13d3f72c-269c-4948-af58-2a900e51a822";
        server.expect(requestTo(startsWith(SUPABASE_URL + "/rest/v1/photos?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Range", "0-0"))
                .andExpect(header("Prefer", "count=exact"))
                .andRespond(withStatus(HttpStatus.PARTIAL_CONTENT)
                        .header("Content-Range", "0-0/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("[]"));
        server.expect(requestTo(startsWith(SUPABASE_URL + "/rest/v1/photos?")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [{
                          "id":"%s",
                          "category":"living",
                          "filename":"room.webp",
                          "content_type":"image/webp",
                          "storage_path":"%s.webp",
                          "byte_size":12,
                          "created_at":"2026-11-12T10:00:00Z"
                        }]
                        """.formatted(id, id), MediaType.APPLICATION_JSON));

        PhotoPage result = photoService.getPhotos(null, null, 20);

        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.photos()).singleElement().satisfies(photo -> {
            assertThat(photo.id()).isEqualTo("living:" + id);
            assertThat(photo.publicId()).isEqualTo("room.webp");
            assertThat(photo.displayUrl()).isEqualTo(
                    "/api/stored-photos/living/" + id);
            assertThat(photo.bytes()).isEqualTo(12);
        });
        server.verify();
    }

    @Test
    void uploadsWebpToPrivateStorageAndInsertsItsMetadata() {
        String id = "b9ac3564-a8dd-4f2d-beb2-3fce205e7584";
        var image = new MockMultipartFile(
                "files", "room.webp", "image/webp", webpBytes());
        server.expect(requestTo(startsWith(
                        SUPABASE_URL + "/storage/v1/object/gallery-images/")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", SERVICE_ROLE_KEY))
                .andExpect(header("x-upsert", "false"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(SUPABASE_URL + "/rest/v1/photos?")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Prefer", "return=representation"))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        containsString("\"category\":\"living\""),
                        containsString("\"filename\":\"room.webp\""),
                        containsString(WEBP_JSON))))
                .andRespond(withSuccess("""
                        [{
                          "id":"%s",
                          "category":"living",
                          "filename":"room.webp",
                          "content_type":"image/webp",
                          "storage_path":"%s.webp",
                          "byte_size":12,
                          "created_at":"2026-11-12T10:00:00Z"
                        }]
                        """.formatted(id, id), MediaType.APPLICATION_JSON));

        List<Photo> result = photoService.savePhotos("living", List.of(image));

        assertThat(result).singleElement()
                .satisfies(photo -> assertThat(photo.id()).isEqualTo("living:" + id));
        server.verify();
    }

    @Test
    void servesPrivateStorageImageOnlyForItsCategory() {
        String id = "fb623195-b0f6-4d80-9987-c0f669f00d9c";
        server.expect(requestTo(startsWith(SUPABASE_URL + "/rest/v1/photos?")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [{
                          "id":"%s",
                          "category":"living",
                          "filename":"room.webp",
                          "content_type":"image/webp",
                          "storage_path":"%s.webp",
                          "byte_size":12,
                          "created_at":"2026-11-12T10:00:00Z"
                        }]
                        """.formatted(id, id), MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                        SUPABASE_URL + "/storage/v1/object/gallery-images/" + id + ".webp"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("apikey", SERVICE_ROLE_KEY))
                .andRespond(withSuccess(webpBytes(), MediaType.parseMediaType("image/webp")));

        SupabasePhotoService.StoredPhoto result = photoService.getPhoto("living", id);

        assertThat(result.content()).containsExactly(webpBytes());
        assertThat(result.contentType()).isEqualTo("image/webp");
        assertThat(result.filename()).isEqualTo("room.webp");
        server.verify();
    }

    @Test
    void rejectsUnknownCategoriesAndInvalidUploadsBeforeCallingSupabase() {
        assertThatThrownBy(() -> photoService.getPhotos("unknown", null, 20))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown photo category");

        var largeImage = new MockMultipartFile(
                "files", "large.webp", "image/webp", new byte[200_001]);
        assertThatThrownBy(() -> photoService.savePhotos("living", List.of(largeImage)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no larger than 200 KB");

        server.verify();
    }

    @Test
    void rejectsBatchesLargerThan50ImagesBeforeCallingSupabase() {
        List<MultipartFile> files = IntStream.range(0, 51)
                .mapToObj(index -> (MultipartFile) new MockMultipartFile(
                        "files", index + ".webp", "image/webp", webpBytes()))
                .toList();

        assertThatThrownBy(() -> photoService.savePhotos("living", files))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("between 1 and 50 images");

        server.verify();
    }

    private static byte[] webpBytes() {
        return new byte[] {
            'R', 'I', 'F', 'F', 4, 0, 0, 0, 'W', 'E', 'B', 'P'
        };
    }
}
