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
	private LosSubmitConfig config;
	@Autowired
	private PdlCodeListRepository codeRepo;

	/** Cambodia, per Sambat (2026-08-28): nationality and country both take KHM. */
	private static final String KHM = "KHM";

	public NewApplicationParam toParam(PaydayLoan loan) {
		config.assertConfigured();

		int uid = loan.getUser().getId();
		PdlPersonalInfo pi = first(personalRepo.findByUser(uid));
		PdlEmploymentInfo ei = first(employmentRepo.findByUser(uid));
		PdlBankInfo bi = first(bankRepo.findByUser(uid));

		NewApplicationParam param = new NewApplicationParam();
		param.setAppId(config.getAppId());
		// custId is the SBF CIF, or 0 for a customer they have never seen.
		param.setCustId(cif(loan));
		param.setDoneBy(str(loan.getUser().getUsername()));
		param.setNewAppRequest(request(loan, pi, ei, bi));
		return param;
	}

	private NewApplicationRequest request(PaydayLoan loan, PdlPersonalInfo pi, PdlEmploymentInfo ei,
			PdlBankInfo bi) {
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
			r.setCustP_CAddCBProvinceCity(str(pi.getCorrProvinceCode()));
			r.setCustP_CAddCBDistrict(str(pi.getCorrDistrictCode()));
			r.setCustP_CAddCBCommune(str(pi.getCorrCommuneCode()));
			r.setCustP_CAddCBVillage(str(pi.getCorrVillageCode()));

			r.setCustP_PRAddNo(str(pi.getPermHouseStreetNo()));
			r.setCustP_PRAddPhoneNo(str(pi.getMobilePhone()));
			r.setCustP_PRAddCBCountry(KHM);
			r.setCustP_PRAddCBProvinceCity(str(pi.getPermProvinceCode()));
			r.setCustP_PRAddCBDistrict(str(pi.getPermDistrictCode()));
			r.setCustP_PRAddCBCommune(str(pi.getPermCommuneCode()));
			r.setCustP_PRAddCBVillage(str(pi.getPermVillageCode()));
			r.setCustP_PRAddCBCoincide(sameAddress(pi));

			// Place of birth: the form collects province and district only.
			r.setCustP_POBCBCountry(KHM);
			r.setCustP_POBCBProvinceCity(str(pi.getBirthProvinceCode()));
			r.setCustP_POBCBDistrict(str(pi.getBirthDistrictCode()));

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
			r.setCustP_JobBusinessStartDate(losDate(ei.getEmploymentStartDate()));
			r.setCustP_EmpAddNo("");
			r.setCustP_EmpAddCBCountry(KHM);
			r.setCustP_EmpAddCBProvinceCity(str(ei.getWorkProvinceCode()));
			r.setCustP_EmpAddCBDistrict(str(ei.getWorkDistrictCode()));
			r.setCustP_EmpAddCBCommune(str(ei.getWorkCommuneCode()));
			r.setCustP_EmpAddCBVillage(str(ei.getWorkVillageCode()));
			r.setCustP_CBEmploymentType(str(config.getEmploymentType()));
			r.setCustP_CBEmploymentContractType(str(config.getEmploymentContractType()));

			if (ei.getMonthlyIncome() != null && ei.getMonthlyIncome() > 0) {
				MonthlyIncomeItem income = new MonthlyIncomeItem();
				// IncomeType is a CBC code we do not hold; the amount and
				// currency are real, so send the row and let LOS name what it
				// still needs.
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

		// ----- disbursement account -----
		if (bi != null) {
			r.setPC_AccountNum(str(bi.getAccountNo()));
			r.setPC_PaymentChannelAccountName(str(bi.getAccountName()));
			r.setPC_PaymentChannel(str(config.getPaymentChannel()));
		}

		// ----- documents -----
		int id = loan.getId() == null ? 0 : loan.getId();
		if (pi != null) {
			// LOS has ONE NID slot and we hold two sides; the front carries the
			// printed fields, so it is the one that goes. Sending the back
			// instead of, or merged with, the front is a question for Sambat.
			put(r, docs.build(pi.getNidFrontFileRef(), "NID", id), "NID");
			put(r, docs.build(pi.getProfilePhotoFileRef(), "ProfilePhoto", id), "ProfilePhoto");
		}
		if (ei != null)
			put(r, docs.build(ei.getEmploymentCardFileRef(), "EmploymentCard", id), "EmploymentCard");
		if (bi != null)
			put(r, docs.build(bi.getBankStatementFileRef(), "BankStatement", id), "BankStatement");
		// Doc_ECBCConsentForm: we hold a consent RECORD, not a file. Left empty
		// until Sambat says what artefact they expect.

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
	 * These two arrive as text from configuration on purpose: an unset numeric
	 * property is indistinguishable from a deliberate 0, and 0 is a real value
	 * to LOS. {@link LosSubmitConfig#assertConfigured()} has already refused
	 * the submit if either is blank, so this only ever parses a set value.
	 */
	private static int intOf(String s) {
		try {
			return Integer.parseInt(str(s).trim());
		} catch (NumberFormatException e) {
			throw new LosSubmitException("LOS_NOT_CONFIGURED",
					"Expected a number in LOS configuration but found '" + s + "'");
		}
	}
}
