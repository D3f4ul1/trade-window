package com.tradewindow;

import com.tradewindow.command.TradeAdminCommand;
import com.tradewindow.command.TradeCommand;
import com.tradewindow.config.TradeConfig;
import com.tradewindow.trade.PendingReturns;
import com.tradewindow.trade.TradeHistory;
import com.tradewindow.trade.TradeManager;
import com.tradewindow.trade.TradeToken;
import com.tradewindow.util.TokenRecipeCheck;
import com.tradewindow.util.TradeText;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side entrypoint for Minecraft 1.21.11.
 *
 * <p><b>Nothing is registered with the game.</b> Earlier revisions declared a custom
 * {@code trade_token} item; on 1.21.11 that both crashed at startup ("Item id not
 * set", because the settings never carried a registry key) and would have kicked every
 * vanilla client with "Received a registry entry that is unknown to this client". The
 * token is now an ordinary Ghast Tear with {@code custom_name} and
 * {@code max_stack_size} components, produced by a data-pack recipe, so the server's
 * registries are bit-for-bit identical to vanilla's and any client can connect.
 *
 * <p>The window is the vanilla double-chest menu ({@code GENERIC_9x6}). The only
 * channels between server and client are therefore vanilla: menu syncing for the grid
 * and its items, and the click on a slot the client already understands. The green
 * LOCK and red CANCEL blocks are ordinary items that the server turns into actions,
 * so there is no client entrypoint, no packet, and no client code of any kind.
 */
public class TradeWindow implements ModInitializer {

	/** The mod id, used for the logger, the config file and the recipe namespace. */
	public static final String MOD_ID = "tradewindow";

	/** Shared logger; per-trade detail is deliberately not logged. */
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Config first: everything below reads it.
		TradeConfig.load();

		// Every player-visible sentence comes from the mod's own language file, read
		// once here so nothing can send a raw key later.
		TradeText.load();

		// Items stranded by a restart are returned on the owner's next login, and the
		// history ring is warmed from whatever the previous run recorded.
		PendingReturns.init(FabricLoader.getInstance().getGameDir());
		TradeHistory.init(FabricLoader.getInstance().getGameDir());

		registerEvents();

		// The token is defined by data in this jar, and one of the two ways of
		// spelling its text components is silently wrong on every Minecraft version.
		// Checked here, at every start, so a mis-built jar says so instead of
		// quietly handing players an unbranded tear or a name made of raw JSON.
		TokenRecipeCheck.run();

		LOGGER.info("Trade Window loaded for Minecraft {}", minecraftVersion());
	}

	/**
	 * Returns the running Minecraft version, read from the loader.
	 *
	 * <p>Read rather than hard-coded: one source tree serves several Minecraft
	 * versions, so a literal here would name the wrong game on every target but one.
	 *
	 * @return the Minecraft version, for example {@code 1.21.11}
	 */
	public static String minecraftVersion() {
		return FabricLoader.getInstance().getModContainer("minecraft")
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");
	}

	private void registerEvents() {
		// Drives request expiry, the window timeout, the distance rule and death of a
		// partner that never logs out cleanly.
		ServerTickEvents.END_SERVER_TICK.register(TradeManager::tick);

		// A departing player ends their trade; anything they cannot carry is held for
		// their next login rather than dropped into the world.
		ServerPlayConnectionEvents.DISCONNECT.register(
				(handler, server) -> TradeManager.onDisconnect(handler.getPlayer()));

		// Hand back anything stranded by a previous restart.
		ServerPlayConnectionEvents.JOIN.register(
				(handler, sender, server) -> TradeManager.onPlayerJoin(handler.getPlayer()));

		// Right-clicking another player while holding a Trade Token sends a request.
		// This is the SERVER-side hook: with no client mod there is nothing to observe
		// the interaction on the client, so the server watches its own interaction event
		// instead. The callback always returns PASS, so it never interferes with any
		// other right-click behaviour.
		//
		// The token's slot is passed in and remembered by the request, so that the tear
		// spent when the request is accepted is the one that was used to make it. Without
		// a token - and with the token requirement switched on - the click stays
		// completely silent, because "I right-clicked a player" is not an error.
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (!world.isClientSide()
					&& hand == InteractionHand.MAIN_HAND
					&& player instanceof ServerPlayer sender
					&& entity instanceof ServerPlayer target) {
				int tokenSlot = TradeToken.findSlot(sender, sender.getInventory().getSelectedSlot());
				if (tokenSlot >= 0 || !TradeConfig.get().requireToken) {
					TradeManager.request(sender, target, tokenSlot);
				}
			}
			return InteractionResult.PASS;
		});

		// Taking a hit ends the trade while cancelOnDamage is on: combat is exactly
		// when a player is least able to watch both halves of the window.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
			if (entity instanceof ServerPlayer player) {
				TradeManager.onDamage(player);
			}
		});

		// Death always ends a trade, silently in chat terms beyond the single
		// cancellation line: the items are returned, not dropped with the corpse.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				TradeManager.onDeath(player);
			}
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(TradeManager::onServerStopping);

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			TradeCommand.register(dispatcher);
			TradeAdminCommand.register(dispatcher);
		});
	}
}
