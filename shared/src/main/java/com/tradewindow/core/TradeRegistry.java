package com.tradewindow.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bookkeeping for who is trading with whom and who is waiting in line.
 *
 * <p>This is the single authority on three edge cases that are pure logic and
 * therefore live here rather than in either version's manager:
 * <ul>
 *   <li><b>Self-trade.</b> A player cannot request a trade with themselves.</li>
 *   <li><b>One trade at a time.</b> A player already in a session cannot start
 *       or accept another.</li>
 *   <li><b>Contested targets.</b> When two players request the same third
 *       player, requests queue FIFO and only the head of the queue is
 *       actionable — the target sees one prompt at a time.</li>
 * </ul>
 *
 * <p>The registry stores only UUIDs and {@link TradeRequest}s; the heavyweight
 * session objects (containers, item stacks) stay in the version-specific
 * manager, which keeps this class trivially unit testable.
 *
 * <p>Not thread safe. All mutation is expected on the server thread.
 */
public final class TradeRegistry {

	/** What happened to a submitted request. */
	public enum RequestOutcome {
		/** The request is now the actionable head of the target's queue; show a prompt. */
		ACCEPTED,
		/** The target is busy with an earlier request; this one waits its turn. */
		QUEUED,
		/** The sender and target are the same player. */
		REJECTED_SELF,
		/** The sender is already in an open trade. */
		REJECTED_SENDER_BUSY,
		/** The sender already has an unresolved request outstanding. */
		REJECTED_DUPLICATE,
		/** The target has opted out of trade requests. */
		REJECTED_TARGET_OPTED_OUT;

		/**
		 * Reports whether the request was accepted in some form.
		 *
		 * @return {@code true} for {@link #ACCEPTED} and {@link #QUEUED}
		 */
		public boolean isAccepted() {
			return this == ACCEPTED || this == QUEUED;
		}
	}

	/** Target player -> FIFO queue of requests awaiting their attention. */
	private final Map<UUID, Deque<TradeRequest>> requestsByTarget = new HashMap<>();

	/** Sender player -> the one unresolved request they have outstanding. */
	private final Map<UUID, TradeRequest> requestBySender = new HashMap<>();

	/** Player -> the partner they are currently trading with. */
	private final Map<UUID, UUID> sessionPartner = new HashMap<>();

	/** Players who ran {@code /trade toggle} to stop receiving requests. */
	private final Set<UUID> optedOut = new HashSet<>();

	/**
	 * Submits a trade request.
	 *
	 * @param request the request to submit
	 * @return what the caller should do next
	 */
	public RequestOutcome submit(TradeRequest request) {
		if (request.senderId().equals(request.targetId())) {
			return RequestOutcome.REJECTED_SELF;
		}
		if (isTrading(request.senderId())) {
			return RequestOutcome.REJECTED_SENDER_BUSY;
		}
		if (requestBySender.containsKey(request.senderId())) {
			return RequestOutcome.REJECTED_DUPLICATE;
		}
		if (optedOut.contains(request.targetId())) {
			return RequestOutcome.REJECTED_TARGET_OPTED_OUT;
		}

		Deque<TradeRequest> queue = requestsByTarget.computeIfAbsent(request.targetId(), k -> new ArrayDeque<>());
		boolean firstInLine = queue.isEmpty();
		queue.addLast(request);
		requestBySender.put(request.senderId(), request);
		return firstInLine ? RequestOutcome.ACCEPTED : RequestOutcome.QUEUED;
	}

	/**
	 * Returns the one request the target should currently be shown.
	 *
	 * @param targetId the invited player
	 * @return the head of their queue, or {@code null} if they have no requests
	 */
	public TradeRequest actionableFor(UUID targetId) {
		Deque<TradeRequest> queue = requestsByTarget.get(targetId);
		return queue == null ? null : queue.peekFirst();
	}

	/**
	 * Returns the request a player has outstanding as a sender.
	 *
	 * @param senderId the initiating player
	 * @return their pending request, or {@code null} if they have none
	 */
	public TradeRequest outgoingFor(UUID senderId) {
		return requestBySender.get(senderId);
	}

	/**
	 * Removes a request from the queues after it has been accepted, declined or expired.
	 *
	 * @param request the request to retire
	 */
	public void retire(TradeRequest request) {
		Deque<TradeRequest> queue = requestsByTarget.get(request.targetId());
		if (queue != null) {
			queue.remove(request);
			if (queue.isEmpty()) {
				requestsByTarget.remove(request.targetId());
			}
		}
		requestBySender.remove(request.senderId());
		request.resolve();
	}

	/**
	 * Marks two players as being in an open trade.
	 *
	 * @param playerA one participant
	 * @param playerB the other participant
	 * @return {@code true} if the session was registered, {@code false} if either
	 *         player was already trading and the caller must abort
	 */
	public boolean startSession(UUID playerA, UUID playerB) {
		if (isTrading(playerA) || isTrading(playerB)) {
			return false;
		}
		sessionPartner.put(playerA, playerB);
		sessionPartner.put(playerB, playerA);
		return true;
	}

	/**
	 * Clears the session flag for a player and their partner.
	 *
	 * @param playerId either participant of the session that just ended
	 */
	public void endSession(UUID playerId) {
		UUID partner = sessionPartner.remove(playerId);
		if (partner != null) {
			sessionPartner.remove(partner);
		}
	}

	/**
	 * Reports whether a player is currently in an open trade.
	 *
	 * @param playerId the player to test
	 * @return {@code true} if they have a partner
	 */
	public boolean isTrading(UUID playerId) {
		return sessionPartner.containsKey(playerId);
	}

	/**
	 * Returns the player's current trading partner.
	 *
	 * @param playerId the player to look up
	 * @return the partner's UUID, or {@code null} if they are not trading
	 */
	public UUID partnerOf(UUID playerId) {
		return sessionPartner.get(playerId);
	}

	/**
	 * Returns a snapshot of every player currently in a trade.
	 *
	 * @return the UUIDs of all trading players, each appearing once
	 */
	public Set<UUID> tradingPlayers() {
		return new HashSet<>(sessionPartner.keySet());
	}

	/**
	 * Sets or clears a player's opt-out flag.
	 *
	 * @param playerId the player to update
	 * @param optedOut {@code true} to stop receiving requests
	 */
	public void setOptedOut(UUID playerId, boolean optedOut) {
		if (optedOut) {
			this.optedOut.add(playerId);
		} else {
			this.optedOut.remove(playerId);
		}
	}

	/**
	 * Reports whether a player has opted out of trade requests.
	 *
	 * @param playerId the player to test
	 * @return {@code true} if requests to them should be refused
	 */
	public boolean isOptedOut(UUID playerId) {
		return optedOut.contains(playerId);
	}

	/**
	 * Toggles a player's opt-out flag.
	 *
	 * @param playerId the player to toggle
	 * @return the new state: {@code true} if they are now opted out
	 */
	public boolean toggleOptOut(UUID playerId) {
		if (optedOut.remove(playerId)) {
			return false;
		}
		optedOut.add(playerId);
		return true;
	}

	/**
	 * Drops every request that has passed its timeout.
	 *
	 * @param nowMillis the current wall-clock time
	 * @return the expired requests, so the caller can notify the senders
	 */
	public List<TradeRequest> expireRequests(long nowMillis) {
		List<TradeRequest> expired = new ArrayList<>();
		for (Deque<TradeRequest> queue : new ArrayList<>(requestsByTarget.values())) {
			for (TradeRequest request : new ArrayList<>(queue)) {
				if (request.isExpired(nowMillis)) {
					expired.add(request);
				}
			}
		}
		for (TradeRequest request : expired) {
			retire(request);
		}
		return expired;
	}

	/**
	 * Forgets everything about a player: pending requests, opt-out, session flags.
	 *
	 * <p>Called when a player disconnects. Any session they were part of must be
	 * ended separately by the manager, which owns the items.
	 *
	 * @param playerId the departing player
	 * @return the requests that were cancelled because of the departure
	 */
	public List<TradeRequest> forget(UUID playerId) {
		List<TradeRequest> dropped = new ArrayList<>();

		// Requests waiting for the departing player. Each one must also be released
		// from its sender's bookkeeping, otherwise that sender would be stuck
		// forever behind a request that can never be answered.
		Deque<TradeRequest> incoming = requestsByTarget.remove(playerId);
		if (incoming != null) {
			for (TradeRequest request : incoming) {
				dropped.add(request);
				requestBySender.remove(request.senderId());
			}
		}

		// A request the departing player had sent themselves.
		TradeRequest outgoing = requestBySender.remove(playerId);
		if (outgoing != null) {
			dropped.add(outgoing);
			Deque<TradeRequest> queue = requestsByTarget.get(outgoing.targetId());
			if (queue != null) {
				queue.remove(outgoing);
				if (queue.isEmpty()) {
					requestsByTarget.remove(outgoing.targetId());
				}
			}
		}
		optedOut.remove(playerId);
		endSession(playerId);
		return dropped;
	}

	/**
	 * Clears all state. Used on server shutdown so a restart starts clean.
	 */
	public void clear() {
		requestsByTarget.clear();
		requestBySender.clear();
		sessionPartner.clear();
		optedOut.clear();
	}
}
