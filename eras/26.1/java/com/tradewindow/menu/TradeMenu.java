package com.tradewindow.menu;

import com.tradewindow.core.TradeSide;
import com.tradewindow.trade.TradeManager;
import com.tradewindow.trade.TradeSession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The server-authoritative trade window, rendered by the vanilla double-chest screen
 * on an unmodified client.
 *
 * <p>The menu is opened with {@code MenuType.GENERIC_9x6}, so the client - vanilla
 * or not - draws a double chest with the title the server sent and reports clicks as
 * ordinary slot clicks. Slot <em>count</em> and <em>order</em> therefore have to be
 * what a double chest has: 54 grid slots, then the viewer's 27 main-inventory slots
 * and 9 hotbar slots.
 *
 * <p><b>The view is mirrored, and this class is where that happens.</b> Both players
 * see the same physical grid - a vanilla client gives us no way to reorder it - but
 * each gets their <em>own</em> {@link TradeMenu} instance, and the instance decides
 * which container each slot id points at:
 *
 * <pre>
 *              viewer A's menu            viewer B's menu
 *   slots  0- 8  A's header container       B's header container
 *   slots  9-26  A's offer                  B's offer
 *   slots 27-35  A's header container       B's header container
 *   slots 36-53  B's offer                  A's offer
 * </pre>
 *
 * <p>So rows 2-3 are always the viewer's own offer and rows 5-6 always their
 * partner's, whoever they are, while the underlying data is the single copy the
 * session owns. The header containers are per viewer too ({@link TradeHeader}), which
 * is what puts the viewer's own head at slot 4 and their partner's at slot 31 for
 * both of them. Nothing about this needs a custom packet: each menu syncs its own
 * slot contents with vanilla's per-tick broadcast, so the two windows show different
 * arrangements of the same trade.
 *
 * <p><b>The buttons are server-side.</b> The green LOCK and red CANCEL blocks are
 * ordinary items as far as the client is concerned - it renders them and reports the
 * click like any other slot click. {@link #clicked} is what turns that click into
 * {@link TradeManager#lock} or {@link TradeManager#cancel}, before any vanilla
 * movement logic runs, so no command and no chat button is involved and the window
 * never has to close first. Either LOCK block locks the player who clicked it and
 * either CANCEL block cancels the trade, so a header row can never be used to lock
 * the other player's side shut. (In Yarn this method is
 * {@code onSlotClick(int, int, SlotActionType, PlayerEntity)}; this tree uses
 * Mojang's official mappings, where it is {@code clicked(int, int, ContainerInput, Player)}.)
 *
 * <p><b>Spectators are read-only.</b> {@code /tradeadmin spectate} opens this same
 * menu with {@link #spectator()} true: the grid is visible, no click on it does
 * anything, the trade's state and timer are untouched, and closing the window does
 * not end the trade.
 */
public class TradeMenu extends AbstractContainerMenu {

	/** The session this menu is a view of. */
	private final TradeSession session;

	/** The side the viewer occupies; {@link TradeSide#PLAYER_1} for a spectator. */
	private final TradeSide viewer;

	/** Whether this window is the read-only admin view. */
	private final boolean spectator;

	/**
	 * Creates the menu.
	 *
	 * @param syncId          the window id assigned by the server
	 * @param playerInventory the viewing player's own inventory
	 * @param session         the backing session
	 * @param viewer          the side the viewer occupies
	 * @param spectator       {@code true} for the read-only admin view
	 */
	public TradeMenu(int syncId, Inventory playerInventory, TradeSession session, TradeSide viewer,
			boolean spectator) {
		super(MenuType.GENERIC_9x6, syncId);
		this.session = session;
		this.viewer = spectator ? TradeSide.PLAYER_1 : viewer;
		this.spectator = spectator;
		addTradeSlots();
		addPlayerSlots(playerInventory);
	}

	/** Returns the side the viewer of this menu occupies. */
	public TradeSide viewer() {
		return viewer;
	}

	/** Reports whether this window is the read-only admin view. */
	public boolean spectator() {
		return spectator;
	}

	/** Reports whether the trade behind this menu is still live. */
	public boolean isTradeOpen() {
		return !session.isFinished();
	}

	/**
	 * Reports whether the viewer may change a given side of the grid right now.
	 *
	 * @param side the side being touched; {@code null} is allowed
	 * @return {@code true} only for the viewer's own, unlocked side while the trade
	 *         is open, and never for a spectator
	 */
	public boolean canEdit(TradeSide side) {
		return !spectator && side != null && side == viewer && !session.isLocked(side) && isTradeOpen();
	}

	/**
	 * Returns the side whose offer a grid slot holds, in this viewer's arrangement.
	 *
	 * @param slot the menu slot
	 * @return the owning side, or {@code null} for a header slot or a player slot
	 */
	public TradeSide ownerOf(int slot) {
		if (slot >= TradeInventory.OWN_OFFER_START
				&& slot < TradeInventory.OWN_OFFER_START + TradeInventory.SLOTS_PER_SIDE) {
			return viewer;
		}
		if (slot >= TradeInventory.OTHER_OFFER_START
				&& slot < TradeInventory.OTHER_OFFER_START + TradeInventory.SLOTS_PER_SIDE) {
			return viewer.opposite();
		}
		return null;
	}

	private void addTradeSlots() {
		TradeHeader header = session.header(viewer);
		TradeOffer own = session.offer(viewer);
		TradeOffer other = session.offer(viewer.opposite());

		// Row 1: the viewer's own header - their head, their lock state.
		for (int column = 0; column < TradeInventory.ROW_SLOTS; column++) {
			addSlot(TradeSlot.header(header, column, x(column), y(0), this));
		}
		// Rows 2-3: the viewer's own offer.
		for (int index = 0; index < TradeInventory.SLOTS_PER_SIDE; index++) {
			addSlot(new TradeSlot(own, index, x(index % TradeInventory.ROW_SLOTS),
					y(1 + index / TradeInventory.ROW_SLOTS), this, viewer));
		}
		// Row 4: the partner's header, holding the partner's head and lock state.
		for (int column = 0; column < TradeInventory.ROW_SLOTS; column++) {
			addSlot(TradeSlot.header(header, TradeInventory.ROW_SLOTS + column, x(column), y(3), this));
		}
		// Rows 5-6: the partner's offer.
		for (int index = 0; index < TradeInventory.SLOTS_PER_SIDE; index++) {
			addSlot(new TradeSlot(other, index, x(index % TradeInventory.ROW_SLOTS),
					y(4 + index / TradeInventory.ROW_SLOTS), this, viewer.opposite()));
		}
	}

	private static int x(int column) {
		return 8 + column * 18;
	}

	private static int y(int row) {
		return 18 + row * 18;
	}

	private void addPlayerSlots(Inventory playerInventory) {
		// The standard double-chest layout: player rows below the 54 container
		// slots, hotbar last, matching the ordering the vanilla client expects.
		int top = 18 + 6 * 18 + 13;
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, top + row * 18));
			}
		}
		for (int column = 0; column < 9; column++) {
			addSlot(new Slot(playerInventory, column, 8 + column * 18, top + 58));
		}
	}

	/**
	 * Handles every click: first the two header buttons, then a hard filter on the
	 * grid, then vanilla.
	 *
	 * <p>A click on a header slot never reaches vanilla movement logic. It is either
	 * a button press - either LOCK block, or either CANCEL block - or it does nothing
	 * at all, which is what keeps the heads and panes exactly where the server put
	 * them even when a modified client sends the click anyway.
	 *
	 * <p>A click on an offer slot is refused unless {@link #canEdit} allows it, so a
	 * modified client cannot take a label, drop something into the partner's rows, or
	 * rearrange a side that has already locked.
	 *
	 * <p>A spectator may rearrange their <em>own</em> inventory while the window is
	 * open - that is ordinary inventory use and touches nothing in the trade - but
	 * the grid itself accepts nothing.
	 */
	@Override
	public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
		if (spectator) {
			if (slotId >= TradeInventory.SLOTS) {
				super.clicked(slotId, button, clickType, player);
			}
			return;
		}
		if (TradeInventory.isGridSlot(slotId)) {
			int headerRow = TradeInventory.headerRowOf(slotId);
			if (headerRow >= 0) {
				pressHeader(slotId, clickType, player);
				return;
			}
			if (!canEdit(ownerOf(slotId))) {
				return;
			}
		}
		super.clicked(slotId, button, clickType, player);
	}

	/**
	 * Turns a click on a header row into the action its button stands for.
	 *
	 * <p>Only an ordinary click counts: shift-clicks, drags and number-key swaps on a
	 * header slot are ignored outright rather than treated as button presses.
	 *
	 * <p>Either LOCK block locks the side of the player who pressed it, and only that
	 * player ever has one under their cursor in their own window - the two menus map
	 * the same slot ids onto different contents - so no arrangement of clicks lets one
	 * player lock the other's offer. The CANCEL blocks belong to the trade rather than
	 * to a side: either player may press either one.
	 *
	 * @param slotId    the header slot that was clicked
	 * @param clickType what kind of click it was
	 * @param player    who clicked
	 */
	private void pressHeader(int slotId, ContainerInput clickType, Player player) {
		if (clickType != ContainerInput.PICKUP || !(player instanceof ServerPlayer server)) {
			return;
		}
		if (slotId == TradeInventory.lockSlot(TradeInventory.TOP_HEADER)
				|| slotId == TradeInventory.lockSlot(TradeInventory.BOTTOM_HEADER)) {
			TradeManager.lock(server);
			return;
		}
		if (slotId == TradeInventory.cancelSlot(TradeInventory.TOP_HEADER)
				|| slotId == TradeInventory.cancelSlot(TradeInventory.BOTTOM_HEADER)) {
			TradeManager.cancel(server);
		}
	}

	/**
	 * Shift-click movement.
	 *
	 * <p>Shift-clicking moves stacks between the viewer's own offer and their
	 * inventory, and never anywhere else: a locked side refuses both directions, a
	 * header row is never a source or a destination, the partner's offer is never a
	 * destination, and a spectator moves nothing at all.
	 */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		if (spectator || index < 0 || index >= slots.size()) {
			return ItemStack.EMPTY;
		}
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();

		if (index < TradeInventory.SLOTS) {
			// Own offer -> own inventory. Header slots and the partner's rows answer
			// canEdit(their side) with false.
			if (!canEdit(ownerOf(index))) {
				return ItemStack.EMPTY;
			}
			if (!moveItemStackTo(stack, TradeInventory.SLOTS, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else {
			// Own inventory -> own offer.
			if (!canEdit(viewer)) {
				return ItemStack.EMPTY;
			}
			if (!moveItemStackTo(stack, TradeInventory.OWN_OFFER_START,
					TradeInventory.OWN_OFFER_START + TradeInventory.SLOTS_PER_SIDE, false)) {
				return ItemStack.EMPTY;
			}
		}

		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return original;
	}

	/**
	 * Reports whether the menu may stay open.
	 *
	 * <p>The server answers from the live session; the client always answers
	 * {@code true} so that only the authoritative side can close the window. Distance
	 * and timeouts are handled by the tick loop rather than here, so a player who walks
	 * away or runs out of time is told the trade closed instead of having it vanish
	 * under them.
	 */
	@Override
	public boolean stillValid(Player player) {
		if (!(player instanceof ServerPlayer)) {
			return true;
		}
		if (!isTradeOpen()) {
			return false;
		}
		return spectator ? session.isSpectator(player.getUUID()) : session.involves(player.getUUID());
	}

	/**
	 * Ends the trade when a viewer closes the window while it is still live.
	 *
	 * <p>A vanilla chest can be closed at any moment - Esc, or the inventory key - and
	 * once it is closed the player has no way back into it: the buttons live in the
	 * window and there is no command that stands in for them. Leaving the session
	 * running would strand both offers until the timeout with nobody able to see or act
	 * on them, so closing the window ends the trade and returns every item, the same
	 * outcome as pressing CANCEL. The {@code isTradeOpen} guard makes this a no-op
	 * whenever the server closes the menu itself, because by then the session has
	 * already been resolved.
	 *
	 * <p>Opening the chat does not close the container, so reading or typing a message
	 * mid-trade changes nothing; only closing the window does. A spectator's window is
	 * not the trade, so their comings and goings are ignored.
	 */
	@Override
	public void removed(Player player) {
		super.removed(player);
		if (player instanceof ServerPlayer server) {
			if (spectator) {
				TradeManager.spectatorLeft(session, server);
			} else if (isTradeOpen()) {
				TradeManager.closedWindow(session);
			}
		}
	}
}
