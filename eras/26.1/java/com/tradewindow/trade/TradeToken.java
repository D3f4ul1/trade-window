package com.tradewindow.trade;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Identity and inventory helpers for the Trade Token.
 *
 * <p>The token is not an item this mod registers. It is an ordinary Ghast Tear
 * carrying two components - a {@code minecraft:custom_name} of "Trade Token" and a
 * {@code minecraft:max_stack_size} of {@value #STACK_SIZE} - produced by the mod's
 * crafting recipe ({@code data/tradewindow/recipe/trade_token.json}).
 *
 * <p><b>Why the stack size is part of the identity.</b> A token is meant to be a
 * deliberate, finite resource: one craft yields one tear, sixteen of them fit in a
 * slot, and a stack of them is a visible commitment rather than an unbounded pile of
 * renamed junk. The component is what makes that true, so it is checked as well as
 * the name - a plain tear, or one renamed in an anvil, has no stack-size component
 * and is not a token.
 *
 * <p><b>Why the name and not a registry entry.</b> A custom item id would have to
 * be known by every connecting client, so a vanilla client would be kicked with
 * "Received a registry entry that is unknown to this client". With a vanilla item
 * plus a component, the server's registries are identical to vanilla's and the
 * token is still unmistakable: no Ghast Tear carries that name unless it was
 * crafted as one.
 *
 * <p>{@link #NAME} is an identity contract rather than a display string: it has to
 * match the {@code custom_name} in the recipe JSON byte for byte, because that is
 * how a crafted token is recognised. The user-visible mentions of the name are
 * still translated - chat lines pass this value in as a format argument from the
 * language file's own template.
 *
 * <p>The name is read through {@link ItemStack#getHoverName()}, which returns the
 * custom name when present and the plain item name otherwise, so an unnamed tear
 * can never be mistaken for a token. Renaming a token in an anvil also stops it
 * being a token, which is the intended reading of "named Trade Token". The stack
 * size is read from its own component for the same reason: identity is the whole
 * recipe result, not one field of it.
 */
public final class TradeToken {

	/** The exact name a Ghast Tear must carry to be a Trade Token. */
	public static final String NAME = "Trade Token";

	/**
	 * How many tokens stack, as set by the recipe's {@code minecraft:max_stack_size}
	 * component. Must match {@code data/tradewindow/recipe/trade_token.json}.
	 */
	public static final int STACK_SIZE = 16;

	private TradeToken() {
	}

	/**
	 * Reports whether a stack is a Trade Token.
	 *
	 * @param stack the stack to test; {@code null} is allowed
	 * @return {@code true} for a Ghast Tear named "Trade Token"
	 */
	public static boolean isToken(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !stack.is(Items.GHAST_TEAR)) {
			return false;
		}
		Integer stackSize = stack.get(DataComponents.MAX_STACK_SIZE);
		return stackSize != null && stackSize == STACK_SIZE
				&& NAME.equals(stack.getHoverName().getString());
	}

	/**
	 * Finds the first Trade Token in a player's inventory.
	 *
	 * @param player the player to search
	 * @return the inventory slot index, or {@code -1} when no token is present
	 */
	public static int findSlot(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (isToken(inventory.getItem(slot))) {
				return slot;
			}
		}
		return -1;
	}

	/**
	 * Finds the Trade Token a player is about to spend, preferring one specific slot.
	 *
	 * <p>Used when a player right-clicks another player: the token in the hand is
	 * "the one they used", so that slot is tried first and the rest of the inventory
	 * only as a fallback. The returned index is what the pending request remembers,
	 * so the same stack is the one spent when the request is accepted.
	 *
	 * @param player    the player to search
	 * @param preferred the slot to try first, or {@code -1} to just scan
	 * @return the inventory slot index, or {@code -1} when no token is present
	 */
	public static int findSlot(ServerPlayer player, int preferred) {
		Inventory inventory = player.getInventory();
		if (preferred >= 0 && preferred < inventory.getContainerSize()
				&& isToken(inventory.getItem(preferred))) {
			return preferred;
		}
		return findSlot(player);
	}

	/**
	 * Reports whether a player carries a Trade Token anywhere in their inventory.
	 *
	 * @param player the player to search
	 * @return {@code true} if at least one slot holds a token
	 */
	public static boolean isHolding(ServerPlayer player) {
		return findSlot(player) >= 0;
	}

	/**
	 * Spends exactly one token from one slot.
	 *
	 * <p>Either decrements a stack of two or more or clears the slot outright, so a
	 * stack of tokens is never destroyed by accident and an empty slot is never left
	 * holding a zero-sized stack.
	 *
	 * @param player the player spending the token
	 * @param slot   the slot the token was in
	 * @return {@code true} when a token was found and spent
	 */
	public static boolean consume(ServerPlayer player, int slot) {
		Inventory inventory = player.getInventory();
		if (slot < 0 || slot >= inventory.getContainerSize()) {
			return false;
		}
		ItemStack stack = inventory.getItem(slot);
		if (!isToken(stack)) {
			return false;
		}
		if (stack.getCount() > 1) {
			ItemStack reduced = stack.copy();
			reduced.shrink(1);
			inventory.setItem(slot, reduced);
		} else {
			inventory.setItem(slot, ItemStack.EMPTY);
		}
		inventory.setChanged();
		return true;
	}
}
