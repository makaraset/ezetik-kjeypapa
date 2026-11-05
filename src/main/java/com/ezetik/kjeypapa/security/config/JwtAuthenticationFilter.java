package com.ezetik.kjeypapa.security.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Security;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.ezetik.kjeypapa.security.service.JwtService;

@lombok.extern.slf4j.Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;
  private final UserDetailsService userDetailsService;

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain
  ) throws ServletException, IOException {
    final String authHeader = request.getHeader("Authorization");
    final String jwt;
    final String userEmail;
    if (authHeader == null ||!authHeader.startsWith("Bearer ")) {
      filterChain.doFilter(request, response);
      return;
    }
    jwt = authHeader.substring(7);
    try {
      userEmail = jwtService.extractUsername(jwt);
    } catch (Exception e) {
      // Expired, malformed, or signed with a key we no longer use. That means
      // the caller is NOT AUTHENTICATED — it does not mean the request is
      // invalid, and it must not throw out of the filter.
      //
      // This filter runs on every request, whitelisted paths included, so an
      // uncaught exception here broke the whole unauthenticated signup flow
      // for anyone whose phone still held an old token: the NID scan and the
      // account-request probe both answered 403, which reads as a permissions
      // problem and was really a stale value in the keychain. Found on a real
      // device against a token six days expired, 2026-09-30.
      //
      // Carry on unauthenticated. Whitelisted endpoints serve the request;
      // protected ones are refused downstream with a proper 403 from the
      // authorization layer rather than a 500 raised inside a filter.
      log.debug("Ignoring unusable bearer token: {}", e.getClass().getSimpleName());
      filterChain.doFilter(request, response);
      return;
    }
    if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
      UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);
      if (jwtService.isTokenValid(jwt, userDetails)) {
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
            userDetails,
            null,
            userDetails.getAuthorities()
        );
        authToken.setDetails(
            new WebAuthenticationDetailsSource().buildDetails(request)
        );
        SecurityContextHolder.getContext().setAuthentication(authToken);
      }
    }
    filterChain.doFilter(request, response);
  }
}
