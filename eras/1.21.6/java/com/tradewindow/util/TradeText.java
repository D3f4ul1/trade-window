package com.tradewindow.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tradewindow.TradeWindow;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Every word the player reads, loaded from the mod's own language file.
 *
 * <p><b>Where the strings live.</b> All of them are in
 * {@code assets/tradewindow/lang/en_us.json}, in this jar, and nowhere else: Java
 * holds keys and format arguments, never sentences. The file is read once at
 * startup through the classpath, which works identically on a dedicated server
 * (mod resources are on the server classpath) and in a development run.
 *
 * <p><b>Why the file is read rather than resolved by the client.</b> This mod is
 * {@code "environment": "server"} and vanilla clients must be able to trade, so no
 * client ever has this mod's language file. A {@code Component.translatable(...)}
 * would therefore reach the player as a raw key such as
 * {@code tradewindow.chat.complete}. The server resolves each key here and sends
 * finished text, which every client renders.
 *
 * <p><b>Missing keys are visible, not fatal.</b> An unknown key resolves to the key
 * itself and is logged once, so a typo shows up as {@code tradewindow.chat.lock} in
 * chat (and fails {@code tools/verify_packaging.py}) instead of throwing inside a
 * click handler.
 */
public final class TradeText {

	/** Classpath location of the language file, also shipped in the jar. */
	private static final String RESOURCE = "/assets/tradewindow/lang/en_us.json";

	/** Keys already reported missing, so one typo logs one line rather than one per send. */
	private static final java.util.Set<String> REPORTED = new java.util.HashSet<>();

	private static Map<String, String> strings = Map.of();
	private static boolean loaded;

	private TradeText() {
	}

	/**
	 * Reads the language file into memory.
	 *
	 * <p>Called once, early in mod initialisation, before anything can send a
	 * message or build a label item.
	 */
	public static synchronized void load() {
		if (loaded) {
			return;
		}
		Map<String, String> parsed = new HashMap<>();
		try (InputStream stream = TradeText.class.getResourceAsStream(RESOURCE)) {
			if (stream == null) {
				TradeWindow.LOGGER.error("Language file {} is missing from the jar - every message will "
						+ "show its key instead of text", RESOURCE);
				return;
			}
			JsonElement root = JsonParser.parseReader(
					new InputStreamReader(stream, StandardCharsets.UTF_8));
			if (!root.isJsonObject()) {
				TradeWindow.LOGGER.error("Language file {} is not a JSON object", RESOURCE);
				return;
			}
			JsonObject object = root.getAsJsonObject();
			for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
				if (entry.getValue().isJsonPrimitive()) {
					parsed.put(entry.getKey(), entry.getValue().getAsString());
				}
			}
			strings = Collections.unmodifiableMap(parsed);
			loaded = true;
			TradeWindow.LOGGER.info("Loaded {} message(s) from {}", strings.size(), RESOURCE);
		} catch (Exception e) {
			TradeWindow.LOGGER.error("Could not read {} - falling back to raw keys", RESOURCE, e);
		}
	}

	/**
	 * Returns a string by key.
	 *
	 * @param key the language key, for example {@code tradewindow.chat.complete}
	 * @return the text, or the key itself when it is not defined
	 */
	public static String get(String key) {
		String value = strings.get(key);
		if (value != null) {
			return value;
		}
		if (REPORTED.add(key)) {
			TradeWindow.LOGGER.warn("Undefined language key: {}", key);
		}
		return key;
	}

	/**
	 * Returns a formatted string by key.
	 *
	 * <p>Formatting runs in {@link Locale#ROOT}, so a server's locale can never
	 * turn a plain {@code %s} into something localised mid-sentence.
	 *
	 * @param key  the language key
	 * @param args the format arguments, in the order the template expects them
	 * @return the formatted text
	 */
	public static String format(String key, Object... args) {
		String template = get(key);
		return args == null || args.length == 0 ? template : String.format(Locale.ROOT, template, args);
	}

	/**
	 * Reports whether a key is defined.
	 *
	 * @param key the language key
	 * @return {@code true} when the key exists in the language file
	 */
	public static boolean has(String key) {
		return strings.containsKey(key);
	}

	/**
	 * Returns the number of loaded strings.
	 *
	 * @return the count, or {@code 0} before {@link #load()} succeeds
	 */
	public static int size() {
		return strings.size();
	}
}
