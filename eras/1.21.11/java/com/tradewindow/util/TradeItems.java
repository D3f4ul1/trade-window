package com.tradewindow.util;

import com.tradewindow.config.TradeConfig;
import com.tradewindow.core.TradeValueTable;
import net.minecraft.world.item.ItemStack;

/**
 * Item questions the trade window asks: what is this, is it allowed, what is it
 * worth.
 *
 * <p><b>How an item is named.</b> The id comes from the stack's own item holder -
 * every stack carries one - rather than from a registry lookup: no global registry is
 * touched, nothing is registered, and the call works identically on a dedicated
 * server. The result is the familiar namespaced id, for example
 * {@code minecraft:bedrock}.
 *
 * <p>1.21.11 is also the version that renamed {@code ResourceLocation} to
 * {@code net.minecraft.resources.Identifier}, so the id is read through
 * {@code ResourceKey.identifier()} here and through {@code ResourceKey.location()}
 * on the older targets.
 *
 * <p><b>Why the blacklist is matched by id and not by item.</b> The config's
 * {@code blacklistedItems} is a list of ids written by an operator, so comparing ids
 * keeps one source of truth and means a blacklisted id that does not exist yet simply
 * never matches, instead of failing the config load. The list is normalised to
 * lower-case by the shared model, which is why a plain {@code contains} is enough.
 */
public final class TradeItems {

	private TradeItems() {
	}

	/**
	 * Returns the namespaced id of a stack's item.
	 *
	 * @param stack the stack to name
	 * @return the id, for example {@code minecraft:diamond}
	 */
	public static String idOf(ItemStack stack) {
		return stack.getItemHolder().unwrapKey()
				.map(key -> key.identifier().toString())
				.orElse("");
	}

	/**
	 * Reports whether the config forbids this stack from entering a trade slot.
	 *
	 * @param stack the stack to test; {@code null} is allowed
	 * @return {@code true} when the item's id is on the blacklist
	 */
	public static boolean blacklisted(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		String id = idOf(stack);
		return !id.isEmpty() && TradeConfig.get().blacklistedItems.contains(id);
	}

	/**
	 * Returns the point value of a whole stack.
	 *
	 * @param stack the stack to value; {@code null} is allowed
	 * @return {@code count * value of one item}, or {@code 0} for an unlisted item
	 */
	public static long valueOf(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return 0L;
		}
		return TradeValueTable.valueOf(idOf(stack)) * stack.getCount();
	}
}
