package com.tradewindow.util;

import com.tradewindow.TradeWindow;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Startup self-check for the encoding of the Trade Token's text components.
 *
 * <p><b>The failure this exists to prevent.</b> The token's name and lore are written
 * into the recipe as text components, and the two ways of spelling a component in a
 * data-pack recipe are not interchangeable - each Minecraft version accepts exactly
 * one of them, and accepts the other *silently*:
 *
 * <ul>
 *   <li>On <b>1.21.1</b> {@code custom_name} is a string codec that parses its input as
 *       JSON. An object is rejected ({@code Not a string}) and the recipe is dropped,
 *       so no token can be crafted at all.</li>
 *   <li>From <b>1.21.2</b> onward - including 1.21.11 and every 26.x - the component
 *       codec takes the object form, and a string is read as <b>literal text</b>. The
 *       recipe loads happily and the item's name is the raw JSON, braces and all.</li>
 * </ul>
 *
 * <p>Neither failure is visible to a compiler, to a unit test, or to a build: one is a
 * data file read at runtime and the other renders wrong only on a client. It shipped
 * once, which is why it is now checked out loud at every start.
 *
 * <p>Reads its own recipe straight out of the mod jar, so it describes what this jar
 * actually contains rather than what the source intended.
 */
public final class TokenRecipeCheck {

	/** The single version whose component codec parses an escaped JSON string. */
	private static final String STRING_FORM_VERSION = "1.21.1";

	/** The recipe, as it sits in the mod jar. */
	private static final String RECIPE_RESOURCE = "/data/tradewindow/recipe/trade_token.json";

	/** Captures the first character of the {@code custom_name} value. */
	private static final Pattern CUSTOM_NAME =
			Pattern.compile("\"minecraft:custom_name\"\\s*:\\s*(.)");

	private TokenRecipeCheck() {
	}

	/**
	 * Checks the shipped recipe against the running Minecraft version and logs the
	 * verdict: one line when the encoding is right, an error when it is not.
	 */
	public static void run() {
		String version = TradeWindow.minecraftVersion();
		boolean wantsString = STRING_FORM_VERSION.equals(version);
		String expected = wantsString ? "string" : "object";

		String raw = readRecipe();
		if (raw == null) {
			TradeWindow.LOGGER.error(
					"Trade Token recipe check FAILED: {} is missing from this jar, so no"
							+ " Trade Token can be crafted and /trade cannot start a trade.",
					RECIPE_RESOURCE);
			return;
		}

		Matcher matcher = CUSTOM_NAME.matcher(raw);
		if (!matcher.find()) {
			TradeWindow.LOGGER.error(
					"Trade Token recipe check FAILED: {} has no minecraft:custom_name"
							+ " component, so a crafted tear would not be recognised as a token.",
					RECIPE_RESOURCE);
			return;
		}

		char lead = matcher.group(1).charAt(0);
		String found = lead == '"' ? "string" : lead == '{' ? "object" : "unknown";

		if (found.equals(expected)) {
			TradeWindow.LOGGER.info(
					"Trade Token recipe check: {} text components, correct for Minecraft {}",
					found, version);
		} else if (found.equals("unknown")) {
			TradeWindow.LOGGER.error(
					"Trade Token recipe check FAILED: cannot read the minecraft:custom_name"
							+ " value in {}; expected {} text components for Minecraft {}.",
					RECIPE_RESOURCE, expected, version);
		} else {
			// The two failures are different, so say the right one.
			String consequence = wantsString
					? "the recipe will NOT load and no Trade Token can be crafted"
					: "the token's name and lore will render as raw JSON";
			TradeWindow.LOGGER.error(
					"Trade Token recipe check FAILED: this jar spells the token's text"
							+ " components as {} but Minecraft {} needs {} - {}."
							+ " Rebuild with the matching era tree (tools/port_eras.py).",
					found, version, expected, consequence);
		}
	}

	/**
	 * Reads the recipe from this mod's own jar.
	 *
	 * @return the file's text, or {@code null} when it cannot be read
	 */
	private static String readRecipe() {
		try (InputStream stream = TokenRecipeCheck.class.getResourceAsStream(RECIPE_RESOURCE)) {
			if (stream == null) {
				return null;
			}
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			return null;
		}
	}
}
