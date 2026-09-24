package com.tradewindow.core;

/**
 * The trade window's slot layout, and the rule that decides who may touch a slot.
 *
 * <p>This lives in the Minecraft-free core, not in the menu, for one reason: the
 * ownership rule is the mod's main anti-scam boundary, so it must be unit tested
 * rather than only exercised by hand. The constants and the layout are also the
 * single source of truth for both the container and the menu.
 *
 * <p>Layout, matching a double chest's six rows of nine:
 * <ul>
 *   <li>slots  0-17 — player A's offer (first two rows);</li>
 *   <li>slots 18-35 — locked glass panes (middle two rows), display only;</li>
 *   <li>slots 36-53 — player B's offer (last two rows).</li>
 * </ul>
 */
public final class TradeLayout {

	/** Total trade slots: both offers plus the glass divider. */
	public static final int SLOTS = 54;
	/** Offer slots per side. */
	public static final int SLOTS_PER_SIDE = 18;
	/** Index of the first glass-pane (locked) slot. */
	public static final int GLASS_START = 18;
	/** Index just past the last glass-pane slot. */
	public static final int GLASS_END = 36;
	/** Index of the first slot belonging to player B's offer. */
	public static final int SECOND_OFFER_START = 36;
	/** Index of the first slot belonging to the viewing player's own inventory. */
	public static final int PLAYER_SLOTS_START = 54;

	private TradeLayout() {
	}

	/** Reports whether an index is one of the 54 trade slots (not a player-inventory slot). */
	public static boolean isTradeSlot(int index) {
		return index >= 0 && index < SLOTS;
	}

	/** Reports whether an index is one of the locked glass-pane slots. */
	public static boolean isGlass(int index) {
		return index >= GLASS_START && index < GLASS_END;
	}

	/**
	 * Returns the side that owns a trade slot.
	 *
	 * @param index the slot index
	 * @return the owning side, or {@code null} for glass and for out-of-range indices
	 */
	public static TradeSide sideOf(int index) {
		if (!isTradeSlot(index) || isGlass(index)) {
			return null;
		}
		return index < SECOND_OFFER_START ? TradeSide.PLAYER_1 : TradeSide.PLAYER_2;
	}

	/**
	 * Reports whether a side may place into or take from a trade slot.
	 *
	 * <p>The {@code side != null} guard matters: without it a null side would
	 * "own" every slot at or past {@link #SECOND_OFFER_START}, because the
	 * comparison against {@link TradeSide#PLAYER_1} degenerates.
	 *
	 * @param index the slot index
	 * @param side  the side asking, or {@code null} for no side (glass panes)
	 * @return {@code true} only when {@code side} owns {@code index}
	 */
	public static boolean slotMayBeUsedBy(int index, TradeSide side) {
		return side != null && sideOf(index) == side;
	}
}
