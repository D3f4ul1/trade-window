package com.tradewindow.trade;

import com.tradewindow.config.TradeConfig;
import com.tradewindow.core.TradeConfigData;
import com.tradewindow.core.TradeDistance;
import com.tradewindow.core.TradeRecord;
import com.tradewindow.core.TradeSide;
import com.tradewindow.menu.TradeMenu;
import com.tradewindow.util.TradeUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns every live trade and drives its lifecycle.
 *
 * <p>This is the single authority for trading on the server. No client is ever
 * trusted: an interaction carries only "I want to trade with that player", and every
 * precondition - token held, not already trading, same dimension, one request at a
 * time - is re-checked here.
 *
 * <p><b>Chat is an announcement channel, not a control panel.</b> Locking and
 * cancelling are done by clicking the blocks inside the window, so the only messages
 * are the events a player cannot see for themselves: the request, the start, their own
 * lock, completion, and the way a trade ended early. Putting an item in the grid says
 * nothing, the partner locking says nothing (their LOCK block turns into a spent green
 * pane), and there are no countdown warnings. Accept and decline are the only
 * remaining chat buttons, because a request has to be answerable before any window
 * exists.
 *
 * <p><b>A trade that cannot open costs nothing.</b> Every precondition is settled
 * <em>before</em> the requester's Trade Token is spent, so the token is only ever paid
 * for a window that really opens. With the distance rule switched on and the two
 * players out of range, the request is refused up front and the acceptance is refused
 * without spending - the request is put back unchanged, so walking closer and pressing
 * Accept again within its timeout still works and still costs exactly one token. The
 * distance question itself is answered in one place, {@link TradeDistance}, so the
 * request, the acceptance and the tick loop cannot disagree about it.
 *
 * <p><b>Failure posture.</b> Whenever a precondition cannot be verified, the trade
 * ends and every item goes back to its owner rather than the swap proceeding on a
 * guess. Items that cannot be handed over - because their owner is gone - are held for
 * that player's next login instead of being dropped into the world.
 */
public final class TradeManager {

	/** Player UUID to the session they are currently in. */
	private static final Map<UUID, TradeSession> SESSIONS = new ConcurrentHashMap<>();

	/** Target UUID to the request they have not answered yet. */
	private static final Map<UUID, PendingRequest> REQUESTS = new ConcurrentHashMap<>();

	private TradeManager() {
	}

	/**
	 * A request waiting for an answer.
	 *
	 * <p>{@code tokenSlot} is the inventory slot the sender's Trade Token was in when
	 * the request was made. It is remembered rather than searched for later so that the
	 * token spent on acceptance is the one the sender actually used, and so that a
	 * sender who has since lost it is refused rather than charged a different tear.
	 */
	private record PendingRequest(UUID senderId, int tokenSlot, long sentAt) {
	}

	// ------------------------------------------------------------------
	// Request phase
	// ------------------------------------------------------------------

	/**
	 * Sends a trade request from {@code /trade <player>}, spending the first token in
	 * the sender's inventory.
	 *
	 * @param sender the initiating player
	 * @param target the invited player
	 * @return {@code true} when the request was sent
	 */
	public static boolean request(ServerPlayer sender, ServerPlayer target) {
		return request(sender, target, -1);
	}

	/**
	 * Sends a trade request, from both {@code /trade <player>} and right-clicking a
	 * player while holding a Trade Token.
	 *
	 * <p>The token is <em>not</em> spent here: it is spent when the other player accepts
	 * and the window actually opens, so an ignored or declined request costs nothing.
	 *
	 * @param sender             the initiating player
	 * @param target             the invited player
	 * @param preferredTokenSlot the slot to take the token from first, or {@code -1}
	 * @return {@code true} when the request was sent
	 */
	public static boolean request(ServerPlayer sender, ServerPlayer target, int preferredTokenSlot) {
		if (sender == target) {
			return fail(sender, "tradewindow.error.self");
		}

		TradeConfigData config = TradeConfig.get();

		int tokenSlot = -1;
		if (config.requireToken) {
			tokenSlot = TradeToken.findSlot(sender, preferredTokenSlot);
			if (tokenSlot < 0) {
				return fail(sender, "tradewindow.error.token", TradeToken.NAME);
			}
		}

		if (sessionOf(sender) != null) {
			return fail(sender, "tradewindow.error.busy.self");
		}
		if (sessionOf(target) != null) {
			return fail(sender, "tradewindow.error.busy.other", nameOf(target));
		}
		if (!config.allowCreativeTrading && sender.isCreative() != target.isCreative()) {
			return fail(sender, "tradewindow.error.creative");
		}
		if (!config.allowCrossDimensionTrading && sender.level() != target.level()) {
			return fail(sender, "tradewindow.error.world");
		}
		if (tooFarApart(sender, target, config)) {
			// Refuse now rather than hand the target a request that the very next tick
			// would cancel. Nothing has been spent at this point, so a refusal here is
			// free for both of them.
			return fail(sender, "tradewindow.error.distance.self",
					Integer.toString(config.maxDistanceBlocks));
		}
		if (REQUESTS.containsKey(target.getUUID())) {
			return fail(sender, "tradewindow.error.request.pending", nameOf(target));
		}

		REQUESTS.put(target.getUUID(),
				new PendingRequest(sender.getUUID(), tokenSlot, System.currentTimeMillis()));

		// Exactly one message, to the player who has to answer it, with both buttons
		// already attached. The sender is told nothing: their window is the answer.
		TradeUtils.send(target, TradeUtils.message("tradewindow.chat.request", nameOf(sender))
				.append(TradeUtils.buttons(TradeUtils.acceptButton(), TradeUtils.declineButton())));
		return true;
	}

	/**
	 * Accepts the request a player is holding, spends the sender's Trade Token and
	 * opens the window for both.
	 *
	 * <p>The token is verified and spent <em>before</em> the windows exist. If the
	 * sender no longer has the tear they used - they dropped it, died or moved it out of
	 * the slot it was in - nothing is opened and both are told, rather than the trade
	 * starting on a token that is no longer there.
	 *
	 * @param target the player who accepted
	 * @return {@code true} when a window was opened
	 */
	public static boolean accept(ServerPlayer target) {
		PendingRequest request = REQUESTS.remove(target.getUUID());
		if (request == null) {
			return fail(target, "tradewindow.error.request.none.accept");
		}

		ServerPlayer sender = playerById(target, request.senderId());
		if (sender == null) {
			return fail(target, "tradewindow.error.offline");
		}
		if (sessionOf(sender) != null || sessionOf(target) != null) {
			return fail(target, "tradewindow.error.busy.either");
		}
		TradeConfigData config = TradeConfig.get();
		if (!config.allowCrossDimensionTrading && sender.level() != target.level()) {
			return fail(target, "tradewindow.error.world");
		}
		if (tooFarApart(sender, target, config)) {
			// The token is spent two lines down, so the distance gate has to come
			// first. v1.4.0 checked the distance only in the tick loop, which spent the
			// tear, opened the window and then closed it on the next tick: the
			// requester paid for a trade that could never happen. The request goes back
			// onto the pending queue unchanged, so it is not the answer that is lost -
			// only the attempt.
			REQUESTS.put(target.getUUID(), request);
			TradeUtils.send(target, TradeUtils.error("tradewindow.error.distance.self",
					Integer.toString(config.maxDistanceBlocks)));
			TradeUtils.send(sender, TradeUtils.error("tradewindow.error.distance.other",
					nameOf(target), Integer.toString(config.maxDistanceBlocks)));
			return false;
		}
		if (config.requireToken && !TradeToken.consume(sender, request.tokenSlot())) {
			TradeUtils.send(target, TradeUtils.error("tradewindow.error.token.gone.other",
					nameOf(sender), TradeToken.NAME));
			TradeUtils.send(sender, TradeUtils.error("tradewindow.error.token.gone.self",
					TradeToken.NAME));
			return false;
		}

		TradeSession session = new TradeSession(sender, target, config.guiTimeoutSeconds);
		SESSIONS.put(sender.getUUID(), session);
		SESSIONS.put(target.getUUID(), session);

		for (TradeSide side : TradeSide.values()) {
			ServerPlayer player = session.player(side);
			TradeUtils.send(player, TradeUtils.message("tradewindow.chat.start"));
			player.openMenu(session.provider(side));
		}
		return true;
	}

	/**
	 * Declines the request a player is holding.
	 *
	 * @param target the player who declined
	 * @return {@code true} when a request was declined
	 */
	public static boolean decline(ServerPlayer target) {
		PendingRequest request = REQUESTS.remove(target.getUUID());
		if (request == null) {
			return fail(target, "tradewindow.error.request.none.decline");
		}

		// The requester is told, because a request that vanished without an answer
		// would leave them waiting on a window that is never going to open. The player
		// who declined already knows - they pressed the button.
		ServerPlayer sender = playerById(target, request.senderId());
		if (sender != null) {
			TradeUtils.send(sender, TradeUtils.message("tradewindow.chat.request.declined",
					nameOf(target)));
		}
		return true;
	}

	// ------------------------------------------------------------------
	// Window phase
	// ------------------------------------------------------------------

	/**
	 * Locks the caller's side. Called by clicking a green LOCK block in their own
	 * window, never by a command.
	 *
	 * <p>Silent when there is nothing to lock, so a click that arrives after the trade
	 * has already ended (a window closing under the player, say) cannot produce a stray
	 * error line. A second click on an already locked side does nothing at all, which is
	 * why a forged click cannot re-announce a lock.
	 *
	 * <p>When the configured value cap would be exceeded the lock is refused with a
	 * single error line instead: locking is the moment an offer becomes binding, so it
	 * is the only honest place to enforce a cap.
	 *
	 * <p>When the other side is already locked this also performs the swap, so the trade
	 * completes on the second lock rather than on a later tick.
	 *
	 * @param player the player locking
	 */
	public static void lock(ServerPlayer player) {
		TradeSession session = sessionOf(player);
		if (session == null || session.isFinished()) {
			return;
		}
		TradeSide side = session.sideOf(player.getUUID());
		if (side == null || session.isLocked(side)) {
			return;
		}

		TradeConfigData config = TradeConfig.get();
		if (config.maxTradeValue >= 0) {
			long total = session.offer(TradeSide.PLAYER_1).value()
					+ session.offer(TradeSide.PLAYER_2).value();
			if (total > config.maxTradeValue) {
				fail(player, "tradewindow.error.valuecap", config.maxTradeValue);
				return;
			}
		}

		session.lock(side);
		TradeUtils.send(player, TradeUtils.message("tradewindow.chat.lock"));

		if (session.bothLocked()) {
			complete(session);
		}
	}

	/**
	 * Cancels the trade the caller is in and returns every item. Called by clicking
	 * either red CANCEL block.
	 *
	 * @param player the player cancelling
	 */
	public static void cancel(ServerPlayer player) {
		TradeSession session = sessionOf(player);
		if (session == null) {
			return;
		}
		end(session, "tradewindow.chat.cancelled", null);
	}

	/**
	 * Called when a viewer closes the window while the trade is live.
	 *
	 * <p>Closing is treated exactly like pressing CANCEL: the buttons live in the
	 * window, so once it is gone the player cannot see or act on their offer, and
	 * leaving the session running would strand both offers until the timeout. The menu
	 * calls this from {@code removed()}, guarded so the server's own closes and a
	 * spectator's window are ignored.
	 *
	 * @param session the session whose window was closed
	 */
	public static void closedWindow(TradeSession session) {
		end(session, "tradewindow.chat.cancelled", null);
	}

	/**
	 * Handles a participant taking damage.
	 *
	 * <p>Disabled by {@code cancelOnDamage}. Ending the trade mid-fight is the default
	 * because combat is exactly when a player is most likely to be tricked into
	 * accepting a bad swap, and because a half-swapped trade during a fight is the one
	 * scenario where items could be missed.
	 *
	 * @param player the player who was hurt
	 */
	public static void onDamage(ServerPlayer player) {
		if (!TradeConfig.get().cancelOnDamage) {
			return;
		}
		TradeSession session = sessionOf(player);
		if (session != null) {
			end(session, "tradewindow.chat.cancelled", null);
		}
	}

	// ------------------------------------------------------------------
	// Administration
	// ------------------------------------------------------------------

	/**
	 * Returns every live trade.
	 *
	 * <p>Backs {@code /tradeadmin list}. The copy is stable, so an administrator
	 * listing trades while one of them ends cannot see a half-removed collection.
	 *
	 * @return the live sessions
	 */
	public static Collection<TradeSession> active() {
		return sessions();
	}

	/**
	 * Returns the trade a player is in.
	 *
	 * @param player the player, possibly {@code null}
	 * @return the session, or {@code null}
	 */
	public static TradeSession sessionOf(ServerPlayer player) {
		return player == null ? null : SESSIONS.get(player.getUUID());
	}

	/**
	 * Opens a live trade for an administrator to watch.
	 *
	 * <p>Read-only and side-effect free: the window is built from the same session, but
	 * nothing about the trade's state, timer or locks changes, and the administrator is
	 * never a participant. Closing the window simply stops watching.
	 *
	 * @param admin  the administrator
	 * @param target a player whose trade to show
	 * @return {@code true} when a window was opened
	 */
	public static boolean spectate(ServerPlayer admin, ServerPlayer target) {
		TradeSession session = sessionOf(target);
		if (session == null || session.involves(admin.getUUID())) {
			return false;
		}
		session.addSpectator(admin.getUUID());
		admin.openMenu(session.spectatorProvider());
		return true;
	}

	/**
	 * Forgets an administrator who closed their view.
	 *
	 * @param session  the session they were watching
	 * @param spectator the administrator
	 */
	public static void spectatorLeft(TradeSession session, ServerPlayer spectator) {
		session.removeSpectator(spectator.getUUID());
	}

	/**
	 * Returns recent completed trades for {@code /tradeadmin history}.
	 *
	 * @param limit        the maximum number of records
	 * @param playerFilter a player name, or {@code null} for every player
	 * @return the records, newest first
	 */
	public static List<TradeRecord> history(int limit, String playerFilter) {
		return TradeHistory.recent(limit, playerFilter);
	}

	// ------------------------------------------------------------------
	// Tick
	// ------------------------------------------------------------------

	/**
	 * Advances every live trade and drops stale requests.
	 *
	 * @param server the running server
	 */
	public static void tick(MinecraftServer server) {
		long now = System.currentTimeMillis();
		TradeConfigData config = TradeConfig.get();

		// Expired requests simply disappear. Nagging a player about a request they
		// ignored is exactly the chat noise this design removed.
		REQUESTS.values().removeIf(request -> now - request.sentAt() > config.tradeTimeoutSeconds * 1000L);

		for (TradeSession session : sessions()) {
			if (session.isFinished()) {
				continue;
			}
			if (!session.bothConnected()) {
				end(session, "tradewindow.chat.disconnected", null);
				continue;
			}
			// The distance rule is off by default: crossDistanceTrading skips it
			// entirely, and cancelOnMove is the master switch an operator can leave
			// alone while turning cross-distance off. Both of those switches, and the
			// radius they gate, live in TradeDistance - this is the same question the
			// request and the acceptance ask, so the three can never disagree.
			if (tooFarApart(session.player(TradeSide.PLAYER_1), session.player(TradeSide.PLAYER_2),
					config)) {
				end(session, "tradewindow.chat.distance", null);
				continue;
			}
			if (session.expired(now)) {
				end(session, "tradewindow.chat.timedout", null);
			}
		}
	}

	// ------------------------------------------------------------------
	// Player events
	// ------------------------------------------------------------------

	/**
	 * Handles a player leaving the server.
	 *
	 * <p>Requests they can no longer answer or honour are dropped, and their trade ends.
	 * The player who stayed gets told why; the one who left is not told anything,
	 * because they are no longer there to read it.
	 *
	 * @param player the departing player
	 */
	public static void onDisconnect(ServerPlayer player) {
		REQUESTS.remove(player.getUUID());
		REQUESTS.values().removeIf(request -> request.senderId().equals(player.getUUID()));

		TradeSession session = sessionOf(player);
		if (session != null) {
			end(session, "tradewindow.chat.disconnected", player);
		}
	}

	/**
	 * Handles a player dying: the trade ends and the items go back to their owners
	 * rather than lying in a window nobody can reach.
	 *
	 * @param player the player who died
	 */
	public static void onDeath(ServerPlayer player) {
		TradeSession session = sessionOf(player);
		if (session != null) {
			end(session, "tradewindow.chat.cancelled", null);
		}
	}

	/**
	 * Handles server shutdown.
	 *
	 * <p>Sessions do not survive a restart, but items do: every stack in every open
	 * window is written to the pending-returns file and handed back when its owner next
	 * logs in. Online players are included, because a shutdown gives nobody the chance
	 * to collect anything first.
	 *
	 * @param server the stopping server
	 */
	public static void onServerStopping(MinecraftServer server) {
		for (TradeSession session : sessions()) {
			if (!session.markFinished()) {
				continue;
			}
			forget(session);
			for (TradeSide side : TradeSide.values()) {
				List<ItemStack> items = session.drain(side);
				if (!items.isEmpty()) {
					PendingReturns.store(session.player(side).getUUID(), items);
				}
			}
			session.clear();
		}
		SESSIONS.clear();
		REQUESTS.clear();
		PendingReturns.flush();
		TradeHistory.close();
	}

	/**
	 * Hands back anything a player left behind in an interrupted trade.
	 *
	 * @param player the joining player
	 */
	public static void onPlayerJoin(ServerPlayer player) {
		List<ItemStack> stranded = PendingReturns.take(player.getUUID());
		if (stranded.isEmpty()) {
			return;
		}
		for (ItemStack stack : stranded) {
			TradeUtils.giveOrDrop(player, stack);
		}
		TradeUtils.send(player, TradeUtils.message("tradewindow.chat.returned", stranded.size()));
	}

	// ------------------------------------------------------------------
	// Terminal paths
	// ------------------------------------------------------------------

	/**
	 * Swaps both offers, records the trade and closes every window.
	 *
	 * <p>Items are drained before anything is handed over, so the two lists cannot see
	 * each other's slots while they are being emptied. The window is closed before the
	 * new items are given out, because after the swap the container is empty and nobody
	 * should still be looking at it. Anything on a player's cursor is returned to them by
	 * vanilla's own {@code removed}, which is why the menu calls {@code super.removed}
	 * first.
	 *
	 * @param session the session to complete
	 */
	private static void complete(TradeSession session) {
		if (!session.markFinished()) {
			return;
		}
		forget(session);

		ServerPlayer playerA = session.player(TradeSide.PLAYER_1);
		ServerPlayer playerB = session.player(TradeSide.PLAYER_2);

		// Snapshot before draining: the record must describe what was offered, not the
		// empty container the swap leaves behind.
		TradeRecord record = new TradeRecord(System.currentTimeMillis(),
				session.name(TradeSide.PLAYER_1), session.name(TradeSide.PLAYER_2),
				session.snapshot(TradeSide.PLAYER_1), session.snapshot(TradeSide.PLAYER_2),
				TradeRecord.OUTCOME_SUCCESS, "");

		List<ItemStack> offerA = session.drain(TradeSide.PLAYER_1);
		List<ItemStack> offerB = session.drain(TradeSide.PLAYER_2);
		session.clear();
		closeMenus(session);

		for (ItemStack stack : offerB) {
			TradeUtils.giveOrDrop(playerA, stack);
		}
		for (ItemStack stack : offerA) {
			TradeUtils.giveOrDrop(playerB, stack);
		}

		TradeHistory.record(record);

		for (TradeSide side : TradeSide.values()) {
			ServerPlayer player = session.player(side);
			TradeUtils.send(player, TradeUtils.message("tradewindow.chat.complete"));
			player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 1.2F);
		}
	}

	/**
	 * Ends a live trade without swapping and gives every item back.
	 *
	 * @param session    the session to end
	 * @param messageKey language key of the single chat line to send each player, or
	 *                   {@code null} to end silently
	 * @param except     a player to leave out of the message - the one who left, so the
	 *                   other is the only reader - or {@code null} for both
	 */
	private static void end(TradeSession session, String messageKey, ServerPlayer except) {
		if (!session.markFinished()) {
			return;
		}
		forget(session);
		closeMenus(session);

		for (TradeSide side : TradeSide.values()) {
			returnItems(session.player(side), session.drain(side));
		}
		session.clear();

		if (messageKey != null) {
			for (TradeSide side : TradeSide.values()) {
				ServerPlayer player = session.player(side);
				if (player != except && !player.hasDisconnected()) {
					TradeUtils.send(player, TradeUtils.message(messageKey));
				}
			}
		}
	}

	/**
	 * Hands a set of stacks back to its owner.
	 *
	 * @param player the owner
	 * @param items  the stacks to return
	 */
	private static void returnItems(ServerPlayer player, List<ItemStack> items) {
		if (items.isEmpty()) {
			return;
		}
		if (player.hasDisconnected()) {
			// Writing into a departed player's inventory can be lost with the
			// connection, so the items wait on disk for the next login instead.
			PendingReturns.store(player.getUUID(), items);
			return;
		}
		for (ItemStack stack : items) {
			TradeUtils.giveOrDrop(player, stack);
		}
	}

	/**
	 * Closes the participants' windows - and any administrator's view of them -
	 * without re-entering the cancel path.
	 */
	private static void closeMenus(TradeSession session) {
		for (TradeSide side : TradeSide.values()) {
			ServerPlayer player = session.player(side);
			if (!player.hasDisconnected() && player.containerMenu instanceof TradeMenu) {
				player.closeContainer();
			}
		}
		for (UUID spectatorId : session.spectators()) {
			ServerPlayer spectator = playerById(session.player(TradeSide.PLAYER_1), spectatorId);
			if (spectator != null && spectator.containerMenu instanceof TradeMenu menu
					&& menu.spectator()) {
				spectator.closeContainer();
			}
		}
	}

	/** Drops a session from the lookup table. */
	private static void forget(TradeSession session) {
		SESSIONS.remove(session.player(TradeSide.PLAYER_1).getUUID(), session);
		SESSIONS.remove(session.player(TradeSide.PLAYER_2).getUUID(), session);
	}

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	/** A stable copy of the live sessions, safe to iterate while ending them. */
	private static Collection<TradeSession> sessions() {
		return new LinkedHashSet<>(SESSIONS.values());
	}

	/**
	 * Reports whether two players are outside the configured distance allowance.
	 *
	 * <p>A thin adapter over {@link TradeDistance#tooFar}: reading the live positions is
	 * this class's job, deciding what they mean is the shared rule's. Used by all three
	 * callers - sending a request, accepting one, and the tick loop - which is the point
	 * of having one implementation.
	 *
	 * @param one    the first player
	 * @param other  the second player
	 * @param config the active configuration
	 * @return {@code true} when the trade is out of range right now
	 */
	private static boolean tooFarApart(ServerPlayer one, ServerPlayer other, TradeConfigData config) {
		return TradeDistance.tooFar(config, one.level() == other.level(), one.distanceToSqr(other));
	}

	private static String nameOf(ServerPlayer player) {
		return player.getName().getString();
	}

	private static ServerPlayer playerById(ServerPlayer context, UUID playerId) {
		MinecraftServer server = context.getServer();
		return server == null ? null : server.getPlayerList().getPlayer(playerId);
	}

	/** Sends an error line and reports the failure, so callers stay one-liners. */
	private static boolean fail(ServerPlayer player, String key, Object... args) {
		TradeUtils.send(player, TradeUtils.error(key, args));
		return false;
	}
}
