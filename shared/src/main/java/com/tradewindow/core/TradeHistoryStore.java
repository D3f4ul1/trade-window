package com.tradewindow.core;

import java.util.List;

/**
 * Persistence for trade history.
 *
 * <p>Two implementations ship: a flat JSON-lines file that always works, and an
 * optional SQLite database that is used when {@code useDatabase} is enabled and
 * a JDBC SQLite driver happens to be on the classpath. The mod deliberately does
 * not bundle a driver, because it has no required dependencies.
 *
 * <p>Implementations must be safe to call from the server thread and must never
 * throw on a write failure — a broken history store must not be able to abort a
 * trade that has already swapped items.
 */
public interface TradeHistoryStore extends AutoCloseable {

	/**
	 * Appends a record.
	 *
	 * <p>Failures are logged and swallowed; history is diagnostic, never load-bearing.
	 *
	 * @param record the trade to persist
	 */
	void record(TradeRecord record);

	/**
	 * Returns the most recent records, newest first.
	 *
	 * @param limit the maximum number of records to return
	 * @return up to {@code limit} records; empty if nothing has been recorded
	 */
	List<TradeRecord> recent(int limit);

	/**
	 * Reports whether this store survives a server restart.
	 *
	 * @return {@code true} when records are persisted to disk
	 */
	boolean isPersistent();

	/**
	 * Returns a short description of where records live, for logs and diagnostics.
	 *
	 * @return a human-readable location, for example a file name
	 */
	String describeLocation();

	/**
	 * Flushes and releases any resources. Never throws.
	 */
	@Override
	void close();
}
