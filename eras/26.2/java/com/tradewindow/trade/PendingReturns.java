package com.tradewindow.trade;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.tradewindow.TradeWindow;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Items stranded by a server restart.
 *
 * <p>Trades do not survive a restart — that is a deliberate, documented
 * limitation. What must survive is the items: if the server stops while a window
 * is open and a player has already disconnected, their offer would otherwise be
 * destroyed. Those stacks are written to {@code tradewindow-pending-returns.json}
 * and handed back the next time that player logs in.
 *
 * <p><b>Why JSON and not NBT.</b> On 1.20.1 this file is compressed NBT, because
 * an item's data <em>is</em> an NBT tag there. On 26.3 an item's data is a set of
 * data components, whose canonical serialised form is JSON — {@code ItemStack.CODEC}
 * with {@code JsonOps} round-trips every component exactly, including
 * enchantments, custom names and container contents. Using the codec rather than
 * hand-rolling a format is what guarantees nothing is dropped.
 */
public final class PendingReturns {

	private static final String FILE_NAME = "tradewindow-pending-returns.json";

	private static final Map<UUID, List<ItemStack>> PENDING = new LinkedHashMap<>();
	private static Path file;

	private PendingReturns() {
	}

	/**
	 * Loads any returns left behind by a previous run.
	 *
	 * @param gameDir the game or server directory
	 */
	public static void init(Path gameDir) {
		file = gameDir.resolve(FILE_NAME);
		PENDING.clear();
		if (!Files.isRegularFile(file)) {
			return;
		}
		try {
			String raw = Files.readString(file, StandardCharsets.UTF_8);
			JsonElement root = JsonParser.parseString(raw);
			if (!root.isJsonObject()) {
				return;
			}
			JsonArray players = root.getAsJsonObject().getAsJsonArray("players");
			if (players == null) {
				return;
			}
			for (JsonElement element : players) {
				JsonObject entry = element.getAsJsonObject();
				UUID uuid;
				try {
					uuid = UUID.fromString(entry.get("uuid").getAsString());
				} catch (RuntimeException e) {
					continue;
				}
				List<ItemStack> stacks = new ArrayList<>();
				JsonArray items = entry.getAsJsonArray("items");
				if (items != null) {
					for (JsonElement item : items) {
						ItemStack stack = ItemStack.CODEC.parse(JsonOps.INSTANCE, item)
								.result()
								.orElse(ItemStack.EMPTY);
						if (!stack.isEmpty()) {
							stacks.add(stack);
						}
					}
				}
				if (!stacks.isEmpty()) {
					PENDING.put(uuid, stacks);
				}
			}
			TradeWindow.LOGGER.warn("Recovered stranded trade items for {} player(s) from a previous run",
					PENDING.size());
		} catch (Exception e) {
			// A corrupt file must not stop the server from starting. The old file is
			// left in place so an operator can inspect it.
			TradeWindow.LOGGER.error("Could not read {} — stranded items were not recovered",
					file.getFileName(), e);
		}
	}

	/**
	 * Queues items for a player who is not currently online.
	 *
	 * @param playerId the owner of the items
	 * @param stacks   the stacks to hold
	 */
	public static void store(UUID playerId, List<ItemStack> stacks) {
		if (stacks.isEmpty()) {
			return;
		}
		PENDING.computeIfAbsent(playerId, k -> new ArrayList<>()).addAll(stacks);
		TradeWindow.LOGGER.info("Holding {} stack(s) for offline player {}", stacks.size(), playerId);
	}

	/**
	 * Removes and returns a player's held items.
	 *
	 * @param playerId the returning player
	 * @return the stacks, or an empty list if nothing was held
	 */
	public static List<ItemStack> take(UUID playerId) {
		List<ItemStack> stacks = PENDING.remove(playerId);
		if (stacks == null) {
			return List.of();
		}
		flush();
		return stacks;
	}

	/**
	 * Reports whether anything is currently being held.
	 *
	 * @return {@code true} if at least one player has items waiting
	 */
	public static boolean hasPending() {
		return !PENDING.isEmpty();
	}

	/**
	 * Writes the held items to disk, or deletes the file when nothing is held.
	 *
	 * <p>Called after every mutation so a crash between a store and a restore
	 * cannot lose items.
	 */
	public static void flush() {
		if (file == null) {
			return;
		}
		if (PENDING.isEmpty()) {
			try {
				Files.deleteIfExists(file);
			} catch (IOException e) {
				TradeWindow.LOGGER.warn("Could not remove {}", file.getFileName(), e);
			}
			return;
		}

		JsonObject root = new JsonObject();
		JsonArray players = new JsonArray();
		for (Map.Entry<UUID, List<ItemStack>> entry : PENDING.entrySet()) {
			JsonObject tag = new JsonObject();
			tag.addProperty("uuid", entry.getKey().toString());
			JsonArray items = new JsonArray();
			for (ItemStack stack : entry.getValue()) {
				if (stack.isEmpty()) {
					continue;
				}
				ItemStack.CODEC.encodeStart(JsonOps.INSTANCE, stack)
						.result()
						.ifPresent(items::add);
			}
			tag.add("items", items);
			players.add(tag);
		}
		root.add("players", players);

		try {
			Path parent = file.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			TradeWindow.LOGGER.error("Could not write {} — stranded items may be lost on restart",
					file.getFileName(), e);
		}
	}
}
