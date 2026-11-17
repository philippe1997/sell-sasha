package ro.sellsasha.catalog.photo;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin")
public class AdminPhotoController {
    private final SupabasePhotoService photoService;

    public AdminPhotoController(SupabasePhotoService photoService) {
        this.photoService = photoService;
    }

    @GetMapping("/session")
    public AdminSession session() {
        return new AdminSession("authenticated");
    }

    @PostMapping(path = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<Photo> upload(
            @RequestParam String category,
            @RequestParam("files") List<MultipartFile> files) {
        return photoService.savePhotos(category, files);
    }

    public record AdminSession(String status) {
    }
}
