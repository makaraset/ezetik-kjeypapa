package com.ezetik.kjeypapa.pdl.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ezetik.kjeypapa.pdl.model.PaydayLoan;
import com.ezetik.kjeypapa.pdl.model.PdlBankInfo;
import com.ezetik.kjeypapa.pdl.model.PdlEmploymentInfo;
import com.ezetik.kjeypapa.pdl.model.PdlPersonalInfo;
import com.ezetik.kjeypapa.pdl.payload.los.MonthlyIncomeItem;
import com.ezetik.kjeypapa.pdl.payload.los.NewApplicationParam;
import com.ezetik.kjeypapa.pdl.payload.los.NewApplicationRequest;
import com.ezetik.kjeypapa.pdl.model.PdlCodeList;
import com.ezetik.kjeypapa.pdl.repository.PdlBankInfoRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlCodeListRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlEmploymentInfoRepository;
import com.ezetik.kjeypapa.pdl.repository.PdlPersonalInfoRepository;
import com.ezetik.kjeypapa.pdl.service.LosDocumentAssembler.Doc;

/**
 * Builds SBF's 102-field loan application from our own records.
 *
 * <p><b>What this mapper deliberately does not do is guess.</b> Roughly a
 * third of SBF's fields are CBC master-list codes (occupation, employer,
 * employment contract type, payment channel) or the four-level geo codes, and
 * we hold names, not codes. SBF's UAT accepts unknown codes without
 * complaining, so a plausible-looking guess does not bounce — it files a real
 * credit application under a real customer's name carrying a wrong occupation
 * or an address in the wrong commune. Those fields are therefore left empty
 * here and SBF's own {@code MissingData} response tells us which of them it
 * actually insists on, which is a far cheaper way to learn the mandatory set
 * than inventing values.
 *
 * <p>The codes we can legitimately supply once Sambat sends their master lists
 * live in {@link LosSubmitConfig}, which refuses to submit while any is unset.
 */
@Component
public class LosApplicationMapper {

	private static final DateTimeFormatter LOS_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final DateTimeFormatter APP_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final ZoneId KH = ZoneId.of("Asia/Phnom_Penh");

	@Autowired
	private PdlPersonalInfoRepository personalRepo;
	@Autowired
	private PdlEmploymentInfoRepository employmentRepo;
	@Autowired
	private PdlBankInfoRepository bankRepo;
	@Autowired
	private LosDocumentAssembler docs;
	@Autowired
	private CbcConsentFormRenderer consentForm;
	@Autowired
	private LosSubmitConfig config;
	@Autowired
	private PdlCodeListRepository codeRepo;

	/** Preview mode: blank numeric config renders as 0 instead of throwing. */
	private boolean lenient = false;

	/** Cambodia, per Sambat (2026-08-28): nationality and country both take KHM. */
	private static final String KHM = "KHM";

	/**
	 * @param consentPdf the consent document as filed. Passed in rather than
	 *        rendered here so that the bytes Sambat receive are the same object
	 *        the caller archives and hashes — rendering twice invites two
	 *        different documents for one consent.
	 */
	public NewApplicationParam toParam(PaydayLoan loan, byte[] consentPdf) {
		config.assertConfigured();
		return build(loan, false, consentPdf);
	}

	/**
	 * The exact payload {@link #toParam} would send, WITHOUT the config gate —
	 * unset codes render as blank / 0 instead of refusing. For comparing our
	 * request with Sambat's reference payload; never for a real submit.
	 */
	public NewApplicationParam preview(PaydayLoan loan) {
		// A preview has no filed document: previewing must never look like
		// evidence that a consent was given.
		return build(loan, true, null);
	}

	private NewApplicationParam build(PaydayLoan loan, boolean lenient, byte[] consentPdf) {
		this.lenient = lenient;
		int uid = loan.getUser().getId();
		PdlPersonalInfo pi = first(personalRepo.findByUser(uid));
		PdlEmploymentInfo ei = first(employmentRepo.findByUser(uid));
		PdlBankInfo bi = first(bankRepo.findByUser(uid));

		NewApplicationParam param = new NewApplicationParam();
		// The envelope appId IS Sambat's LOS AppId (confirmed 2026-09-03): 0
		// asks them to create an application, an existing id updates that one.
		// Submitting is retryable, so once we hold an AppId we must send it
		// back — otherwise a retry files a SECOND credit application for the
		// same loan, with its own CBC enquiry, under the customer's name.
		param.setAppId(loan.getLosAppId() != null ? loan.getLosAppId() : config.getAppId());
		// custId is the SBF CIF, or 0 for a customer they have never seen.
		param.setCustId(cif(loan));
		// A constant, NEVER the customer's username: their LOS dies with a slow
		// 500/timeout on a doneBy it cannot resolve (root-caused 2026-09-03).
		param.setDoneBy(str(config.getDoneBy()));
		param.setNewAppRequest(request(loan, pi, ei, bi, consentPdf));
		return param;
	}

	private NewApplicationRequest request(PaydayLoan loan, PdlPersonalInfo pi, PdlEmploymentInfo ei,
			PdlBankInfo bi, byte[] consentPdf) {
		NewApplicationRequest r = new NewApplicationRequest();

		r.setHidCurrentUserId(intOf(config.getHidCurrentUserId()));

		// ----- identity -----
		if (pi != null) {
			r.setCustP_FamilyNameKH(str(pi.getKhmerFamilyName()));
			r.setCustP_FirstNameKH(str(pi.getKhmerFirstName()));
			r.setCustP_FamilyNameLatin(str(pi.getLatinFamilyName()));
			r.setCustP_FirstNameLatin(str(pi.getLatinFirstName()));
			r.setCustP_CBSex(sex(pi.getGender()));
			r.setCustP_DateOfBirth(losDate(pi.getDateOfBirth()));
			r.setCustP_Age(ageFrom(pi.getDateOfBirth()));
			r.setCustP_IdNo(str(pi.getIdNo()));
			r.setCustP_IdIssuedDate(losDate(pi.getIdIssuedDate()));
			r.setCustP_IdExpiryDate(losDate(pi.getIdExpiryDate()));
			r.setCustP_Nationality(str(pi.getNationality()));
			r.setCustP_PhoneNo(str(pi.getMobilePhone()));
			r.setCustP_CBIdType(idTypeCode(pi.getIdType()));
			r.setCustP_CBIdIssuedBy(str(config.getIdIssuedBy()));

			r.setCustP_Nationality(KHM);
			r.setCustP_CBMaritalStatus(maritalCode(pi.getMaritalStatus()));

			// ----- addresses -----
			// The geo slots now carry Sambat's own NCDD codes, mirrored from
			// their gazetteer; a blank means we hold no code for that level and
			// their MissingData will say so, which beats sending a guess.
			r.setCustP_CAddNo(str(pi.getCorrHouseStreetNo()));
			r.setCustP_CAddPhoneNo(str(pi.getMobilePhone()));
			r.setCustP_CAddCBCountry(KHM);
			r.setCustP_CAddCBProvinceCity(province(pi.getCorrProvinceCode()));
			r.setCustP_CAddCBDistrict(district(pi.getCorrDistrictCode()));
			r.setCustP_CAddCBCommune(commune(pi.getCorrCommuneCode()));
			r.setCustP_CAddCBVillage(village(pi.getCorrVillageCode()));

			r.setCustP_PRAddNo(str(pi.getPermHouseStreetNo()));
			r.setCustP_PRAddPhoneNo(str(pi.getMobilePhone()));
			r.setCustP_PRAddCBCountry(KHM);
			r.setCustP_PRAddCBProvinceCity(province(pi.getPermProvinceCode()));
			r.setCustP_PRAddCBDistrict(district(pi.getPermDistrictCode()));
			r.setCustP_PRAddCBCommune(commune(pi.getPermCommuneCode()));
			r.setCustP_PRAddCBVillage(village(pi.getPermVillageCode()));
			r.setCustP_PRAddCBCoincide(sameAddress(pi));

			// Place of birth: the form collects province and district only.
			// Sambat's convention for an unknown POB level is zero-fill to the
			// NCDD width (confirmed 2026-08-31), so the uncaptured commune and
			// village always go as zeros, and blank province/district do too.
			r.setCustP_POBCBCountry(KHM);
			r.setCustP_POBCBProvinceCity(zeroIfBlank(province(pi.getBirthProvinceCode()), 2));
			r.setCustP_POBCBDistrict(zeroIfBlank(district(pi.getBirthDistrictCode()), 4));
			r.setCustP_POBCBCommune("000000");
			r.setCustP_POBCBVillage("00000000");

			// CustP_CAddLocationId / PRAddLocationId / EmpAddLocationId are
			// Google Maps coordinates (Sambat, 2026-08-28). The app does not
			// collect a map pin, so they stay empty rather than carrying
			// something that is not a location.
		}
		if (loan.getUser() != null)
			r.setCustP_Email(str(loan.getUser().getEmail()));
		r.setCustP_CIFNo(cif(loan) == 0 ? "" : String.valueOf(cif(loan)));

		// ----- employment -----
		if (ei != null) {
			r.setCustP_EmployerName(str(ei.getEmployerName()));
			// Their /occupation id and 8-digit business-activity bizCode —
			// both in their MissingData mandatory set (2026-09-03).
			r.setCustP_Occupation(str(ei.getOccupationCode()));
			r.setCustP_BusinessActivity(str(ei.getBusinessActivityCode()));
			r.setCustP_JobBusinessStartDate(losDate(ei.getEmploymentStartDate()));
			r.setCustP_EmpAddNo("");
			r.setCustP_EmpAddCBCountry(KHM);
			r.setCustP_EmpAddCBProvinceCity(province(ei.getWorkProvinceCode()));
			r.setCustP_EmpAddCBDistrict(district(ei.getWorkDistrictCode()));
			r.setCustP_EmpAddCBCommune(commune(ei.getWorkCommuneCode()));
			r.setCustP_EmpAddCBVillage(village(ei.getWorkVillageCode()));
			r.setCustP_CBEmploymentType(str(config.getEmploymentType()));
			r.setCustP_CBEmploymentContractType(str(config.getEmploymentContractType()));
			// Screen 7's employment type IS their status code (Sambat,
			// 2026-08-31): 1 self-employed, 2 employee, 3 other.
			r.setCustP_CBEmploymentStatus(employmentStatusCode(ei.getEmploymentType()));
			// The employer entity code the LPO assigned at approval (their
			// /employer comId). Blank until assigned — MissingData names it.
			r.setCustP_EntityFactoryId(str(ei.getEmployerCode()));

			if (ei.getMonthlyIncome() != null && ei.getMonthlyIncome() > 0) {
				MonthlyIncomeItem income = new MonthlyIncomeItem();
				// IncomeType "S" = Salary, confirmed by Sambat's reference payload
				// (Manith, 2026-08-30). Every payday customer is a salaried
				// employee by product definition, so this is safe.
				income.setIncomeType("S");
				income.setIncomeAmount(ei.getMonthlyIncome());
				income.setCurrency(str(ei.getCurrency()));
				r.setMonthlyIncomes(List.of(income));
			}
		}

		// ----- the loan -----
		r.setLR_LoanRequestAmount(loan.getRequestAmount() == null ? 0 : loan.getRequestAmount());
		r.setLR_CBCurrency(str(loan.getCurrency()));
		r.setLR_DisbursementDate(losDate(loan.getDisbursementDate()));
		r.setLR_CBProductType(str(config.getProductType()));
		r.setLR_CBLoanCategory(str(config.getLoanCategory()));
		r.setLR_CBRepaymentMethod(str(config.getRepaymentMethod()));
		r.setLR_DisbursementScheme(str(config.getDisbursementScheme()));
		r.setLR_LoanTerm(intOf(config.getLoanTerm()));
		r.setAgreedFirstDueDate(losDate(loan.getRepaymentDate()));

		// LoanUtilizationProject is mandatory (their MissingData, 2026-09-03).
		// A payday loan has exactly one "project": the cash need itself — one
		// unit priced at the requested amount, fully financed by Sambat.
		double amount = loan.getRequestAmount() == null ? 0 : loan.getRequestAmount();
		com.ezetik.kjeypapa.pdl.payload.los.LoanUtilizationProjectItem util =
				new com.ezetik.kjeypapa.pdl.payload.los.LoanUtilizationProjectItem();
		util.setUltilizationCategory(str(config.getUtilizationCategory()));
		util.setTotalUnit(1);
		util.setUnitPrice(amount);
		util.setSambatLoan(amount);
		r.setLoanUtilizationProject(List.of(util));

		// MonthlyExpenses is also mandatory (their MissingData, 2026-09-03).
		// The app does not capture expenses, so the row declares zero — the
		// honest value for "not captured" — with the type code their reference
		// uses. If their validator insists on a positive amount, that is a
		// signup-capture question for Sambat, not a value to invent here.
		com.ezetik.kjeypapa.pdl.payload.los.MonthlyExpenseItem expense =
				new com.ezetik.kjeypapa.pdl.payload.los.MonthlyExpenseItem();
		expense.setExpenseType("S");
		expense.setExpenseAmount(0);
		expense.setCurrency(str(loan.getCurrency()));
		r.setMonthlyExpenses(List.of(expense));

		// ----- disbursement account -----
		// Channel and channel-name are config constants (BANK / sheet row 12),
		// not bank-row data, so they do not depend on the bank row existing.
		r.setPC_PaymentChannel(str(config.getPaymentChannel()));
		r.setPC_PaymentChannelName(str(config.getPaymentChannelName()));
		if (bi != null) {
			r.setPC_AccountNum(str(bi.getAccountNo()));
			r.setPC_PaymentChannelAccountName(str(bi.getAccountName()));
		}

		// ----- documents -----
		int id = loan.getId() == null ? 0 : loan.getId();
		if (pi != null) {
			// LOS has ONE NID slot and a Cambodian ID has two sides. Sambat
			// asked for the two MERGED into one image (2026-09-03).
			put(r, docs.mergedNid(pi.getNidFrontFileRef(), pi.getNidBackFileRef(), id), "NID");
			put(r, docs.build(pi.getProfilePhotoFileRef(), "ProfilePhoto", id), "ProfilePhoto");
		}
		if (ei != null)
			put(r, docs.build(ei.getEmploymentCardFileRef(), "EmploymentCard", id), "EmploymentCard");
		if (bi != null)
			put(r, docs.build(bi.getBankStatementFileRef(), "BankStatement", id), "BankStatement");
		// Doc_ECBCConsentForm: rendered at submit time from the consent the
		// customer actually gave in-app (the CBC page checkbox) — text, name,
		// NID, ref, timestamp. In their MissingData mandatory set (2026-09-03);
		// their own reference reuses an arbitrary file here, so a rendered
		// record of the real consent is well within what the slot accepts.
		if (consentPdf != null && consentPdf.length > 0) {
			r.setDoc_ECBCConsentForm(java.util.Base64.getEncoder().encodeToString(consentPdf));
			r.setDoc_ECBCConsentForm_FileName("ECBCConsentForm-" + id + ".pdf");
		}

		return r;
	}

	private void put(NewApplicationRequest r, Doc doc, String slot) {
		switch (slot) {
		case "NID" -> {
			r.setDoc_NID(doc.base64());
			r.setDoc_NID_FileName(doc.fileName());
		}
		case "ProfilePhoto" -> {
			r.setDoc_CustomerProfilePhoto(doc.base64());
			r.setDoc_CustomerProfilePhoto_FileName(doc.fileName());
		}
		case "EmploymentCard" -> {
			r.setDoc_EmploymentCard(doc.base64());
			r.setDoc_EmploymentCard_FileName(doc.fileName());
		}
		case "BankStatement" -> {
			r.setDoc_BankStatement(doc.base64());
			r.setDoc_BankStatement_FileName(doc.fileName());
		}
		default -> throw new IllegalArgumentException("unknown document slot " + slot);
		}
	}

	/**
	 * Our stored ID-type label to Sambat's {@code idCode} from
	 * {@code /idregistration} ({@code N} = National ID, {@code P} = Passport).
	 *
	 * <p>Normalised rather than exact: we have stored "National ID Card" and
	 * "NID" for what they call "National ID". Those are the same document, so
	 * folding them is not a guess — unlike marital "Other", which is a different
	 * claim and is left blank.
	 */
	public String idTypeCode(String label) {
		String n = normaliseIdType(label);
		if (n.isEmpty())
			return "";
		// Stored as the idCode itself since 2026-08-29: pass a known code through.
		for (PdlCodeList row : codeRepo.findByListNameOrderByNameEnAsc("ID_TYPE"))
			if (row.getCode() != null && row.getCode().equalsIgnoreCase(label.trim()))
				return row.getCode();
		for (PdlCodeList row : codeRepo.findByListNameOrderByNameEnAsc("ID_TYPE")) {
			if (normaliseIdType(row.getNameEn()).equals(n))
				return str(row.getCode());
		}
		return "";
	}

	static String normaliseIdType(String s) {
		String n = str(s).trim().toLowerCase(java.util.Locale.ROOT);
		if (n.equals("nid"))
			return "national id";
		return n.replaceAll("\\s*card$", "").replaceAll("\\s+", " ");
	}

	/**
	 * Turns our stored marital label into Sambat's {@code cbcCode} (confirmed
	 * 2026-08-28 as the member this field wants).
	 *
	 * <p>Exact description match against their mirrored list, and blank when
	 * there is none. Our option list offers "Other", which has no counterpart
	 * in theirs — mapping it to their "Unknown" would be us deciding something
	 * about a customer that they did not tell us.
	 */
	public String maritalCode(String label) {
		if (label == null || label.isBlank())
			return "";
		PdlCodeList row = codeRepo.findFirstByListNameAndNameEnIgnoreCase("MARITAL_STATUS", label.trim());
		return row == null ? "" : str(row.getCode());
	}

	/** SBF CIF, or 0 when they have never seen this customer. */
	private long cif(PaydayLoan loan) {
		try {
			String reg = loan.getUser() == null ? null : loan.getUser().getRegistedId();
			return (reg == null || reg.isBlank()) ? 0L : Long.parseLong(reg.trim());
		} catch (NumberFormatException e) {
			return 0L;
		}
	}

	private boolean sameAddress(PdlPersonalInfo pi) {
		return eq(pi.getCorrProvince(), pi.getPermProvince()) && eq(pi.getCorrDistrict(), pi.getPermDistrict())
				&& eq(pi.getCorrCommune(), pi.getPermCommune()) && eq(pi.getCorrVillage(), pi.getPermVillage())
				&& eq(pi.getCorrHouseStreetNo(), pi.getPermHouseStreetNo());
	}

	private static boolean eq(String a, String b) {
		return str(a).equalsIgnoreCase(str(b));
	}

	/** Our profile stores "M"/"F"; anything else goes empty rather than guessed. */
	private static String sex(String gender) {
		String g = str(gender).trim().toUpperCase();
		return (g.startsWith("M") || g.startsWith("F")) ? g.substring(0, 1) : "";
	}

	/** dd/MM/yyyy (how the app stores dates) to LOS's yyyy-MM-dd. */
	static String losDate(String appDate) {
		String s = str(appDate).trim();
		if (s.isEmpty())
			return "";
		try {
			return LocalDate.parse(s, APP_DATE).format(LOS_DATE);
		} catch (Exception ignore) {
			try {
				return LocalDate.parse(s).format(LOS_DATE);
			} catch (Exception ignore2) {
				return "";
			}
		}
	}

	static String losDate(Instant instant) {
		return instant == null ? "" : LocalDate.ofInstant(instant, KH).format(LOS_DATE);
	}

	static int ageFrom(String dob) {
		String iso = losDate(dob);
		if (iso.isEmpty())
			return 0;
		try {
			return Period.between(LocalDate.parse(iso), LocalDate.now(KH)).getYears();
		} catch (Exception e) {
			return 0;
		}
	}

	private static <T> T first(List<T> list) {
		return (list == null || list.isEmpty()) ? null : list.get(0);
	}

	private static String str(String s) {
		return s == null ? "" : s;
	}

	/**
	 * Left-pad an NCDD geo code to its canonical width — province 2, district 4,
	 * commune 6, village 8.
	 *
	 * <p>Sambat's dictionary API returns the codes as integers, so a
	 * single-digit province and its children arrive with the leading zero
	 * dropped (Kampot is {@code 7}, its first district {@code 701}). Their own
	 * loan payload restores it ({@code 07} / {@code 0702} / {@code 070204}), so
	 * we must too — otherwise every address in the nine single-digit provinces
	 * (codes 1-9) is filed against a wrong or non-existent locality. Confirmed
	 * against Sambat's reference payload, 2026-08-30. Blank stays blank.
	 */
	static String pad(String code, int width) {
		String c = str(code).trim();
		if (c.isEmpty() || c.length() >= width)
			return c;
		return "0".repeat(width - c.length()) + c;
	}

	/**
	 * Sambat's payment-channel sheet row for now is fixed ("use only #12" =
	 * BANK / HATTHA BANK PLC, 2026-08-31); per-customer bank mapping comes
	 * later with their sheet-3 list.
	 */
	/** Public so the mapping rule can be pinned by tests. */
	public static String employmentStatusCode(String employmentType) {
		String t = str(employmentType).trim().toLowerCase(java.util.Locale.ROOT);
		if (t.isEmpty())
			return "";
		if (t.startsWith("self"))
			return "1";
		if (t.startsWith("employ"))
			return "2";
		return "3";
	}

	static String zeroIfBlank(String code, int width) {
		return code == null || code.isBlank() ? "0".repeat(width) : code;
	}

	private static String province(String c) {
		return pad(c, 2);
	}

	private static String district(String c) {
		return pad(c, 4);
	}

	private static String commune(String c) {
		return pad(c, 6);
	}

	private static String village(String c) {
		return pad(c, 8);
	}

	/**
	 * These two arrive as text from configuration on purpose: an unset numeric
	 * property is indistinguishable from a deliberate 0, and 0 is a real value
	 * to LOS. {@link LosSubmitConfig#assertConfigured()} has already refused
	 * the submit if either is blank, so this only ever parses a set value.
	 */
	private int intOf(String s) {
		try {
			return Integer.parseInt(str(s).trim());
		} catch (NumberFormatException e) {
			if (lenient)
				return 0;
			throw new LosSubmitException("LOS_NOT_CONFIGURED",
					"Expected a number in LOS configuration but found '" + s + "'");
		}
	}
}
