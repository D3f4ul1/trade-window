package com.tradewindow.menu;

import com.tradewindow.util.TradeItems;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One player's offer: the eighteen slots in the middle of their window.
 *
 * <p>The offer is the only part of the grid that holds items belonging to a player,
 * and it is stored separately from the header, so a header can never be mistaken for
 * an offer and never ends up in a swap. Both menus point at the same two offer
 * containers - one per player - which is what makes the trade a shared object rather
 * than two private halves that have to be kept in step.
 *
 * <p>Nothing here enforces who may touch the slots: that lives in {@link TradeSlot}
 * and {@link TradeMenu}, both of which are viewer-aware, whereas this class is not.
 */
public final class TradeOffer extends SimpleContainer {

	/** Slots in one player's offer. */
	public static final int SIZE = TradeInventory.SLOTS_PER_SIDE;

	/** Creates an empty offer. */
	public TradeOffer() {
		super(SIZE);
	}

	/**
	 * Removes and returns every stack in the offer.
	 *
	 * <p>Called by the swap and by every cancellation path, so the two lists the
	 * swap works with can never see each other's slots while they are being emptied.
	 *
	 * @return the stacks that were in the slots, in slot order
	 */
	public List<ItemStack> drain() {
		List<ItemStack> taken = new ArrayList<>(SIZE);
		for (int slot = 0; slot < SIZE; slot++) {
			ItemStack stack = getItem(slot);
			if (!stack.isEmpty()) {
				taken.add(stack);
				setItem(slot, ItemStack.EMPTY);
			}
		}
		setChanged();
		return taken;
	}

	/**
	 * Returns a copy of the offer without changing it.
	 *
	 * <p>Used to describe a trade in the history before the swap moves anything.
	 *
	 * @return the non-empty stacks, in slot order
	 */
	public List<ItemStack> contents() {
		List<ItemStack> present = new ArrayList<>(SIZE);
		for (int slot = 0; slot < SIZE; slot++) {
			ItemStack stack = getItem(slot);
			if (!stack.isEmpty()) {
				present.add(stack.copy());
			}
		}
		return present;
	}

	/**
	 * Returns the total value of the offer in points.
	 *
	 * @return the sum of {@link TradeItems#valueOf} over every stack
	 */
	public long value() {
		long total = 0L;
		for (int slot = 0; slot < SIZE; slot++) {
			total += TradeItems.valueOf(getItem(slot));
		}
		return total;
	}

	/**
	 * Reports whether the offer is empty.
	 *
	 * @return {@code true} when no slot holds anything
	 */
	public boolean empty() {
		for (int slot = 0; slot < SIZE; slot++) {
			if (!getItem(slot).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** Empties the offer. */
	public void emptyOut() {
		clearContent();
	}
}
