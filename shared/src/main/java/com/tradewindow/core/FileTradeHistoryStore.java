package com.tradewindow.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Append-only JSON-lines trade history.
 *
 * <p>This is the always-available store: one JSON object per line, no external
 * dependencies, trivially greppable, and trivially recoverable if a write is
 * interrupted — a torn final line is skipped when reading rather than throwing.
 */
public final class FileTradeHistoryStore implements TradeHistoryStore {

	private final Path file;

	/**
	 * Creates a store backed by the given file.
	 *
	 * @param file the JSON-lines file; parent directories are created on first write
	 */
	public FileTradeHistoryStore(Path file) {
		this.file = file;
	}

	@Override
	public void record(TradeRecord record) {
		try {
			Path parent = file.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			String line = TradeRecordJson.toJson(record) + System.lineSeparator();
			Files.writeString(file, line, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			// History is diagnostic only: a disk problem must never propagate into
			// the trade path.
			System.err.println("[tradewindow] Could not append trade history: " + e.getMessage());
		}
	}

	@Override
	public List<TradeRecord> recent(int limit) {
		if (limit <= 0 || !Files.isRegularFile(file)) {
			return List.of();
		}
		Deque<String> tail = new ArrayDeque<>(limit);
		try {
			// Trade logs are small text files, so a full read beats the complexity
			// of a real tail-seek here.
			for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
				if (line.isBlank()) {
					continue;
				}
				if (tail.size() == limit) {
					tail.removeFirst();
				}
				tail.addLast(line);
			}
		} catch (IOException e) {
			System.err.println("[tradewindow] Could not read trade history: " + e.getMessage());
			return List.of();
		}

		List<String> ordered = new ArrayList<>(tail);
		Collections.reverse(ordered);
		List<TradeRecord> out = new ArrayList<>(ordered.size());
		for (String line : ordered) {
			try {
				out.add(TradeRecordJson.fromJson(line));
			} catch (RuntimeException ignored) {
				// A torn or hand-edited line must not break /trade history.
			}
		}
		return out;
	}

	@Override
	public boolean isPersistent() {
		return true;
	}

	@Override
	public String describeLocation() {
		return file.getFileName().toString();
	}

	@Override
	public void close() {
		// Nothing to release: every write opens and closes the file.
	}
}
