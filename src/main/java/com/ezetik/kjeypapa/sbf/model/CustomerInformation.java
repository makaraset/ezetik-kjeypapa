package com.ezetik.kjeypapa.sbf.model;

import java.time.Instant;

import com.ezetik.kjeypapa.security.model.GenderEnum;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Data;

@Data
//@Entity
//@Table(name = "sbf_cust_info")
public class CustomerInformation {

//	@Id
	private Long custKeyNum;

	private int raceId;
	private int msId;
	private int ctryId;
	private String name;
	private String khName;
	private String familyNameKh;
	private String firstNameKh;

	@Enumerated(EnumType.STRING)
	private GenderEnum sex;

	private String dob;
	private int regisType;
	private String idNo;
	private String idExpDate;
	private String idIssueDate;
	private int idIssueBy;
	private int addType;
	private String street;
	private int proId;
	private int disId;
	private int comId;
	private int vilId;
	private String city;
	private String state;
	private String country;
	private String zipCode;
	private String address1;
	private String address2;
	private String phoneNumber;
	private String email;
	private String fax;
	private String occupation;
	private int occupationId;
	private String incomeCurrency;
	private Double income;
	private int brId;
	private int empRep;
	private String secType;
	private int applicationType;
	private int busTypeId;
	private Double limitAmount;
	private String committeeId;
	private String cashCardId;
	private Long custInfoDetailId;
	private int numberOfChildren;
	private Instant commenceDate;
	private String employerName;

	private String selfEmploy;

	private String employmentContractType;
	private String resiAddress;
	private String corrAddress;
	private String corrVilId;
	private String workAddress;
	private String workVilId;
	private String employerId;
	private String hphone;
	private String ophone;
	private String fname;
	private String lname;

}
