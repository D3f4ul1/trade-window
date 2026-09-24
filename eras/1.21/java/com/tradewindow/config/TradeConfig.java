package com.tradewindow.config;

import com.tradewindow.TradeWindow;
import com.tradewindow.core.TradeConfigData;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code config/tradewindow.json} for the 1.21.11 build.
 *
 * <p>Thin wrapper around {@link TradeConfigData}: the model, the defaults and every
 * clamp live in the shared core, which is unit tested and identical on every
 * target. This class only touches the filesystem.
 *
 * <p><b>Every field is honoured.</b> v1.3.0 of this target shipped a three-field
 * config; v1.4.0 restores the documented twelve plus the cross-distance switch, and
 * each one drives real behaviour:
 *
 * <ul>
 *   <li>{@code tradeTimeoutSeconds} - how long a request waits for an answer;</li>
 *   <li>{@code guiTimeoutSeconds} - how long the window may sit idle;</li>
 *   <li>{@code maxDistanceBlocks} and {@code cancelOnMove} - the distance rule,
 *       which only runs when {@code crossDistanceTrading} is false, and is then
 *       checked before a request is sent, before the requester's token is spent and
 *       on every tick;</li>
 *   <li>{@code crossDistanceTrading} - the {@code /tradeadmin crossdistance}
 *       switch, default true (no distance check at all);</li>
 *   <li>{@code cancelOnDamage} - taking a hit ends the trade;</li>
 *   <li>{@code requireToken} - whether a Trade Token is needed to ask;</li>
 *   <li>{@code logTrades} and {@code useDatabase} - where trade history is
 *       written, and therefore what {@code /tradeadmin history} reads back;</li>
 *   <li>{@code allowCreativeTrading} - whether a creative player may trade with a
 *       survival one;</li>
 *   <li>{@code blacklistedItems} - items the grid refuses, matched by id;</li>
 *   <li>{@code maxTradeValue} - optional point cap on a combined offer;</li>
 *   <li>{@code allowCrossDimensionTrading} - whether the two traders must share a
 *       dimension.</li>
 * </ul>
 *
 * @see TradeConfigData for the defaults, the clamping rules and the JSON contract
 */
public final class TradeConfig {

	private static final String FILE_NAME = "tradewindow.json";

	private static TradeConfigData data = TradeConfigData.defaults();

	private TradeConfig() {
	}

	/**
	 * Returns the active configuration.
	 *
	 * @return the live config object; mutate and call {@link #save()} to persist
	 */
	public static TradeConfigData get() {
		return data;
	}

	/**
	 * Returns the path of the config file.
	 *
	 * @return {@code <config dir>/tradewindow.json}
	 */
	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	/**
	 * Reads the config file, creating it with defaults when absent.
	 *
	 * <p>Called once during mod initialisation.
	 */
	public static void load() {
		Path file = path();
		if (!Files.isRegularFile(file)) {
			data = TradeConfigData.defaults();
			save();
			TradeWindow.LOGGER.info("Created default config at {}", file);
			return;
		}

		try {
			String json = Files.readString(file, StandardCharsets.UTF_8);
			data = TradeConfigData.fromJson(json);
			// Persist the normalised form so any clamping is visible to the operator
			// instead of being silently applied on every start.
			save();
			TradeWindow.LOGGER.info("Loaded config from {}", file);
		} catch (Exception e) {
			TradeWindow.LOGGER.error("Could not read {} - falling back to defaults", file, e);
			data = TradeConfigData.defaults();
		}
	}

	/**
	 * Writes the current configuration back to disk.
	 *
	 * <p>Failures are logged rather than thrown: the in-memory config stays
	 * authoritative for the running session.
	 */
	public static void save() {
		Path file = path();
		try {
			Path parent = file.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Files.writeString(file, data.toJson(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			TradeWindow.LOGGER.error("Could not write {}", file, e);
		}
	}
}
