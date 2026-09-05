package com.ezetik.kjeypapa.pdl.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One entry of a Sambat "selection dictionary" — occupation, marital status,
 * nationality, country, ID type, ID issuer, business activity.
 *
 * <p>Same rationale as {@link PdlGeoUnit}: these are THEIR codes, mirrored so
 * the app can offer them and the LOS submit can send them, instead of us
 * matching our own option lists against theirs by description.
 */
@Data
@Entity
@Table(name = "pdl_code_list", indexes = {
		@Index(name = "idx_code_list_name", columnList = "list_name") })
@AllArgsConstructor
@NoArgsConstructor
public class PdlCodeList {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	/** OCCUPATION / MARITAL_STATUS / NATIONALITY / COUNTRY / ID_TYPE / ID_ISSUER / BUSINESS_ACTIVITY. */
	@Column(name = "list_name", nullable = false, length = 32)
	private String listName;

	@Column(nullable = false, length = 32)
	private String code;

	@Column(length = 256)
	private String nameEn;

	@Column(length = 256)
	private String nameKh;

	public PdlCodeList(String listName, String code, String nameEn, String nameKh) {
		this.listName = listName;
		this.code = code;
		this.nameEn = nameEn;
		this.nameKh = nameKh;
	}
}
