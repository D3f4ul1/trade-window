package com.tradewindow.core;

/**
 * Identifies one of the two participants in a trade.
 *
 * <p>Sides are assigned when the session is created and never change: the player
 * who sent the request is always {@link #PLAYER_1}, the player who accepted is
 * always {@link #PLAYER_2}. A side is an identity, not a screen position: the
 * 1.21.11 window maps each viewer's own side into the same two rows, so both
 * players see <em>their own</em> offer in the middle of the window no matter which
 * side they occupy.
 */
public enum TradeSide {
	/** The player who initiated the trade request. Rendered on the left. */
	PLAYER_1,
	/** The player who accepted the trade request. Rendered on the right. */
	PLAYER_2;

	/**
	 * Returns the other side.
	 *
	 * @return {@link #PLAYER_2} when called on {@link #PLAYER_1}, and vice versa
	 */
	public TradeSide opposite() {
		return this == PLAYER_1 ? PLAYER_2 : PLAYER_1;
	}

	/**
	 * Resolves a side from the index used on the wire and in slot arithmetic.
	 *
	 * @param ordinal {@code 0} for {@link #PLAYER_1}, {@code 1} for {@link #PLAYER_2}
	 * @return the matching side
	 * @throws IllegalArgumentException if the ordinal is not 0 or 1
	 */
	public static TradeSide fromOrdinal(int ordinal) {
		if (ordinal == 0) {
			return PLAYER_1;
		}
		if (ordinal == 1) {
			return PLAYER_2;
		}
		throw new IllegalArgumentException("Unknown trade side ordinal: " + ordinal);
	}
}
