package com.ezetik.kjeypapa.security.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.ezetik.kjeypapa.security.model.Permission;
import com.ezetik.kjeypapa.security.model.Role;
import com.ezetik.kjeypapa.security.model.User;
import com.ezetik.kjeypapa.security.repository.UserRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

	@Autowired
	private UserRepository userRepository;

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		User user = userRepository.findByUsername(username);

		if (user == null || !user.isEnabled() || user.isDeleted()) {
			throw new UsernameNotFoundException("No User Found");
		}

		return user;

	}

	public Collection<GrantedAuthority> getAuthorities(List<Role> roles) {
		return populateGrantedAuthorities(roles);
	}

	private List<GrantedAuthority> populateGrantedAuthorities(List<Role> roles) {
		final List<GrantedAuthority> grantedAuthorities = new ArrayList<>();
		for (final Role role : roles) {
			final Collection<Permission> permissions = role.getPermissions();
			for (final Permission permission : permissions) {
				grantedAuthorities.add(new SimpleGrantedAuthority(permission.getCode()));
			}
		}
		return grantedAuthorities;
	}

}
