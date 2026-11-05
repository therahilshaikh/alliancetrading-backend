package in.ruhmans.alliancetrading.kyc.service;

import java.util.UUID;

import in.ruhmans.alliancetrading.bank.service.BankAccountService;
import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;
import in.ruhmans.alliancetrading.payment.domain.UserPaymentAssignment;
import in.ruhmans.alliancetrading.payment.repository.UserPaymentAssignmentRepository;
import in.ruhmans.alliancetrading.payment.service.PaymentService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Approving a member is one admin action: assign the payment account + amount they will
 * pay, approve their KYC, and verify the bank account they gave during onboarding.
 * All steps commit together or not at all.
 */
@Service
public class AccountApprovalService {

    private final KycService kycService;
    private final PaymentService paymentService;
    private final UserPaymentAssignmentRepository assignmentRepository;
    private final BankAccountService bankAccountService;

    public AccountApprovalService(KycService kycService, PaymentService paymentService,
                                  UserPaymentAssignmentRepository assignmentRepository,
                                  BankAccountService bankAccountService) {
        this.kycService = kycService;
        this.paymentService = paymentService;
        this.assignmentRepository = assignmentRepository;
        this.bankAccountService = bankAccountService;
    }

    public record ApprovalResult(UserPaymentAssignment assignment, boolean bankVerified) {}

    /**
     * @param payment payment details to assign; may be null only if the user already has an
     *                active assignment (e.g. assigned earlier from the review screen)
     */
    @Transactional
    public ApprovalResult approve(UUID userId, UUID adminId, PaymentService.AssignmentCommand payment) {
        UserPaymentAssignment assignment;
        if (payment != null) {
            assignment = paymentService.assign(userId, adminId, payment);
        } else {
            assignment = assignmentRepository.findByUserIdAndActiveTrue(userId)
                    .orElseThrow(() -> new ApiException(ErrorCode.NO_PAYMENT_ASSIGNMENT,
                            "Assign payment details before approving this account"));
        }
        kycService.approve(userId, adminId);
        boolean bankVerified = bankAccountService.verifyPendingIfEligible(userId, adminId);
        return new ApprovalResult(assignment, bankVerified);
    }
}
