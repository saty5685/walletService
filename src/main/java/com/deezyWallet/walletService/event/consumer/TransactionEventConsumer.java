package com.deezyWallet.walletService.event.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.deezyWallet.walletService.constants.WalletConstants;
import com.deezyWallet.walletService.dto.request.BlockFundsRequest;
import com.deezyWallet.walletService.dto.request.CreditRequest;
import com.deezyWallet.walletService.dto.request.DebitRequest;
import com.deezyWallet.walletService.entity.WalletTransaction;
import com.deezyWallet.walletService.enums.WalletCommandTypeEnum;
import com.deezyWallet.walletService.event.EventFactory;
import com.deezyWallet.walletService.event.inbound.WalletCommandEvent;
import com.deezyWallet.walletService.event.producer.WalletEventProducer;
import com.deezyWallet.walletService.exception.DuplicateOperationException;
import com.deezyWallet.walletService.exception.InsufficientBalanceException;
import com.deezyWallet.walletService.exception.WalletBaseException;
import com.deezyWallet.walletService.exception.WalletFrozenException;
import com.deezyWallet.walletService.exception.WalletLimitExceededException;
import com.deezyWallet.walletService.service.WalletService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Consumes "wallet.commands" topic — the Saga command channel.
 * Published by Transaction Service to command wallet operations.
 *
 * COMMANDS HANDLED:
 *   DEBIT_CMD   → WalletService.debit()
 *   CREDIT_CMD  → WalletService.credit()
 *   BLOCK_CMD   → WalletService.blockFunds()
 *   UNBLOCK_CMD → WalletService.unblockFunds()
 *
 * SAGA FAILURE HANDLING:
 *   On business exception (InsufficientBalance, WalletFrozen, LimitExceeded):
 *     1. ACK the command (remove from queue — retry won't fix it)
 *     2. Publish WALLET_DEBIT_FAILED event → Transaction Service triggers compensation
 *     This is CRITICAL — without ACK, Kafka redelivers indefinitely.
 *     Without the failure event, Transaction Service waits forever (Saga hangs).
 *
 *   On DuplicateOperationException:
 *     1. ACK the command (already processed — idempotent success)
 *     2. Do NOT publish failure event — the original success event was already published
 *
 *   On system exception (DB down, Redis timeout):
 *     1. DO NOT ACK — Kafka redelivers with exponential backoff
 *     2. After max retries → DLQ for manual inspection
 *
 * ACK MATRIX (summary):
 *   DEBIT_CMD success          → ACK (WALLET_DEBITED published by WalletService)
 *   DEBIT_CMD business failure → ACK + publish WALLET_DEBIT_FAILED
 *   DEBIT_CMD duplicate        → ACK (already processed)
 *   DEBIT_CMD system failure   → NO ACK → retry → DLQ
 *   CREDIT_CMD success         → ACK (WALLET_CREDITED published by WalletService)
 *   CREDIT_CMD business failure→ ACK + publish WALLET_CREDIT_FAILED (future)
 *   CREDIT_CMD duplicate       → ACK
 *   BLOCK/UNBLOCK success      → ACK
 *   BLOCK/UNBLOCK failure      → ACK + publish failure event
 *
 * ORDERING GUARANTEE:
 *   Kafka topic "wallet.commands" is keyed by walletId (set by Transaction Service).
 *   All commands for the same wallet arrive at the same partition in order.
 *   With concurrency=3, each partition is processed by one thread — no
 *   out-of-order processing per wallet.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventConsumer {

	private final WalletService       walletService;
	private final WalletEventProducer eventProducer;
	private final EventFactory        eventFactory;

	@KafkaListener(
			topics           = WalletConstants.TOPIC_WALLET_COMMANDS,
			groupId          = WalletConstants.GROUP_WALLET_SERVICE,
			containerFactory = "kafkaListenerContainerFactory"
	)
	public void consume(ConsumerRecord<String, Object> record, Acknowledgment ack) {
		log.info("TransactionEventConsumer received: topic={} partition={} offset={} key={}",
				record.topic(), record.partition(), record.offset(), record.key());

		WalletCommandEvent cmd = deserialise(record.value());

		if (cmd == null) {
			log.warn("Null WalletCommandEvent at offset={} — ACKing to skip", record.offset());
			ack.acknowledge();
			return;
		}

		if (!validateCommand(cmd, record.offset())) {
			// Malformed command — ACK and skip, publish to DLQ manually
			ack.acknowledge();
			return;
		}

		log.info("Processing command: type={} walletId={} amount={} idempotencyKey={} txRef={}",
				cmd.getCommandType(), cmd.getWalletId(),
				cmd.getAmount(), cmd.getIdempotencyKey(), cmd.getTransactionRef());

		try {
			switch (cmd.getCommandType()) {
				case DEBIT_CMD   -> handleDebit(cmd);
				case CREDIT_CMD  -> handleCredit(cmd);
				case BLOCK_CMD   -> handleBlock(cmd);
				case UNBLOCK_CMD -> handleUnblock(cmd);
				default -> log.warn("Unknown command type={} — skipping", cmd.getCommandType());
			}

			// All paths above either succeed or throw.
			// Success: WalletService already published the success event.
			ack.acknowledge();
			log.info("Command ACKed: type={} walletId={} offset={}",
					cmd.getCommandType(), cmd.getWalletId(), record.offset());

		} catch (DuplicateOperationException ex) {
			// Already processed — idempotent success. ACK, do not publish failure.
			log.info("Duplicate command (idempotent): idempotencyKey={} offset={}",
					ex.getIdempotencyKey(), record.offset());
			ack.acknowledge();

		} catch (InsufficientBalanceException | WalletFrozenException |
				 WalletLimitExceededException ex) {
			// Business failures on DEBIT path — Saga compensation required.
			// ACK to remove from queue (retrying won't fix these).
			// Publish WALLET_DEBIT_FAILED → Transaction Service compensates.
			log.warn("Business failure on DEBIT_CMD: walletId={} error={}: {}",
					cmd.getWalletId(), ex.getErrorCode(), ex.getMessage());

			publishDebitFailedEvent(cmd, ex);
			ack.acknowledge(); // CRITICAL: must ACK or Kafka redelivers forever

		} catch (WalletBaseException ex) {
			// Other business exceptions (WalletNotFound on CREDIT, etc.)
			// ACK to prevent infinite redelivery of an unfixable command.
			log.warn("WalletBaseException on command type={} walletId={} error={}: {}",
					cmd.getCommandType(), cmd.getWalletId(), ex.getErrorCode(), ex.getMessage());
			publishDebitFailedEvent(cmd, ex); // notify Transaction Service
			ack.acknowledge();

		} catch (Exception ex) {
			// System exception — DO NOT ACK.
			// KafkaConsumerConfig retries with exponential backoff.
			// After max retries → DLQ.
			log.error("System exception processing command type={} walletId={} offset={}: {}",
					cmd.getCommandType(), cmd.getWalletId(), record.offset(), ex.getMessage(), ex);
			throw ex; // rethrow — Spring Kafka error handler manages retry/DLQ
		}
	}

	// ── Command handlers ──────────────────────────────────────────────────────

	/**
	 * Handle DEBIT_CMD.
	 * WalletService.debit() handles idempotency, locking, limit check,
	 * balance mutation, and publishing WALLET_DEBITED event internally.
	 */
	private void handleDebit(WalletCommandEvent cmd) {
		DebitRequest request = DebitRequest.from(cmd);
		WalletTransaction tx = walletService.debit(request);
		log.info("DEBIT_CMD processed: walletId={} txId={} amount={}",
				cmd.getWalletId(), tx.getId(), cmd.getAmount());
	}

	/**
	 * Handle CREDIT_CMD.
	 * WalletService.credit() handles idempotency, optimistic lock, balance mutation,
	 * and publishing WALLET_CREDITED event internally.
	 */
	private void handleCredit(WalletCommandEvent cmd) {
		CreditRequest request = CreditRequest.from(cmd);
		WalletTransaction tx = walletService.credit(request);
		log.info("CREDIT_CMD processed: walletId={} txId={} amount={}",
				cmd.getWalletId(), tx.getId(), cmd.getAmount());
	}

	/**
	 * Handle BLOCK_CMD — reserve funds for escrow/pending Saga.
	 */
	private void handleBlock(WalletCommandEvent cmd) {
		BlockFundsRequest request = BlockFundsRequest.from(cmd);
		walletService.blockFunds(request);
		log.info("BLOCK_CMD processed: walletId={} amount={}", cmd.getWalletId(), cmd.getAmount());
	}

	/**
	 * Handle UNBLOCK_CMD — release reserved funds (Saga compensation).
	 */
	private void handleUnblock(WalletCommandEvent cmd) {
		BlockFundsRequest request = BlockFundsRequest.from(cmd);
		walletService.unblockFunds(request);
		log.info("UNBLOCK_CMD processed: walletId={} amount={}", cmd.getWalletId(), cmd.getAmount());
	}

	// ── Failure event publishing ──────────────────────────────────────────────

	/**
	 * Publish WALLET_DEBIT_FAILED event — Saga compensation trigger.
	 *
	 * Called when a DEBIT_CMD cannot be fulfilled.
	 * Transaction Service consumes this → marks TXN as FAILED → no credit issued.
	 *
	 * If this publish itself fails (Kafka down), we log the error with full context.
	 * The Saga will eventually time out on the Transaction Service side and
	 * trigger compensation through its own timeout handler.
	 *
	 * We do NOT throw here — the command was already ACKed. A throw at this
	 * point would cause the consumer to reprocess the entire message.
	 */
	private void publishDebitFailedEvent(WalletCommandEvent cmd, WalletBaseException ex) {
		try {
			eventProducer.publish(
					eventFactory.buildDebitFailedEvent(
							cmd,
							ex.getErrorCode(),
							ex.getMessage()
					)
			);
			log.info("WALLET_DEBIT_FAILED published: walletId={} txRef={} code={}",
					cmd.getWalletId(), cmd.getTransactionRef(), ex.getErrorCode());
		} catch (Exception publishEx) {
			// Kafka down after ACK — log enough to manually replay
			log.error("CRITICAL: Failed to publish WALLET_DEBIT_FAILED. " +
							"walletId={} txRef={} code={} error={}. Manual intervention required.",
					cmd.getWalletId(), cmd.getTransactionRef(),
					ex.getErrorCode(), publishEx.getMessage(), publishEx);
		}
	}

	// ── Validation ────────────────────────────────────────────────────────────

	/**
	 * Validates mandatory fields on WalletCommandEvent.
	 * Malformed commands (missing walletId, amount, idempotencyKey) cannot
	 * be processed and should not block the partition — ACK and skip.
	 *
	 * Returns false if the command should be skipped.
	 */
	private boolean validateCommand(WalletCommandEvent cmd, long offset) {
		if (cmd.getCommandType() == null) {
			log.warn("WalletCommandEvent missing commandType at offset={} — skipping", offset);
			return false;
		}
		if (cmd.getWalletId() == null || cmd.getWalletId().isBlank()) {
			log.warn("WalletCommandEvent missing walletId at offset={} — skipping", offset);
			return false;
		}
		if (cmd.getAmount() == null || cmd.getAmount().signum() <= 0) {
			log.warn("WalletCommandEvent invalid amount={} at offset={} — skipping",
					cmd.getAmount(), offset);
			return false;
		}
		if (cmd.getIdempotencyKey() == null || cmd.getIdempotencyKey().isBlank()) {
			log.warn("WalletCommandEvent missing idempotencyKey at offset={} — skipping", offset);
			return false;
		}
		return true;
	}

	// ── Deserialisation ───────────────────────────────────────────────────────

	/**
	 * Safe deserialisation — same pattern as UserEventConsumer.
	 * Handles pre-deserialised WalletCommandEvent or raw LinkedHashMap fallback.
	 */
	@SuppressWarnings("unchecked")
	private WalletCommandEvent deserialise(Object value) {
		if (value instanceof WalletCommandEvent event) {
			return event;
		}
		if (value instanceof java.util.Map) {
			java.util.Map<String, Object> map = (java.util.Map<String, Object>) value;
			try {
				String commandTypeStr = (String) map.get("commandType");
				WalletCommandTypeEnum commandType = commandTypeStr != null
						? WalletCommandTypeEnum.valueOf(commandTypeStr) : null;

				return WalletCommandEvent.builder()
						.eventId((String) map.get("eventId"))
						.commandType(commandType)
						.walletId((String) map.get("walletId"))
						.amount(map.get("amount") != null
								? new java.math.BigDecimal(map.get("amount").toString()) : null)
						.idempotencyKey((String) map.get("idempotencyKey"))
						.transactionRef((String) map.get("transactionRef"))
						.description((String) map.get("description"))
						.metadata((String) map.get("metadata"))
						.build();
			} catch (Exception ex) {
				log.warn("Failed to deserialise WalletCommandEvent from Map: {}", ex.getMessage());
				return null;
			}
		}
		log.warn("Unexpected value type in TransactionEventConsumer: {}",
				value != null ? value.getClass().getName() : "null");
		return null;
	}
}
