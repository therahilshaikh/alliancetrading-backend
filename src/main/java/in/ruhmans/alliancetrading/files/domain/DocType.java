package in.ruhmans.alliancetrading.files.domain;

public enum DocType {
    PAN_CARD("kyc"),
    AADHAAR_MASKED_FRONT("kyc"),
    AADHAAR_MASKED_BACK("kyc"),
    BANK_PROOF("kyc"),
    PAYMENT_PROOF("payment-proof"),
    PAYOUT_PROOF("payout-proof"),
    PNL_EVIDENCE("pnl-evidence"),
    PAYMENT_QR("payment-qr");

    private final String folder;

    DocType(String folder) {
        this.folder = folder;
    }

    /** Top-level folder in the storage bucket for documents that belong to a member. */
    public String folder() {
        return folder;
    }
}
