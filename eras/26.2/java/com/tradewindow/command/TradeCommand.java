package com.tradewindow.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.tradewindow.trade.TradeManager;
import com.tradewindow.util.TradeUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

/**
 * The {@code /trade} command tree, and the whole of it.
 *
 * <pre>
 *   /trade &lt;player&gt;   send a trade request (needs a Trade Token unless configured off)
 *   /trade accept     accept the request shown to you
 *   /trade decline    decline it
 * </pre>
 *
 * <p><b>Three commands, because only three things need one.</b> Everything that
 * happens inside the window - locking a side, cancelling the trade - is a click on a
 * block in that window, handled by the menu itself, so {@code /trade lock},
 * {@code /trade unlock} and {@code /trade cancel} are gone along with the whole
 * confirm/unconfirm vocabulary they belonged to. Accept and decline stay commands
 * because they answer a request that arrives before any window exists, which is why
 * the request message can offer them as buttons.
 *
 * <p>Every branch re-checks its preconditions server-side through {@link TradeManager},
 * so running {@code /trade accept} with nothing pending is a polite error rather than
 * an empty window or a crash.
 *
 * <p>The operator tools - listing trades, spectating one, reading history, flipping
 * the distance rule - live in {@code /tradeadmin}, not here: they are op-gated, they
 * are not player actions, and keeping them out of this file leaves the player-facing
 * command surface auditable at a glance.
 */
public final class TradeCommand {

	private TradeCommand() {
	}

	/**
	 * Registers the command tree.
	 *
	 * @param dispatcher the server's command dispatcher
	 */
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("trade")
				.then(Commands.literal("accept")
						.executes(TradeCommand::accept))
				.then(Commands.literal("decline")
						.executes(TradeCommand::decline))
				.then(Commands.argument("player", EntityArgument.player())
						.executes(TradeCommand::request)));
	}

	private static int request(CommandContext<CommandSourceStack> context) {
		ServerPlayer sender = playerOrNull(context);
		if (sender == null) {
			return 0;
		}
		ServerPlayer target;
		try {
			target = EntityArgument.getPlayer(context, "player");
		} catch (Exception e) {
			context.getSource().sendFailure(TradeUtils.error("tradewindow.error.offline"));
			return 0;
		}
		return TradeManager.request(sender, target) ? 1 : 0;
	}

	private static int accept(CommandContext<CommandSourceStack> context) {
		ServerPlayer player = playerOrNull(context);
		return player != null && TradeManager.accept(player) ? 1 : 0;
	}

	private static int decline(CommandContext<CommandSourceStack> context) {
		ServerPlayer player = playerOrNull(context);
		return player != null && TradeManager.decline(player) ? 1 : 0;
	}

	/**
	 * Returns the player running the command, or reports that a player is required.
	 *
	 * @param context the command context
	 * @return the player, or {@code null} when the source was the console
	 */
	private static ServerPlayer playerOrNull(CommandContext<CommandSourceStack> context) {
		try {
			return context.getSource().getPlayerOrException();
		} catch (Exception e) {
			context.getSource().sendFailure(TradeUtils.error("tradewindow.error.player.only"));
			return null;
		}
	}
}
