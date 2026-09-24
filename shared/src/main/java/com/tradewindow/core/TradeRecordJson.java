package com.tradewindow.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON serialisation for {@link TradeRecord}, shared by every history backend.
 *
 * <p>Kept separate from {@link TradeLogFormatter} because the two have different
 * jobs: the formatter produces text for humans, this produces a round-trippable
 * representation for storage. Both are exercised by the unit tests so a change
 * to one cannot silently break the other.
 */
public final class TradeRecordJson {

	private static final Gson GSON = new GsonBuilder().create();

	private TradeRecordJson() {
	}

	/**
	 * Serialises a record to a single-line JSON object.
	 *
	 * @param record the record to serialise
	 * @return the JSON text
	 */
	public static String toJson(TradeRecord record) {
		return GSON.toJson(Row.of(record));
	}

	/**
	 * Parses a record previously produced by {@link #toJson(TradeRecord)}.
	 *
	 * @param json the JSON text
	 * @return the parsed record
	 * @throws com.google.gson.JsonSyntaxException if the text is not a valid record
	 */
	public static TradeRecord fromJson(String json) {
		Row row = GSON.fromJson(json, Row.class);
		if (row == null) {
			throw new com.google.gson.JsonSyntaxException("Empty trade record");
		}
		return row.toRecord();
	}

	/** Gson-friendly mirror of {@link TradeRecord}. */
	static final class Row {
		long timestamp;
		String player1;
		String player2;
		String outcome;
		String reason;
		List<ItemRow> offer1;
		List<ItemRow> offer2;

		static Row of(TradeRecord record) {
			Row row = new Row();
			row.timestamp = record.timestampMillis();
			row.player1 = record.player1Name();
			row.player2 = record.player2Name();
			row.outcome = record.outcome();
			row.reason = record.detail();
			row.offer1 = ItemRow.of(record.offer1());
			row.offer2 = ItemRow.of(record.offer2());
			return row;
		}

		TradeRecord toRecord() {
			return new TradeRecord(timestamp, player1, player2,
					ItemRow.toSnapshots(offer1), ItemRow.toSnapshots(offer2), outcome, reason);
		}
	}

	/** Gson-friendly mirror of {@link ItemSnapshot}. */
	static final class ItemRow {
		String id;
		int count;
		String name;
		String data;

		static List<ItemRow> of(List<ItemSnapshot> snapshots) {
			List<ItemRow> out = new ArrayList<>(snapshots.size());
			for (ItemSnapshot snapshot : snapshots) {
				ItemRow row = new ItemRow();
				row.id = snapshot.itemId();
				row.count = snapshot.count();
				row.name = snapshot.displayName();
				row.data = snapshot.dataFingerprint();
				out.add(row);
			}
			return out;
		}

		static List<ItemSnapshot> toSnapshots(List<ItemRow> rows) {
			if (rows == null) {
				return List.of();
			}
			List<ItemSnapshot> out = new ArrayList<>(rows.size());
			for (ItemRow row : rows) {
				out.add(new ItemSnapshot(row.id, row.count, row.name, row.data));
			}
			return out;
		}
	}
}
