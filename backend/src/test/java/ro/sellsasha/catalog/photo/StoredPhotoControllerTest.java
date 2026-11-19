package ro.sellsasha.catalog.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class StoredPhotoControllerTest {
    @Test
    void allowsBrowserAndCdnToCacheImmutablePhotoResponses() {
        SupabasePhotoService photoService = mock(SupabasePhotoService.class);
        when(photoService.getPhoto("living", "photo-id"))
                .thenReturn(new SupabasePhotoService.StoredPhoto(
                        new byte[] {1}, "image/webp", "room.webp"));

        var response = new StoredPhotoController(photoService).image("living", "photo-id");

        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL))
                .contains("public", "max-age=2592000", "s-maxage=2592000");
    }
}
