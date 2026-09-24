package com.tradewindow.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The item-movement half of a trade: normalise, validate, and build the two
 * delivery lists.
 *
 * <p>The whole class is written against {@link StackOps} rather than any
 * Minecraft type, so the exact same algorithm is exercised by the JUnit tests
 * with a fake stack implementation and by the game with a real {@code ItemStack}
 * adapter.
 *
 * <p><b>Atomicity contract.</b> {@link #prepare(List, List, StackOps, TradeConfigData)}
 * never mutates its inputs and never partially succeeds: it either returns a
 * plan whose two delivery lists are complete and independent copies, or it
 * returns a failure that names a {@link CancelReason}. The caller is expected to
 * treat the plan as a transaction — validate first, then move items, and if the
 * move cannot be completed for any reason, abort and return everything.
 */
public final class TradeSwap {

	private TradeSwap() {
	}

	/**
	 * An all-or-nothing plan describing what each player should receive.
	 *
	 * @param <T> the concrete stack type
	 */
	public static final class Plan<T> {
		private final boolean valid;
		private final CancelReason failure;
		private final List<T> deliveryForPlayer1;
		private final List<T> deliveryForPlayer2;
		private final long totalValue;

		private Plan(boolean valid, CancelReason failure, List<T> forPlayer1, List<T> forPlayer2, long totalValue) {
			this.valid = valid;
			this.failure = failure;
			this.deliveryForPlayer1 = forPlayer1;
			this.deliveryForPlayer2 = forPlayer2;
			this.totalValue = totalValue;
		}

		/**
		 * Reports whether the swap may proceed.
		 *
		 * @return {@code true} if both deliveries are safe to commit
		 */
		public boolean isValid() {
			return valid;
		}

		/**
		 * Returns why the swap was rejected.
		 *
		 * @return the failure reason, or {@code null} when {@link #isValid()} is true
		 */
		public CancelReason failure() {
			return failure;
		}

		/**
		 * Returns what player 1 receives, which is player 2's offer.
		 *
		 * @return an immutable list of independent copies
		 */
		public List<T> deliveryForPlayer1() {
			return deliveryForPlayer1;
		}

		/**
		 * Returns what player 2 receives, which is player 1's offer.
		 *
		 * @return an immutable list of independent copies
		 */
		public List<T> deliveryForPlayer2() {
			return deliveryForPlayer2;
		}

		/**
		 * Returns the combined value of both offers.
		 *
		 * @return the total value, or {@code 0} when the cap is disabled
		 */
		public long totalValue() {
			return totalValue;
		}
	}

	/**
	 * Validates both offers and builds the two delivery lists.
	 *
	 * <p>Checks, in order:
	 * <ol>
	 *   <li>every non-empty stack is at least one item and no larger than its
	 *       maximum stack size (an over-stacked entry means a hacked client);</li>
	 *   <li>no stack is on the blacklist;</li>
	 *   <li>if a value cap is configured, the combined offer does not exceed it.</li>
	 * </ol>
	 *
	 * @param offer1 the stacks player 1 placed, in slot order
	 * @param offer2 the stacks player 2 placed, in slot order
	 * @param ops    the stack adapter for the running Minecraft version
	 * @param config the active configuration
	 * @param <T>    the concrete stack type
	 * @return a plan that is either valid or carries a failure reason
	 */
	public static <T> Plan<T> prepare(List<T> offer1, List<T> offer2, StackOps<T> ops, TradeConfigData config) {
		List<T> clean1 = new ArrayList<>();
		List<T> clean2 = new ArrayList<>();

		for (T stack : offer1) {
			if (!ops.isEmpty(stack)) {
				clean1.add(stack);
			}
		}
		for (T stack : offer2) {
			if (!ops.isEmpty(stack)) {
				clean2.add(stack);
			}
		}

		// 1. Structural sanity. A count outside [1, maxStackSize] cannot be
		//    produced by the vanilla client, so refuse rather than guess.
		for (List<T> offer : List.of(clean1, clean2)) {
			for (T stack : offer) {
				int count = ops.count(stack);
				if (count <= 0 || count > ops.maxStackSize(stack)) {
					return failed(CancelReason.COMMIT_FAILED);
				}
			}
		}

		// 2. Blacklist.
		for (List<T> offer : List.of(clean1, clean2)) {
			for (T stack : offer) {
				if (config.blacklistSet().contains(ops.itemId(stack))) {
					return failed(CancelReason.BLACKLISTED_ITEM);
				}
			}
		}

		// 3. Optional value cap over the combined offer.
		long total = 0L;
		if (config.hasValueCap()) {
			for (List<T> offer : List.of(clean1, clean2)) {
				for (T stack : offer) {
					total += TradeValueTable.valueOf(ops.itemId(stack)) * ops.count(stack);
				}
			}
			if (total > config.maxTradeValue) {
				return failed(CancelReason.VALUE_CAP);
			}
		}

		// Build deliveries as independent copies, in slot order, so that the
		// receiving player's inventory layout is deterministic and neither side
		// can end up holding a live reference into the other side's container.
		List<T> toPlayer1 = new ArrayList<>(clean2.size());
		for (T stack : clean2) {
			toPlayer1.add(ops.copy(stack));
		}
		List<T> toPlayer2 = new ArrayList<>(clean1.size());
		for (T stack : clean1) {
			toPlayer2.add(ops.copy(stack));
		}

		return new Plan<>(true, null, Collections.unmodifiableList(toPlayer1),
				Collections.unmodifiableList(toPlayer2), total);
	}

	/**
	 * Returns the combined value of one offer.
	 *
	 * @param offer  the stacks to appraise
	 * @param ops    the stack adapter
	 * @param <T>    the concrete stack type
	 * @return the total value; items with no known value count as {@link TradeValueTable#UNKNOWN_VALUE}
	 */
	public static <T> long appraise(List<T> offer, StackOps<T> ops) {
		long total = 0L;
		for (T stack : offer) {
			if (ops.isEmpty(stack)) {
				continue;
			}
			total += TradeValueTable.valueOf(ops.itemId(stack)) * ops.count(stack);
		}
		return total;
	}

	/**
	 * Confirms that two lists hold the same items with the same data.
	 *
	 * <p>Used by the commit step to prove a copy survived the move intact — the
	 * cheapest available guard against a partial or desynchronised swap.
	 *
	 * @param expected the list that was planned
	 * @param actual   the list that ended up in the destination
	 * @param ops      the stack adapter
	 * @param <T>      the concrete stack type
	 * @return {@code true} if both lists describe the same items, counts and data
	 */
	public static <T> boolean matches(List<T> expected, List<T> actual, StackOps<T> ops) {
		List<T> left = nonEmpty(expected, ops);
		List<T> right = nonEmpty(actual, ops);
		if (left.size() != right.size()) {
			return false;
		}
		for (int i = 0; i < left.size(); i++) {
			T a = left.get(i);
			T b = right.get(i);
			if (ops.count(a) != ops.count(b)
					|| !ops.itemId(a).equals(ops.itemId(b))
					|| !ops.dataFingerprint(a).equals(ops.dataFingerprint(b))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Strips empty entries from a stack list.
	 *
	 * @param stacks the source list
	 * @param ops    the stack adapter
	 * @param <T>    the concrete stack type
	 * @return a new list containing only non-empty stacks
	 */
	public static <T> List<T> nonEmpty(List<T> stacks, StackOps<T> ops) {
		List<T> out = new ArrayList<>(stacks.size());
		for (T stack : stacks) {
			if (!ops.isEmpty(stack)) {
				out.add(stack);
			}
		}
		return out;
	}

	/**
	 * Counts how many non-empty slots an offer occupies.
	 *
	 * @param offer the stacks to count
	 * @param ops   the stack adapter
	 * @param <T>   the concrete stack type
	 * @return the number of occupied slots
	 */
	public static <T> int occupiedSlots(List<T> offer, StackOps<T> ops) {
		return nonEmpty(offer, ops).size();
	}

	private static <T> Plan<T> failed(CancelReason reason) {
		return new Plan<>(false, reason, List.of(), List.of(), 0L);
	}
}
