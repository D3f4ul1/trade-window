package com.tradewindow.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Plain data model for {@code config/tradewindow.json}.
 *
 * <p>This class deliberately contains no Minecraft types so that it can be unit
 * tested directly and shared verbatim by every Minecraft target, from 1.20.1 to
 * 26.3. Loading from disk, watching for external edits and reporting parse errors
 * is the job of the version-specific {@code config.TradeConfig} wrapper.
 *
 * <p>All values are public fields rather than getters because Gson binds to
 * fields directly and because the resulting JSON is the documented public
 * contract of the mod.
 */
public final class TradeConfigData {

	/**
	 * How long a trade <em>request</em> stays pending before it expires, in seconds.
	 *
	 * <p>Short on purpose. A request is answered from a chat line that is still on
	 * screen, so fifteen seconds is long enough to notice and act on it, and short
	 * enough that a request a player has stopped looking at does not sit armed. It
	 * also bounds how long the requester's Trade Token is reserved.
	 */
	public int tradeTimeoutSeconds = 15;

	/**
	 * How long the trade window may stay open before it is force-cancelled, in
	 * seconds.
	 *
	 * <p>Two minutes, because a trade window is where the actual work happens: reading
	 * the other side, fetching things out of chests, deciding. This timer is an idle
	 * safeguard rather than a countdown the player is racing.
	 */
	public int guiTimeoutSeconds = 120;

	/**
	 * Maximum distance in blocks the two traders may drift apart before the trade
	 * cancels.
	 *
	 * <p>Twenty blocks, and only read when the distance rule is on at all - see
	 * {@link #crossDistanceTrading} and {@link #cancelOnMove}. Eight blocks was tight
	 * enough that shuffling around a base could end a trade.
	 */
	public int maxDistanceBlocks = 20;

	/**
	 * When true, {@link #maxDistanceBlocks} is not enforced at all: the two traders
	 * may be arbitrarily far apart, in any dimension.
	 *
	 * <p>Defaults to {@code true}, which is the behaviour a server wants out of the
	 * box: the distance rule exists to stop a player being held in a trade window
	 * while they try to run away, and modern servers teleport players constantly, so
	 * enforcing a block radius produced far more cancelled trades than scams. Setting
	 * this to {@code false} restores the classic rule, which then also honours
	 * {@link #cancelOnMove} - itself off by default - so an operator has to turn both
	 * switches over before a trade can be ended by distance.
	 */
	public boolean crossDistanceTrading = true;

	/** When true, taking any damage cancels the trade. */
	public boolean cancelOnDamage = true;

	/**
	 * Master switch for the distance rule: when false, {@link #maxDistanceBlocks} is
	 * never enforced.
	 *
	 * <p>Defaults to {@code false} - the same posture as {@link #crossDistanceTrading}:
	 * a trade that is cancelled because somebody stepped over a line is a worse
	 * experience than a trade that finishes, and the window's own timeout already
	 * bounds how long a trade can sit open. Both switches have to be on for the rule
	 * to run; {@link TradeDistance#enforced} is the single place that decides.
	 */
	public boolean cancelOnMove = false;

	/** When true, a Trade Token is required to send a request and is consumed on open. */
	public boolean requireToken = true;

	/** When true, completed and cancelled trades are written to {@code tradewindow.log}. */
	public boolean logTrades = true;

	/** When true, completed trades are additionally written to the SQLite history store. */
	public boolean useDatabase = false;

	/** When false, creative players may not trade with non-creative players. */
	public boolean allowCreativeTrading = false;

	/**
	 * When false, the two players must be in the same dimension.
	 *
	 * <p>Not present in the original specification, which only says cross-dimension
	 * trading is "blocked (configurable)" without naming a key.
	 */
	public boolean allowCrossDimensionTrading = false;

	/** Item ids that may never be placed into a trade slot. */
	public List<String> blacklistedItems = new ArrayList<>(List.of("minecraft:bedrock", "minecraft:command_block"));

	/**
	 * Cap on the combined value of both offers, or {@code -1} to disable the cap.
	 *
	 * <p>Values come from {@link TradeValueTable}; an item with no known value
	 * contributes {@link TradeValueTable#UNKNOWN_VALUE}.
	 */
	public int maxTradeValue = -1;

	/**
	 * Builds a config populated entirely with defaults.
	 *
	 * @return a fresh default configuration
	 */
	public static TradeConfigData defaults() {
		return new TradeConfigData();
	}

	/**
	 * Parses a config from JSON text, falling back to defaults on malformed input.
	 *
	 * <p>Fields missing from the JSON keep their default value, which is what makes
	 * it safe to add new options in a later release without breaking existing files.
	 *
	 * @param json the raw file contents
	 * @return the parsed and normalised configuration, never {@code null}
	 * @throws JsonSyntaxException if the text is not valid JSON at all
	 */
	public static TradeConfigData fromJson(String json) {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		TradeConfigData data = gson.fromJson(json, TradeConfigData.class);
		if (data == null) {
			data = defaults();
		}
		data.normalize();
		return data;
	}

	/**
	 * Serialises this configuration to pretty-printed JSON.
	 *
	 * @return the JSON representation, ready to be written to disk
	 */
	public String toJson() {
		return new GsonBuilder()
				.setPrettyPrinting()
				.disableHtmlEscaping()
				.create()
				.toJson(this);
	}

	/**
	 * Clamps out-of-range values and repairs null collections in place.
	 *
	 * <p>Called automatically by {@link #fromJson(String)}. Every clamp is chosen
	 * so that a corrupt config degrades into <em>more</em> safety, never less: a
	 * negative timeout becomes a short-but-finite one, a nonsense distance becomes
	 * the default, and so on.
	 *
	 * @return {@code this}, for chaining
	 */
	public TradeConfigData normalize() {
		tradeTimeoutSeconds = clamp(tradeTimeoutSeconds, 5, 3600, 30);
		guiTimeoutSeconds = clamp(guiTimeoutSeconds, 10, 3600, 60);
		maxDistanceBlocks = clamp(maxDistanceBlocks, 1, 512, 8);

		if (blacklistedItems == null) {
			blacklistedItems = new ArrayList<>(List.of("minecraft:bedrock", "minecraft:command_block"));
		}

		// De-duplicate while preserving order, and store ids in a canonical
		// lower-case form so comparisons never depend on how the file was typed.
		Set<String> seen = new LinkedHashSet<>();
		List<String> cleaned = new ArrayList<>(blacklistedItems.size());
		for (String raw : blacklistedItems) {
			if (raw == null || raw.isBlank()) {
				continue;
			}
			String id = raw.trim().toLowerCase(Locale.ROOT);
			if (seen.add(id)) {
				cleaned.add(id);
			}
		}
		blacklistedItems = cleaned;

		if (maxTradeValue < -1) {
			maxTradeValue = -1;
		}
		return this;
	}

	/**
	 * Returns an immutable, lower-case view of the blacklist for fast lookup.
	 *
	 * @return the blacklisted item ids
	 */
	public Set<String> blacklistSet() {
		Set<String> set = new LinkedHashSet<>(blacklistedItems.size());
		for (String id : blacklistedItems) {
			set.add(id.toLowerCase(Locale.ROOT));
		}
		return set;
	}

	/**
	 * Reports whether the value cap is active.
	 *
	 * @return {@code true} when {@link #maxTradeValue} is zero or greater
	 */
	public boolean hasValueCap() {
		return maxTradeValue >= 0;
	}

	private static int clamp(int value, int min, int max, int fallback) {
		if (value < min) {
			return min;
		}
		if (value > max) {
			return max;
		}
		return value;
	}
}
