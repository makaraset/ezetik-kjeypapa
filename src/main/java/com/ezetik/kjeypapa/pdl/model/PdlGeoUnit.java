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
 * One Cambodian administrative unit as SAMBAT defines it — their code is the
 * value we store on a customer and send to LOS.
 *
 * <p>Sambat confirmed (2026-08-28) that these ids are the NCDD codes the loan
 * payload expects: province {@code 12}, district {@code 1214}, commune
 * {@code 121402}, village {@code 12140204}. Taking the code from them rather
 * than deriving it ourselves is the whole point — a locally-derived code that
 * disagrees with theirs would file a credit application against the wrong
 * locality, and nothing would bounce.
 *
 * <p>Deliberately NOT extending {@code UserDateAudit}: rows here are a mirror
 * of Sambat's master list, not something a user creates.
 */
@Data
@Entity
@Table(name = "pdl_geo_unit", indexes = {
		@Index(name = "idx_geo_level_parent", columnList = "level,parent_code"),
		@Index(name = "idx_geo_level_code", columnList = "level,code") })
@AllArgsConstructor
@NoArgsConstructor
public class PdlGeoUnit {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	/** PROVINCE / DISTRICT / COMMUNE / VILLAGE. */
	@Column(nullable = false, length = 16)
	private String level;

	/** Sambat's code for this unit (the NCDD code). */
	@Column(nullable = false, length = 16)
	private String code;

	/** Code of the parent unit; null for a province. */
	@Column(name = "parent_code", length = 16)
	private String parentCode;

	@Column(length = 128)
	private String nameEn;

	@Column(length = 128)
	private String nameKh;

	public PdlGeoUnit(String level, String code, String parentCode, String nameEn, String nameKh) {
		this.level = level;
		this.code = code;
		this.parentCode = parentCode;
		this.nameEn = nameEn;
		this.nameKh = nameKh;
	}
}
