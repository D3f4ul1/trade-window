package com.tradewindow.trade;

import com.tradewindow.core.ItemSnapshot;
import com.tradewindow.core.TradeSide;
import com.tradewindow.menu.TradeHeader;
import com.tradewindow.menu.TradeMenu;
import com.tradewindow.menu.TradeOffer;
import com.tradewindow.util.TradeItems;
import com.tradewindow.util.TradeText;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A live trade: two offers, two headers, two locks, a deadline and any number of
 * read-only spectators.
 *
 * <p><b>The session owns the data, the menus own the view.</b> There is exactly one
 * authoritative copy of each player's offer and one header per viewer
 * ({@link TradeHeader}). Each player's {@link TradeMenu} points its slot ids at those
 * containers; vanilla syncs each menu independently by polling slot contents, so a
 * change made by one player appears in the other's window on the next tick with no
 * bespoke packet, and the two windows can show different arrangements of the same
 * items.
 *
 * <p>Sides are fixed when the session is created and never change: the player who
 * sent the request is always {@link TradeSide#PLAYER_1} and the player who accepted
 * always {@link TradeSide#PLAYER_2}. A side is an identity, not a screen position -
 * each viewer sees their own offer in the middle rows regardless of which side they
 * occupy.
 *	 * <p>The session is the unit of atomicity. {@link #drain} removes items from a
	 * player's eighteen slots, and the manager either swaps both drained lists or hands
	 * each back to its owner; there is no path that leaves an item in limbo.
	 *
	 * <p><b>The distance rule is not decided here.</b> Whether the two are too far
	 * apart to trade is answered by {@link com.tradewindow.core.TradeDistance}, which
	 * the manager asks before a request is sent, before the requester's token is spent
	 * and on every tick. One definition is what keeps those three answers from
	 * disagreeing, which is exactly how v1.4.0 managed to charge a player a Trade Token
	 * for a trade the tick loop then cancelled.
	 */
public final class TradeSession {

	/** Language key of the window title. */
	public static final String TITLE_KEY = "container.tradewindow.title";

	private final ServerPlayer player1;
	private final ServerPlayer player2;
	private final TradeOffer offer1;
	private final TradeOffer offer2;
	private final TradeHeader header1;
	private final TradeHeader header2;
	private final Set<UUID> spectators = new LinkedHashSet<>();
	private final long startedAt;
	private final long deadline;

	private boolean locked1;
	private boolean locked2;
	private boolean finished;

	/**
	 * Creates a session, paints its headers and starts its timer.
	 *
	 * @param player1           the player who sent the request
	 * @param player2           the player who accepted
	 * @param guiTimeoutSeconds how long the window may sit idle before it times out
	 */
	public TradeSession(ServerPlayer player1, ServerPlayer player2, int guiTimeoutSeconds) {
		this.player1 = player1;
		this.player2 = player2;
		this.offer1 = new TradeOffer();
		this.offer2 = new TradeOffer();
		this.header1 = new TradeHeader(TradeSide.PLAYER_1);
		this.header2 = new TradeHeader(TradeSide.PLAYER_2);
		this.startedAt = System.currentTimeMillis();
		this.deadline = startedAt + guiTimeoutSeconds * 1000L;
		repaintHeaders();
	}

	/**
	 * Returns the player on a side.
	 *
	 * @param side the side
	 * @return the player occupying it
	 */
	public ServerPlayer player(TradeSide side) {
		return side == TradeSide.PLAYER_1 ? player1 : player2;
	}

	/**
	 * Returns the display name of the player on a side.
	 *
	 * @param side the side
	 * @return the player's name, as shown in chat and on their head
	 */
	public String name(TradeSide side) {
		return player(side).getName().getString();
	}

	/**
	 * Resolves which side a player occupies.
	 *
	 * @param playerId the player's UUID
	 * @return the side, or {@code null} if that player is not a participant
	 */
	public TradeSide sideOf(UUID playerId) {
		if (player1.getUUID().equals(playerId)) {
			return TradeSide.PLAYER_1;
		}
		if (player2.getUUID().equals(playerId)) {
			return TradeSide.PLAYER_2;
		}
		return null;
	}

	/**
	 * Reports whether a player is one of the two participants.
	 *
	 * @param playerId the player's UUID
	 * @return {@code true} for either participant
	 */
	public boolean involves(UUID playerId) {
		return sideOf(playerId) != null;
	}

	/**
	 * Returns a side's offer.
	 *
	 * @param side the side
	 * @return the offer container
	 */
	public TradeOffer offer(TradeSide side) {
		return side == TradeSide.PLAYER_1 ? offer1 : offer2;
	}

	/**
	 * Returns a viewer's header container.
	 *
	 * @param viewer the side whose window the header belongs to
	 * @return the header container
	 */
	public TradeHeader header(TradeSide viewer) {
		return viewer == TradeSide.PLAYER_1 ? header1 : header2;
	}

	/**
	 * Reports whether a side has locked its offer.
	 *
	 * @param side the side to test
	 * @return {@code true} when that side is locked
	 */
	public boolean isLocked(TradeSide side) {
		return side == TradeSide.PLAYER_1 ? locked1 : locked2;
	}

	/**
	 * Locks a side and repaints both headers, so both viewers see the new state
	 * without a chat message.
	 *
	 * <p>The lock is one-way within a trade: the green LOCK block becomes a spent
	 * green pane reading "LOCKED", so there is nothing left to click and no way to
	 * change an offer after both sides have agreed to it. A player who locks by
	 * mistake cancels instead.
	 *
	 * @param side the side to lock
	 */
	public void lock(TradeSide side) {
		if (side == TradeSide.PLAYER_1) {
			locked1 = true;
		} else {
			locked2 = true;
		}
		repaintHeaders();
	}

	/** Reports whether both sides have locked, which is what triggers the swap. */
	public boolean bothLocked() {
		return locked1 && locked2;
	}

	/** Returns when the window was opened, in milliseconds. */
	public long startedAt() {
		return startedAt;
	}

	/**
	 * Returns how long the window has been open.
	 *
	 * @param now the current wall-clock time in milliseconds
	 * @return the elapsed time in whole seconds
	 */
	public long elapsedSeconds(long now) {
		return Math.max(0L, (now - startedAt) / 1000L);
	}

	/**
	 * Reports whether the window has sat open for longer than its timeout.
	 *
	 * @param now the current wall-clock time in milliseconds
	 * @return {@code true} when the deadline has passed
	 */
	public boolean expired(long now) {
		return now >= deadline;
	}

	/**
	 * Marks the session finished, exactly once.
	 *
	 * @return {@code true} for the caller that finished it, {@code false} for every
	 *         later caller, which is what keeps the swap and the return path from both
	 *         running on the same trade
	 */
	public boolean markFinished() {
		if (finished) {
			return false;
		}
		finished = true;
		return true;
	}

	/** Reports whether the session has already been resolved. */
	public boolean isFinished() {
		return finished;
	}

	/** Reports whether both players are still online and connected. */
	public boolean bothConnected() {
		return !player1.hasDisconnected() && !player2.hasDisconnected();
	}

	/**
	 * Builds the window title both players see.
	 *
	 * <p>One title for both, and no player names in it: both are looking at the same
	 * kind of grid, the heads in the header rows say who is who, and a title that
	 * repeated two names would be the longest thing on the screen for the least
	 * information.
	 *
	 * <p>The text is resolved here rather than left as a translatable component. This
	 * mod is server-side only, so no vanilla client has its language file, and a
	 * translatable title would reach the player as the raw key
	 * {@code container.tradewindow.title}. Resolving the key from the mod's own
	 * language file - where the words still live, and still under that key - sends
	 * finished text that every client renders.
	 *
	 * @return the title, for example {@code Trading Window}
	 */
	public String title() {
		return TradeText.get(TITLE_KEY);
	}

	/**
	 * Builds the menu provider that opens this window for one participant.
	 *
	 * @param side the viewer's side
	 * @return the provider, carrying the window title
	 */
	public MenuProvider provider(TradeSide side) {
		return new SimpleMenuProvider(
				(syncId, playerInventory, player) -> new TradeMenu(syncId, playerInventory, this, side, false),
				Component.literal(title()));
	}

	/**
	 * Builds the menu provider that opens the read-only admin view.
	 *
	 * <p>The viewer side is {@link TradeSide#PLAYER_1} so the top header row holds
	 * player one's head, which makes the two offers identifiable without a legend.
	 *
	 * @return the provider for a spectator
	 */
	public MenuProvider spectatorProvider() {
		return new SimpleMenuProvider(
				(syncId, playerInventory, player) -> new TradeMenu(syncId, playerInventory, this,
						TradeSide.PLAYER_1, true),
				Component.literal(title()));
	}

	/**
	 * Records an administrator as watching this trade.
	 *
	 * @param playerId the spectator's UUID
	 */
	public void addSpectator(UUID playerId) {
		spectators.add(playerId);
	}

	/**
	 * Forgets an administrator who has stopped watching.
	 *
	 * @param playerId the spectator's UUID
	 */
	public void removeSpectator(UUID playerId) {
		spectators.remove(playerId);
	}

	/**
	 * Reports whether a player is watching without taking part.
	 *
	 * @param playerId the player's UUID
	 * @return {@code true} for a spectator
	 */
	public boolean isSpectator(UUID playerId) {
		return spectators.contains(playerId);
	}

	/** Returns a stable copy of the spectator list, safe to iterate while closing. */
	public Set<UUID> spectators() {
		return new LinkedHashSet<>(spectators);
	}

	/**
	 * Removes and returns every stack in one side's offer.
	 *
	 * @param side the offer to empty
	 * @return the stacks that were in the slots, in slot order
	 */
	public List<ItemStack> drain(TradeSide side) {
		return offer(side).drain();
	}

	/**
	 * Describes one side's offer for the trade history.
	 *
	 * <p>Taken before the swap, so the record says what was actually offered.
	 *
	 * @param side the offer to describe
	 * @return one snapshot per non-empty stack, in slot order
	 */
	public List<ItemSnapshot> snapshot(TradeSide side) {
		List<ItemSnapshot> snapshots = new ArrayList<>();
		for (ItemStack stack : offer(side).contents()) {
			snapshots.add(new ItemSnapshot(TradeItems.idOf(stack), stack.getCount(),
					stack.getHoverName().getString(), ""));
		}
		return snapshots;
	}

	/** Empties everything: both offers and both header rows. */
	public void clear() {
		offer1.emptyOut();
		offer2.emptyOut();
		header1.empty();
		header2.empty();
	}

	/**
	 * Repaints both header containers from the current lock state.
	 *
	 * <p>Called on creation and on every lock, so the two windows never disagree
	 * about who has locked. Each header is painted for its own viewer, which is what
	 * puts that viewer's head in the top row of their own window.
	 */
	private void repaintHeaders() {
		header1.paint(player1, player2, locked1, locked2);
		header2.paint(player2, player1, locked2, locked1);
	}
}
