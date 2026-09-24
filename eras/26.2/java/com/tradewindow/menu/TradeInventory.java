package com.tradewindow.menu;

import com.tradewindow.util.TradeText;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.List;

/**
 * The trade window's slot layout and the items the server puts in it.
 *
 * <p><b>The window is a vanilla double chest and nothing more.</b> An unmodified
 * client renders a title bar and fifty-four slots; it cannot draw text, buttons,
 * dividers or colours inside a container. Labels and controls therefore have to be
 * ordinary items in slots the server owns, which is what this class builds: player
 * heads for identity, black panes for spacing, and green/red concrete for the two
 * buttons.
 *
 * <p><b>The layout, as every viewer sees it:</b>
 *
 * <pre>
 *   row 1   slots  0- 8   header   panes, LOCK, head, CANCEL, panes   (the viewer)
 *   row 2   slots  9-17   the viewer's own offer, upper row
 *   row 3   slots 18-26   the viewer's own offer, lower row
 *   row 4   slots 27-35   header   panes, LOCK, head, CANCEL, panes   (the partner)
 *   row 5   slots 36-44   the partner's offer, upper row
 *   row 6   slots 45-53   the partner's offer, lower row
 * </pre>
 *
 * <p>Both players see that shape - that is the point of the mirroring described in
 * {@link TradeMenu}, which is the only place the two viewers' slot ids diverge.
 * Inside a header row the offsets are fixed: {@link #HEAD} (4) carries the real
 * player head, {@link #LOCK} (3) the green LOCK block, {@link #CANCEL} (5) the red
 * CANCEL block, and 0-2 and 6-8 are black panes.
 *
 * <p><b>Both header buttons do the same thing for whoever clicks them.</b> A click
 * on either LOCK block locks the clicker's own side, and a click on either CANCEL
 * block cancels the trade. Neither can be used on the other player, so a header row
 * never becomes a way to lock someone else's offer shut.
 *
 * <p><b>Why the heads carry real profiles.</b> {@code ResolvableProfile} built from
 * the live player's own {@code GameProfile} already contains the signed texture
 * property, so the vanilla client draws the real skin without contacting a session
 * server or knowing anything about this mod.
 */
public final class TradeInventory {

	/** Total slots, matching a double chest. */
	public static final int SLOTS = 54;

	/** Slots in one row. */
	public static final int ROW_SLOTS = 9;

	/** Offer slots per player, across two rows. */
	public static final int SLOTS_PER_SIDE = 18;

	/** First slot of the viewer's own header row: row 1. */
	public static final int TOP_HEADER = 0;

	/** First slot of the viewer's own offer: row 2, one row below the header. */
	public static final int OWN_OFFER_START = 9;

	/** First slot of the partner's header row: row 4. */
	public static final int BOTTOM_HEADER = 27;

	/** First slot of the partner's offer: row 5, one row below the partner's header. */
	public static final int OTHER_OFFER_START = 36;

	/** Header offset of the player head. */
	public static final int HEAD = 4;

	/** Header offset of the green LOCK block. */
	public static final int LOCK = 3;

	/** Header offset of the red CANCEL block. */
	public static final int CANCEL = 5;

	/** Header offsets of the black separator panes. */
	private static final int[] PANES = {0, 1, 2, 6, 7, 8};

	private TradeInventory() {
	}

	/**
	 * Returns the menu slot of a header row's LOCK block.
	 *
	 * @param headerRow {@link #TOP_HEADER} or {@link #BOTTOM_HEADER}
	 * @return the slot index
	 */
	public static int lockSlot(int headerRow) {
		return headerRow + LOCK;
	}

	/**
	 * Returns the menu slot of a header row's CANCEL block.
	 *
	 * @param headerRow {@link #TOP_HEADER} or {@link #BOTTOM_HEADER}
	 * @return the slot index
	 */
	public static int cancelSlot(int headerRow) {
		return headerRow + CANCEL;
	}

	/**
	 * Returns the first slot of the header row a slot belongs to.
	 *
	 * @param slot the menu slot
	 * @return {@link #TOP_HEADER}, {@link #BOTTOM_HEADER}, or {@code -1} for an
	 *         offer slot or an out-of-range index
	 */
	public static int headerRowOf(int slot) {
		if (slot >= TOP_HEADER && slot < TOP_HEADER + ROW_SLOTS) {
			return TOP_HEADER;
		}
		if (slot >= BOTTOM_HEADER && slot < BOTTOM_HEADER + ROW_SLOTS) {
			return BOTTOM_HEADER;
		}
		return -1;
	}

	/**
	 * Reports whether a menu slot is inside the 54-slot grid at all.
	 *
	 * @param slot the menu slot
	 * @return {@code true} for a grid slot, {@code false} for a player-inventory
	 *         slot or a click outside the window
	 */
	public static boolean isGridSlot(int slot) {
		return slot >= 0 && slot < SLOTS;
	}

	/**
	 * Fills one header row: head, both buttons and the separator panes.
	 *
	 * @param container the header container to write into
	 * @param rowBase   the row's first slot inside that container
	 * @param owner     the player the row belongs to
	 * @param locked    whether that player's side is locked
	 * @param colour    the colour of the head's name, which is how each viewer tells
	 *                  their own row from the partner's
	 */
	public static void paintHeaderRow(Container container, int rowBase, ServerPlayer owner,
			boolean locked, ChatFormatting colour) {
		container.setItem(rowBase + HEAD, head(owner, colour));
		container.setItem(rowBase + LOCK, lockButton(locked));
		container.setItem(rowBase + CANCEL, cancelButton());
		for (int offset : PANES) {
			container.setItem(rowBase + offset, pane());
		}
	}

	/**
	 * Rewrites one header row's LOCK block to match a side's lock state.
	 *
	 * @param container the header container
	 * @param rowBase   the row's first slot inside that container
	 * @param locked    whether the row's owner has locked
	 */
	public static void refreshLock(Container container, int rowBase, boolean locked) {
		container.setItem(rowBase + LOCK, lockButton(locked));
	}

	/**
	 * Builds a player-head label.
	 *
	 * <p>No lore: the head's tooltip is the player's name and nothing else, which is
	 * all a label inside a shared window needs to say.
	 *
	 * @param owner  the player the head represents
	 * @param colour the colour of that name
	 * @return the head stack
	 */
	public static ItemStack head(ServerPlayer owner, ChatFormatting colour) {
		ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
		stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(owner.getGameProfile()));
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(owner.getName().getString())
				.withStyle(colour));
		return stack;
	}

	/** Builds a black separator pane, named with a single space so it reads blank. */
	public static ItemStack pane() {
		ItemStack stack = new ItemStack(Items.STAINED_GLASS_PANE.black());
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(TradeText.get("tradewindow.gui.blank")));
		return stack;
	}

	/**
	 * Builds the LOCK control.
	 *
	 * <p>Green concrete while the side can still be locked; once it is locked the
	 * block is replaced by a green pane named "LOCKED", so the button is visibly
	 * spent, both players can see the state without any chat line, and a second
	 * click has nothing left to press.
	 *
	 * @param locked whether the side this button belongs to is locked
	 * @return the button stack
	 */
	public static ItemStack lockButton(boolean locked) {
		if (locked) {
			ItemStack spent = new ItemStack(Items.STAINED_GLASS_PANE.green());
			spent.set(DataComponents.CUSTOM_NAME, Component
					.literal(TradeText.get("tradewindow.gui.locked"))
					.withStyle(ChatFormatting.GREEN));
			return spent;
		}
		return namedConcrete(Items.CONCRETE.green(), "tradewindow.gui.lock.name",
				"tradewindow.gui.lock.lore", ChatFormatting.GREEN);
	}

	/** Builds the CANCEL control: red concrete, which either player may press. */
	public static ItemStack cancelButton() {
		return namedConcrete(Items.CONCRETE.red(), "tradewindow.gui.cancel.name",
				"tradewindow.gui.cancel.lore", ChatFormatting.RED);
	}

	private static ItemStack namedConcrete(net.minecraft.world.item.Item item, String nameKey,
			String loreKey, ChatFormatting colour) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME,
				Component.literal(TradeText.get(nameKey)).withStyle(colour));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
				Component.literal(TradeText.get(loreKey)).withStyle(ChatFormatting.GRAY))));
		return stack;
	}
}
