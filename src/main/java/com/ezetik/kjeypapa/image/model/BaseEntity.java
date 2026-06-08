package com.ezetik.kjeypapa.image.model;

import java.util.Date;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

//import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Data
public abstract class BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", unique = true)
	private int id;

	@CreatedBy
	@JsonIgnore
	private String createdBy;

	// @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", locale = "tr_TR", timezone =
	// "GMT+3")
	@CreatedDate
	@JsonIgnore
	private Date createdDate;

	@LastModifiedBy
	@JsonIgnore
	private String updatedBy;

	// @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", locale = "tr_TR", timezone =
	// "GMT+3")
	@LastModifiedDate
	@JsonIgnore
	private Date updatedDate;

	@Column
	private boolean status = true;
}