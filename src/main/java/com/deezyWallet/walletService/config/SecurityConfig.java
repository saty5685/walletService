package com.deezyWallet.walletService.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.deezyWallet.walletService.security.CustomAccessDeniedHandler;
import com.deezyWallet.walletService.security.CustomAuthEntryPoint;
import com.deezyWallet.walletService.security.JwtAuthFilter;

import lombok.RequiredArgsConstructor;

import static com.deezyWallet.walletService.constants.WalletConstants.ADMIN_V1_WALLETS;
import static com.deezyWallet.walletService.constants.WalletConstants.API_V1_WALLETS;
import static com.deezyWallet.walletService.constants.WalletConstants.INTERNAL_V1_WALLETS;

/**
 * Security configuration for the Wallet Service.
 *
 * Architecture role: RESOURCE SERVER
 *   - Does NOT issue tokens (that's User Service).
 *   - Validates tokens from every request via JwtAuthFilter.
 *   - Enforces Role-Based Access Control (RBAC) at both route and method level.
 *
 * Auth layers:
 *   Layer 1 — Route-level:  configured here via authorizeHttpRequests()
 *   Layer 2 — Method-level: @PreAuthorize("hasRole(...)") on controller methods
 *   Layer 3 — Data-level:   WalletService.validateOwnership() — user can only
 *                           access their own wallet, not others'
 *
 * Role hierarchy:
 *   ROLE_USER             → can access /api/v1/wallets/me/**
 *   ROLE_MERCHANT         → same as USER + merchant-specific endpoints
 *   ROLE_INTERNAL_SERVICE → can access /internal/v1/wallets/**
 *   ROLE_ADMIN            → can access /api/v1/admin/wallets/**
 *
 * NOTE: There is NO PasswordEncoder bean here because the Wallet Service
 * never handles passwords. Only the User Service needs BCryptPasswordEncoder.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)  // enables @PreAuthorize on methods
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtAuthFilter            jwtAuthFilter;
	private final CustomAuthEntryPoint     authEntryPoint;
	private final CustomAccessDeniedHandler accessDeniedHandler;

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		return http
				// ── Disable CSRF — stateless REST API, no sessions ────────────────
				.csrf(csrf -> csrf.disable())

				// ── Stateless session — JWT carries all state ─────────────────────
				.sessionManagement(session ->
						session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

				// ── Custom error handlers ─────────────────────────────────────────
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint(authEntryPoint)
						.accessDeniedHandler(accessDeniedHandler))

				// ── Route-level authorization ─────────────────────────────────────
				.authorizeHttpRequests(auth -> auth

						// Public — health check, Swagger
						.requestMatchers(
								"/actuator/health",
								"/actuator/info",
								"/v3/api-docs/**",
								"/swagger-ui/**"
						).permitAll()

						// Internal service-to-service — requires INTERNAL_SERVICE role JWT
						.requestMatchers(INTERNAL_V1_WALLETS + "/**")
						.hasRole("INTERNAL_SERVICE")

						// Admin routes
						.requestMatchers(ADMIN_V1_WALLETS + "/**")
						.hasRole("ADMIN")

						// All other /api/v1/** routes require at minimum USER role
						// Fine-grained method-level @PreAuthorize handles the rest
						.requestMatchers(API_V1_WALLETS + "/**")
						.hasAnyRole("USER", "MERCHANT", "ADMIN")

						// Deny everything else by default
						.anyRequest().authenticated()
				)

				// ── Inject JWT filter before Spring's default auth filter ─────────
				.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

				.build();
	}
}

