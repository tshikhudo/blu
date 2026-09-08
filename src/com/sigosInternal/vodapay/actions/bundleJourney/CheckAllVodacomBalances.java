package com.sigosInternal.vodapay.actions.bundleJourney;


import com.mc.api.action.Action;
import com.mc.api.device.Device;
import com.mc.api.device.ObjectLevelApi;
import com.mc.api.script.IScriptContext;
import com.mc.api.script.ScriptReturn;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Extracts every real SIM-level balance from VodaPay's "My Vodacom" screen --
 * CONFIRMED live 2026-09-06 on a real Vodacom-linked account (this needs a real
 * Vodacom SIM linked to the logged-in number; an MTN/other-network number just
 * shows a "Link your Vodacom SIM" prompt instead, per an earlier live check).
 *
 * Each balance shows as one card whose entire on-screen text is "Label Value" in
 * a single node (e.g. "Anytime data 22.53GB", "Anytime sms 297") -- confirmed via
 * a real uiautomator dump. Real card types seen live: VodaBucks, Anytime data,
 * Night Owl RED (a time-of-day data bundle -- text has two numbers, bundle size
 * then remaining; this takes the last one), Anytime voice, Anytime sms. "View all
 * balances" needs tapping first or some of these stay collapsed/hidden.
 */
public class CheckAllVodacomBalances extends Action
{
	/*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

	public static final CheckAllVodacomBalances instance = new CheckAllVodacomBalances();

	private CheckAllVodacomBalances()
	{
		super();
	}


	/*** END GENERATED CODE ***/

	private static final String VODAPAY_PACKAGE = "za.co.vodacom.vodapay";
	private static final String MY_VODACOM_TAB_LABEL = "My Vodacom";
	private static final String LINK_SIM_MARKER = "Link your Vodacom SIM";
	private static final String VIEW_ALL_BALANCES_LABEL = "View all balances";

	// Known card label prefixes, and the value pattern to pull the LAST occurrence
	// of out of that card's text (so "Night Owl RED 7.5GB 7.49GB" correctly yields
	// 7.49GB, the remaining balance, not the bundle's original size, since a card
	// can contain more than one number). "Anytime airtime" (a Rand-value card,
	// confirmed live 2026-09-07 on a prepaid-style account, e.g. "Anytime
	// airtime R 445.00") was a known gap noted earlier this project -- added here.
	private static final String[] KNOWN_LABELS = {"VodaBucks", "Anytime data", "Night Owl", "Anytime voice", "Anytime sms", "Anytime airtime"};
	private static final Pattern DATA_VALUE = Pattern.compile("[\\d.]+\\s?[GM]B");
	private static final Pattern VOICE_VALUE = Pattern.compile("\\d+m\\d+s");
	private static final Pattern RAND_VALUE = Pattern.compile("R\\s?\\d+(?:[.,]\\d{2})?");
	private static final Pattern PLAIN_NUMBER = Pattern.compile("\\d+");

	// Postpaid/hybrid accounts also show a "Billing" section with "Bill so far"
	// and "Amount you owe" (both confirmed live, e.g. "R 223.00"/"R 126627.38")
	// -- NOT extracted below yet. Unlike every card above, these two values sit
	// side by side rather than one-value-per-card, and it's unconfirmed whether
	// they're two separate card nodes or one combined node containing both
	// labels and both values -- if the latter, "last match wins" (used for
	// every other label here) would silently return "Amount you owe"'s value
	// for "Bill so far" too. Add real Billing extraction only once that's
	// confirmed with SpyCurrentScreen/uiautomator, rather than guess.
	private static final long SCREEN_TRANSITION_TIMEOUT_MS = 15000;

	@Override
	protected ScriptReturn run(Device device, IScriptContext context) throws Exception
	{
		ObjectLevelApi api = device.getObjectLevelApi();
		api.experimental.startApplication(VODAPAY_PACKAGE);
		ScreenSync.waitFor(device, getCurrentContext(), SCREEN_TRANSITION_TIMEOUT_MS, MY_VODACOM_TAB_LABEL);
		PopupDismisser.dismissKnownPopups(device, getCurrentContext());

		if (api.findObjectsByText(MY_VODACOM_TAB_LABEL).length == 0)
			return fail("Could not find nav tab: " + MY_VODACOM_TAB_LABEL);
		tapCenter(device, api.findObjectsByText(MY_VODACOM_TAB_LABEL)[0]);
		waitForKnownBalanceCard(device, api, SCREEN_TRANSITION_TIMEOUT_MS);

		if (api.findObjectsByText(LINK_SIM_MARKER).length > 0)
			return fail("This account has no linked Vodacom SIM -- My Vodacom balances aren't available (" + LINK_SIM_MARKER + " shown instead).");

		if (api.findObjectsByText(VIEW_ALL_BALANCES_LABEL).length > 0)
		{
			tapCenter(device, api.findObjectsByText(VIEW_ALL_BALANCES_LABEL)[0]);
			waitForKnownBalanceCard(device, api, SCREEN_TRANSITION_TIMEOUT_MS);
		}

		// Prepaid/postpaid/hybrid accounts show different sets of these cards (a
		// postpaid account, like the one this was confirmed against, shows a
		// billing section too -- not extracted here yet). Each card's own text is
		// the whole "Label Value" string -- iterate cards directly rather than the
		// flattened whole-screen text, so each value stays paired with its label.
		int found = 0;
		for (com.mc.api.device.ObjectTree card : api.findObjectsByClassName("android.view.View"))
		{
			String cardText = card.getText();
			if (cardText == null || cardText.isEmpty())
				continue;

			for (String label : KNOWN_LABELS)
			{
				if (!cardText.startsWith(label))
					continue;

				String value = lastMatch(VOICE_VALUE, cardText);
				if (value == null)
					value = lastMatch(DATA_VALUE, cardText);
				if (value == null)
					value = lastMatch(RAND_VALUE, cardText);
				if (value == null)
					value = lastMatch(PLAIN_NUMBER, cardText);
				if (value == null)
					continue;

				String key = "balance_" + label.toLowerCase().replaceAll("[^a-z0-9]+", "_");
				context.put(key, value);
				System.out.println(label + ": " + value);
				found++;
			}
		}

		if (found == 0)
			return fail("My Vodacom loaded but no known balance cards were found -- labels may have changed (this account is postpaid; a prepaid/hybrid account may show a different set), check with SpyCurrentScreen");

		// TODO: tapping a balance card opens a detailed breakdown screen (confirmed
		// by the user, not yet explored live) -- not implemented here; add a
		// per-balance-type drill-in action if a test needs that detail.

		return SUCCESS();
	}

	/**
	 * Polls until either the "no linked SIM" marker or at least one known
	 * balance-card label is actually rendered, or timeoutMs elapses -- these
	 * cards are WebView content whose load time isn't predictable, so this
	 * checks for real readiness instead of sleeping a guessed duration.
	 */
	private boolean waitForKnownBalanceCard(Device device, ObjectLevelApi api, long timeoutMs) throws Exception
	{
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (true)
		{
			if (api.findObjectsByText(LINK_SIM_MARKER).length > 0)
				return true;
			for (com.mc.api.device.ObjectTree card : api.findObjectsByClassName("android.view.View"))
			{
				String cardText = card.getText();
				if (cardText == null)
					continue;
				for (String label : KNOWN_LABELS)
					if (cardText.startsWith(label))
						return true;
			}
			if (System.currentTimeMillis() >= deadline)
				return false;
			device.serverWait(getCurrentContext(), 300);
		}
	}

	/** Returns the last regex match in text, or null if there's none. */
	private String lastMatch(Pattern pattern, String text)
	{
		Matcher matcher = pattern.matcher(text);
		String last = null;
		while (matcher.find())
			last = matcher.group();
		return last;
	}

	private void tapCenter(Device device, com.mc.api.device.ObjectTree node) throws Exception
	{
		device.sendTouchClick(new java.awt.Point(node.getX() + node.getWidth() / 2, node.getY() + node.getHeight() / 2));
	}

	private ScriptReturn fail(String message)
	{
		return new ScriptReturn(getCurrentContext(), ScriptReturn.ScriptReturnCode.FAIL, message);
	}

}
