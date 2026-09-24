package com.tradewindow.core;

import java.util.UUID;

/**
 * A pending trade request: "player A right-clicked player B with a Trade Token".
 *
 * <p>Requests are immutable in their participants and creation time; only
 * {@link #resolve()} mutates state. Instances are short-lived — the owning
 * manager discards them once they are resolved or expired.
 */
public final class TradeRequest {

	private final UUID senderId;
	private final String senderName;
	private final UUID targetId;
	private final String targetName;
	private final long createdAtMillis;
	private final int timeoutSeconds;

	private boolean resolved;

	/**
	 * Creates a request.
	 *
	 * @param senderId        the initiating player's UUID
	 * @param senderName      the initiating player's display name, for messages and logs
	 * @param targetId        the invited player's UUID
	 * @param targetName      the invited player's display name
	 * @param createdAtMillis wall-clock creation time, from {@code System.currentTimeMillis()}
	 * @param timeoutSeconds  how long the invitation stays valid
	 */
	public TradeRequest(UUID senderId, String senderName, UUID targetId, String targetName,
						long createdAtMillis, int timeoutSeconds) {
		this.senderId = senderId;
		this.senderName = senderName;
		this.targetId = targetId;
		this.targetName = targetName;
		this.createdAtMillis = createdAtMillis;
		this.timeoutSeconds = timeoutSeconds;
	}

	/**
	 * Returns the initiating player's UUID.
	 *
	 * @return the sender UUID
	 */
	public UUID senderId() {
		return senderId;
	}

	/**
	 * Returns the initiating player's display name.
	 *
	 * @return the sender name
	 */
	public String senderName() {
		return senderName;
	}

	/**
	 * Returns the invited player's UUID.
	 *
	 * @return the target UUID
	 */
	public UUID targetId() {
		return targetId;
	}

	/**
	 * Returns the invited player's display name.
	 *
	 * @return the target name
	 */
	public String targetName() {
		return targetName;
	}

	/**
	 * Returns the wall-clock creation time.
	 *
	 * @return milliseconds since the epoch
	 */
	public long createdAtMillis() {
		return createdAtMillis;
	}

	/**
	 * Reports whether this request has already been accepted, declined or expired.
	 *
	 * @return {@code true} if the request can no longer be acted on
	 */
	public boolean isResolved() {
		return resolved;
	}

	/**
	 * Reports whether the request has passed its timeout.
	 *
	 * @param nowMillis the current wall-clock time
	 * @return {@code true} once {@code timeoutSeconds} have elapsed
	 */
	public boolean isExpired(long nowMillis) {
		return nowMillis - createdAtMillis >= timeoutSeconds * 1000L;
	}

	/**
	 * Returns the whole seconds left before this request expires.
	 *
	 * @param nowMillis the current wall-clock time
	 * @return remaining seconds, never negative
	 */
	public long remainingSeconds(long nowMillis) {
		long remaining = timeoutSeconds - (nowMillis - createdAtMillis) / 1000L;
		return Math.max(0L, remaining);
	}

	/**
	 * Marks the request as handled.
	 *
	 * <p>Idempotent-safe by design: a request that is already resolved cannot be
	 * resolved again, which is what stops a double-clicked "Accept" button (or a
	 * duplicate packet) from opening two trade windows.
	 *
	 * @return {@code true} if this call performed the resolution, {@code false} if
	 *         the request had already been resolved by an earlier caller
	 */
	public boolean resolve() {
		if (resolved) {
			return false;
		}
		resolved = true;
		return true;
	}

	/**
	 * Returns whether the given player is the target of this request.
	 *
	 * @param playerId the player to test
	 * @return {@code true} if that player is the invitee
	 */
	public boolean isTarget(UUID playerId) {
		return targetId.equals(playerId);
	}

	/**
	 * Returns whether the given player sent this request.
	 *
	 * @param playerId the player to test
	 * @return {@code true} if that player is the initiator
	 */
	public boolean isSender(UUID playerId) {
		return senderId.equals(playerId);
	}
}
