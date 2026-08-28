package com.ezetik.kjeypapa.pdl.service;

import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.ezetik.kjeypapa.image.helper.FileNameHelper;
import com.ezetik.kjeypapa.image.model.Image;
import com.ezetik.kjeypapa.image.repository.ImageRepository;
import com.ezetik.kjeypapa.pdl.model.PdlAccountRequest;
import com.ezetik.kjeypapa.pdl.model.PdlAccountStatusEnum;
import com.ezetik.kjeypapa.pdl.model.PdlBankInfo;
import com.ezetik.kjeypapa.pdl.model.PdlEmploymentInfo;
import com.ezetik.kjeypapa.pdl.model.PdlPersonalInfo;
import com.ezetik.kjeypapa.pdl.payload.BankInfoRequest;
import com.ezetik.kjeypapa.pdl.payload.EmploymentInfoRequest;
import com.ezetik.kjeypapa.pdl.payload.PdlAccountRequestPayload;
import com.ezetik.kjeypapa.pdl.payload.PdlAccountRequestPayload.DocFile;
import com.ezetik.kjeypapa.pdl.payload.PersonalInfoRequest;
import com.ezetik.kjeypapa.pdl.repository.PdlAccountRequestRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlBankInfoRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlEmploymentInfoRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlPersonalInfoRepository;
import com.ezetik.kjeypapa.sbf.service.SMSService;
import com.ezetik.kjeypapa.security.model.OneTimePassword;
import com.ezetik.kjeypapa.security.model.Role;
import com.ezetik.kjeypapa.security.model.User;
import com.ezetik.kjeypapa.security.repository.RoleRepository;
import com.ezetik.kjeypapa.security.repository.UserRepository;
import com.ezetik.kjeypapa.security.service.OneTimePasswordService;
import com.ezetik.kjeypapa.security.util.Message;

import jakarta.transaction.Transactional;

/**
 * Pre-login PDL account requests (G7 — V26 page 1, V8 screens 4-10).
 *
 * One unauthenticated call captures credentials + the three profile sections +
 * the V8 document set (base64 inline). The User is created DISABLED; the
 * existing OTP flow verifies the phone; login stays blocked until the LPO
 * decision (mocked by {@code POST /pdl/los/account-decision}) approves the
 * request (QC2.1). Employee → ROLE CUSTOMER (id 2).
 */
@Service
public class PdlAccountRequestService {

	private static final long CUSTOMER_ROLE_ID = 2L;
	private static final long MAX_DOC_BYTES = 20L * 1024 * 1024; // QB2.4

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private PdlAccountRequestRepository requestRepo;

	@Autowired
	private PdlPersonalInfoRepository personalRepo;

	@Autowired
	private SbfGatewayClient sbfGateway;

	@Autowired
	private PdlEmploymentInfoRepository employmentRepo;

	@Autowired
	private PdlBankInfoRepository bankRepo;

	@Autowired
	private ImageRepository imageRepo;

	@Autowired
	private OneTimePasswordService otpService;

	@Autowired
	private SMSService sms;

	private final FileNameHelper fileHelper = new FileNameHelper();

	@Transactional
	public ResponseEntity<Message<PdlAccountRequest>> create(PdlAccountRequestPayload p) {
		try {
			if (p == null || isBlank(p.getUsername()) || isBlank(p.getPassword()))
				return resp("INVALID", "Username and password are required", null, HttpStatus.EXPECTATION_FAILED);
			if (p.getPassword().length() < 6)
				return resp("INVALID", "Password requires at least 6 characters", null, HttpStatus.EXPECTATION_FAILED);
			if (userRepository.findByUsername(p.getUsername()) != null)
				return resp("USERNAME_EXIST", "Username is already exist, Please change it.", null,
						HttpStatus.NOT_ACCEPTABLE);

			// --- user (disabled until OTP; login gated on the LPO decision) ---
			User user = new User();
			user.setUsername(p.getUsername());
			user.setPhoneNumber(p.getUsername()); // User ID = phone (V8 screen 6)
			user.setEmail(p.getEmail());
			// ez_user.first_name/last_name are NOT NULL — take the latin names.
			PersonalInfoRequest per = p.getPersonal();
			user.setFirstname(per != null && !isBlank(per.getLatinFirstName()) ? per.getLatinFirstName()
					: p.getUsername());
			user.setLastname(per != null && !isBlank(per.getLatinFamilyName()) ? per.getLatinFamilyName() : "");
			if (per != null && !isBlank(per.getDateOfBirth()))
				user.setDateOfBirth(per.getDateOfBirth());
			// Gender when parseable (M/F) — /auth/me consumers expect it.
			if (per != null && !isBlank(per.getGender())) {
				try {
					user.setGender(com.ezetik.kjeypapa.security.model.GenderEnum
							.valueOf(per.getGender().trim().substring(0, 1).toUpperCase()));
				} catch (Exception ignore) {
					// unknown format — leave null (client is null-tolerant)
				}
			}
			user.setPassword(passwordEncoder.encode(p.getPassword()));
			user.setPasswordNeverExpires(true);
			user.setEnabled(false);
			// Resolve the SBF CIF from the KYC ID number (server-authoritative,
			// Makara 2026-08-26: custId = CIF or 0 at loan submit; backs the
			// profile "CIF No" row). Best-effort — signup never fails on SBF.
			if (per != null && per.getIdNo() != null && !per.getIdNo().isBlank()) {
				try {
					Integer cif = sbfGateway.findCifByIdNo(per.getIdNo());
					if (cif != null)
						user.setRegistedId(String.valueOf(cif));
				} catch (Exception ignore) {
				}
			}
			Role customer = roleRepository.findById(CUSTOMER_ROLE_ID).orElse(null);
			if (customer == null)
				return resp("INTERNAL_SERVER_ERROR", "Customer role missing", null, HttpStatus.INTERNAL_SERVER_ERROR);
			user.setRoles(List.of(customer));
			user = userRepository.save(user);

			// --- documents (base64 inline; stored like /file/upload does) ---
			String nidFront = null, nidBack = null, selfie = null, empCard = null, stmt = null;
			if (p.getDocs() != null) {
				for (DocFile d : p.getDocs()) {
					if (d == null || isBlank(d.getBase64()))
						continue;
					byte[] bytes = Base64.getDecoder().decode(d.getBase64());
					if (bytes.length > MAX_DOC_BYTES)
						return resp("INVALID", "Document too large: " + d.getDocType(), null,
								HttpStatus.EXPECTATION_FAILED);
					String ref = storeDoc(d, bytes, user.getId());
					switch (d.getDocType() == null ? "" : d.getDocType()) {
					case "NID_FRONT" -> nidFront = ref;
					case "NID_BACK" -> nidBack = ref;
					case "SELFIE" -> selfie = ref;
					case "EMPLOYMENT_CARD" -> empCard = ref;
					case "BANK_STATEMENT" -> stmt = ref;
					default -> { /* unknown type — stored but not referenced */ }
					}
				}
			}

			// --- profile sections (mirror the authenticated upserts) ---
			if (p.getPersonal() != null) {
				PdlPersonalInfo pi = new PdlPersonalInfo();
				pi.setUser(user);
				applyPersonal(pi, p.getPersonal());
				if (nidFront != null)
					pi.setNidFrontFileRef(nidFront);
				if (nidBack != null)
					pi.setNidBackFileRef(nidBack);
				if (selfie != null)
					pi.setProfilePhotoFileRef(selfie);
				personalRepo.save(pi);
			}
			if (p.getEmployment() != null) {
				PdlEmploymentInfo ei = new PdlEmploymentInfo();
				ei.setUser(user);
				applyEmployment(ei, p.getEmployment());
				if (empCard != null)
					ei.setEmploymentCardFileRef(empCard);
				employmentRepo.save(ei);
			}
			if (p.getBank() != null) {
				PdlBankInfo bi = new PdlBankInfo();
				bi.setUser(user);
				BankInfoRequest br = p.getBank();
				bi.setBankName(br.getBankName());
				bi.setAccountName(br.getAccountName());
				bi.setAccountNo(br.getAccountNo());
				bi.setCurrency(br.getCurrency());
				if (stmt != null)
					bi.setBankStatementFileRef(stmt);
				// V8 screen 8: bank-information disclosure consent (QB2.3).
				bi.setConsentGiven(Boolean.TRUE.equals(p.getDisclosureConsent()));
				bankRepo.save(bi);
			}

			PdlAccountRequest req = new PdlAccountRequest();
			req.setUser(user);
			req.setUserType(isBlank(p.getUserType()) ? "EMPLOYEE" : p.getUserType());
			req.setStatus(PdlAccountStatusEnum.PENDING);
			req = requestRepo.save(req);

			// --- OTP last (V8 screen 9) — reuse the registration OTP + SMS ---
			try {
				OneTimePassword otp = otpService.returnOneTimePassword(user);
				sms.sendSms(user.getPhoneNumber(), "Kjeypapa_OTP_" + otp.getOneTimePasswordCode());
			} catch (Exception smsEx) {
				smsEx.printStackTrace(); // OTP delivery failure must not lose the request
			}

			return resp("SUCCESS", "Account request submitted — verify the OTP sent to your phone", req,
					HttpStatus.OK);
		} catch (Exception e) {
			e.printStackTrace();
			return resp("INTERNAL_SERVER_ERROR", e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * The LPO decision (G7). Our own approval process — SBF/LOS does NOT handle
	 * account approval (product decision 2026-08-21). Called by the admin API;
	 * the /pdl/los/account-decision webhook delegates here for test tooling.
	 */
	@Transactional
	public ResponseEntity<Message<String>> decide(int requestId, boolean approve, String reason, String decidedBy) {
		PdlAccountRequest req = requestRepo.findById(requestId).orElse(null);
		if (req == null)
			return resp2("NOT_FOUND", "Account request not found: " + requestId, null, HttpStatus.OK);
		if (req.getStatus() != PdlAccountStatusEnum.PENDING)
			return resp2("INVALID", "Already decided: " + req.getStatus(), req.getStatus().name(), HttpStatus.OK);
		req.setStatus(approve ? PdlAccountStatusEnum.APPROVED : PdlAccountStatusEnum.REJECTED);
		req.setDecisionReason(reason);
		req.setDecidedBy(decidedBy);
		req.setDecidedDate(java.time.Instant.now());
		User u = req.getUser();
		if (u != null)
			u.setEnabled(approve);
		requestRepo.save(req);
		try {
			if (u != null && u.getPhoneNumber() != null)
				sms.sendSms(u.getPhoneNumber(), approve
						? "Kjey PAPA: your account has been created successfully. You can now sign in."
						: "Kjey PAPA: your account request was not approved. Please contact 023 99 77 22.");
		} catch (Exception smsEx) {
			smsEx.printStackTrace();
		}
		return resp2("SUCCESS", "Account decision processed: " + req.getStatus(), req.getStatus().name(),
				HttpStatus.OK);
	}

	/** LPO list view: PENDING by default, or any status / all. */
	public ResponseEntity<Message<java.util.List<PdlAccountRequest>>> list(String status) {
		java.util.List<PdlAccountRequest> out;
		if (status == null || status.isBlank() || "PENDING".equalsIgnoreCase(status))
			out = requestRepo.findByStatusOrderByIdDesc(PdlAccountStatusEnum.PENDING);
		else if ("ALL".equalsIgnoreCase(status))
			out = requestRepo.findAllByOrderByIdDesc();
		else {
			try {
				out = requestRepo.findByStatusOrderByIdDesc(PdlAccountStatusEnum.valueOf(status.toUpperCase()));
			} catch (IllegalArgumentException bad) {
				return new ResponseEntity<>(new Message<>("INVALID", "Unknown status: " + status, null),
						HttpStatus.EXPECTATION_FAILED);
			}
		}
		return new ResponseEntity<>(new Message<>("SUCCESS", "Account requests", out), HttpStatus.OK);
	}

	/** LPO review detail: the request + profile sections (doc refs included). */
	public ResponseEntity<Message<com.ezetik.kjeypapa.pdl.payload.PdlAccountReviewResponse>> review(int requestId) {
		PdlAccountRequest req = requestRepo.findById(requestId).orElse(null);
		if (req == null || req.getUser() == null)
			return new ResponseEntity<>(new Message<>("NOT_FOUND", "Account request not found: " + requestId, null),
					HttpStatus.OK);
		int uid = req.getUser().getId();
		var pi = personalRepo.findByUser(uid);
		var ei = employmentRepo.findByUser(uid);
		var bi = bankRepo.findByUser(uid);
		var out = new com.ezetik.kjeypapa.pdl.payload.PdlAccountReviewResponse(req,
				pi.isEmpty() ? null : pi.get(0), ei.isEmpty() ? null : ei.get(0), bi.isEmpty() ? null : bi.get(0));
		return new ResponseEntity<>(new Message<>("SUCCESS", "Account request detail", out), HttpStatus.OK);
	}

	/** Public status probe for the pending screen (returns the status only). */
	public ResponseEntity<Message<String>> status(String username) {
		PdlAccountRequest r = requestRepo.findByUser_Username(username).orElse(null);
		if (r == null)
			return resp2("NOT_FOUND", "No account request", null, HttpStatus.OK);
		return resp2("SUCCESS", "Status", r.getStatus().name(), HttpStatus.OK);
	}

	private String storeDoc(DocFile d, byte[] bytes, Integer userId) {
		Image image = Image.build();
		String name = fileHelper.generateDisplayName(
				isBlank(d.getFileName()) ? (d.getDocType() + ".jpg") : d.getFileName());
		image.setFileName(name);
		image.setFileType(isBlank(d.getContentType()) ? "image/jpeg" : d.getContentType());
		image.setSize(bytes.length);
		image.setData(bytes);
		image.setEntityClass("PDL_ACCOUNT_REQUEST");
		image.setEntityId(String.valueOf(userId));
		imageRepo.save(image);
		return name;
	}

	private static void applyPersonal(PdlPersonalInfo p, PersonalInfoRequest req) {
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
		p.setBirthProvinceCode(req.getBirthProvinceCode());
		p.setBirthDistrict(req.getBirthDistrict());
		p.setBirthDistrictCode(req.getBirthDistrictCode());
		p.setNationality(req.getNationality());
		p.setMaritalStatus(req.getMaritalStatus());
		p.setMobilePhone(req.getMobilePhone());
		p.setCorrCountry(req.getCorrCountry());
		p.setCorrProvince(req.getCorrProvince());
		p.setCorrProvinceCode(req.getCorrProvinceCode());
		p.setCorrDistrict(req.getCorrDistrict());
		p.setCorrDistrictCode(req.getCorrDistrictCode());
		p.setCorrCommune(req.getCorrCommune());
		p.setCorrCommuneCode(req.getCorrCommuneCode());
		p.setCorrVillage(req.getCorrVillage());
		p.setCorrVillageCode(req.getCorrVillageCode());
		p.setCorrHouseStreetNo(req.getCorrHouseStreetNo());
		p.setPermCountry(req.getPermCountry());
		p.setPermProvince(req.getPermProvince());
		p.setPermProvinceCode(req.getPermProvinceCode());
		p.setPermDistrict(req.getPermDistrict());
		p.setPermDistrictCode(req.getPermDistrictCode());
		p.setPermCommune(req.getPermCommune());
		p.setPermCommuneCode(req.getPermCommuneCode());
		p.setPermVillage(req.getPermVillage());
		p.setPermVillageCode(req.getPermVillageCode());
		p.setPermHouseStreetNo(req.getPermHouseStreetNo());
	}

	private static void applyEmployment(PdlEmploymentInfo e, EmploymentInfoRequest req) {
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
		e.setWorkProvinceCode(req.getWorkProvinceCode());
		e.setWorkDistrict(req.getWorkDistrict());
		e.setWorkDistrictCode(req.getWorkDistrictCode());
		e.setWorkCommune(req.getWorkCommune());
		e.setWorkCommuneCode(req.getWorkCommuneCode());
		e.setWorkVillage(req.getWorkVillage());
		e.setWorkVillageCode(req.getWorkVillageCode());
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

	private static ResponseEntity<Message<PdlAccountRequest>> resp(String t, String m, PdlAccountRequest d,
			HttpStatus st) {
		return new ResponseEntity<>(new Message<>(t, m, d), st);
	}

	private static ResponseEntity<Message<String>> resp2(String t, String m, String d, HttpStatus st) {
		return new ResponseEntity<>(new Message<>(t, m, d), st);
	}
}
