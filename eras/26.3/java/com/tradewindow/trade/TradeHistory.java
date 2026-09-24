package com.tradewindow.trade;

import com.tradewindow.TradeWindow;
import com.tradewindow.config.TradeConfig;
import com.tradewindow.core.FileTradeHistoryStore;
import com.tradewindow.core.SqliteTradeHistoryStore;
import com.tradewindow.core.TradeHistoryStore;
import com.tradewindow.core.TradeRecord;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * The record of completed trades that {@code /tradeadmin history} reads.
 *
 * <p><b>Two layers, on purpose.</b> A small in-memory ring always keeps the last
 * {@link #CAPACITY} trades, because the command promises to show recent history and
 * an operator who has switched persistence off still expects that to work. When
 * {@code logTrades} is on, every record is also written to disk - SQLite when
 * {@code useDatabase} is set and a driver is present, otherwise a JSON-lines file -
 * and the ring is warmed from that store at startup, so history survives a restart.
 *
 * <p>Draws on the same shared {@link TradeRecord} as every other target, so the file
 * this build writes is readable by the other builds' history command and vice versa.
 *
 * <p>Nothing here may break a trade: a store that cannot write logs and carries on,
 * which is why every failure is swallowed by the store implementations.
 */
public final class TradeHistory {

	/** How many trades the in-memory ring holds. */
	public static final int CAPACITY = 20;

	private static final String HISTORY_FILE_NAME = "tradewindow-history.jsonl";
	private static final String DATABASE_FILE_NAME = "tradewindow-history.db";

	private static final Deque<TradeRecord> RECENT = new ArrayDeque<>(CAPACITY);

	private static TradeHistoryStore store;

	private TradeHistory() {
	}

	/**
	 * Prepares the history store and warms the in-memory ring from it.
	 *
	 * <p>Called once during mod initialisation. When {@code logTrades} is off, history
	 * is memory-only and the ring starts empty - the command still works, it just
	 * forgets across a restart.
	 *
	 * @param gameDir the game or server directory
	 */
	public static void init(Path gameDir) {
		store = null;
		RECENT.clear();
		if (!TradeConfig.get().logTrades) {
			TradeWindow.LOGGER.info("logTrades is off: trade history is kept in memory only");
			return;
		}

		TradeHistoryStore chosen = createStore(gameDir);
		if (chosen == null) {
			return;
		}
		store = chosen;
		// Seed the ring so /tradeadmin history is useful immediately after a restart.
		for (TradeRecord record : store.recent(CAPACITY)) {
			addToRing(record);
		}
		TradeWindow.LOGGER.info("Trade history backend: {} ({} record(s) loaded)",
				store.describeLocation(), RECENT.size());
	}

	private static TradeHistoryStore createStore(Path gameDir) {
		if (TradeConfig.get().useDatabase) {
			if (SqliteTradeHistoryStore.isDriverAvailable()) {
				try {
					return new SqliteTradeHistoryStore(gameDir.resolve(DATABASE_FILE_NAME));
				} catch (SQLException e) {
					TradeWindow.LOGGER.warn("useDatabase is enabled but SQLite could not be opened ({}); "
							+ "falling back to the JSON history file", e.getMessage());
				}
			} else {
				TradeWindow.LOGGER.warn("useDatabase is enabled but no SQLite JDBC driver is on the "
						+ "classpath. Trade Window bundles no dependencies, so add one (for example "
						+ "org.xerial:sqlite-jdbc) to enable the database. Falling back to the JSON file.");
			}
		}
		return new FileTradeHistoryStore(gameDir.resolve(HISTORY_FILE_NAME));
	}

	/**
	 * Records a completed or cancelled trade.
	 *
	 * @param record the trade to remember
	 */
	public static void record(TradeRecord record) {
		addToRing(record);
		if (store != null) {
			store.record(record);
		}
		TradeWindow.LOGGER.info("Trade {}: {} vs {}", record.outcome(), record.player1Name(),
				record.player2Name());
	}

	/**
	 * Returns recent trades, newest first, optionally filtered to one player.
	 *
	 * <p>The filter matches the recorded names case-insensitively, so it works for a
	 * player who is offline - which is when an operator most often wants it.
	 *
	 * @param limit        the maximum number of records
	 * @param playerFilter a player name, or {@code null} for every player
	 * @return the matching records
	 */
	public static List<TradeRecord> recent(int limit, String playerFilter) {
		String wanted = playerFilter == null ? null : playerFilter.toLowerCase(Locale.ROOT);
		List<TradeRecord> out = new ArrayList<>(Math.max(1, limit));
		for (TradeRecord record : RECENT) {
			if (out.size() >= limit) {
				break;
			}
			if (wanted == null || wanted.equals(record.player1Name().toLowerCase(Locale.ROOT))
					|| wanted.equals(record.player2Name().toLowerCase(Locale.ROOT))) {
				out.add(record);
			}
		}
		return out;
	}

	/** Releases the store, if any. Called on server shutdown. */
	public static void close() {
		if (store != null) {
			store.close();
			store = null;
		}
	}

	private static void addToRing(TradeRecord record) {
		if (RECENT.size() == CAPACITY) {
			RECENT.removeLast();
		}
		RECENT.addFirst(record);
	}
}
