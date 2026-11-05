package in.ruhmans.alliancetrading.files.api;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import in.ruhmans.alliancetrading.common.security.SecurityUtils;
import in.ruhmans.alliancetrading.files.api.dto.DocumentResponse;
import in.ruhmans.alliancetrading.files.domain.DocType;
import in.ruhmans.alliancetrading.files.domain.Document;
import in.ruhmans.alliancetrading.files.service.FileService;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(@RequestParam("file") MultipartFile file,
                                                   @RequestParam("docType") DocType docType) {
        UUID userId = SecurityUtils.currentUserId();
        Document doc = fileService.upload(file, docType, userId, "USER", userId);
        return ResponseEntity.ok(toResponse(doc));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> getOwnContent(@PathVariable UUID id) {
        UUID userId = SecurityUtils.currentUserId();
        var content = fileService.getContentForUser(id, userId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.mime()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(content.filename()))
                .body(content.bytes());
    }

    /** {@code inline} so images and PDFs still preview in the browser; saving uses the original name. */
    static String contentDisposition(String filename) {
        return ContentDisposition.inline().filename(filename, StandardCharsets.UTF_8).build().toString();
    }

    static DocumentResponse toResponse(Document doc) {
        return new DocumentResponse(doc.getId(), doc.getDocType(), doc.getOriginalFilename(), doc.getMime(),
                doc.getSizeBytes(), doc.getSha256(), doc.getCreatedAt());
    }
}
