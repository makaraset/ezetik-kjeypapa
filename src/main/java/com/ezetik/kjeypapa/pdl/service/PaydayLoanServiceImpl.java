package com.ezetik.kjeypapa.pdl.service;

import java.sql.Date;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.ezetik.kjeypapa.image.model.Image;
import com.ezetik.kjeypapa.image.repository.ImageRepository;
import com.ezetik.kjeypapa.pdl.model.PaydayLoan;
import com.ezetik.kjeypapa.pdl.model.PdlAttachment;
import com.ezetik.kjeypapa.pdl.model.PdlBankInfo;
import com.ezetik.kjeypapa.pdl.model.PdlDocTypeEnum;
import com.ezetik.kjeypapa.pdl.model.PdlEmploymentInfo;
import com.ezetik.kjeypapa.pdl.model.PdlLoanTypeEnum;
import com.ezetik.kjeypapa.pdl.model.PdlPaymentSchedule;
import com.ezetik.kjeypapa.pdl.model.PdlPersonalInfo;
import com.ezetik.kjeypapa.pdl.model.PdlStatusEnum;
import com.ezetik.kjeypapa.pdl.payload.BankInfoRequest;
import com.ezetik.kjeypapa.pdl.payload.EmploymentInfoRequest;
import com.ezetik.kjeypapa.pdl.payload.PersonalInfoRequest;
import com.ezetik.kjeypapa.pdl.payload.PdlAcceptDecision;
import com.ezetik.kjeypapa.pdl.payload.PdlApplicationPayload;
import com.ezetik.kjeypapa.pdl.payload.PdlCbcConsentResponse;
import com.ezetik.kjeypapa.pdl.payload.PdlProfileResponse;
import com.ezetik.kjeypapa.pdl.payload.PdlSettlementAccountResponse;
import com.ezetik.kjeypapa.pdl.payload.PdlTransaction;
import com.ezetik.kjeypapa.pdl.repository.PdlAttachmentRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlBankInfoRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlEmploymentInfoRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlPaymentScheduleRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlPersonalInfoRepository;
import com.ezetik.kjeypapa.pdl.repository.PaydayLoanRepository;
import com.ezetik.kjeypapa.security.model.User;
import com.ezetik.kjeypapa.security.service.UserService;
import com.ezetik.kjeypapa.security.util.Message;

import jakarta.transaction.Transactional;

@Service
public class PaydayLoanServiceImpl implements PaydayLoanService {

	@Autowired
	private PaydayLoanRepository repo;

	@Autowired
	private PdlAttachmentRepository attachmentRepo;

	@Autowired
	private PdlPaymentScheduleRepository scheduleRepo;

	@Autowired
	private PdlEmploymentInfoRepository employmentRepo;

	@Autowired
	private PdlBankInfoRepository bankRepo;

	@Autowired
	private PdlPersonalInfoRepository personalRepo;

	@Autowired
	private ImageRepository imageRepo;

	@Autowired
	private UserService userService;

	@Autowired
	private LosProvider losProvider;

	@Autowired
	private PdlPricingService pricingService;

	@Value("${pdl.cbc.text-version:v1-2026-08}")
	private String cbcTextVersion;

	private static boolean blank(String s) {
		return s == null || s.isBlank();
	}

	@Override
	@Transactional
	public ResponseEntity<Message<PaydayLoan>> createApplication(PdlApplicationPayload p) {
		try {
			PdlLoanTypeEnum loanType;
			try {
				loanType = blank(p.getLoanType()) ? PdlLoanTypeEnum.PAYDAY
						: PdlLoanTypeEnum.valueOf(p.getLoanType().trim().toUpperCase());
			} catch (IllegalArgumentException bad) {
				return resp("INVALID", "Unknown loan type: " + p.getLoanType(), null, HttpStatus.EXPECTATION_FAILED);
			}
			if (loanType != PdlLoanTypeEnum.PAYDAY)
				return resp("INVALID", "Loan product not yet available: " + loanType, null,
						HttpStatus.EXPECTATION_FAILED);

			PaydayLoan loan = new PaydayLoan();
			loan.setUser(getCurrentUser());
			User u = loan.getUser();
			loan.setCustomerName(((u.getFirstname() == null ? "" : u.getFirstname()) + " "
					+ (u.getLastname() == null ? "" : u.getLastname())).trim());
			loan.setLoanType(loanType);
			loan.setCurrency(p.getCurrency());

			if (p.getRepaymentAmount() != null) {
				// V8 wizard path: the client picks a repayment TIER; everything else is
				// derived server-side (QC1.6) — client-sent derived values are ignored.
				com.ezetik.kjeypapa.pdl.payload.PdlQuoteResponse q;
				try {
					q = pricingService.quote(loanType, p.getCurrency(), p.getRepaymentAmount());
				} catch (IllegalArgumentException bad) {
					return resp("INVALID", bad.getMessage(), null, HttpStatus.EXPECTATION_FAILED);
				}
				loan.setRepaymentAmount(q.getRepaymentAmount());
				loan.setRequestAmount(q.getLoanAmount()); // principal = disbursed
				loan.setInterestAmount(q.getInterestAmount());
				loan.setInterestRatePercent(q.getInterestRatePercent());
				loan.setProcessingFee(q.getProcessingFee());
				loan.setCbcEnquiryFee(q.getCbcEnquiryFee());
				loan.setNetDisbursedAmount(q.getNetDisbursedAmount());
				loan.setLoanPeriodDays(q.getLoanPeriodDays());
				loan.setDisbursementDate(q.getDisbursementDate());
				loan.setRepaymentDate(q.getRepaymentDate());
			} else {
				// Legacy free-amount path (pre-wizard app builds) — still capped by product.
				if (p.getRequestAmount() == null || p.getRequestAmount() <= 0)
					return resp("INVALID", "Request amount is required", null, HttpStatus.EXPECTATION_FAILED);
				if (!pricingService.withinProductCap(loanType, p.getCurrency(), p.getRequestAmount()))
					return resp("INVALID", "Request amount is over the product limit", null,
							HttpStatus.EXPECTATION_FAILED);
				loan.setRequestAmount(p.getRequestAmount());
				loan.setRepaymentAmount(p.getRepaymentAmount());
				loan.setInterestAmount(p.getInterestAmount());
				loan.setProcessingFee(p.getProcessingFee());
				loan.setLoanPeriodDays(p.getLoanPeriodDays());
				loan.setDisbursementDate(p.getDisbursementDate());
				loan.setRepaymentDate(p.getRepaymentDate());
			}

			loan.setCbcConsentRef(p.getCbcConsentRef());
			loan.setBankConsent(Boolean.TRUE.equals(p.getBankConsent()));
			loan.setApplicationDate(Instant.now());
			loan.setStatus(PdlStatusEnum.Draft);

			// Link employment/bank profiles only when they belong to the caller —
			// never trust a raw id from the body (prevents IDOR / cross-user linkage).
			if (p.getEmploymentInfoId() != null) {
				PdlEmploymentInfo emp = employmentRepo.findById(p.getEmploymentInfoId()).orElse(null);
				if (emp != null && emp.getUser() != null
						&& Objects.equals(emp.getUser().getId(), loan.getUser().getId()))
					loan.setEmploymentInfo(emp);
			}
			if (p.getBankInfoId() != null) {
				PdlBankInfo bnk = bankRepo.findById(p.getBankInfoId()).orElse(null);
				if (bnk != null && bnk.getUser() != null
						&& Objects.equals(bnk.getUser().getId(), loan.getUser().getId()))
					loan.setBankInfo(bnk);
			}

			return resp("SUCCESS", "Application created (Draft)", repo.save(loan), HttpStatus.OK);

		} catch (Exception e) {
			e.printStackTrace();
			return resp("INTERNAL_SERVER_ERROR", e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@Override
	@Transactional
	public ResponseEntity<Message<PaydayLoan>> submit(int id) {
		try {
			PaydayLoan loan = ownedLoanOrNull(id);
			if (loan == null)
				return resp("NOT_FOUND", "Application not found", null, HttpStatus.EXPECTATION_FAILED);

			if (loan.getStatus() != PdlStatusEnum.Draft)
				return resp("INVALID", "Only a Draft application can be submitted", null,
						HttpStatus.EXPECTATION_FAILED);

			// V8 document set (Sambat 2026-08-13 QB2.1): NID front+back, selfie,
			// employment card, bank statement — captured at signup as profile
			// file-refs; validate those, not per-loan re-uploads.
			int uid = loan.getUser().getId();
			List<PdlPersonalInfo> pl = personalRepo.findByUser(uid);
			PdlPersonalInfo pi = pl.isEmpty() ? null : pl.get(0);
			List<PdlBankInfo> bl = bankRepo.findByUser(uid);
			PdlBankInfo bi = bl.isEmpty() ? null : bl.get(0);
			List<PdlEmploymentInfo> el = employmentRepo.findByUser(uid);
			PdlEmploymentInfo ei = el.isEmpty() ? null : el.get(0);
			if (pi == null || blank(pi.getNidFrontFileRef()))
				return resp("MISSING_DOCUMENT", "Missing document: NID photo (front)", null,
						HttpStatus.EXPECTATION_FAILED);
			if (blank(pi.getNidBackFileRef()))
				return resp("MISSING_DOCUMENT", "Missing document: NID photo (back)", null,
						HttpStatus.EXPECTATION_FAILED);
			if (blank(pi.getProfilePhotoFileRef()))
				return resp("MISSING_DOCUMENT", "Missing document: profile photo", null, HttpStatus.EXPECTATION_FAILED);
			if (ei == null || blank(ei.getEmploymentCardFileRef()))
				return resp("MISSING_DOCUMENT", "Missing document: employment card", null,
						HttpStatus.EXPECTATION_FAILED);
			if (bi == null || blank(bi.getBankStatementFileRef()))
				return resp("MISSING_DOCUMENT", "Missing document: bank statement", null, HttpStatus.EXPECTATION_FAILED);

			// Stamp the CBC consent record (QC4.2): generated by us at submit, viewable
			// via GET /pdl/{id}/cbc-consent.
			loan.setCbcConsentDate(Instant.now());
			loan.setCbcConsentTextVersion(cbcTextVersion);
			// Server-authoritative: overwrite any client-sent placeholder ref.
			loan.setCbcConsentRef("CBC-" + loan.getId() + "-" + cbcTextVersion);

			String losNo = losProvider.submitApplication(loan);
			loan.setLosApplicationNo(losNo);
			loan.setStatus(PdlStatusEnum.Submitted);

			return resp("SUCCESS", "Application submitted to LOS", repo.save(loan), HttpStatus.OK);

		} catch (Exception e) {
			e.printStackTrace();
			return resp("INTERNAL_SERVER_ERROR", e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@Override
	@Transactional
	public ResponseEntity<Message<PdlAttachment>> uploadDocument(int pdlId, PdlDocTypeEnum docType, List<Image> images) {
		try {
			PaydayLoan loan = ownedLoanOrNull(pdlId);
			if (loan == null)
				return resp("NOT_FOUND", "Application not found", null, HttpStatus.EXPECTATION_FAILED);

			// Re-upload replaces the existing (non-reviewed) attachment of this type.
			PdlAttachment a = attachmentRepo.findActive(pdlId, docType).orElse(new PdlAttachment());
			a.setPdl(loan);
			a.setDocType(docType);
			a.setAttachFiles(imageRepo.saveAll(images));
			a.setReviewed(false);
			a.setDeleted(false);

			return resp("SUCCESS", "Document uploaded", attachmentRepo.save(a), HttpStatus.OK);

		} catch (Exception e) {
			e.printStackTrace();
			return resp("INTERNAL_SERVER_ERROR", e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@Override
	@Transactional
	public ResponseEntity<Message<PaydayLoan>> accept(int id, PdlAcceptDecision decision) {
		try {
			PaydayLoan loan = ownedLoanOrNull(id);
			if (loan == null)
				return resp("NOT_FOUND", "Application not found", null, HttpStatus.EXPECTATION_FAILED);

			if (loan.getStatus() != PdlStatusEnum.Approved)
				return resp("INVALID", "Only an Approved application can be accepted/rejected", null,
						HttpStatus.EXPECTATION_FAILED);

			// Require an explicit Y/N — never silently reject on a missing/garbage value.
			String dec = decision == null ? null : decision.getDecision();
			boolean isAccept = "Y".equalsIgnoreCase(dec);
			boolean isReject = "N".equalsIgnoreCase(dec);
			if (!isAccept && !isReject)
				return resp("INVALID", "decision must be Y or N", null, HttpStatus.EXPECTATION_FAILED);

			if (isAccept) {
				if (decision.getSignedContractRef() == null)
					return resp("INVALID", "Signed contract is required to accept", null, HttpStatus.EXPECTATION_FAILED);

				loan.setAccepted(true);
				loan.setAcceptedBy(getCurrentUser().getUsername());
				loan.setAcceptedDate(new Date(System.currentTimeMillis()));
				loan.setSignedContractRef(decision.getSignedContractRef());
				loan.setStatus(PdlStatusEnum.Accepted);
				losProvider.sendDecision(loan.getLosApplicationNo(), "Y", decision.getSignedContractRef());

				return resp("SUCCESS", "Loan accepted", repo.save(loan), HttpStatus.OK);

			} else {
				loan.setStatus(PdlStatusEnum.Rejected);
				loan.setLosMessage("Loan Application is rejected by you");
				losProvider.sendDecision(loan.getLosApplicationNo(), "N", null);

				return resp("SUCCESS", "Loan rejected by customer", repo.save(loan), HttpStatus.OK);
			}

		} catch (Exception e) {
			e.printStackTrace();
			return resp("INTERNAL_SERVER_ERROR", e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@Override
	@Transactional
	public ResponseEntity<Message<PaydayLoan>> revoke(int id, String reason) {
		try {
			PaydayLoan loan = ownedLoanOrNull(id);
			if (loan == null)
				return resp("NOT_FOUND", "Application not found", null, HttpStatus.EXPECTATION_FAILED);

			// Only a pre-disbursement application may be revoked (not Accepted/Disbursed/Active/Closed).
			PdlStatusEnum st = loan.getStatus();
			if (st != PdlStatusEnum.Draft && st != PdlStatusEnum.Submitted && st != PdlStatusEnum.Approved)
				return resp("INVALID", "Only a pre-disbursement application can be revoked", null,
						HttpStatus.EXPECTATION_FAILED);

			loan.setRevoked(true);
			loan.setRevokedBy(getCurrentUser().getUsername());
			loan.setRevokedDate(new Date(System.currentTimeMillis()));
			loan.setRevokeReason(reason);
			loan.setStatus(PdlStatusEnum.Revoked);

			return resp("SUCCESS", "Application revoked", repo.save(loan), HttpStatus.OK);

		} catch (Exception e) {
			e.printStackTrace();
			return resp("INTERNAL_SERVER_ERROR", e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@Override
	public ResponseEntity<Message<List<PdlTransaction>>> getMyTransactions() {
		try {
			List<PdlTransaction> list = repo.findTransactionByUserId(getCurrentUser().getId());
			if (list.isEmpty())
				return resp("NOT_FOUND", "You don't have any application", null, HttpStatus.OK);
			return resp("SUCCESS", "Get data success", list, HttpStatus.OK);
		} catch (Exception e) {
			e.printStackTrace();
			return resp("INTERNAL_SERVER_ERROR", e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@Override
	public ResponseEntity<Message<List<PaydayLoan>>> getMyApplications() {
		try {
			return resp("SUCCESS", "Get data success", repo.findByUserId(getCurrentUser().getId()), HttpStatus.OK);
		} catch (Exception e) {
			e.printStackTrace();
			return resp("INTERNAL_SERVER_ERROR", e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@Override
	public ResponseEntity<Message<List<PdlPaymentSchedule>>> getPaymentSchedule(int id) {
		try {
			if (ownedLoanOrNull(id) == null)
				return resp("NOT_FOUND", "Application not found", null, HttpStatus.EXPECTATION_FAILED);
			return resp("SUCCESS", "Get data success", scheduleRepo.findSchedule(id), HttpStatus.OK);
		} catch (Exception e) {
			e.printStackTrace();
			return resp("INTERNAL_SERVER_ERROR", e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@Override
	public ResponseEntity<Message<PaydayLoan>> getLoanById(int id) {
		try {
			PaydayLoan loan = ownedLoanOrNull(id);
			if (loan == null)
				return resp("NOT_FOUND", "Application not found", null, HttpStatus.EXPECTATION_FAILED);
			return resp("SUCCESS", "Get data success", loan, HttpStatus.OK);
		} catch (Exception e) {
			e.printStackTrace();
			return resp("INTERNAL_SERVER_ERROR", e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@Value("${pdl.cbc.consent-text:I consent to Sambat Finance conducting a credit enquiry with the Credit Bureau of Cambodia (CBC) for the purpose of assessing this loan application, in accordance with the Prakas on Credit Reporting.}")
	private String cbcConsentText;

	@Value("${pdl.settlement.mock:true}")
	private boolean settlementMock;

	@Value("${pdl.settlement.mock-balance:10.0}")
	private double settlementMockBalance;

	/**
	 * The settlement account + balance (V8 screen 26, G20). Balance comes from
	 * SBF core banking (QC3.1) — MOCKED until Sambat provides the balance API;
	 * the account no falls back from the latest LOS-pushed loan to none.
	 */
	@Override
	public ResponseEntity<Message<PdlSettlementAccountResponse>> getSettlementAccount() {
		try {
			User user = getCurrentUser();
			// Repo order is unspecified — sort by id so "latest" really is latest
			// (device test caught an older rejected loan's account winning).
			String accountNo = repo.findByUserId(user.getId()).stream()
					.sorted(java.util.Comparator.comparing(PaydayLoan::getId))
					.map(PaydayLoan::getSettlementAccountNo)
					.filter(a -> a != null && !a.isBlank())
					.reduce((first, second) -> second) // latest wins
					.orElse(null);
			if (accountNo == null)
				return new ResponseEntity<>(new Message<>("NOT_FOUND", "No settlement account yet", null),
						HttpStatus.OK);
			String name = ((user.getFirstname() == null ? "" : user.getFirstname()) + " "
					+ (user.getLastname() == null ? "" : user.getLastname())).trim();
			if (!settlementMock)
				return new ResponseEntity<>(new Message<>("NOT_IMPLEMENTED",
						"Real core-banking balance API pending Sambat (QC3.1)", null), HttpStatus.OK);
			PdlSettlementAccountResponse r = new PdlSettlementAccountResponse(accountNo, name, "USD",
					settlementMockBalance, Instant.now(), true);
			return new ResponseEntity<>(new Message<>("SUCCESS", "Settlement account", r), HttpStatus.OK);
		} catch (Exception e) {
			e.printStackTrace();
			return new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/** The generated CBC-consent record (QC4.2) — ownership-checked like the loan itself. */
	@Override
	public ResponseEntity<Message<PdlCbcConsentResponse>> getCbcConsent(int id) {
		try {
			PaydayLoan loan = ownedLoanOrNull(id);
			if (loan == null)
				return new ResponseEntity<>(new Message<>("NOT_FOUND", "Application not found", null),
						HttpStatus.EXPECTATION_FAILED);
			if (loan.getCbcConsentDate() == null)
				return new ResponseEntity<>(
						new Message<>("NOT_FOUND", "No consent recorded — application not yet submitted", null),
						HttpStatus.EXPECTATION_FAILED);
			PdlCbcConsentResponse r = new PdlCbcConsentResponse(loan.getId(), loan.getLoanRefNo(),
					loan.getLosApplicationNo(), loan.getCustomerName(), loan.getCbcConsentRef(),
					loan.getCbcConsentDate(), loan.getCbcConsentTextVersion(), cbcConsentText);
			return new ResponseEntity<>(new Message<>("SUCCESS", "Get data success", r), HttpStatus.OK);
		} catch (Exception e) {
			e.printStackTrace();
			return new ResponseEntity<>(new Message<>("INTERNAL_SERVER_ERROR", e.getMessage(), null),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@Override
	@Transactional
	public ResponseEntity<Message<PdlEmploymentInfo>> saveEmploymentInfo(EmploymentInfoRequest req) {
		try {
			User user = getCurrentUser();
			// Upsert the single current row per user (POST creates if none, else updates).
			List<PdlEmploymentInfo> existing = employmentRepo.findByUser(user.getId());
			PdlEmploymentInfo e = existing.isEmpty() ? new PdlEmploymentInfo() : existing.get(0);

			e.setUser(user);
			e.setEmploymentType(req.getEmploymentType());
			e.setEmployerName(req.getEmployerName());
			e.setBusinessActivities(req.getBusinessActivities());
			e.setOccupation(req.getOccupation());
			e.setEmploymentStartDate(req.getEmploymentStartDate());
			e.setEmploymentStatus(req.getEmploymentStatus());
			e.setMonthlyIncome(req.getMonthlyIncome());
			e.setCurrency(req.getCurrency());
			e.setWorkCountry(req.getWorkCountry());
			e.setWorkProvince(req.getWorkProvince());
			e.setWorkDistrict(req.getWorkDistrict());
			e.setWorkCommune(req.getWorkCommune());
			e.setWorkVillage(req.getWorkVillage());
			if (req.getEmploymentCardFileRef() != null)
				e.setEmploymentCardFileRef(req.getEmploymentCardFileRef());
			// verified / verifiedBy / verifiedDate are server-set only — never from req.

			return resp("SUCCESS", "Employment info saved", employmentRepo.save(e), HttpStatus.OK);
		} catch (Exception ex) {
			ex.printStackTrace();
			return resp("INTERNAL_SERVER_ERROR", ex.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@Override
	public ResponseEntity<Message<PdlEmploymentInfo>> getEmploymentInfo() {
		try {
			List<PdlEmploymentInfo> list = employmentRepo.findByUser(getCurrentUser().getId());
			PdlEmploymentInfo e = list.isEmpty() ? null : list.get(0);
			return resp(e == null ? "NOT_FOUND" : "SUCCESS",
					e == null ? "No employment info" : "Get data success", e, HttpStatus.OK);
		} catch (Exception ex) {
			ex.printStackTrace();
			return resp("INTERNAL_SERVER_ERROR", ex.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@Override
	@Transactional
	public ResponseEntity<Message<PdlBankInfo>> saveBankInfo(BankInfoRequest req) {
		try {
			User user = getCurrentUser();
			List<PdlBankInfo> existing = bankRepo.findByUser(user.getId());
			PdlBankInfo b = existing.isEmpty() ? new PdlBankInfo() : existing.get(0);

			b.setUser(user);
			b.setBankName(req.getBankName());
			b.setAccountName(req.getAccountName());
			b.setAccountNo(req.getAccountNo());
			b.setCurrency(req.getCurrency());
			if (req.getBankStatementFileRef() != null)
				b.setBankStatementFileRef(req.getBankStatementFileRef());
			// consentGiven / verified* are server-set only — never from req.

			return resp("SUCCESS", "Bank info saved", bankRepo.save(b), HttpStatus.OK);
		} catch (Exception ex) {
			ex.printStackTrace();
			return resp("INTERNAL_SERVER_ERROR", ex.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@Override
	public ResponseEntity<Message<PdlBankInfo>> getBankInfo() {
		try {
			List<PdlBankInfo> list = bankRepo.findByUser(getCurrentUser().getId());
			PdlBankInfo b = list.isEmpty() ? null : list.get(0);
			return resp(b == null ? "NOT_FOUND" : "SUCCESS",
					b == null ? "No bank info" : "Get data success", b, HttpStatus.OK);
		} catch (Exception ex) {
			ex.printStackTrace();
			return resp("INTERNAL_SERVER_ERROR", ex.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@Override
	@Transactional
	public ResponseEntity<Message<PdlPersonalInfo>> savePersonalInfo(PersonalInfoRequest req) {
		try {
			User user = getCurrentUser();
			List<PdlPersonalInfo> existing = personalRepo.findByUser(user.getId());
			PdlPersonalInfo p = existing.isEmpty() ? new PdlPersonalInfo() : existing.get(0);

			p.setUser(user);
			p.setKhmerFamilyName(req.getKhmerFamilyName());
			p.setKhmerFirstName(req.getKhmerFirstName());
			p.setLatinFamilyName(req.getLatinFamilyName());
			p.setLatinFirstName(req.getLatinFirstName());
			p.setGender(req.getGender());
			p.setDateOfBirth(req.getDateOfBirth());
			p.setIdType(req.getIdType());
			p.setIdNo(req.getIdNo());
			p.setIdIssuedDate(req.getIdIssuedDate());
			p.setIdExpiryDate(req.getIdExpiryDate());
			p.setBirthCountry(req.getBirthCountry());
			p.setBirthProvince(req.getBirthProvince());
			p.setBirthDistrict(req.getBirthDistrict());
			p.setNationality(req.getNationality());
			p.setMaritalStatus(req.getMaritalStatus());
			p.setMobilePhone(req.getMobilePhone());
			p.setCorrCountry(req.getCorrCountry());
			p.setCorrProvince(req.getCorrProvince());
			p.setCorrDistrict(req.getCorrDistrict());
			p.setCorrCommune(req.getCorrCommune());
			p.setCorrVillage(req.getCorrVillage());
			p.setCorrHouseStreetNo(req.getCorrHouseStreetNo());
			p.setPermCountry(req.getPermCountry());
			p.setPermProvince(req.getPermProvince());
			p.setPermDistrict(req.getPermDistrict());
			p.setPermCommune(req.getPermCommune());
			p.setPermVillage(req.getPermVillage());
			p.setPermHouseStreetNo(req.getPermHouseStreetNo());
			if (req.getNidFrontFileRef() != null)
				p.setNidFrontFileRef(req.getNidFrontFileRef());
			if (req.getNidBackFileRef() != null)
				p.setNidBackFileRef(req.getNidBackFileRef());
			if (req.getProfilePhotoFileRef() != null)
				p.setProfilePhotoFileRef(req.getProfilePhotoFileRef());
			// verified / verifiedBy / verifiedDate are server-set only — never from req.

			return resp("SUCCESS", "Personal info saved", personalRepo.save(p), HttpStatus.OK);
		} catch (Exception ex) {
			ex.printStackTrace();
			return resp("INTERNAL_SERVER_ERROR", ex.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@Override
	public ResponseEntity<Message<PdlPersonalInfo>> getPersonalInfo() {
		try {
			List<PdlPersonalInfo> list = personalRepo.findByUser(getCurrentUser().getId());
			PdlPersonalInfo p = list.isEmpty() ? null : list.get(0);
			return resp(p == null ? "NOT_FOUND" : "SUCCESS",
					p == null ? "No personal info" : "Get data success", p, HttpStatus.OK);
		} catch (Exception ex) {
			ex.printStackTrace();
			return resp("INTERNAL_SERVER_ERROR", ex.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@Override
	public ResponseEntity<Message<PdlProfileResponse>> getProfile() {
		try {
			User user = getCurrentUser();
			List<PdlPersonalInfo> pl = personalRepo.findByUser(user.getId());
			List<PdlEmploymentInfo> el = employmentRepo.findByUser(user.getId());
			List<PdlBankInfo> bl = bankRepo.findByUser(user.getId());

			PdlProfileResponse pr = new PdlProfileResponse();
			pr.setPersonalInfo(pl.isEmpty() ? null : pl.get(0));
			pr.setUserId(user.getId());
			pr.setUsername(user.getUsername());
			// Self-heal: resolve + persist the SBF CIF from the KYC ID number
			// when missing (accounts signed up before CIF persistence existed,
			// or created while SBF was unreachable). Best-effort, one SBF call
			// only while empty.
			if ((user.getRegistedId() == null || user.getRegistedId().isBlank())
					&& !pl.isEmpty() && pl.get(0).getIdNo() != null
					&& !pl.get(0).getIdNo().isBlank()) {
				try {
					Integer cif = sbfGateway.findCifByIdNo(pl.get(0).getIdNo());
					if (cif != null) {
						user.setRegistedId(String.valueOf(cif));
						userRepository.save(user);
					}
				} catch (Exception ignore) {
					// SBF hiccup must not break the profile screen
				}
			}
			pr.setCif(user.getRegistedId());
			pr.setFirstname(user.getFirstname());
			pr.setLastname(user.getLastname());
			pr.setGender(user.getGender() != null ? user.getGender().name() : null);
			pr.setDateOfBirth(user.getDateOfBirth());
			pr.setPhoneNumber(user.getPhoneNumber());
			pr.setEmail(user.getEmail());
			pr.setEmploymentInfo(el.isEmpty() ? null : el.get(0));
			pr.setBankInfo(bl.isEmpty() ? null : bl.get(0));

			return resp("SUCCESS", "Get data success", pr, HttpStatus.OK);
		} catch (Exception ex) {
			ex.printStackTrace();
			return resp("INTERNAL_SERVER_ERROR", ex.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@org.springframework.beans.factory.annotation.Autowired
	private SbfGatewayClient sbfGateway;

	@org.springframework.beans.factory.annotation.Autowired
	private com.ezetik.kjeypapa.security.repository.UserRepository userRepository;

	User getCurrentUser() {
		String userPrincipal = SecurityContextHolder.getContext().getAuthentication().getName();
		User u = userService.findUserByUsername(userPrincipal);
		if (u == null)
			throw new IllegalStateException("Authenticated user not found: " + userPrincipal);
		return u;
	}

	/**
	 * Load a loan only if it belongs to the current user; otherwise null so the
	 * caller returns NOT_FOUND. Prevents IDOR on every per-id endpoint (we never
	 * leak whether another user's loan exists).
	 */
	private PaydayLoan ownedLoanOrNull(int id) {
		PaydayLoan loan = repo.findById(id).orElse(null);
		if (loan == null || loan.getUser() == null
				|| !Objects.equals(loan.getUser().getId(), getCurrentUser().getId()))
			return null;
		return loan;
	}

	private <T> ResponseEntity<Message<T>> resp(String type, String message, T data, HttpStatus status) {
		return new ResponseEntity<>(new Message<>(type, message, data), status);
	}
}
