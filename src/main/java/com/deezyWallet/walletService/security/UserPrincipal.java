package com.deezyWallet.walletService.security;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import lombok.Builder;
import lombok.Getter;

/**
 * Immutable security principal populated from JWT claims.
 * Injected into controller methods via @AuthenticationPrincipal.
 *
 * Contains only what the Wallet Service needs from the token:
 *  - userId   : to scope operations to the requesting user's wallet
 *  - roles    : to enforce @PreAuthorize rules
 *
 * NOTE: We deliberately do NOT store email, name, or other PII here.
 * The Wallet Service has no business knowing user profile data.
 */
@Getter
@Builder
public class UserPrincipal implements UserDetails {

	private final String       userId;
	private final List<String> roles;

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return roles.stream()
				.map(role -> new SimpleGrantedAuthority("ROLE_" + role))
				.collect(Collectors.toList());
	}

	// ── UserDetails boilerplate (stateless — always true/null) ───────────────
	@Override public String  getPassword()                  { return null;  }
	@Override public String  getUsername()                  { return userId; }
	@Override public boolean isAccountNonExpired()          { return true;  }
	@Override public boolean isAccountNonLocked()           { return true;  }
	@Override public boolean isCredentialsNonExpired()      { return true;  }
	@Override public boolean isEnabled()                    { return true;  }
}
