package com.tradewindow.core;

/**
 * The distance rule: whether it is in force at all, and whether two traders are
 * outside it.
 *
 * <p><b>One definition, asked from every point that needs the answer</b> - sending a
 * request, accepting one, and the tick loop that watches a window. v1.4.0 asked it in
 * the tick loop only, and that single omission was a real bug: with cross-distance
 * trading switched off, a request could be accepted from three hundred blocks away,
 * the requester's Trade Token was spent, the window opened, and the next tick closed
 * it again. The player paid a token for a trade that never happened.
 *
 * <p>Pure on purpose: the separation arrives as an already-squared distance and a
 * same-dimension flag, so the rule can be unit tested without a server and shared
 * verbatim by every target. Reading a live {@code ServerPlayer}'s position is the
 * caller's job.
 *
 * @see TradeConfigData#maxDistanceBlocks
 * @see TradeConfigData#cancelOnMove
 * @see TradeConfigData#crossDistanceTrading
 */
public final class TradeDistance {

	private TradeDistance() {
	}

	/**
	 * Reports whether the distance rule should be enforced at all.
	 *
	 * <p>Two switches have to agree. {@code crossDistanceTrading} is the policy -
	 * on by default, meaning trades work at any range - and {@code cancelOnMove} is
	 * the master switch, off by default. The rule therefore runs only when an operator
	 * has deliberately turned cross-distance trading off <em>and</em> left the master
	 * switch on.
	 *
	 * @param config the active configuration
	 * @return {@code true} when {@link #tooFar} can ever return {@code true}
	 */
	public static boolean enforced(TradeConfigData config) {
		return config.cancelOnMove && !config.crossDistanceTrading;
	}

	/**
	 * Reports whether two traders are too far apart to trade.
	 *
	 * <p>Different dimensions have no meaningful distance between them, so with the
	 * rule on they are out of range unless cross-dimension trading is explicitly
	 * allowed - a separation of "unknown" cannot be compared against a block radius.
	 *
	 * @param config         the active configuration
	 * @param sameDimension  whether both players are in the same world
	 * @param distanceSquared the squared distance between them, ignored when they are
	 *                       not in the same dimension
	 * @return {@code true} when the trade is outside the allowance
	 */
	public static boolean tooFar(TradeConfigData config, boolean sameDimension, double distanceSquared) {
		if (!enforced(config)) {
			return false;
		}
		if (!sameDimension) {
			return !config.allowCrossDimensionTrading;
		}
		return distanceSquared > (double) config.maxDistanceBlocks * config.maxDistanceBlocks;
	}
}
