package ro.sellsasha.catalog.photo;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/stored-photos")
public class StoredPhotoController {
    private final SupabasePhotoService photoService;

    public StoredPhotoController(SupabasePhotoService photoService) {
        this.photoService = photoService;
    }

    @GetMapping
    public PhotoPage photos(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        if (cursor != null && cursor.length() > 4096) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "cursor must not exceed 4096 characters.");
        }
        return photoService.getPhotos(category, cursor, limit);
    }

    @GetMapping("/{category}/{id}")
    public ResponseEntity<byte[]> image(
            @PathVariable String category, @PathVariable String id) {
        SupabasePhotoService.StoredPhoto storedPhoto = photoService.getPhoto(category, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(storedPhoto.contentType()))
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(30))
                        .sMaxAge(java.time.Duration.ofDays(30))
                        .cachePublic())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + storedPhoto.filename().replace("\"", "") + "\"")
                .body(storedPhoto.content());
    }
}
