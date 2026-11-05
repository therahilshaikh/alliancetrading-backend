package in.ruhmans.alliancetrading.ledger.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;
import in.ruhmans.alliancetrading.common.pagination.CursorPage;
import in.ruhmans.alliancetrading.ledger.domain.LedgerEntry;
import in.ruhmans.alliancetrading.ledger.repository.LedgerEntryRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Newest-first, id-cursor pagination over a user's ledger entries. */
@Service
public class LedgerQueryService {

    public record LedgerEntryView(Long id, String bucket, String entryType, BigDecimal amount,
                                  String sourceType, Instant createdAt) {}

    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerQueryService(LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional(readOnly = true)
    public CursorPage<LedgerEntryView> page(UUID userId, String cursor, int limit) {
        int capped = Math.min(Math.max(limit, 1), 100);
        List<LedgerEntry> entries = cursor != null && !cursor.isBlank()
                ? ledgerEntryRepository.findByUserIdBeforeCursor(userId, decodeCursor(cursor), capped + 1)
                : ledgerEntryRepository.findByUserIdLatest(userId, capped + 1);

        List<LedgerEntryView> views = entries.stream()
                .map(e -> new LedgerEntryView(e.getId(), e.getBucket().name(), e.getEntryType().name(),
                        e.getAmount(), e.getSourceType(), e.getCreatedAt()))
                .toList();
        return CursorPage.of(views, capped, v -> encodeCursor(v.id()));
    }

    private static String encodeCursor(Long id) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(String.valueOf(id).getBytes());
    }

    private static Long decodeCursor(String cursor) {
        try {
            return Long.parseLong(new String(Base64.getUrlDecoder().decode(cursor)));
        } catch (Exception e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid cursor");
        }
    }
}
