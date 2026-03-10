package com.deezyWallet.walletService.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.deezyWallet.walletService.constants.WalletConstants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages the Redis balance cache and idempotency processing locks.
 *
 * TWO RESPONSIBILITIES (both are Redis operations — kept together intentionally):
 *
 * 1. BALANCE CACHE
 *    Caches wallet balance in Redis with 30s TTL.
 *    Key: "wallet:balance:{walletId}"
 *    Value: BigDecimal as plain string — e.g. "12345.6789"
 *
 *    Cache-aside pattern (READ):
 *      check Redis → HIT:  return cached value
 *                  → MISS: read DB, warm cache, return value
 *
 *    On WRITE: always write DB first inside @Transactional, then EVICT cache.
 *    WHY EVICT not UPDATE:
 *      If we update cache but the DB transaction rolls back, the cache holds
 *      a value that was never committed. Eviction forces the next read to DB —
 *      which always holds the true committed state.
 *
 * 2. PROCESSING LOCK (fast-path idempotency guard)
 *    Key: "wallet:lock:{idempotencyKey}"
 *    TTL: 10s auto-expires even if service crashes
 *
 *    Redis SETNX (atomic) — fast dedup check BEFORE hitting DB.
 *    DB UNIQUE constraint on idempotency_key is the durable safety net.
 *    This is the speed optimisation — avoids DB roundtrip for obvious dupes.
 *
 * FAILURE MODE:
 *    If Redis is down, every method fails gracefully and falls through to DB.
 *    try/catch wraps EVERY Redis call — Redis outage must never cause 500s.
 *    Service degrades in performance, never in correctness.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BalanceCacheService {

	private final RedisTemplate<String, String> redisTemplate;

	// ── Balance cache ─────────────────────────────────────────────────────────

	/**
	 * Read cached balance. Returns empty on miss OR Redis failure.
	 * Caller falls back to DB on empty.
	 */
	public Optional<BigDecimal> getCachedBalance(String walletId) {
		try {
			String cached = redisTemplate.opsForValue().get(balanceKey(walletId));
			if (cached == null) {
				log.debug("Balance cache MISS for wallet={}", walletId);
				return Optional.empty();
			}
			log.debug("Balance cache HIT for wallet={}", walletId);
			return Optional.of(new BigDecimal(cached).setScale(4, RoundingMode.HALF_EVEN));
		} catch (Exception ex) {
			log.warn("Redis unavailable — balance GET wallet={} error={}", walletId, ex.getMessage());
			return Optional.empty();
		}
	}

	/**
	 * Warm the balance cache after a DB read (cache-miss path).
	 * toPlainString() — never scientific notation in Redis.
	 */
	public void cacheBalance(String walletId, BigDecimal balance) {
		try {
			String value = balance.setScale(4, RoundingMode.HALF_EVEN).toPlainString();
			redisTemplate.opsForValue().set(
					balanceKey(walletId), value,
					WalletConstants.BALANCE_CACHE_TTL_SECONDS, TimeUnit.SECONDS
			);
			log.debug("Balance cached wallet={} value={}", walletId, value);
		} catch (Exception ex) {
			log.warn("Redis unavailable — balance SET wallet={} error={}", walletId, ex.getMessage());
		}
	}

	/**
	 * Evict balance cache after a successful debit or credit.
	 *
	 * MUST be called after the @Transactional block commits — not inside it.
	 * If called inside and the transaction rolls back, the eviction is harmless
	 * (next read goes to DB, which has the correct pre-transaction balance).
	 *
	 * Never call this before committing — evicting then rolling back leaves
	 * a cold cache that will warm from a DB value that wasn't committed yet.
	 */
	public void evictBalance(String walletId) {
		try {
			redisTemplate.delete(balanceKey(walletId));
			log.debug("Balance cache EVICTED wallet={}", walletId);
		} catch (Exception ex) {
			log.warn("Redis unavailable — balance EVICT wallet={} error={}", walletId, ex.getMessage());
		}
	}

	// ── Processing lock (idempotency fast-path) ───────────────────────────────

	/**
	 * Attempt to acquire idempotency processing lock via Redis SETNX.
	 *
	 * Returns true  → lock acquired, safe to proceed with processing
	 * Returns false → lock already held, this is a duplicate — skip
	 *
	 * On Redis failure: returns true (fail OPEN).
	 * Reasoning: DB UNIQUE constraint on idempotency_key is the real guard.
	 * Failing closed here would block legitimate retries when Redis is down.
	 */
	public boolean acquireProcessingLock(String idempotencyKey) {
		try {
			Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
					lockKey(idempotencyKey), "1",
					WalletConstants.PROCESSING_LOCK_TTL_SECONDS, TimeUnit.SECONDS
			);
			boolean result = Boolean.TRUE.equals(acquired);
			log.debug("Processing lock {} for idempotencyKey={}", result ? "ACQUIRED" : "REJECTED", idempotencyKey);
			return result;
		} catch (Exception ex) {
			log.warn("Redis unavailable — processing lock idempotencyKey={} error={}. Failing open.",
					idempotencyKey, ex.getMessage());
			return true; // fail open — DB dedup is the safety net
		}
	}

	/**
	 * Release the processing lock after the WalletTransaction is committed to DB.
	 *
	 * NOT called on failure — let the TTL (10s) expire it naturally.
	 * If released on failure, a retry within 10s re-acquires the lock and
	 * re-processes — which is the correct behaviour for a genuine first-attempt failure.
	 */
	public void releaseProcessingLock(String idempotencyKey) {
		try {
			redisTemplate.delete(lockKey(idempotencyKey));
			log.debug("Processing lock RELEASED idempotencyKey={}", idempotencyKey);
		} catch (Exception ex) {
			log.warn("Redis unavailable — lock release idempotencyKey={}", idempotencyKey);
		}
	}

	// ── User → WalletId mapping cache ────────────────────────────────────────

	/**
	 * Cache the immutable userId → walletId mapping.
	 * TTL: 5 minutes — longer than balance cache since this never changes.
	 * Hot path: every authenticated request needs this mapping.
	 */
	public void cacheUserWalletId(String userId, String walletId) {
		try {
			redisTemplate.opsForValue().set(
					userWalletKey(userId), walletId,
					WalletConstants.USER_WALLET_CACHE_TTL_SECONDS, TimeUnit.SECONDS
			);
		} catch (Exception ex) {
			log.warn("Redis unavailable — userWallet SET userId={}", userId);
		}
	}

	/** Get cached walletId for userId. Empty on miss or Redis failure. */
	public Optional<String> getCachedWalletId(String userId) {
		try {
			return Optional.ofNullable(redisTemplate.opsForValue().get(userWalletKey(userId)));
		} catch (Exception ex) {
			log.warn("Redis unavailable — userWallet GET userId={}", userId);
			return Optional.empty();
		}
	}

	// ── Key builders ──────────────────────────────────────────────────────────

	private String balanceKey(String walletId)      { return WalletConstants.REDIS_BALANCE_PREFIX     + walletId;      }
	private String lockKey(String idempotencyKey)   { return WalletConstants.REDIS_LOCK_PREFIX         + idempotencyKey; }
	private String userWalletKey(String userId)     { return WalletConstants.REDIS_USER_WALLET_PREFIX  + userId;        }
}