package com.tradewindow;

import com.tradewindow.core.CancelReason;
import com.tradewindow.core.FileTradeHistoryStore;
import com.tradewindow.core.ItemSnapshot;
import com.tradewindow.core.StackOps;
import com.tradewindow.core.TradeConfigData;
import com.tradewindow.core.TradeDistance;
import com.tradewindow.core.TradeHistoryStore;
import com.tradewindow.core.TradeLayout;
import com.tradewindow.core.TradeLogFormatter;
import com.tradewindow.core.TradeRecord;
import com.tradewindow.core.TradeRecordJson;
import com.tradewindow.core.TradeRegistry;
import com.tradewindow.core.TradeRequest;
import com.tradewindow.core.TradeSessionCore;
import com.tradewindow.core.TradeSide;
import com.tradewindow.core.TradeSwap;
import com.tradewindow.core.TradeValueTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the version-agnostic trade core.
 *
 * <p>Everything under test lives in {@code com.tradewindow.core}, which has no
 * Minecraft on its classpath. Stacks are represented by {@link FakeStack}, a
 * stand-in that models the parts of {@code ItemStack} the algorithm depends on:
 * an item id, a count, a max stack size, and an opaque "item data" fingerprint
 * that plays the role of 1.20.1 NBT / 26.3 data components.
 *
 * <p>Because both version modules compile these same core sources, a green run
 * here means the algorithm is correct for 1.20.1 and 26.3 alike.
 */
class TradeLogicTest {

	// ------------------------------------------------------------------
	// Test double
	// ------------------------------------------------------------------

	/** Minimal stand-in for {@code ItemStack}. */
	static final class FakeStack {
		final String itemId;
		final int maxStackSize;
		/** Opaque stand-in for NBT (1.20.1) or data components (26.3). */
		final String data;
		final String name;
		int count;

		FakeStack(String itemId, int count) {
			this(itemId, count, 64, "", itemId);
		}

		FakeStack(String itemId, int count, int maxStackSize, String data, String name) {
			this.itemId = itemId;
			this.count = count;
			this.maxStackSize = maxStackSize;
			this.data = data;
			this.name = name;
		}

		FakeStack withCount(int newCount) {
			return new FakeStack(itemId, newCount, maxStackSize, data, name);
		}
	}

	/** {@link StackOps} over {@link FakeStack}. */
	static final class FakeStackOps implements StackOps<FakeStack> {
		@Override
		public boolean isEmpty(FakeStack stack) {
			return stack == null || stack.count <= 0;
		}

		@Override
		public int count(FakeStack stack) {
			return stack.count;
		}

		@Override
		public void setCount(FakeStack stack, int count) {
			stack.count = count;
		}

		@Override
		public FakeStack copy(FakeStack stack) {
			// A deep copy: same id, count, size cap and data, but a distinct object.
			return new FakeStack(stack.itemId, stack.count, stack.maxStackSize, stack.data, stack.name);
		}

		@Override
		public int maxStackSize(FakeStack stack) {
			return stack.maxStackSize;
		}

		@Override
		public boolean sameItemAndData(FakeStack a, FakeStack b) {
			return a.itemId.equals(b.itemId) && a.data.equals(b.data);
		}

		@Override
		public String itemId(FakeStack stack) {
			return stack.itemId;
		}

		@Override
		public String dataFingerprint(FakeStack stack) {
			return stack.data;
		}

		@Override
		public String displayName(FakeStack stack) {
			return stack.name;
		}

		@Override
		public List<FakeStack> emptyContainer(int size) {
			List<FakeStack> list = new ArrayList<>(size);
			for (int i = 0; i < size; i++) {
				list.add(new FakeStack("minecraft:air", 0));
			}
			return list;
		}
	}

	private static final FakeStackOps OPS = new FakeStackOps();

	private static TradeConfigData defaultConfig() {
		return TradeConfigData.defaults();
	}

	// ------------------------------------------------------------------
	// Item swap logic
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("Item swap logic")
	class SwapLogic {

		@Test
		@DisplayName("both offers empty yields a valid, empty plan")
		void emptyOffers() {
			TradeSwap.Plan<FakeStack> plan = TradeSwap.prepare(
					List.of(new FakeStack("minecraft:air", 0)),
					List.of(new FakeStack("minecraft:air", 0)),
					OPS, defaultConfig());

			assertTrue(plan.isValid());
			assertTrue(plan.deliveryForPlayer1().isEmpty());
			assertTrue(plan.deliveryForPlayer2().isEmpty());
		}

		@Test
		@DisplayName("one-sided trade still validates and delivers")
		void oneSided() {
			TradeSwap.Plan<FakeStack> plan = TradeSwap.prepare(
					List.of(new FakeStack("minecraft:diamond", 5)),
					List.of(),
					OPS, defaultConfig());

			assertTrue(plan.isValid());
			// Player 1 offered diamonds, so player 2 receives them.
			assertEquals(1, plan.deliveryForPlayer2().size());
			assertEquals(5, plan.deliveryForPlayer2().get(0).count);
			assertTrue(plan.deliveryForPlayer1().isEmpty());
		}

		@Test
		@DisplayName("partial stacks keep their exact counts")
		void partialStacks() {
			TradeSwap.Plan<FakeStack> plan = TradeSwap.prepare(
					List.of(new FakeStack("minecraft:iron_ingot", 7)),
					List.of(new FakeStack("minecraft:gold_ingot", 3)),
					OPS, defaultConfig());

			assertTrue(plan.isValid());
			assertEquals(3, plan.deliveryForPlayer1().get(0).count);
			assertEquals(7, plan.deliveryForPlayer2().get(0).count);
		}

		@Test
		@DisplayName("full stacks keep their exact counts")
		void fullStacks() {
			TradeSwap.Plan<FakeStack> plan = TradeSwap.prepare(
					List.of(new FakeStack("minecraft:cobblestone", 64)),
					List.of(new FakeStack("minecraft:oak_log", 64)),
					OPS, defaultConfig());

			assertTrue(plan.isValid());
			assertEquals(64, plan.deliveryForPlayer1().get(0).count);
			assertEquals(64, plan.deliveryForPlayer2().get(0).count);
		}

		@Test
		@DisplayName("non-stackable items with a max size of 1 survive the swap")
		void nonStackableItems() {
			FakeStack sword = new FakeStack("minecraft:diamond_sword", 1, 1, "Damage:1567", "Diamond Sword");
			TradeSwap.Plan<FakeStack> plan = TradeSwap.prepare(
					List.of(sword), List.of(), OPS, defaultConfig());

			assertTrue(plan.isValid());
			FakeStack delivered = plan.deliveryForPlayer2().get(0);
			assertEquals(1, delivered.count);
			assertEquals("Damage:1567", delivered.data);
		}

		@Test
		@DisplayName("item data (NBT / data components) is preserved exactly through the copy")
		void itemDataPreserved() {
			FakeStack enchanted = new FakeStack("minecraft:diamond_sword", 1, 1,
					"Enchantments:[{id:sharpness,lvl:5}]", "Sharp Blade");

			TradeSwap.Plan<FakeStack> plan = TradeSwap.prepare(
					List.of(enchanted), List.of(), OPS, defaultConfig());

			FakeStack delivered = plan.deliveryForPlayer2().get(0);
			assertEquals("Enchantments:[{id:sharpness,lvl:5}]", delivered.data,
					"data fingerprint must survive the swap untouched");
			assertEquals("Sharp Blade", delivered.name);
			assertTrue(OPS.sameItemAndData(enchanted, delivered));
		}

		@Test
		@DisplayName("deliveries are independent copies, not live references")
		void deliveriesAreCopies() {
			FakeStack offered = new FakeStack("minecraft:diamond", 10);
			TradeSwap.Plan<FakeStack> plan = TradeSwap.prepare(
					List.of(offered), List.of(), OPS, defaultConfig());

			FakeStack delivered = plan.deliveryForPlayer2().get(0);
			assertTrue(delivered != offered, "must be a distinct object");

			// Mutating the source afterwards must not change what was delivered.
			offered.count = 1;
			assertEquals(10, delivered.count);
		}

		@Test
		@DisplayName("empty slots are stripped before delivery")
		void emptySlotsStripped() {
			List<FakeStack> offer = new ArrayList<>();
			offer.add(new FakeStack("minecraft:air", 0));
			offer.add(new FakeStack("minecraft:diamond", 2));
			offer.add(new FakeStack("minecraft:air", 0));

			TradeSwap.Plan<FakeStack> plan = TradeSwap.prepare(offer, List.of(), OPS, defaultConfig());

			assertTrue(plan.isValid());
			assertEquals(1, plan.deliveryForPlayer2().size());
			assertEquals(1, TradeSwap.occupiedSlots(offer, OPS));
		}

		@Test
		@DisplayName("a copy that lost its item data is detected by matches()")
		void matchesDetectsDataLoss() {
			FakeStack original = new FakeStack("minecraft:diamond_sword", 1, 1, "Enchantments:[looting]", "Looter");
			FakeStack stripped = new FakeStack("minecraft:diamond_sword", 1, 1, "", "Looter");

			assertFalse(TradeSwap.matches(List.of(original), List.of(stripped), OPS),
					"losing item data must fail the post-commit check");
			assertTrue(TradeSwap.matches(List.of(original), List.of(OPS.copy(original)), OPS));
		}

		@Test
		@DisplayName("matches() detects a count mismatch")
		void matchesDetectsCountLoss() {
			FakeStack original = new FakeStack("minecraft:diamond", 10);
			assertFalse(TradeSwap.matches(List.of(original), List.of(original.withCount(9)), OPS));
		}
	}

	// ------------------------------------------------------------------
	// Slot layout and ownership — the anti-scam boundary
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("Slot layout and ownership")
	class SlotLayout {

		@Test
		@DisplayName("the layout is two 18-slot offers around an 18-slot divider")
		void layoutShape() {
			assertEquals(54, TradeLayout.SLOTS);
			assertEquals(18, TradeLayout.SLOTS_PER_SIDE);
			assertEquals(18, TradeLayout.GLASS_START);
			assertEquals(36, TradeLayout.GLASS_END);
			assertEquals(36, TradeLayout.SECOND_OFFER_START);
			assertEquals(54, TradeLayout.PLAYER_SLOTS_START);
			assertEquals(TradeLayout.GLASS_END - TradeLayout.GLASS_START,
					TradeLayout.SLOTS_PER_SIDE, "divider is one side wide");
			assertEquals(TradeLayout.SLOTS,
					TradeLayout.SECOND_OFFER_START + TradeLayout.SLOTS_PER_SIDE, "B fills the tail");
		}

		@Test
		@DisplayName("player A owns 0-17 and player B owns 36-53")
		void sides() {
			assertEquals(TradeSide.PLAYER_1, TradeLayout.sideOf(0));
			assertEquals(TradeSide.PLAYER_1, TradeLayout.sideOf(17));
			assertEquals(TradeSide.PLAYER_2, TradeLayout.sideOf(36));
			assertEquals(TradeSide.PLAYER_2, TradeLayout.sideOf(53));
		}

		@Test
		@DisplayName("the glass divider belongs to nobody")
		void glassHasNoSide() {
			for (int i = TradeLayout.GLASS_START; i < TradeLayout.GLASS_END; i++) {
				assertTrue(TradeLayout.isGlass(i), "glass at " + i);
				assertNull(TradeLayout.sideOf(i), "no side at " + i);
			}
			assertFalse(TradeLayout.isGlass(TradeLayout.GLASS_START - 1));
			assertFalse(TradeLayout.isGlass(TradeLayout.GLASS_END));
		}

		@Test
		@DisplayName("each offer slot has exactly one owning side; the divider has none")
		void exactlyOneOwner() {
			for (int i = 0; i < TradeLayout.SLOTS; i++) {
				boolean a = TradeLayout.slotMayBeUsedBy(i, TradeSide.PLAYER_1);
				boolean b = TradeLayout.slotMayBeUsedBy(i, TradeSide.PLAYER_2);
				if (TradeLayout.isGlass(i)) {
					// The divider is owned by neither side.
					assertFalse(a, "glass must not be usable by A at " + i);
					assertFalse(b, "glass must not be usable by B at " + i);
				} else {
					assertNotEquals(a, b, "exactly one side owns slot " + i);
					assertEquals(i < TradeLayout.SECOND_OFFER_START, a, "player A at " + i);
				}
			}
		}

		@Test
		@DisplayName("a null side owns nothing, including slots past the divider")
		void nullSideOwnsNothing() {
			// Regression: the old inline rule returned true here for every index
			// at or past SECOND_OFFER_START, because (null == PLAYER_1) is false
			// and the comparison against firstHalf degenerated.
			for (int i = -1; i <= TradeLayout.SLOTS; i++) {
				assertFalse(TradeLayout.slotMayBeUsedBy(i, null), "null side at " + i);
			}
		}

		@Test
		@DisplayName("glass and out-of-range indices are never usable")
		void glassAndOutOfRange() {
			for (int i = TradeLayout.GLASS_START; i < TradeLayout.GLASS_END; i++) {
				assertFalse(TradeLayout.slotMayBeUsedBy(i, TradeSide.PLAYER_1));
				assertFalse(TradeLayout.slotMayBeUsedBy(i, TradeSide.PLAYER_2));
			}
			for (int i : new int[] {-1, -100, 54, 89, 1000}) {
				assertFalse(TradeLayout.isTradeSlot(i), "not a trade slot: " + i);
				assertFalse(TradeLayout.slotMayBeUsedBy(i, TradeSide.PLAYER_1), "A at " + i);
				assertFalse(TradeLayout.slotMayBeUsedBy(i, TradeSide.PLAYER_2), "B at " + i);
				assertNull(TradeLayout.sideOf(i), "no side at " + i);
			}
			assertTrue(TradeLayout.isTradeSlot(0));
			assertTrue(TradeLayout.isTradeSlot(TradeLayout.SLOTS - 1));
			assertFalse(TradeLayout.isTradeSlot(TradeLayout.SLOTS));
		}

		@Test
		@DisplayName("the player's own inventory sits outside the trade layout")
		void playerInventoryIsSeparate() {
			assertEquals(TradeLayout.SLOTS, TradeLayout.PLAYER_SLOTS_START);
			for (int i = TradeLayout.PLAYER_SLOTS_START;
					i < TradeLayout.PLAYER_SLOTS_START + 36; i++) {
				assertFalse(TradeLayout.isTradeSlot(i), "trade slot: " + i);
				assertFalse(TradeLayout.slotMayBeUsedBy(i, TradeSide.PLAYER_1));
				assertFalse(TradeLayout.slotMayBeUsedBy(i, TradeSide.PLAYER_2));
			}
		}
	}

	// ------------------------------------------------------------------
	// Blacklist and value cap
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("Blacklist and value cap")
	class Policy {

		@Test
		@DisplayName("a blacklisted item rejects the whole trade")
		void blacklistRejects() {
			TradeConfigData config = defaultConfig();
			TradeSwap.Plan<FakeStack> plan = TradeSwap.prepare(
					List.of(new FakeStack("minecraft:bedrock", 1)),
					List.of(),
					OPS, config);

			assertFalse(plan.isValid());
			assertEquals(CancelReason.BLACKLISTED_ITEM, plan.failure());
		}

		@Test
		@DisplayName("a blacklisted item on the second side also rejects")
		void blacklistRejectsOnEitherSide() {
			TradeSwap.Plan<FakeStack> plan = TradeSwap.prepare(
					List.of(new FakeStack("minecraft:diamond", 1)),
					List.of(new FakeStack("minecraft:command_block", 1)),
					OPS, defaultConfig());

			assertFalse(plan.isValid());
			assertEquals(CancelReason.BLACKLISTED_ITEM, plan.failure());
		}

		@Test
		@DisplayName("blacklist matching is case insensitive")
		void blacklistIsCaseInsensitive() {
			TradeConfigData config = defaultConfig();
			config.blacklistedItems = List.of("MineCraft:Bedrock");
			config.normalize();

			TradeSwap.Plan<FakeStack> plan = TradeSwap.prepare(
					List.of(new FakeStack("minecraft:bedrock", 1)), List.of(), OPS, config);

			assertFalse(plan.isValid());
		}

		@Test
		@DisplayName("an over-stacked entry is refused rather than silently clamped")
		void overStackedRejected() {
			// 999 diamonds in one slot is impossible from a vanilla client.
			TradeSwap.Plan<FakeStack> plan = TradeSwap.prepare(
					List.of(new FakeStack("minecraft:diamond", 999)),
					List.of(), OPS, defaultConfig());

			assertFalse(plan.isValid());
			assertEquals(CancelReason.COMMIT_FAILED, plan.failure());
		}

		@Test
		@DisplayName("the value cap is disabled by default")
		void valueCapDisabledByDefault() {
			TradeConfigData config = defaultConfig();
			assertFalse(config.hasValueCap());

			TradeSwap.Plan<FakeStack> plan = TradeSwap.prepare(
					List.of(new FakeStack("minecraft:netherite_block", 64)), List.of(), OPS, config);

			assertTrue(plan.isValid(), "with no cap configured, huge trades must pass");
		}

		@Test
		@DisplayName("the value cap rejects a trade worth more than the limit")
		void valueCapRejects() {
			TradeConfigData config = defaultConfig();
			config.maxTradeValue = 100; // 100 points

			// 64 diamonds at 16 points each = 1024 points, well over the cap.
			TradeSwap.Plan<FakeStack> plan = TradeSwap.prepare(
					List.of(new FakeStack("minecraft:diamond", 64)), List.of(), OPS, config);

			assertFalse(plan.isValid());
			assertEquals(CancelReason.VALUE_CAP, plan.failure());
		}

		@Test
		@DisplayName("the value cap allows a trade within the limit")
		void valueCapAllows() {
			TradeConfigData config = defaultConfig();
			config.maxTradeValue = 100;

			TradeSwap.Plan<FakeStack> plan = TradeSwap.prepare(
					List.of(new FakeStack("minecraft:diamond", 4)), List.of(), OPS, config);

			assertTrue(plan.isValid());
			assertEquals(64, plan.totalValue());
		}

		@Test
		@DisplayName("appraise totals one offer")
		void appraiseTotals() {
			long value = TradeSwap.appraise(
					List.of(new FakeStack("minecraft:diamond", 2), new FakeStack("minecraft:iron_ingot", 5)),
					OPS);
			assertEquals(2 * TradeValueTable.valueOf("minecraft:diamond") + 5, value);
		}

		@Test
		@DisplayName("unlisted items contribute the documented unknown value")
		void unknownItemValue() {
			assertEquals(TradeValueTable.UNKNOWN_VALUE, TradeValueTable.valueOf("somemod:mystery_rock"));
			assertFalse(TradeValueTable.isKnown("somemod:mystery_rock"));
			assertTrue(TradeValueTable.isKnown("minecraft:diamond"));
		}
	}

	// ------------------------------------------------------------------
	// Confirm / unconfirm state machine
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("Confirm state machine")
	class ConfirmStateMachine {

		private TradeSessionCore newSession() {
			return new TradeSessionCore(UUID.randomUUID(), "Alice", UUID.randomUUID(), "Bob", 0L, 60);
		}

		@Test
		@DisplayName("a single confirmation is not enough to execute")
		void oneConfirmationIsNotEnough() {
			TradeSessionCore session = newSession();

			assertTrue(session.confirm(TradeSide.PLAYER_1));
			assertFalse(session.bothConfirmed());
			assertEquals(TradeSessionCore.TickResult.CONTINUE, session.tick(1000L));
		}

		@Test
		@DisplayName("both confirmations request an execute")
		void bothConfirmationsExecute() {
			TradeSessionCore session = newSession();

			session.confirm(TradeSide.PLAYER_1);
			session.confirm(TradeSide.PLAYER_2);

			assertTrue(session.bothConfirmed());
			assertEquals(TradeSessionCore.TickResult.EXECUTE, session.tick(1000L));
		}

		@Test
		@DisplayName("un-confirming resets both sides, not just one")
		void unconfirmResetsBothSides() {
			TradeSessionCore session = newSession();

			session.confirm(TradeSide.PLAYER_1);
			session.confirm(TradeSide.PLAYER_2);
			assertTrue(session.bothConfirmed());

			session.unconfirm(TradeSide.PLAYER_1);

			assertFalse(session.isConfirmed(TradeSide.PLAYER_1));
			assertFalse(session.isConfirmed(TradeSide.PLAYER_2),
					"the other side's confirmation must be cleared too");
			assertEquals(TradeSessionCore.TickResult.CONTINUE, session.tick(1000L));
		}

		@Test
		@DisplayName("confirming twice is a no-op")
		void doubleConfirmIsNoop() {
			TradeSessionCore session = newSession();

			assertTrue(session.confirm(TradeSide.PLAYER_1));
			assertFalse(session.confirm(TradeSide.PLAYER_1));
		}

		@Test
		@DisplayName("un-confirming when nothing is confirmed is a no-op")
		void unconfirmWithNothingConfirmed() {
			TradeSessionCore session = newSession();
			assertFalse(session.unconfirm(TradeSide.PLAYER_1));
		}

		@Test
		@DisplayName("a late confirm after cancellation is ignored")
		void lateConfirmIgnored() {
			TradeSessionCore session = newSession();

			assertTrue(session.cancel(CancelReason.MANUAL));
			assertFalse(session.confirm(TradeSide.PLAYER_1));
			assertTrue(session.isFinished());
			assertFalse(session.isExecuted());
			assertEquals(CancelReason.MANUAL, session.cancelReason());
		}

		@Test
		@DisplayName("an executed session cannot be cancelled afterwards")
		void executedSessionCannotBeCancelled() {
			TradeSessionCore session = newSession();

			assertTrue(session.markExecuted());
			assertFalse(session.cancel(CancelReason.MANUAL));
			assertTrue(session.isExecuted());
			assertNull(session.cancelReason());
		}

		@Test
		@DisplayName("sideOf resolves participants and rejects outsiders")
		void sideOfResolution() {
			UUID alice = UUID.randomUUID();
			UUID bob = UUID.randomUUID();
			TradeSessionCore session = new TradeSessionCore(alice, "Alice", bob, "Bob", 0L, 60);

			assertEquals(TradeSide.PLAYER_1, session.sideOf(alice));
			assertEquals(TradeSide.PLAYER_2, session.sideOf(bob));
			assertThrows(IllegalArgumentException.class, () -> session.sideOf(UUID.randomUUID()));
			assertTrue(session.involves(alice));
			assertFalse(session.involves(UUID.randomUUID()));
		}

		@Test
		@DisplayName("opposite() round-trips")
		void oppositeRoundTrip() {
			assertEquals(TradeSide.PLAYER_1, TradeSide.PLAYER_2.opposite());
			assertSame(TradeSide.PLAYER_1, TradeSide.PLAYER_1.opposite().opposite());
		}
	}

	// ------------------------------------------------------------------
	// Timeouts
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("Timeout logic")
	class Timeouts {

		@Test
		@DisplayName("the GUI timeout fires exactly at the configured second")
		void guiTimeoutBoundary() {
			TradeSessionCore session = new TradeSessionCore(
					UUID.randomUUID(), "Alice", UUID.randomUUID(), "Bob", 0L, 60);

			assertEquals(TradeSessionCore.TickResult.CONTINUE, session.tick(59_000L));
			assertEquals(1L, session.remainingSeconds(59_000L));

			assertEquals(TradeSessionCore.TickResult.TIMEOUT, session.tick(60_000L));
			assertEquals(0L, session.remainingSeconds(60_000L));
		}

		@Test
		@DisplayName("a finished session never times out")
		void finishedSessionDoesNotTimeOut() {
			TradeSessionCore session = new TradeSessionCore(
					UUID.randomUUID(), "Alice", UUID.randomUUID(), "Bob", 0L, 60);
			session.cancel(CancelReason.MANUAL);

			assertEquals(TradeSessionCore.TickResult.CONTINUE, session.tick(999_999L));
		}

		@Test
		@DisplayName("execution wins over the timeout when both are due")
		void executeBeatsTimeout() {
			TradeSessionCore session = new TradeSessionCore(
					UUID.randomUUID(), "Alice", UUID.randomUUID(), "Bob", 0L, 60);
			session.confirm(TradeSide.PLAYER_1);
			session.confirm(TradeSide.PLAYER_2);

			assertEquals(TradeSessionCore.TickResult.EXECUTE, session.tick(120_000L),
					"a trade that both sides confirmed must not be thrown away by the clock");
		}

		@Test
		@DisplayName("a request expires after its timeout and not before")
		void requestTimeout() {
			TradeRequest request = new TradeRequest(
					UUID.randomUUID(), "Alice", UUID.randomUUID(), "Bob", 1_000L, 30);

			assertFalse(request.isExpired(30_999L));
			assertTrue(request.isExpired(31_000L));
			assertEquals(0L, request.remainingSeconds(999_999L));
			assertEquals(10L, request.remainingSeconds(21_000L));
		}

		@Test
		@DisplayName("a request can only be resolved once")
		void requestResolvesOnce() {
			TradeRequest request = new TradeRequest(
					UUID.randomUUID(), "Alice", UUID.randomUUID(), "Bob", 0L, 30);

			assertTrue(request.resolve());
			assertFalse(request.resolve(), "a second Accept click must not open a second window");
			assertTrue(request.isResolved());
		}

		@Test
		@DisplayName("the registry drops expired requests")
		void registryExpiresRequests() {
			TradeRegistry registry = new TradeRegistry();
			UUID alice = UUID.randomUUID();
			UUID bob = UUID.randomUUID();

			TradeRequest request = new TradeRequest(alice, "Alice", bob, "Bob", 0L, 30);
			assertEquals(TradeRegistry.RequestOutcome.ACCEPTED, registry.submit(request));

			assertTrue(registry.expireRequests(10_000L).isEmpty());
			assertNotNull(registry.actionableFor(bob));

			List<TradeRequest> expired = registry.expireRequests(30_000L);
			assertEquals(1, expired.size());
			assertNull(registry.actionableFor(bob));
			assertNull(registry.outgoingFor(alice));
		}
	}

	// ------------------------------------------------------------------
	// Concurrency, queueing and disconnect handling
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("Concurrency and disconnect handling")
	class Concurrency {

		private final UUID alice = UUID.randomUUID();
		private final UUID bob = UUID.randomUUID();
		private final UUID carol = UUID.randomUUID();

		private TradeRequest request(UUID from, String fromName, UUID to, String toName, long at) {
			return new TradeRequest(from, fromName, to, toName, at, 30);
		}

		@Test
		@DisplayName("trading with yourself is blocked")
		void selfTradeBlocked() {
			TradeRegistry registry = new TradeRegistry();
			assertEquals(TradeRegistry.RequestOutcome.REJECTED_SELF,
					registry.submit(request(alice, "Alice", alice, "Alice", 0L)));
		}

		@Test
		@DisplayName("a player already in a trade cannot start another")
		void busySenderBlocked() {
			TradeRegistry registry = new TradeRegistry();
			assertTrue(registry.startSession(alice, bob));

			assertEquals(TradeRegistry.RequestOutcome.REJECTED_SENDER_BUSY,
					registry.submit(request(alice, "Alice", carol, "Carol", 0L)));
		}

		@Test
		@DisplayName("a player in a trade cannot be made to start a second session")
		void startSessionFailsWhenBusy() {
			TradeRegistry registry = new TradeRegistry();
			assertTrue(registry.startSession(alice, bob));
			assertFalse(registry.startSession(alice, carol),
					"the second session must be refused so items cannot be double-committed");
			assertEquals(bob, registry.partnerOf(alice));
		}

		@Test
		@DisplayName("a sender cannot have two outstanding requests")
		void duplicateRequestBlocked() {
			TradeRegistry registry = new TradeRegistry();
			assertEquals(TradeRegistry.RequestOutcome.ACCEPTED,
					registry.submit(request(alice, "Alice", bob, "Bob", 0L)));

			assertEquals(TradeRegistry.RequestOutcome.REJECTED_DUPLICATE,
					registry.submit(request(alice, "Alice", carol, "Carol", 0L)));
		}

		@Test
		@DisplayName("two players requesting the same target queue FIFO, one prompt at a time")
		void contestedTargetQueues() {
			TradeRegistry registry = new TradeRegistry();

			TradeRequest fromAlice = request(alice, "Alice", carol, "Carol", 0L);
			TradeRequest fromBob = request(bob, "Bob", carol, "Carol", 0L);

			assertEquals(TradeRegistry.RequestOutcome.ACCEPTED, registry.submit(fromAlice));
			assertEquals(TradeRegistry.RequestOutcome.QUEUED, registry.submit(fromBob),
					"the second requester must wait rather than stacking prompts");

			// Carol only ever sees one actionable request.
			assertSame(fromAlice, registry.actionableFor(carol));

			// Once Alice's request retires, Bob's becomes actionable.
			registry.retire(fromAlice);
			assertSame(fromBob, registry.actionableFor(carol));
		}

		@Test
		@DisplayName("declining promotes the next queued request")
		void declinePromotesNext() {
			TradeRegistry registry = new TradeRegistry();
			TradeRequest fromAlice = request(alice, "Alice", carol, "Carol", 0L);
			TradeRequest fromBob = request(bob, "Bob", carol, "Carol", 0L);

			registry.submit(fromAlice);
			registry.submit(fromBob);
			registry.retire(fromAlice);

			assertSame(fromBob, registry.actionableFor(carol));
			assertNull(registry.outgoingFor(alice), "the retired sender is free to try again");
		}

		@Test
		@DisplayName("a target can only be in one session at a time")
		void targetNotDoubleBooked() {
			TradeRegistry registry = new TradeRegistry();
			assertTrue(registry.startSession(alice, carol));
			assertTrue(registry.isTrading(carol));
			assertEquals(alice, registry.partnerOf(carol));

			assertFalse(registry.startSession(bob, carol));
			assertNull(registry.partnerOf(bob));
		}

		@Test
		@DisplayName("disconnecting drops pending requests in both directions")
		void disconnectDropsRequests() {
			TradeRegistry registry = new TradeRegistry();

			TradeRequest outgoing = request(alice, "Alice", bob, "Bob", 0L);
			TradeRequest incoming = request(carol, "Carol", alice, "Alice", 0L);

			registry.submit(outgoing);
			registry.submit(incoming);

			List<TradeRequest> dropped = registry.forget(alice);

			assertEquals(2, dropped.size());
			assertNull(registry.actionableFor(bob), "Bob must not keep a dead prompt");
			assertNull(registry.actionableFor(alice));
			assertNull(registry.outgoingFor(carol));
			assertNull(registry.outgoingFor(alice));
		}

		@Test
		@DisplayName("disconnecting clears the session flag on both sides")
		void disconnectEndsSession() {
			TradeRegistry registry = new TradeRegistry();
			registry.startSession(alice, bob);

			registry.forget(alice);

			assertFalse(registry.isTrading(alice));
			assertFalse(registry.isTrading(bob), "the partner must be freed as well");
			assertTrue(registry.tradingPlayers().isEmpty());
		}

		@Test
		@DisplayName("endSession frees both participants")
		void endSessionFreesBoth() {
			TradeRegistry registry = new TradeRegistry();
			registry.startSession(alice, bob);

			registry.endSession(bob);

			assertFalse(registry.isTrading(alice));
			assertFalse(registry.isTrading(bob));
		}

		@Test
		@DisplayName("opting out blocks incoming requests")
		void optOutBlocksRequests() {
			TradeRegistry registry = new TradeRegistry();

			assertFalse(registry.isOptedOut(bob));
			assertTrue(registry.toggleOptOut(bob));
			assertTrue(registry.isOptedOut(bob));

			assertEquals(TradeRegistry.RequestOutcome.REJECTED_TARGET_OPTED_OUT,
					registry.submit(request(alice, "Alice", bob, "Bob", 0L)));

			assertFalse(registry.toggleOptOut(bob));
			assertEquals(TradeRegistry.RequestOutcome.ACCEPTED,
					registry.submit(request(alice, "Alice", bob, "Bob", 0L)));
		}

		@Test
		@DisplayName("clear() resets every kind of state")
		void clearResetsEverything() {
			TradeRegistry registry = new TradeRegistry();
			registry.submit(request(alice, "Alice", bob, "Bob", 0L));
			registry.startSession(carol, UUID.randomUUID());
			registry.setOptedOut(alice, true);

			registry.clear();

			assertNull(registry.actionableFor(bob));
			assertTrue(registry.tradingPlayers().isEmpty());
			assertFalse(registry.isOptedOut(alice));
		}
	}

	// ------------------------------------------------------------------
	// Config
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("Configuration")
	class Configuration {

		@Test
		@DisplayName("defaults match the documented config file")
		void defaultsMatchSpec() {
			TradeConfigData config = TradeConfigData.defaults();

			assertEquals(15, config.tradeTimeoutSeconds, "a request is answered quickly or not at all");
			assertEquals(120, config.guiTimeoutSeconds, "the window itself gets two minutes");
			assertEquals(20, config.maxDistanceBlocks);
			assertTrue(config.cancelOnDamage);
			assertFalse(config.cancelOnMove, "the distance rule is off out of the box");
			assertTrue(config.requireToken);
			assertTrue(config.logTrades);
			assertFalse(config.useDatabase);
			assertFalse(config.allowCreativeTrading);
			assertEquals(-1, config.maxTradeValue);
			assertTrue(config.blacklistedItems.contains("minecraft:bedrock"));
			assertTrue(config.blacklistedItems.contains("minecraft:command_block"));
			assertTrue(config.crossDistanceTrading, "distance is unlimited unless asked otherwise");
		}

		@Test
		@DisplayName("cross-distance trading survives a round trip both ways")
		void crossDistanceRoundTrips() {
			assertEquals(Boolean.TRUE, TradeConfigData.fromJson("{}").crossDistanceTrading);
			assertFalse(TradeConfigData.fromJson(
					"{\"crossDistanceTrading\": false}").crossDistanceTrading);
		}

		@Test
		@DisplayName("JSON round-trips without losing values")
		void jsonRoundTrip() {
			TradeConfigData original = TradeConfigData.defaults();
			original.tradeTimeoutSeconds = 45;
			original.maxTradeValue = 500;
			original.blacklistedItems = List.of("minecraft:tnt");

			TradeConfigData parsed = TradeConfigData.fromJson(original.toJson());

			assertEquals(45, parsed.tradeTimeoutSeconds);
			assertEquals(500, parsed.maxTradeValue);
			assertEquals(List.of("minecraft:tnt"), parsed.blacklistedItems);
		}

		@Test
		@DisplayName("missing fields keep their defaults")
		void partialJsonKeepsDefaults() {
			TradeConfigData parsed = TradeConfigData.fromJson("{\"tradeTimeoutSeconds\": 12}");

			assertEquals(12, parsed.tradeTimeoutSeconds);
			assertEquals(120, parsed.guiTimeoutSeconds, "absent keys must not zero out");
			assertTrue(parsed.requireToken);
		}

		@Test
		@DisplayName("hostile values are clamped toward safety")
		void hostileValuesClamped() {
			TradeConfigData parsed = TradeConfigData.fromJson(
					"{\"tradeTimeoutSeconds\": -9999, \"guiTimeoutSeconds\": 0, \"maxDistanceBlocks\": -1,"
							+ " \"maxTradeValue\": -50, \"blacklistedItems\": null}");

			assertEquals(5, parsed.tradeTimeoutSeconds);
			assertEquals(10, parsed.guiTimeoutSeconds);
			assertEquals(1, parsed.maxDistanceBlocks);
			assertEquals(-1, parsed.maxTradeValue, "anything below -1 means disabled");
			assertFalse(parsed.blacklistedItems.isEmpty(), "a null blacklist is repaired, not honoured");
		}

		@Test
		@DisplayName("the blacklist is de-duplicated and normalised")
		void blacklistNormalised() {
			TradeConfigData config = TradeConfigData.fromJson(
					"{\"blacklistedItems\": [\"  MINECRAFT:Bedrock \", \"minecraft:bedrock\", \"\", \"minecraft:tnt\"]}");

			assertEquals(List.of("minecraft:bedrock", "minecraft:tnt"), config.blacklistedItems);
		}

		@Test
		@DisplayName("an empty JSON object yields pure defaults")
		void emptyObjectYieldsDefaults() {
			TradeConfigData parsed = TradeConfigData.fromJson("{}");
			assertEquals(15, parsed.tradeTimeoutSeconds);
			assertEquals(20, parsed.maxDistanceBlocks);
			assertFalse(parsed.cancelOnMove);
		}
	}

	// ------------------------------------------------------------------
	// The distance rule
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("Distance rule")
	class DistanceRule {

		/**
		 * A config with the rule deliberately switched on: cross-distance trading off
		 * and the master switch left as the operator set it.
		 */
		private TradeConfigData enforcing() {
			TradeConfigData config = TradeConfigData.defaults();
			config.crossDistanceTrading = false;
			config.cancelOnMove = true;
			return config;
		}

		@Test
		@DisplayName("is not enforced with the shipped defaults")
		void offByDefault() {
			TradeConfigData config = TradeConfigData.defaults();

			assertFalse(TradeDistance.enforced(config));
			assertFalse(TradeDistance.tooFar(config, true, 1_000_000_000),
					"no separation may end a trade while the rule is off");
		}

		@Test
		@DisplayName("needs both switches, not either")
		void bothSwitchesNeeded() {
			TradeConfigData crossDistanceOff = TradeConfigData.defaults();
			crossDistanceOff.crossDistanceTrading = false;
			assertFalse(TradeDistance.enforced(crossDistanceOff),
					"cancelOnMove is the master switch and defaults to off");

			TradeConfigData masterOn = TradeConfigData.defaults();
			masterOn.cancelOnMove = true;
			assertFalse(TradeDistance.enforced(masterOn),
					"cross-distance trading is on, so there is no radius to enforce");
		}

		@Test
		@DisplayName("measures against maxDistanceBlocks once enforced")
		void measuresAgainstRadius() {
			TradeConfigData config = enforcing();
			int max = config.maxDistanceBlocks;

			assertTrue(TradeDistance.enforced(config));
			assertFalse(TradeDistance.tooFar(config, true, (double) max * max),
					"exactly on the boundary is still in range");
			assertTrue(TradeDistance.tooFar(config, true, (double) max * max + 1));
		}

		@Test
		@DisplayName("treats another dimension as out of range unless allowed")
		void otherDimension() {
			TradeConfigData config = enforcing();

			assertTrue(TradeDistance.tooFar(config, false, 0),
					"a separation of 'unknown' cannot be compared against a radius");

			config.allowCrossDimensionTrading = true;
			assertFalse(TradeDistance.tooFar(config, false, 0));
		}
	}

	// ------------------------------------------------------------------
	// Logging and history
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("Logging and history")
	class Logging {

		private TradeRecord sampleRecord() {
			return new TradeRecord(1_700_000_000_000L, "Alice", "Bob",
					List.of(new ItemSnapshot("minecraft:diamond", 3, "Diamond", "abc123")),
					List.of(new ItemSnapshot("minecraft:iron_ingot", 12, "Iron Ingot", "")),
					TradeRecord.OUTCOME_SUCCESS, "");
		}

		@Test
		@DisplayName("the log line names both players and both offers")
		void logLineShape() {
			String line = TradeLogFormatter.line(sampleRecord());

			assertTrue(line.contains("SUCCESS"));
			assertTrue(line.contains("Alice -> Bob"));
			assertTrue(line.contains("3x minecraft:diamond"));
			assertTrue(line.contains("12x minecraft:iron_ingot"));
			assertTrue(line.contains("abc123"), "the data fingerprint must be logged");
		}

		@Test
		@DisplayName("the log line records the cancellation reason")
		void logLineRecordsReason() {
			TradeRecord record = new TradeRecord(0L, "Alice", "Bob", List.of(), List.of(),
					TradeRecord.OUTCOME_CANCELLED, CancelReason.DISTANCE.name());

			String line = TradeLogFormatter.line(record);
			assertTrue(line.contains("CANCELLED"));
			assertTrue(line.contains("reason=DISTANCE"));
		}

		@Test
		@DisplayName("an empty offer renders as 'nothing'")
		void emptyOfferRenders() {
			TradeRecord record = new TradeRecord(0L, "Alice", "Bob", List.of(), List.of(),
					TradeRecord.OUTCOME_CANCELLED, "MANUAL");
			assertTrue(TradeLogFormatter.line(record).contains("nothing"));
		}

		@Test
		@DisplayName("records survive a JSON round-trip")
		void recordJsonRoundTrip() {
			TradeRecord original = sampleRecord();
			TradeRecord parsed = TradeRecordJson.fromJson(TradeRecordJson.toJson(original));

			assertEquals(original.timestampMillis(), parsed.timestampMillis());
			assertEquals(original.player1Name(), parsed.player1Name());
			assertEquals(original.outcome(), parsed.outcome());
			assertEquals(1, parsed.offer1().size());
			assertEquals("minecraft:diamond", parsed.offer1().get(0).itemId());
			assertEquals(3, parsed.offer1().get(0).count());
			assertEquals("abc123", parsed.offer1().get(0).dataFingerprint());
		}

		@Test
		@DisplayName("the file history store returns the newest records first")
		void fileStoreRoundTrip(@TempDir Path tempDir) {
			Path file = tempDir.resolve("history.jsonl");
			try (TradeHistoryStore store = new FileTradeHistoryStore(file)) {
				for (int i = 0; i < 5; i++) {
					store.record(new TradeRecord(i, "P" + i, "Q" + i, List.of(), List.of(),
							TradeRecord.OUTCOME_SUCCESS, ""));
				}

				List<TradeRecord> recent = store.recent(3);
				assertEquals(3, recent.size());
				assertEquals(4L, recent.get(0).timestampMillis(), "newest first");
				assertEquals(2L, recent.get(2).timestampMillis());
				assertTrue(store.isPersistent());
			}
		}

		@Test
		@DisplayName("a corrupt history line is skipped instead of throwing")
		void corruptLineSkipped(@TempDir Path tempDir) throws java.io.IOException {
			Path file = tempDir.resolve("history.jsonl");
			try (TradeHistoryStore store = new FileTradeHistoryStore(file)) {
				store.record(new TradeRecord(1L, "Alice", "Bob", List.of(), List.of(),
						TradeRecord.OUTCOME_SUCCESS, ""));
			}
			java.nio.file.Files.writeString(file, "this is not json" + System.lineSeparator(),
					java.nio.charset.StandardCharsets.UTF_8,
					java.nio.file.StandardOpenOption.APPEND);

			try (TradeHistoryStore store = new FileTradeHistoryStore(file)) {
				assertEquals(1, store.recent(10).size(), "the readable record must still come back");
			}
		}

		@Test
		@DisplayName("reading an absent history file returns nothing rather than failing")
		void absentHistoryFile(@TempDir Path tempDir) {
			try (TradeHistoryStore store = new FileTradeHistoryStore(tempDir.resolve("nope.jsonl"))) {
				assertTrue(store.recent(10).isEmpty());
			}
		}

		@Test
		@DisplayName("snapshots normalise their own fields")
		void snapshotNormalisation() {
			ItemSnapshot snapshot = new ItemSnapshot(null, 1, null, null);
			assertEquals("", snapshot.itemId());
			assertEquals("", snapshot.displayName());
			assertEquals("1x ", snapshot.summary());
		}

		@Test
		@DisplayName("a zero or negative history limit returns nothing")
		void historyLimitGuarded(@TempDir Path tempDir) {
			try (TradeHistoryStore store = new FileTradeHistoryStore(tempDir.resolve("h.jsonl"))) {
				store.record(new TradeRecord(1L, "A", "B", List.of(), List.of(),
						TradeRecord.OUTCOME_SUCCESS, ""));
				assertTrue(store.recent(0).isEmpty());
				assertTrue(store.recent(-5).isEmpty());
			}
		}
	}
}
