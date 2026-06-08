package com.ezetik.kjeypapa.security.audit;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;
import lombok.EqualsAndHashCode;

@MappedSuperclass
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class UserDateAudit extends DateAudit {

	private static final long serialVersionUID = 1L;

	@CreatedBy
	@JsonProperty(access = JsonProperty.Access.READ_ONLY)
	@Column(updatable = false, nullable = false)
	@JsonInclude(Include.NON_NULL)
	private String createdBy;

	@LastModifiedBy
	@JsonProperty(access = JsonProperty.Access.READ_ONLY)
	@Column(insertable = false)
	@JsonInclude(Include.NON_NULL)
	private String updatedBy;

}