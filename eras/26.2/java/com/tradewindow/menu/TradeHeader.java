package com.tradewindow.menu;

import com.tradewindow.core.TradeSide;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;

/**
 * One viewer's two header rows.
 *
 * <p>Each player's menu has its <em>own</em> header container, which is what makes
 * the mirroring work: the row at the top of the window always belongs to the player
 * looking at it, so it holds that player's head, and the row further down holds
 * their partner's. Both containers are painted from the same session state, so the
 * two views never disagree about who has locked.
 *
 * <p>Eighteen slots, laid out as two rows of nine. Offsets inside a row are the ones
 * {@link TradeInventory} defines, and nothing here is ever movable: the slots are
 * wrapped by {@link TradeSlot#header} on the way into a menu, and their contents are
 * only ever written by the server.
 */
public final class TradeHeader extends SimpleContainer {

	/** Slots in this container: two rows of nine. */
	public static final int SIZE = TradeInventory.SLOTS_PER_SIDE;

	/** Row index of the viewer's own header row. */
	public static final int OWN_ROW = 0;

	/** Row index of the partner's header row. */
	public static final int PARTNER_ROW = 1;

	private final TradeSide viewer;

	/**
	 * Creates the container for one viewer.
	 *
	 * @param viewer the side whose window this is
	 */
	public TradeHeader(TradeSide viewer) {
		super(SIZE);
		this.viewer = viewer;
	}

	/** Returns the side this header belongs to. */
	public TradeSide viewer() {
		return viewer;
	}

	/**
	 * Paints both rows.
	 *
	 * <p>Only the heads carry identity, and their colour is viewer-relative: the
	 * viewer's own row is green and the partner's red, so the colour says "you" or
	 * "them" rather than naming a side the viewer has no way to see. Both names are
	 * the real player names, so neither viewer has to translate anything.
	 *
	 * @param own            the viewer
	 * @param partner        the other participant
	 * @param ownLocked      whether the viewer has locked
	 * @param partnerLocked  whether the partner has locked
	 */
	public void paint(ServerPlayer own, ServerPlayer partner, boolean ownLocked, boolean partnerLocked) {
		TradeInventory.paintHeaderRow(this, rowBase(OWN_ROW), own, ownLocked, ChatFormatting.GREEN);
		TradeInventory.paintHeaderRow(this, rowBase(PARTNER_ROW), partner, partnerLocked, ChatFormatting.RED);
	}

	/**
	 * Rewrites one row's LOCK block.
	 *
	 * @param row    {@link #OWN_ROW} or {@link #PARTNER_ROW}
	 * @param locked whether that row's owner has locked
	 */
	public void refreshLock(int row, boolean locked) {
		TradeInventory.refreshLock(this, rowBase(row), locked);
	}

	private static int rowBase(int row) {
		return row * TradeInventory.ROW_SLOTS;
	}

	/** Empties the container, which is how a finished trade clears the grid. */
	public void empty() {
		clearContent();
	}
}
