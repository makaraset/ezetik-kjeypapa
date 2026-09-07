package com.ezetik.kjeypapa.notification.config;

import com.ezetik.kjeypapa.security.audit.UserDateAudit;
import com.ezetik.kjeypapa.security.model.User;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "ez_notification")
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class NotificationModel extends UserDateAudit {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;
	private String title;
	private String body;
	private String data;
	private boolean isRead;
	private boolean isDeleted;

	/**
	 * The recipient. <b>Many</b> notifications to one user.
	 *
	 * <p>This was {@code @OneToOne}, which made Hibernate generate
	 * {@code UNIQUE (user_id)} on the table — a user could hold exactly one
	 * notification, ever. The first one saved and every one after it failed on
	 * the constraint, marked the surrounding transaction rollback-only, and took
	 * the whole request down with it. For an inbound LOS webhook that meant a
	 * loan status update from Sambat was discarded because a *notification*
	 * could not be written, and the caller received an opaque 403.
	 *
	 * <p>It stayed hidden because {@code notify()} skips the database write when
	 * a user has no FCM token, so most test users never stored a first row.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "FK_USER_NOTIFICATION"))
	@JsonIgnore
	private User user;

}
