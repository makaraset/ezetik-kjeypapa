package com.ezetik.kjeypapa.security.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ez_user")
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class User implements UserDetails {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "first_name", length = 100, nullable = false)
	private String firstname;

	@Column(name = "last_name", length = 100, nullable = true)
	private String lastname;

	@Column(nullable = false, length = 100, unique = true)
	private String username;

	@Column(length = 60, nullable = false)
	@JsonProperty(access = Access.WRITE_ONLY)
	private String password;

	@Column(length = 100)
	private String email;

	@Column(length = 100)
	private String registedId;

//	@Column(length = 100, nullable = false)
	private String phoneNumber;

	@Enumerated(EnumType.STRING)
	private GenderEnum gender;

//	@Column(nullable = false)
	private String dateOfBirth;

	@Column(name = "nonexpired", nullable = false)
	private boolean accountNonExpired;

	@Column(name = "nonlocked", nullable = false)
	private boolean accountNonLocked;

	@Column(name = "nonexpired_credentials", nullable = false)
	private boolean credentialsNonExpired;

	@Column(name = "enabled", nullable = false)
	private boolean enabled;

	@Column(name = "firsttime_login_remaining", nullable = false)
	private boolean firstTimeLoginRemaining;

	@Column(name = "is_deleted", nullable = false)
	private boolean deleted;

	@Column(name = "last_time_password_updated")
	private LocalDate lastTimePasswordUpdated;

	@Column(name = "password_never_expires", nullable = false)
	private boolean passwordNeverExpires;

	private String profilePhoto;

	@JsonProperty(access = Access.WRITE_ONLY)
	String fcmToken;

	@ManyToMany
	@JoinTable(name = "ez_user_role_join", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
	private List<Role> roles;

	@Override
	public Collection<GrantedAuthority> getAuthorities() {
		return populateGrantedAuthorities();
	}

	private List<GrantedAuthority> populateGrantedAuthorities() {
		final List<GrantedAuthority> grantedAuthorities = new ArrayList<>();
		for (final Role role : this.roles) {
			final Collection<Permission> permissions = role.getPermissions();
			for (final Permission permission : permissions) {
				grantedAuthorities.add(new SimpleGrantedAuthority(permission.getCode()));
			}
		}
		return grantedAuthorities;
	}

}
