package com.deezyWallet.walletService.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Data;

/**
 * Binds jwt.* from application.yml into a typed bean.
 * The Wallet Service is a RESOURCE SERVER — it only VALIDATES JWTs,
 * never issues them. Issuance is exclusively the User Service's responsibility.
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
	private String secret;    // HS512 shared secret — inject from Vault / env
	private String issuer;    // expected "iss" claim value
}
