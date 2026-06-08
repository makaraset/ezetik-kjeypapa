package com.ezetik.kjeypapa.security.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "ez_user_permission")
public class Permission {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "grouping", nullable = false, length = 45)
	private String grouping;

	@Column(name = "code", unique = true, nullable = false, length = 100)
	private String code;

	@Column(name = "entity_name", nullable = true, length = 100)
	private String entityName;

	@Column(name = "action_name", nullable = true, length = 100)
	private String actionName;

	@Column(name = "can_maker_checker", nullable = false)
	private boolean canMakerChecker;

	public boolean hasCode(final String checkCode) {
		return this.code.equalsIgnoreCase(checkCode);
	}
}
