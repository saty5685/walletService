package com.deezyWallet.walletService.security;

import java.util.List;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.deezyWallet.walletService.config.JwtProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Stateless JWT validator for the Wallet Service (resource server role).
 *
 * Flow:
 *   Request → JwtAuthFilter → JwtTokenValidator.validate()
 *                           → extracts userId, roles
 *                           → builds UserPrincipal
 *                           → SecurityContextHolder.setAuthentication(...)
 *
 * The Wallet Service never calls User Service to validate tokens —
 * it validates the signature locally using the shared secret.
 * This keeps auth fully stateless and avoids a network call per request.
 *
 * For the INTERNAL_SERVICE role:
 *   Other microservices present a JWT with roles=["ROLE_INTERNAL_SERVICE"].
 *   These are issued by those services themselves using the same shared secret.
 *   The Wallet Service treats them identically — just checks the role claim.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenValidator {

	private final JwtProperties jwtProperties;

	public Claims validateAndExtract(String token) {
		try {
			return Jwts.parser()
					.verifyWith(signingKey())
					.build()
					.parseSignedClaims(token)
					.getPayload();
		} catch (ExpiredJwtException ex) {
			log.warn("JWT expired: {}", ex.getMessage());
			throw ex;  // caught by JwtAuthFilter → 401
		} catch (JwtException ex) {
			log.warn("JWT invalid: {}", ex.getMessage());
			throw ex;
		}
	}

	public String extractUserId(Claims claims) {
		return claims.getSubject();   // "sub" = userId (UUID)
	}

	@SuppressWarnings("unchecked")
	public List<String> extractRoles(Claims claims) {
		return (List<String>) claims.get("roles", List.class);
	}

	private SecretKey signingKey() {
		return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.getSecret()));
	}
}
