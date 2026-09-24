package com.tradewindow.core;

import java.util.Locale;

/**
 * An immutable, Minecraft-free description of one traded stack.
 *
 * <p>Snapshots are taken <em>before</em> the swap so that the log records what
 * was actually offered, and they carry a data fingerprint so that two otherwise
 * identical stacks with different enchantments or durability are distinguishable
 * after the fact.
 *
 * @param itemId          the namespaced item id, for example {@code minecraft:diamond_sword}
 * @param count           how many items were in the stack
 * @param displayName     the name shown to players, including any custom name
 * @param dataFingerprint a stable digest of the stack's item data, possibly empty
 */
public record ItemSnapshot(String itemId, int count, String displayName, String dataFingerprint) {

	/**
	 * Normalises the fields of this snapshot.
	 *
	 * @param itemId          the namespaced item id
	 * @param count           how many items were in the stack
	 * @param displayName     the display name
	 * @param dataFingerprint a digest of the item data
	 */
	public ItemSnapshot {
		itemId = itemId == null ? "" : itemId.toLowerCase(Locale.ROOT);
		displayName = displayName == null ? "" : displayName;
		dataFingerprint = dataFingerprint == null ? "" : dataFingerprint;
	}

	/**
	 * Returns a compact single-line description, for example {@code 3x minecraft:diamond}.
	 *
	 * @return the summary used in logs and chat
	 */
	public String summary() {
		return count + "x " + itemId;
	}

	/**
	 * Returns the description written to the human-readable log.
	 *
	 * <p>Includes the display name and data fingerprint only when they add
	 * information, so ordinary trades stay readable.
	 *
	 * @return the verbose description
	 */
	public String detailed() {
		StringBuilder sb = new StringBuilder(summary());
		if (!displayName.isEmpty() && !displayName.equalsIgnoreCase(itemId)) {
			sb.append(" (\"").append(displayName).append("\")");
		}
		if (!dataFingerprint.isEmpty()) {
			sb.append(" [").append(dataFingerprint).append(']');
		}
		return sb.toString();
	}
}
