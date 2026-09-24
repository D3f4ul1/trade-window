package com.tradewindow.core;

import java.util.UUID;

/**
 * The confirm/unconfirm state machine at the heart of a trade.
 *
 * <p>This class owns <em>only</em> protocol state — who confirmed, whether the
 * session is still live, and whether it timed out. It never touches items, the
 * GUI, or the network, which is what allows it to be exhaustively unit tested
 * and reused unchanged by both the 1.20.1 and 26.3 builds.
 *
 * <p>Rules implemented here, per the specification:
 * <ul>
 *   <li>both players must confirm before anything is exchanged;</li>
 *   <li>un-confirming resets <em>both</em> confirmations, so one player cannot
 *       be tricked into leaving a confirmation standing after the other side
 *       changed their offer;</li>
 *   <li>a session that has not been executed within {@code guiTimeoutSeconds}
 *       is force-cancelled rather than silently hanging.</li>
 * </ul>
 *
 * <p>Instances are not thread safe. The owning manager guarantees that all
 * mutation happens on the server thread.
 */
public final class TradeSessionCore {

	/** What the session wants the caller to do after a tick. */
	public enum TickResult {
		/** Nothing to do; the window stays open. */
		CONTINUE,
		/** Both sides confirmed — the caller must now attempt the atomic swap. */
		EXECUTE,
		/** The GUI timeout elapsed; the caller must cancel with {@link CancelReason#GUI_TIMEOUT}. */
		TIMEOUT
	}

	private final UUID player1Id;
	private final String player1Name;
	private final UUID player2Id;
	private final String player2Name;
	private final long openedAtMillis;
	private final int guiTimeoutSeconds;

	private boolean confirmed1;
	private boolean confirmed2;
	private boolean finished;
	private CancelReason cancelReason;
	private boolean executed;

	/**
	 * Creates a session that has just opened its GUI.
	 *
	 * @param player1Id         the initiating player's UUID
	 * @param player1Name       the initiating player's display name
	 * @param player2Id         the accepting player's UUID
	 * @param player2Name       the accepting player's display name
	 * @param openedAtMillis    wall-clock time the window opened
	 * @param guiTimeoutSeconds how long the window may stay open
	 */
	public TradeSessionCore(UUID player1Id, String player1Name, UUID player2Id, String player2Name,
							long openedAtMillis, int guiTimeoutSeconds) {
		this.player1Id = player1Id;
		this.player1Name = player1Name;
		this.player2Id = player2Id;
		this.player2Name = player2Name;
		this.openedAtMillis = openedAtMillis;
		this.guiTimeoutSeconds = guiTimeoutSeconds;
	}

	/**
	 * Returns the UUID of the player on the given side.
	 *
	 * @param side which participant to look up
	 * @return that participant's UUID
	 */
	public UUID playerId(TradeSide side) {
		return side == TradeSide.PLAYER_1 ? player1Id : player2Id;
	}

	/**
	 * Returns the display name of the player on the given side.
	 *
	 * @param side which participant to look up
	 * @return that participant's name
	 */
	public String playerName(TradeSide side) {
		return side == TradeSide.PLAYER_1 ? player1Name : player2Name;
	}

	/**
	 * Resolves which side a given player occupies.
	 *
	 * @param playerId the player to look up
	 * @return that player's side
	 * @throws IllegalArgumentException if the player is not part of this session
	 */
	public TradeSide sideOf(UUID playerId) {
		if (player1Id.equals(playerId)) {
			return TradeSide.PLAYER_1;
		}
		if (player2Id.equals(playerId)) {
			return TradeSide.PLAYER_2;
		}
		throw new IllegalArgumentException("Player " + playerId + " is not part of this trade");
	}

	/**
	 * Reports whether the given player participates in this session.
	 *
	 * @param playerId the player to test
	 * @return {@code true} if they are one of the two traders
	 */
	public boolean involves(UUID playerId) {
		return player1Id.equals(playerId) || player2Id.equals(playerId);
	}

	/**
	 * Reports whether the given side has confirmed and is therefore locked.
	 *
	 * @param side the side to query
	 * @return {@code true} if that side's offer is locked
	 */
	public boolean isConfirmed(TradeSide side) {
		return side == TradeSide.PLAYER_1 ? confirmed1 : confirmed2;
	}

	/**
	 * Reports whether both sides have confirmed.
	 *
	 * @return {@code true} when the trade is ready to execute
	 */
	public boolean bothConfirmed() {
		return confirmed1 && confirmed2;
	}

	/**
	 * Records a confirmation from one side.
	 *
	 * <p>Has no effect on a finished session, so a late packet from a
	 * disconnecting client cannot revive a cancelled trade.
	 *
	 * @param side the side that confirmed
	 * @return {@code true} if this call changed the state
	 */
	public boolean confirm(TradeSide side) {
		if (finished || isConfirmed(side)) {
			return false;
		}
		if (side == TradeSide.PLAYER_1) {
			confirmed1 = true;
		} else {
			confirmed2 = true;
		}
		return true;
	}

	/**
	 * Clears a confirmation, resetting <em>both</em> sides.
	 *
	 * <p>The reset is deliberately symmetric. If player A could un-confirm while
	 * leaving player B's confirmation standing, A could edit their offer after B
	 * had committed to the old one.
	 *
	 * @param side the side that un-confirmed
	 * @return {@code true} if this call changed the state
	 */
	public boolean unconfirm(TradeSide side) {
		if (finished || (!confirmed1 && !confirmed2)) {
			return false;
		}
		confirmed1 = false;
		confirmed2 = false;
		return true;
	}

	/**
	 * Advances the session clock by one server tick.
	 *
	 * @param nowMillis the current wall-clock time
	 * @return what the caller should do next
	 */
	public TickResult tick(long nowMillis) {
		if (finished) {
			return TickResult.CONTINUE;
		}
		if (bothConfirmed()) {
			return TickResult.EXECUTE;
		}
		if (elapsedSeconds(nowMillis) >= guiTimeoutSeconds) {
			return TickResult.TIMEOUT;
		}
		return TickResult.CONTINUE;
	}

	/**
	 * Returns the whole seconds this window has been open.
	 *
	 * @param nowMillis the current wall-clock time
	 * @return elapsed seconds, never negative
	 */
	public long elapsedSeconds(long nowMillis) {
		return Math.max(0L, (nowMillis - openedAtMillis) / 1000L);
	}

	/**
	 * Returns the whole seconds left before the GUI timeout fires.
	 *
	 * @param nowMillis the current wall-clock time
	 * @return remaining seconds, never negative
	 */
	public long remainingSeconds(long nowMillis) {
		return Math.max(0L, guiTimeoutSeconds - elapsedSeconds(nowMillis));
	}

	/**
	 * Marks the session as successfully executed.
	 *
	 * @return {@code true} if this call performed the transition, {@code false}
	 *         if the session was already finished
	 */
	public boolean markExecuted() {
		if (finished) {
			return false;
		}
		finished = true;
		executed = true;
		return true;
	}

	/**
	 * Terminates the session without executing it.
	 *
	 * @param reason why the trade ended
	 * @return {@code true} if this call performed the transition, {@code false}
	 *         if the session was already finished
	 */
	public boolean cancel(CancelReason reason) {
		if (finished) {
			return false;
		}
		finished = true;
		executed = false;
		cancelReason = reason;
		return true;
	}

	/**
	 * Reports whether the session has ended, either way.
	 *
	 * @return {@code true} once the session is executed or cancelled
	 */
	public boolean isFinished() {
		return finished;
	}

	/**
	 * Reports whether the session ended in a successful swap.
	 *
	 * @return {@code true} if items were exchanged
	 */
	public boolean isExecuted() {
		return executed;
	}

	/**
	 * Returns why the session was cancelled.
	 *
	 * @return the cancellation reason, or {@code null} if it was never cancelled
	 */
	public CancelReason cancelReason() {
		return cancelReason;
	}
}
