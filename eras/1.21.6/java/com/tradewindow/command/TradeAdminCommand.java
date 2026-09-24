package com.tradewindow.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.tradewindow.config.TradeConfig;
import com.tradewindow.core.ItemSnapshot;
import com.tradewindow.core.TradeRecord;
import com.tradewindow.core.TradeSide;
import com.tradewindow.trade.TradeManager;
import com.tradewindow.trade.TradeSession;
import com.tradewindow.util.TradeText;
import com.tradewindow.util.TradeUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The {@code /tradeadmin} tree: the four things an operator needs and nothing else.
 *
 * <pre>
 *   /tradeadmin crossdistance &lt;on|off&gt;   allow or forbid trading at any distance
 *   /tradeadmin list                      show every live trade
 *   /tradeadmin spectate &lt;player&gt;         watch a trade read-only
 *   /tradeadmin history [player]          the last trades, newest first
 * </pre>
 *
 * <p><b>Why this is a separate tree from {@code /trade}.</b> {@code /trade} is for
 * players and holds only what a window cannot do; these four are operator tools, are
 * gated at permission level 2, and should stay out of a player's tab-completion. They
 * live in their own file for the same reason - the player command's surface should be
 * auditable at a glance.
 *
 * <p>Every reply goes through the command source, so all four work from the console
 * as well as from an op's chat, and every line is one line: an operator running
 * {@code /tradeadmin list} should not have to scroll.
 */
public final class TradeAdminCommand {

	/** How many records {@code /tradeadmin history} shows. */
	private static final int HISTORY_LIMIT = 20;

	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter
			.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

	/** String argument name for the optional history filter. */
	private static final String PLAYER_ARGUMENT = "player";

	private TradeAdminCommand() {
	}

	/**
	 * Registers the command tree.
	 *
	 * @param dispatcher the server's command dispatcher
	 */
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("tradeadmin")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("crossdistance")
						.then(Commands.literal("on").executes(context -> crossDistance(context, true)))
						.then(Commands.literal("off").executes(context -> crossDistance(context, false))))
				.then(Commands.literal("list").executes(TradeAdminCommand::list))
				.then(Commands.literal("spectate")
						.then(Commands.argument(PLAYER_ARGUMENT, EntityArgument.player())
								.executes(TradeAdminCommand::spectate)))
				.then(Commands.literal("history")
						.executes(context -> history(context, null))
						.then(Commands.argument(PLAYER_ARGUMENT, StringArgumentType.word())
								.executes(context -> history(context,
										StringArgumentType.getString(context, PLAYER_ARGUMENT))))));
	}

	/**
	 * Toggles the distance rule and persists it.
	 *
	 * <p>Written to the config file immediately: an operator who changes this expects
	 * it to survive the next restart, and this is the one setting they are most likely
	 * to change while a trade is in flight.
	 *
	 * @param context the command context
	 * @param on      whether players may trade from any distance
	 * @return the command result
	 */
	private static int crossDistance(CommandContext<CommandSourceStack> context, boolean on) {
		TradeConfig.get().crossDistanceTrading = on;
		TradeConfig.save();
		if (on) {
			reply(context.getSource(), "tradewindow.admin.crossdistance.on");
		} else {
			reply(context.getSource(), "tradewindow.admin.crossdistance.off",
					Integer.toString(TradeConfig.get().maxDistanceBlocks));
		}
		return 1;
	}

	/**
	 * Lists every live trade: who, how long it has been open, and each side's lock
	 * state.
	 *
	 * <p>Only the two lock states are reported, because those are the only things about
	 * a live trade that change without a player action.
	 *
	 * @param context the command context
	 * @return the command result
	 */
	private static int list(CommandContext<CommandSourceStack> context) {
		List<TradeSession> sessions = List.copyOf(TradeManager.active());
		if (sessions.isEmpty()) {
			reply(context.getSource(), "tradewindow.admin.list.none");
			return 0;
		}
		long now = System.currentTimeMillis();
		reply(context.getSource(), "tradewindow.admin.list.header",
				Integer.toString(sessions.size()));
		for (TradeSession session : sessions) {
			reply(context.getSource(), "tradewindow.admin.list.entry",
					session.name(TradeSide.PLAYER_1),
					session.name(TradeSide.PLAYER_2),
					Long.toString(session.elapsedSeconds(now)),
					stateText(session, TradeSide.PLAYER_1),
					stateText(session, TradeSide.PLAYER_2));
		}
		return sessions.size();
	}

	/**
	 * Opens a live trade for the caller to watch, read-only.
	 *
	 * @param context the command context
	 * @return the command result
	 */
	private static int spectate(CommandContext<CommandSourceStack> context) {
		ServerPlayer admin;
		ServerPlayer target;
		try {
			admin = context.getSource().getPlayerOrException();
			target = EntityArgument.getPlayer(context, PLAYER_ARGUMENT);
		} catch (Exception e) {
			context.getSource().sendFailure(TradeUtils.error("tradewindow.error.player.only"));
			return 0;
		}

		if (!TradeManager.spectate(admin, target)) {
			TradeUtils.send(admin, TradeUtils.message("tradewindow.error.spectator.none",
					target.getName().getString()));
			return 0;
		}

		TradeSession session = TradeManager.sessionOf(target);
		if (session != null) {
			TradeUtils.send(admin, TradeUtils.message("tradewindow.admin.spectate.opened",
					session.name(TradeSide.PLAYER_1), session.name(TradeSide.PLAYER_2)));
		}
		return 1;
	}

	/**
	 * Shows recent completed trades, optionally for one player.
	 *
	 * @param context the command context
	 * @param filter  a player name to filter by, or {@code null}
	 * @return the command result
	 */
	private static int history(CommandContext<CommandSourceStack> context, String filter) {
		List<TradeRecord> records = TradeManager.history(HISTORY_LIMIT, filter);
		if (records.isEmpty()) {
			reply(context.getSource(), "tradewindow.admin.history.none");
			return 0;
		}
		reply(context.getSource(), "tradewindow.admin.history.header",
				Integer.toString(records.size()));
		for (TradeRecord record : records) {
			reply(context.getSource(), "tradewindow.admin.history.entry",
					TIMESTAMP.format(Instant.ofEpochMilli(record.timestampMillis())
							.atZone(ZoneId.systemDefault())),
					record.player1Name(),
					record.player2Name(),
					describe(record));
		}
		return records.size();
	}

	/**
	 * Abbreviates both offers of a record into one readable clause.
	 *
	 * @param record the record to describe
	 * @return for example {@code 3x minecraft:diamond <-> 1x minecraft:emerald}
	 */
	private static String describe(TradeRecord record) {
		return abbreviate(record.offer1()) + " <-> " + abbreviate(record.offer2());
	}

	private static String abbreviate(List<ItemSnapshot> offer) {
		if (offer.isEmpty()) {
			return "-";
		}
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < offer.size(); i++) {
			if (i > 0) {
				text.append(", ");
			}
			text.append(offer.get(i).summary());
			// Two stacks from each side is enough to recognise a trade; the file
			// holds the rest.
			if (i == 1 && offer.size() > 2) {
				text.append(" (+").append(offer.size() - 2).append(')');
				break;
			}
		}
		return text.toString();
	}

	/** Returns the localised name of a side's lock state. */
	private static String stateText(TradeSession session, TradeSide side) {
		return TradeText.get(session.isLocked(side)
				? "tradewindow.admin.state.locked"
				: "tradewindow.admin.state.editing");
	}

	/**
	 * Sends one prefixed line to the command source.
	 *
	 * <p>Built through {@link TradeUtils} so administrator output has exactly the same
	 * shape as player output - gold prefix, white body - whether it lands in an op's
	 * chat or the server console. Arguments are optional because most lines take none.
	 *
	 * @param source the command source
	 * @param key    the language key of the body
	 * @param args   the format arguments, if any
	 */
	private static void reply(CommandSourceStack source, String key, String... args) {
		Component line = TradeUtils.message(key, (Object[]) args);
		source.sendSuccess(() -> line, false);
	}
}
