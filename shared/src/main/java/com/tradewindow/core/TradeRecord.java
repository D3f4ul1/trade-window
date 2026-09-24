package com.tradewindow.core;

import java.util.List;

/**
 * One row of trade history: who traded what, when, and how it ended.
 *
 * @param timestampMillis wall-clock time the trade reached a terminal state
 * @param player1Name     the initiating player's name
 * @param player2Name     the accepting player's name
 * @param offer1          what player 1 offered, in slot order
 * @param offer2          what player 2 offered, in slot order
 * @param outcome         {@code "SUCCESS"} or {@code "CANCELLED"}
 * @param detail          the {@link CancelReason} name for cancellations, otherwise empty
 */
public record TradeRecord(long timestampMillis,
						  String player1Name,
						  String player2Name,
						  List<ItemSnapshot> offer1,
						  List<ItemSnapshot> offer2,
						  String outcome,
						  String detail) {

	/** Outcome marker for a trade that swapped items. */
	public static final String OUTCOME_SUCCESS = "SUCCESS";

	/** Outcome marker for a trade that ended without swapping. */
	public static final String OUTCOME_CANCELLED = "CANCELLED";

	/**
	 * Normalises a record's fields, substituting empty values for nulls.
	 *
	 * @param timestampMillis wall-clock time
	 * @param player1Name     the initiator's name
	 * @param player2Name     the acceptor's name
	 * @param offer1          player 1's offer
	 * @param offer2          player 2's offer
	 * @param outcome         the outcome marker
	 * @param detail          extra context for cancellations
	 */
	public TradeRecord {
		player1Name = player1Name == null ? "?" : player1Name;
		player2Name = player2Name == null ? "?" : player2Name;
		offer1 = offer1 == null ? List.of() : List.copyOf(offer1);
		offer2 = offer2 == null ? List.of() : List.copyOf(offer2);
		outcome = outcome == null ? OUTCOME_CANCELLED : outcome;
		detail = detail == null ? "" : detail;
	}

	/**
	 * Reports whether this record describes a completed swap.
	 *
	 * @return {@code true} when the trade succeeded
	 */
	public boolean isSuccess() {
		return OUTCOME_SUCCESS.equals(outcome);
	}
}
