package com.ezetik.kjeypapa.sbf.model;

import lombok.Data;

@Data
public class Address {
	private int id;
	private int provinceId;
	private String province;
	private int districtId;
	private String district;
	private int communeId;
	private String commune;
	private String village;
	private String khProvinace;
	private String khDistrict;
	private String khCommnune;
	private String khVillage;
	private String khFullAddress;
}
