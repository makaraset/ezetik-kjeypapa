package com.ezetik.kjeypapa.security.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.ezetik.kjeypapa.security.service.CustomAuthenticationProvider;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

	@Autowired
	private JwtAuthenticationFilter jwtAuthFilter;

	@Autowired
	private AuthenticationProvider authenticationProvider;

	@Autowired
	private CustomAuthenticationProvider customAuthenticationProvider;

	// @formatter:off
	
	private static final String[] WHITE_LIST_URLS = 
		{ 
		  "/v3/api-docs/**", 
		  "/v2/api-docs/**", 
		  "/swagger-resources/**",
		  "/swagger-ui/**",
		  "/api/public/**",
		  "/api/v1/auth/**",
		  // Sambat LOS server-to-server webhooks (no customer JWT).
		  // TODO: replace whitelist with LOS signature verification once the
		  // contract is delivered (BRS Appendix). See LosWebhookController.
		  "/api/v1/pdl/los/**",
		  // Pre-login PDL account request (G7): the caller has no account yet.
		  "/api/v1/pdl/account-request/**",
		  "/api/v1/pdl/ocr-nid"
		 };

	@Bean
	@Primary
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(WHITE_LIST_URLS).permitAll()
                        //.requestMatchers("/api/v1/file/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_CUSTOMER")
                        .anyRequest().authenticated())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
	 
	@Autowired
	public void bindAuthenticationProvider(AuthenticationManagerBuilder authenticationManagerBuilder) {
		authenticationManagerBuilder.authenticationProvider(customAuthenticationProvider);
	}
	
	@Bean
	AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
		return config.getAuthenticationManager();
	}
  
  
  //@formatter:on
}
