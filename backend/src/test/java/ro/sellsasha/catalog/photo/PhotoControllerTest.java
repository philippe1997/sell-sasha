package ro.sellsasha.catalog.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;

@ExtendWith(MockitoExtension.class)
class PhotoControllerTest {
    @Mock
    private CloudinaryPhotoService photoService;

    @InjectMocks
    private PhotoController controller;

    @Test
    void categoriesCanBeCachedByBrowserAndCdn() {
        List<String> categories = List.of("living");
        when(photoService.categories()).thenReturn(categories);

        var response = controller.categories();

        assertThat(response.getBody()).isEqualTo(categories);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL))
                .isEqualTo("public, max-age=300, s-maxage=3600, stale-while-revalidate=86400");
    }

    @Test
    void photoPagesCanBeCachedByBrowserAndCdn() {
        PhotoPage page = new PhotoPage(List.of(), null, null, 40, 0);
        when(photoService.defaultPageSize()).thenReturn(40);
        when(photoService.getPhotos(null, null, 40)).thenReturn(page);

        var response = controller.photos(null, null, null, null);

        assertThat(response.getBody()).isEqualTo(page);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL))
                .isEqualTo("public, max-age=60, s-maxage=300, stale-while-revalidate=3600");
    }
}
