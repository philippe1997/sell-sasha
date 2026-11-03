package ro.sellsasha.catalog.photo;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Validated
@RestController
@RequestMapping("/api")
public class PhotoController {
    private final CloudinaryPhotoService photoService;

    public PhotoController(CloudinaryPhotoService photoService) {
        this.photoService = photoService;
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return photoService.categories();
    }

    @GetMapping("/photos")
    public PhotoPage photos(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit) {
        if (page != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Offset pagination is not supported; use the cursor returned by the previous page.");
        }

        int pageSize = limit == null ? photoService.defaultPageSize() : limit;
        if (pageSize < 1 || pageSize > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "limit must be between 1 and 100.");
        }
        if (cursor != null && cursor.length() > 4096) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "cursor must not exceed 4096 characters.");
        }

        return photoService.getPhotos(category, cursor, pageSize);
    }

    @GetMapping("/health")
    public Health health() {
        return new Health("ok");
    }

    public record Health(String status) {
    }
}
