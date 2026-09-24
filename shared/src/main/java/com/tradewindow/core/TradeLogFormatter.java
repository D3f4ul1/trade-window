package com.tradewindow.core;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.StringJoiner;

/**
 * Renders {@link TradeRecord}s for the trade log and for in-game history output.
 *
 * <p>Two formats are produced from the same record: a one-line human-readable
 * entry for {@code tradewindow.log}, and a compact JSON object for machine
 * consumption. Keeping both in one place guarantees they can never disagree
 * about what was traded.
 */
public final class TradeLogFormatter {

	private static final DateTimeFormatter TIMESTAMP =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

	private TradeLogFormatter() {
	}

	/**
	 * Formats a record as a single log line.
	 *
	 * @param record the record to render
	 * @return a line such as
	 *         {@code [2026-09-22 11:40:02] SUCCESS Alice -> Bob: 3x minecraft:diamond | Bob -> Alice: 12x minecraft:iron_ingot}
	 */
	public static String line(TradeRecord record) {
		StringBuilder sb = new StringBuilder(128);
		sb.append('[').append(TIMESTAMP.format(Instant.ofEpochMilli(record.timestampMillis()))).append("] ");
		sb.append(record.outcome()).append(' ');
		sb.append(record.player1Name()).append(" -> ").append(record.player2Name()).append(": ");
		sb.append(describe(record.offer1()));
		sb.append(" | ");
		sb.append(record.player2Name()).append(" -> ").append(record.player1Name()).append(": ");
		sb.append(describe(record.offer2()));
		if (!record.detail().isEmpty()) {
			sb.append(" | reason=").append(record.detail());
		}
		return sb.toString();
	}

	/**
	 * Formats a record as a single-line JSON object.
	 *
	 * @param record the record to render
	 * @return a JSON object, with no trailing newline
	 */
	public static String json(TradeRecord record) {
		StringBuilder sb = new StringBuilder(256);
		sb.append('{');
		sb.append("\"timestamp\":").append(record.timestampMillis()).append(',');
		sb.append("\"time\":\"").append(TIMESTAMP.format(Instant.ofEpochMilli(record.timestampMillis()))).append("\",");
		sb.append("\"player1\":\"").append(escape(record.player1Name())).append("\",");
		sb.append("\"player2\":\"").append(escape(record.player2Name())).append("\",");
		sb.append("\"outcome\":\"").append(escape(record.outcome())).append("\",");
		sb.append("\"reason\":\"").append(escape(record.detail())).append("\",");
		sb.append("\"offer1\":").append(jsonItems(record.offer1())).append(',');
		sb.append("\"offer2\":").append(jsonItems(record.offer2()));
		sb.append('}');
		return sb.toString();
	}

	/**
	 * Formats a short, player-facing summary of an offer.
	 *
	 * @param offer the stacks to summarise
	 * @return a comma-separated list such as {@code 3x minecraft:diamond}, or
	 *         {@code tradewindow.history.nothing} when the offer is empty
	 */
	public static String summary(List<ItemSnapshot> offer) {
		if (offer.isEmpty()) {
			return "nothing";
		}
		return describe(offer);
	}

	private static String describe(List<ItemSnapshot> offer) {
		if (offer.isEmpty()) {
			return "nothing";
		}
		StringJoiner joiner = new StringJoiner(", ");
		for (ItemSnapshot item : offer) {
			joiner.add(item.detailed());
		}
		return joiner.toString();
	}

	private static String jsonItems(List<ItemSnapshot> offer) {
		StringJoiner joiner = new StringJoiner(",", "[", "]");
		for (ItemSnapshot item : offer) {
			joiner.add("{\"id\":\"" + escape(item.itemId())
					+ "\",\"count\":" + item.count()
					+ ",\"name\":\"" + escape(item.displayName())
					+ "\",\"data\":\"" + escape(item.dataFingerprint()) + "\"}");
		}
		return joiner.toString();
	}

	private static String escape(String raw) {
		if (raw == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder(raw.length() + 8);
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				default -> {
					if (c < 0x20) {
						sb.append(String.format("\\u%04x", (int) c));
					} else {
						sb.append(c);
					}
				}
			}
		}
		return sb.toString();
	}
}
