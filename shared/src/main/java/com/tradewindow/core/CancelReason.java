package com.tradewindow.core;

/**
 * Why a trade request or an open trade session ended without swapping items.
 *
 * <p>Every terminal path in the mod funnels through one of these constants so
 * that cancellation is always explainable to the player and greppable in the
 * log. Adding a constant here is the only supported way to introduce a new
 * abort path — the session state machine rejects reasons it does not know.
 */
public enum CancelReason {
	/** The target player clicked "Decline" in chat. */
	DECLINED("tradewindow.cancel.declined"),
	/** The target never responded within {@code tradeTimeoutSeconds}. */
	REQUEST_TIMEOUT("tradewindow.cancel.request_timeout"),
	/** The GUI sat open without both players confirming for too long. */
	GUI_TIMEOUT("tradewindow.cancel.gui_timeout"),
	/** One of the two players left the server. */
	DISCONNECT("tradewindow.cancel.disconnect"),
	/** One of the two players died. */
	DEATH("tradewindow.cancel.death"),
	/** The players drifted further apart than {@code maxDistanceBlocks}. */
	DISTANCE("tradewindow.cancel.distance"),
	/** One of the two players took damage and {@code cancelOnDamage} is enabled. */
	DAMAGE("tradewindow.cancel.damage"),
	/** The players are in different dimensions and cross-dimension trading is off. */
	DIMENSION("tradewindow.cancel.dimension"),
	/** The server is stopping; sessions do not survive a restart. */
	SERVER_SHUTDOWN("tradewindow.cancel.server_shutdown"),
	/** A player explicitly closed the window or ran {@code /trade cancel}. */
	MANUAL("tradewindow.cancel.manual"),
	/** A player tried to offer an item on the blacklist. */
	BLACKLISTED_ITEM("tradewindow.cancel.blacklisted"),
	/** The offered items exceeded {@code maxTradeValue}. */
	VALUE_CAP("tradewindow.cancel.value_cap"),
	/** {@code allowCreativeTrading} is false and the two players' game modes are incompatible. */
	GAMEMODE("tradewindow.cancel.gamemode"),
	/** The initiating player did not actually hold a Trade Token. */
	NO_TOKEN("tradewindow.cancel.no_token"),
	/** A precondition failed at commit time and the swap was rolled back. */
	COMMIT_FAILED("tradewindow.cancel.commit_failed");

	private final String translationKey;

	CancelReason(String translationKey) {
		this.translationKey = translationKey;
	}

	/**
	 * Returns the {@code en_us.json} key describing this reason to a player.
	 *
	 * @return a translation key such as {@code tradewindow.cancel.distance}
	 */
	public String translationKey() {
		return translationKey;
	}
}
