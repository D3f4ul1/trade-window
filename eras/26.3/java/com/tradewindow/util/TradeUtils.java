package com.tradewindow.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Chat and item helpers for the 1.21.11 build.
 *
 * <p><b>Every message is one line, built from the language file.</b> The shape is
 * fixed so it reads the same everywhere it appears:
 *
 * <pre>
 *   [Trade] Body text in white. [ Accept ] [ Decline ]
 * </pre>
 *
 * <p>The {@code [Trade]} prefix is gold, the body is white (red for an error), and
 * buttons are bracketed and coloured by what they do. Buttons carry a
 * {@code run_command} click event, which is vanilla chat behaviour, so an
 * unmodified client can press them.
 *
 * <p>No sentence is written here: {@link TradeText} resolves each key from
 * {@code assets/tradewindow/lang/en_us.json} and this class only adds the styling
 * and the spacing. The one space between the prefix, the body and each button is
 * inserted here, in {@link #buttons(Component...)}, so no template has to carry
 * trailing whitespace.
 *
 * <p>The inventory helper is the single funnel for every stack that leaves a trade
 * window: it prefers the player's inventory and drops the remainder at their feet.
 * It never silently deletes an item.
 */
public final class TradeUtils {

	/** Language key of the chat prefix, {@code "[Trade] "} including its space. */
	public static final String PREFIX_KEY = "tradewindow.chat.prefix";

	private TradeUtils() {
	}

	/**
	 * Builds a normal message: gold prefix, white body.
	 *
	 * @param key  the language key of the body
	 * @param args format arguments for the body
	 * @return the styled, single-line component
	 */
	public static MutableComponent message(String key, Object... args) {
		return line(key, ChatFormatting.WHITE, args);
	}

	/**
	 * Builds a red error message.
	 *
	 * @param key  the language key of the body
	 * @param args format arguments for the body
	 * @return the styled component
	 */
	public static MutableComponent error(String key, Object... args) {
		return line(key, ChatFormatting.RED, args);
	}

	private static MutableComponent line(String key, ChatFormatting colour, Object... args) {
		return Component.literal(TradeText.get(PREFIX_KEY)).withStyle(ChatFormatting.GOLD)
				.append(Component.literal(TradeText.format(key, args)).withStyle(colour));
	}

	/**
	 * Appends clickable buttons to a message, each preceded by exactly one space.
	 *
	 * @param buttons the buttons, in display order
	 * @return the button block, to be appended to a message
	 */
	public static MutableComponent buttons(Component... buttons) {
		MutableComponent block = Component.empty();
		for (Component button : buttons) {
			block.append(Component.literal(" ")).append(button);
		}
		return block;
	}

	/**
	 * Builds one clickable button.
	 *
	 * @param key      language key of the visible label, brackets included
	 * @param command  the command the client runs on click, for example {@code /trade accept}
	 * @param colour   the button colour
	 * @param hoverKey language key of the tooltip shown on hover
	 * @return the button component
	 */
	public static MutableComponent button(String key, String command, ChatFormatting colour,
			String hoverKey) {
		return Component.literal(TradeText.get(key)).withStyle(style -> style
				.withColor(colour)
				.withBold(true)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(TradeText.get(hoverKey)))));
	}

	/** @return the green "[ Accept ]" button */
	public static MutableComponent acceptButton() {
		return button("tradewindow.button.accept", "/trade accept", ChatFormatting.GREEN,
				"tradewindow.button.accept.hover");
	}

	/** @return the red "[ Decline ]" button */
	public static MutableComponent declineButton() {
		return button("tradewindow.button.decline", "/trade decline", ChatFormatting.RED,
				"tradewindow.button.decline.hover");
	}

	/**
	 * Sends a message to a player.
	 *
	 * @param player  the recipient
	 * @param message the message
	 */
	public static void send(ServerPlayer player, Component message) {
		player.sendSystemMessage(message);
	}

	/**
	 * Hands a stack to a player, dropping only what does not fit at their feet.
	 *
	 * @param player the receiving player
	 * @param stack  the stack to hand over
	 */
	public static void giveOrDrop(Player player, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		// A copy is inserted and any remainder dropped, so a partial insert can
		// never leave the caller holding a half-consumed stack.
		ItemStack toInsert = stack.copy();
		if (!player.getInventory().add(toInsert) && !toInsert.isEmpty()) {
			player.drop(toInsert, false, Prediction.SERVER_ONLY);
		}
	}
}
