package com.tradewindow.core;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Optional SQLite-backed trade history.
 *
 * <p>Enabled by {@code "useDatabase": true}. The mod does not bundle a SQLite
 * JDBC driver — that would turn a zero-dependency mod into a dependency-carrying
 * one — so this class reports its own availability via {@link #isDriverAvailable()}
 * and the caller falls back to {@link FileTradeHistoryStore} when the driver is
 * missing. See {@code DESIGN_DECISIONS.md} #7.
 *
 * <p>Every method swallows {@link SQLException}: history is diagnostic, and a
 * database problem must never abort a trade that has already moved items.
 */
public final class SqliteTradeHistoryStore implements TradeHistoryStore {

	private static final String CREATE_TABLE = """
			CREATE TABLE IF NOT EXISTS trades (
				id        INTEGER PRIMARY KEY AUTOINCREMENT,
				timestamp INTEGER NOT NULL,
				player1   TEXT    NOT NULL,
				player2   TEXT    NOT NULL,
				outcome   TEXT    NOT NULL,
				reason    TEXT,
				payload   TEXT    NOT NULL
			)""";

	private static final String INSERT = """
			INSERT INTO trades (timestamp, player1, player2, outcome, reason, payload)
			VALUES (?, ?, ?, ?, ?, ?)""";

	private static final String SELECT_RECENT = """
			SELECT payload FROM trades ORDER BY id DESC LIMIT ?""";

	private final String jdbcUrl;
	private final String location;

	private Connection connection;

	/**
	 * Creates a store and opens (or creates) the database file.
	 *
	 * @param databaseFile path to the SQLite file
	 * @throws SQLException if the driver is missing or the file cannot be opened
	 */
	public SqliteTradeHistoryStore(java.nio.file.Path databaseFile) throws SQLException {
		// DriverManager only auto-loads drivers declared via the service loader;
		// an explicit load gives a clear failure when the driver is absent.
		ensureDriverLoaded();
		this.jdbcUrl = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
		this.location = databaseFile.getFileName().toString();
		this.connection = DriverManager.getConnection(jdbcUrl);
		try (Statement statement = connection.createStatement()) {
			statement.execute(CREATE_TABLE);
		}
	}

	/**
	 * Reports whether a SQLite JDBC driver is on the classpath.
	 *
	 * @return {@code true} if {@code org.sqlite.JDBC} can be loaded
	 */
	public static boolean isDriverAvailable() {
		try {
			ensureDriverLoaded();
			return true;
		} catch (SQLException e) {
			return false;
		}
	}

	private static void ensureDriverLoaded() throws SQLException {
		try {
			Class.forName("org.sqlite.JDBC");
		} catch (ClassNotFoundException e) {
			throw new SQLException("No SQLite JDBC driver on the classpath (org.sqlite.JDBC)", e);
		}
	}

	@Override
	public void record(TradeRecord record) {
		Connection conn = ensureConnection();
		if (conn == null) {
			return;
		}
		try (PreparedStatement statement = conn.prepareStatement(INSERT)) {
			statement.setLong(1, record.timestampMillis());
			statement.setString(2, record.player1Name());
			statement.setString(3, record.player2Name());
			statement.setString(4, record.outcome());
			statement.setString(5, record.detail());
			statement.setString(6, TradeRecordJson.toJson(record));
			statement.executeUpdate();
		} catch (SQLException e) {
			System.err.println("[tradewindow] Could not write trade history to SQLite: " + e.getMessage());
		}
	}

	@Override
	public List<TradeRecord> recent(int limit) {
		if (limit <= 0) {
			return List.of();
		}
		Connection conn = ensureConnection();
		if (conn == null) {
			return List.of();
		}
		List<TradeRecord> out = new ArrayList<>(limit);
		try (PreparedStatement statement = conn.prepareStatement(SELECT_RECENT)) {
			statement.setInt(1, limit);
			try (ResultSet rs = statement.executeQuery()) {
				while (rs.next()) {
					try {
						out.add(TradeRecordJson.fromJson(rs.getString("payload")));
					} catch (RuntimeException ignored) {
						// Skip unreadable rows rather than failing the whole query.
					}
				}
			}
		} catch (SQLException e) {
			System.err.println("[tradewindow] Could not read trade history from SQLite: " + e.getMessage());
			return List.of();
		}
		return out;
	}

	@Override
	public boolean isPersistent() {
		return true;
	}

	@Override
	public String describeLocation() {
		return location;
	}

	@Override
	public void close() {
		if (connection != null) {
			try {
				connection.close();
			} catch (SQLException ignored) {
				// Nothing useful to do while shutting down.
			}
			connection = null;
		}
	}

	private Connection ensureConnection() {
		try {
			if (connection == null || connection.isClosed()) {
				connection = DriverManager.getConnection(jdbcUrl);
			}
			return connection;
		} catch (SQLException e) {
			System.err.println("[tradewindow] SQLite connection unavailable: " + e.getMessage());
			return null;
		}
	}
}
