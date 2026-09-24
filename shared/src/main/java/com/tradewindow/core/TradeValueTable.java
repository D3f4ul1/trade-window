package com.tradewindow.core;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A small item-to-value table backing the optional {@code maxTradeValue} cap.
 *
 * <p>Values are abstract "points", not emeralds or any in-game currency. They
 * exist only so a server operator can say "no single trade may move more than
 * N points of stuff" as a blunt anti-RMT / anti-accident guard.
 *
 * <p>The table is intentionally short and easy to replace. It ships with
 * commonly-traded materials; anything not listed contributes
 * {@link #UNKNOWN_VALUE}. See {@code DESIGN_DECISIONS.md} #6 for why unknown
 * items count as zero rather than as an automatic rejection.
 */
public final class TradeValueTable {

	/**
	 * Value contributed by an item that is not in the table.
	 *
	 * <p>Zero, deliberately: the cap is disabled by default, and an incomplete
	 * value table that silently made every trade impossible would be a far worse
	 * failure mode than a cap that can be walked past with unlisted items.
	 */
	public static final long UNKNOWN_VALUE = 0L;

	private static final Map<String, Long> VALUES = new LinkedHashMap<>();

	static {
		// Vanilla currency-ish baseline: one gold ingot == 1 point.
		put("minecraft:gold_nugget", 0L);
		put("minecraft:gold_ingot", 1L);
		put("minecraft:gold_block", 9L);

		put("minecraft:iron_nugget", 0L);
		put("minecraft:iron_ingot", 1L);
		put("minecraft:iron_block", 9L);

		put("minecraft:copper_ingot", 1L);
		put("minecraft:copper_block", 9L);
		put("minecraft:coal", 1L);
		put("minecraft:charcoal", 1L);
		put("minecraft:redstone", 1L);
		put("minecraft:lapis_lazuli", 1L);
		put("minecraft:quartz", 2L);

		put("minecraft:diamond", 16L);
		put("minecraft:diamond_block", 144L);
		put("minecraft:emerald", 16L);
		put("minecraft:emerald_block", 144L);
		put("minecraft:netherite_ingot", 128L);
		put("minecraft:netherite_scrap", 32L);
		put("minecraft:netherite_block", 1152L);
		put("minecraft:ancient_debris", 32L);
		put("minecraft:amethyst_shard", 4L);
		put("minecraft:echo_shard", 32L);

		put("minecraft:nether_star", 512L);
		put("minecraft:elytra", 256L);
		put("minecraft:shulker_shell", 32L);
		put("minecraft:dragon_egg", 512L);
		put("minecraft:enchanted_golden_apple", 256L);
		put("minecraft:golden_apple", 16L);
		put("minecraft:totem_of_undying", 128L);

		put("minecraft:oak_log", 1L);
		put("minecraft:stone", 1L);
		put("minecraft:dirt", 0L);
		put("minecraft:cobblestone", 0L);
		put("minecraft:glass", 1L);
		put("minecraft:bread", 1L);
		put("minecraft:book", 2L);
		put("minecraft:paper", 0L);
		put("minecraft:leather", 1L);
		put("minecraft:string", 0L);

		put("tradewindow:trade_token", 1L);
	}

	private TradeValueTable() {
	}

	private static void put(String id, long value) {
		VALUES.put(id, value);
	}

	/**
	 * Looks up the value of a single item.
	 *
	 * @param itemId the namespaced item id, for example {@code minecraft:diamond}
	 * @return the per-item value, or {@link #UNKNOWN_VALUE} if the id is not listed
	 */
	public static long valueOf(String itemId) {
		if (itemId == null || itemId.isEmpty()) {
			return UNKNOWN_VALUE;
		}
		Long value = VALUES.get(itemId.toLowerCase(Locale.ROOT));
		return value == null ? UNKNOWN_VALUE : value;
	}

	/**
	 * Reports whether an item has an explicit entry in the table.
	 *
	 * @param itemId the namespaced item id
	 * @return {@code true} if a value is known for this id
	 */
	public static boolean isKnown(String itemId) {
		return itemId != null && VALUES.containsKey(itemId.toLowerCase(Locale.ROOT));
	}

	/**
	 * Returns an immutable snapshot of the built-in table.
	 *
	 * @return every known item id mapped to its value
	 */
	public static Map<String, Long> all() {
		return Map.copyOf(VALUES);
	}
}
