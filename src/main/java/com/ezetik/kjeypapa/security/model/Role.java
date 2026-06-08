package com.ezetik.kjeypapa.security.model;

import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "ez_user_role")
public class Role {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "name", unique = true, nullable = false, length = 100)
	private String name;

	@Column(name = "description", nullable = false, length = 500)
	private String description;

	@Column(name = "is_disabled", nullable = false)
	private Boolean disabled;

	@ManyToMany(fetch = FetchType.EAGER)
	@JoinTable(name = "ez_role_permission", joinColumns = @JoinColumn(name = "role_id"), inverseJoinColumns = @JoinColumn(name = "permission_id"))
	private List<Permission> permissions;

	public boolean hasPermissionTo(final String permissionCode) {
		boolean match = false;
		for (final Permission permission : this.permissions) {
			if (permission.hasCode(permissionCode)) {
				match = true;
				break;
			}
		}
		return match;
	}

	public boolean updatePermission(final Permission permission, final boolean isSelected) {
		boolean changed = false;
		if (isSelected) {
			changed = addPermission(permission);
		} else {
			changed = removePermission(permission);
		}

		return changed;
	}

	private boolean addPermission(final Permission permission) {
		return this.permissions.add(permission);
	}

	private boolean removePermission(final Permission permission) {
		return this.permissions.remove(permission);
	}

}
