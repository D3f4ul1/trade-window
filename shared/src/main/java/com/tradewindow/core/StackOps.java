package com.tradewindow.core;

import java.util.List;

/**
 * The minimal view of an item stack that the trade algorithm needs.
 *
 * <p>This interface is the seam that lets one implementation of the swap logic
 * serve both Minecraft targets. {@code ItemStack} is shaped almost identically
 * in 1.20.1 and 26.3 for the operations that matter here, but the two versions
 * disagree about how item data is stored — raw NBT in 1.20.1, data components
 * from 1.20.5 onward — and about how an item is identified. Both version
 * modules therefore supply their own adapter and the algorithm below never
 * learns which one it is running against.
 *
 * <p>Implementations must be pure: no method may mutate the stack it is handed
 * except {@link #setCount(Object, int)}.
 *
 * @param <T> the concrete stack type, e.g. {@code ItemStack}
 */
public interface StackOps<T> {

	/**
	 * Reports whether the stack is empty.
	 *
	 * @param stack the stack to test
	 * @return {@code true} if the stack holds nothing
	 */
	boolean isEmpty(T stack);

	/**
	 * Returns the number of items in the stack.
	 *
	 * @param stack the stack to measure
	 * @return the stack size, {@code 0} when empty
	 */
	int count(T stack);

	/**
	 * Sets the number of items in the stack.
	 *
	 * <p>This is the one permitted mutation.
	 *
	 * @param stack the stack to modify
	 * @param count the new size
	 */
	void setCount(T stack, int count);

	/**
	 * Returns an independent deep copy.
	 *
	 * <p>Must copy item data as well as item type and count, so that a committed
	 * trade preserves enchantments, custom names and durability exactly.
	 *
	 * @param stack the stack to copy
	 * @return a detached copy
	 */
	T copy(T stack);

	/**
	 * Returns the largest legal stack size for this stack.
	 *
	 * @param stack the stack to measure
	 * @return the maximum stack size, always at least 1
	 */
	int maxStackSize(T stack);

	/**
	 * Reports whether two stacks are the same item with identical item data.
	 *
	 * @param a first stack
	 * @param b second stack
	 * @return {@code true} if the two could legally merge
	 */
	boolean sameItemAndData(T a, T b);

	/**
	 * Returns the namespaced id of the item, for example {@code minecraft:diamond}.
	 *
	 * @param stack the stack to inspect
	 * @return the registry id, lower-case, or an empty string for an empty stack
	 */
	String itemId(T stack);

	/**
	 * Returns a stable fingerprint of the stack's item data.
	 *
	 * <p>Used to prove that a copy preserved NBT/components exactly, and to log
	 * what was traded. Must be equal for two stacks that
	 * {@link #sameItemAndData(Object, Object)} reports as equal.
	 *
	 * @param stack the stack to fingerprint
	 * @return a hex digest, or an empty string when there is no item data
	 */
	String dataFingerprint(T stack);

	/**
	 * Returns the human-readable name shown to players and written to the log.
	 *
	 * @param stack the stack to describe
	 * @return the display name
	 */
	String displayName(T stack);

	/**
	 * Returns the list of stacks for an empty container of the given size.
	 *
	 * @param size how many slots the container has
	 * @return a list of empty stacks
	 */
	List<T> emptyContainer(int size);
}
