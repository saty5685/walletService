package com.deezyWallet.walletService.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Intercepts every HTTP request, extracts the Bearer token, validates it,
 * and populates the SecurityContext so Spring Security's @PreAuthorize works.
 *
 * Executed ONCE per request (extends OncePerRequestFilter).
 *
 * Filter order in SecurityFilterChain:
 *   ... → JwtAuthFilter → UsernamePasswordAuthenticationFilter → ...
 *
 * What happens here:
 *  1. Extract "Authorization: Bearer <token>" header
 *  2. If absent → continue chain (public endpoints will pass; protected will fail at authorization)
 *  3. Validate signature + expiry via JwtTokenValidator
 *  4. Build UserPrincipal from claims
 *  5. Set authentication in SecurityContextHolder
 *  6. Continue filter chain
 *
 * On any JWT error → clear context + return 401 immediately.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

	private final JwtTokenValidator tokenValidator;

	@Override
	protected void doFilterInternal(HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain)
			throws ServletException, IOException {
		String requestURI = request.getRequestURI();
		log.info("Request URI: {}: {}", request.getMethod(), request.getRequestURI());
		String token = extractToken(request);

		if (token != null) {
			try {
				Claims claims = tokenValidator.validateAndExtract(token);

				String       userId = tokenValidator.extractUserId(claims);
				List<String> roles  = tokenValidator.extractRoles(claims);

				UserPrincipal principal = UserPrincipal.builder()
						.userId(userId)
						.roles(roles)
						.build();

				// Wrap in Spring Security's auth token (credentials = null — stateless)
				UsernamePasswordAuthenticationToken auth =
						new UsernamePasswordAuthenticationToken(
								principal, null, principal.getAuthorities());

				SecurityContextHolder.getContext().setAuthentication(auth);
				log.debug("Authenticated user: {} with roles: {}", userId, roles);

			} catch (JwtException ex) {
				SecurityContextHolder.clearContext();
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				response.setContentType("application/json");
				response.getWriter().write(
						"""
						{"code":"WALLET_UNAUTHORIZED","message":"Invalid or expired token"}
						""");
				return;  // stop filter chain — don't process the request
			}
		}

		filterChain.doFilter(request, response);
	}

	/**
	 * Extracts the raw JWT from "Authorization: Bearer <token>".
	 * Returns null if header is missing or malformed.
	 */
	private String extractToken(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
			return header.substring(7);
		}
		return null;
	}
}
