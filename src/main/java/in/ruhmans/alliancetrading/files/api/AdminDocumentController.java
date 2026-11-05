package in.ruhmans.alliancetrading.files.api;

import java.util.Set;
import java.util.UUID;

import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;
import in.ruhmans.alliancetrading.common.security.SecurityUtils;
import in.ruhmans.alliancetrading.files.api.dto.DocumentResponse;
import in.ruhmans.alliancetrading.files.domain.DocType;
import in.ruhmans.alliancetrading.files.domain.Document;
import in.ruhmans.alliancetrading.files.service.FileService;
import in.ruhmans.alliancetrading.user.repository.UserRepository;

import org.springframework.http.HttpHeaders;
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
@RequestMapping("/api/v1/admin/documents")
public class AdminDocumentController {

    /** Proofs about one member's money; the member can view them too. */
    private static final Set<DocType> MEMBER_OWNED = Set.of(DocType.PAYOUT_PROOF);
    /** Company documents not tied to a member. */
    private static final Set<DocType> SHARED = Set.of(DocType.PNL_EVIDENCE, DocType.PAYMENT_QR);

    private final FileService fileService;
    private final UserRepository userRepository;

    public AdminDocumentController(FileService fileService, UserRepository userRepository) {
        this.fileService = fileService;
        this.userRepository = userRepository;
    }

    /**
     * Admin upload for payout proof, P&L evidence and payment QR images.
     * {@code ownerUserId} is required for PAYOUT_PROOF and must be omitted otherwise.
     * Use the returned id in mark-paid, P&L batch or payment-account requests.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(@RequestParam("file") MultipartFile file,
                                                   @RequestParam("docType") DocType docType,
                                                   @RequestParam(required = false) UUID ownerUserId) {
        if (MEMBER_OWNED.contains(docType)) {
            if (ownerUserId == null || !userRepository.existsById(ownerUserId)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "ownerUserId of an existing member is required for " + docType);
            }
        } else if (SHARED.contains(docType)) {
            if (ownerUserId != null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "ownerUserId must be omitted for " + docType);
            }
        } else {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Admins cannot upload " + docType + " documents");
        }
        UUID adminId = SecurityUtils.currentAdminId();
        Document doc = fileService.upload(file, docType, ownerUserId, "ADMIN", adminId);
        return ResponseEntity.ok(FileController.toResponse(doc));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> getContent(@PathVariable UUID id) {
        UUID adminId = SecurityUtils.currentAdminId();
        var content = fileService.getContentForAdmin(id, adminId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.mime()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, FileController.contentDisposition(content.filename()))
                .body(content.bytes());
    }
}
