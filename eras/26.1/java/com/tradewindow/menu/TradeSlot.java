package com.tradewindow.menu;

import com.tradewindow.core.TradeSide;
import com.tradewindow.util.TradeItems;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * One slot of the trade grid.
 *
 * <p>The rules that keep a trade fair live here as well as in {@link TradeMenu},
 * because a slot's {@code mayPlace}/{@code mayPickup} are consulted by <em>every</em>
 * interaction path the game offers - mouse clicks, shift-clicks, drag-and-drop,
 * number-key swaps, double-click gathering and quick-craft - whereas the menu only
 * sees the click that arrived. Putting the checks in the slot closes all of those
 * paths at once, including ones the click handler never sees.
 *
 * <p>Rules:
 *
 * <ul>
 *   <li>a player may only touch the slots holding <em>their own</em> offer;</li>
 *   <li>a side that has locked accepts no changes at all, from either player;</li>
 *   <li>the header rows (slots 0-8 and 27-35) are display only: their items can
 *       never be taken, replaced or moved, so the server stays the sole author of
 *       every head, pane and button. A click on a header slot is either a button
 *       press, handled by {@link TradeMenu#clicked}, or nothing at all;</li>
 *   <li>an item on the config blacklist never enters the grid - refused silently,
 *       because a trade window is not the place to argue about item policy;</li>
 *   <li>a spectator's window accepts nothing at all.</li>
 * </ul>
 *
 * <p>{@code isActive()} is deliberately not overridden. It plays no part in this
 * menu's rules, and leaving it at vanilla's {@code true} keeps the header slots
 * ordinary slots in every code path that inspects them.
 */
public class TradeSlot extends Slot {

	private final TradeMenu menu;

	/** The side whose offer this slot belongs to, or {@code null} for a header slot. */
	private final TradeSide owner;

	/**
	 * Creates an offer slot.
	 *
	 * @param container the offer container
	 * @param index     the index inside that container
	 * @param x         GUI-local x position
	 * @param y         GUI-local y position
	 * @param menu      the owning menu
	 * @param owner     the player whose offer this slot belongs to
	 */
	public TradeSlot(Container container, int index, int x, int y, TradeMenu menu, TradeSide owner) {
		super(container, index, x, y);
		this.menu = menu;
		this.owner = owner;
	}

	/**
	 * Creates a display-only slot for a header row: a head, a separator pane or one
	 * of the two buttons.
	 *
	 * @param container the header container
	 * @param index     the index inside that container
	 * @param x         GUI-local x position
	 * @param y         GUI-local y position
	 * @param menu      the owning menu
	 * @return the locked slot
	 */
	public static TradeSlot header(Container container, int index, int x, int y, TradeMenu menu) {
		return new TradeSlot(container, index, x, y, menu, null);
	}

	@Override
	public boolean mayPlace(ItemStack stack) {
		return owner != null && stack != null && !stack.isEmpty() && menu.canEdit(owner)
				&& !TradeItems.blacklisted(stack);
	}

	@Override
	public boolean mayPickup(Player player) {
		return owner != null && menu.canEdit(owner);
	}

	/**
	 * Reports whether the slot may be modified at all.
	 *
	 * <p>Vanilla consults this before letting a click rearrange a slot's contents,
	 * so it has to agree with {@link #mayPickup}: a locked or foreign slot must
	 * answer {@code false} or a click could still shuffle what is in it.
	 */
	@Override
	public boolean allowModification(Player player) {
		return mayPickup(player);
	}
}
