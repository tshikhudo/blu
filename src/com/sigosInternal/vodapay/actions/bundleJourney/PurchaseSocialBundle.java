package com.sigosInternal.vodapay.actions.bundleJourney;


import com.mc.api.action.Action;
import com.mc.api.device.Device;
import com.mc.api.device.ObjectLevelApi;
import com.mc.api.device.ObjectTree;
import com.mc.api.script.IScriptContext;
import com.mc.api.script.ScriptReturn;
import com.sigosInternal.vodapay.BundleComposition;

import java.awt.Point;


/**
 * Buys a bundle through VodaPay's "Buy" quick action -- CONFIRMED end-to-end
 * live on a real device on 2026-09-07, including a completed real purchase
 * (60MB/1-hour data bundle, R5.50, paid via Airtime) with its provisioning
 * independently verified via a before/after Detailed-balances row diff.
 *
 * This replaces an earlier version of this action built around the "Airtime &
 * bundles" Home tile instead. That flow was only ever pushed as far as
 * VodaPay's own "insufficient funds" validation live (the test wallet had
 * R0.00 at the time) -- its actual payment-completion step was never
 * confirmed. This flow, by contrast, has been driven start to finish with a
 * real successful purchase, so it's the one a "final" buy-journey test should
 * use. See [[project-vodapay-bundle-testing]] in memory for the full write-up.
 *
 * Real flow (Home screen already showing, i.e. LaunchVodaPay/login already ran):
 *   Home -> "Buy" quick action (sits under the "My Vodacom balances" card,
 *     alongside "My account"/"View all", NOT the same as the "Airtime &
 *     bundles" tile) -> [one-time per account] "For added security, you may
 *     be asked for your CVV/Security Code" info screen, dismissed via its own
 *     "Got it" button (now in PopupDismisser's catalogue) ->
 *   Buy screen opens pre-filled "Buying for Myself: {international number}",
 *     with tabs (Data / Voice / a third, unconfirmed tab) and a top "Your best
 *     deals" carousel -> below that, "Choose a bundle" groups the whole
 *     catalog by validity period as COLLAPSED rows: "1 hour" / "Until
 *     midnight" / "7 days" / "30 days" / "30 days Recurring" -- the bundle's
 *     own validity isn't parsed from its name, so this expands each category
 *     in turn (tapping its chevron) until bundleName's row is found, rather
 *     than guessing which one it belongs to ->
 *   Tapping the bundle row goes straight to "Payment summary" (recipient,
 *     bundle description, Total) with "Choose your payment method": Airtime
 *     (shows its real Rand balance inline), Bank Card, Wallet -- selecting one
 *     enables "Buy now" ->
 *   Completing it shows "Thank you, {registered first name}" with an "Order
 *     purchase no" (format "YYYY-MM-DD, E<digits>") and a Close button.
 *
 * Two things intentionally NOT implemented here, to avoid guessing untested
 * UI rather than because they don't matter:
 *  - "Buy for another" (a different recipient than the logged-in account) --
 *    this action only ever exercised "Buying for Myself" live. If
 *    recipientNumber differs from cellNumber, this FAILS with a clear message
 *    instead of attempting an unconfirmed tap sequence.
 *  - The third product tab next to Data/Voice (its exact text was truncated
 *    on screen as "Your tow..." and never confirmed) -- SMS bundles can't be
 *    bought through this action yet. composition.hasSms() without hasData()/
 *    hasVoiceMinutes() also FAILS clearly rather than guessing a tab name.
 */
public class PurchaseSocialBundle extends Action
{
	/*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

	public static final PurchaseSocialBundle instance = new PurchaseSocialBundle();

	private PurchaseSocialBundle()
	{
		super();
	}


	/*** END GENERATED CODE ***/

	private static final String VODAPAY_PACKAGE = "za.co.vodacom.vodapay";
	private static final String BUY_QUICK_ACTION_LABEL = "Buy";
	private static final String BUY_FOR_ANOTHER_LABEL = "Buy for another";
	private static final String CHOOSE_A_BUNDLE_LABEL = "Choose a bundle";
	private static final String[] VALIDITY_CATEGORIES = {"1 hour", "Until midnight", "7 days", "30 days", "30 days Recurring"};
	private static final String PAYMENT_SUMMARY_TOTAL_LABEL = "Total";
	private static final String BUY_NOW_BUTTON_LABEL = "Buy now";
	private static final String THANK_YOU_REGEX = "^Thank you,.*";
	private static final String ORDER_PURCHASE_NO_REGEX = "^Order purchase no.*";
	// Recorded live on the OTHER (tile-based) flow -- kept here since VodaPay's
	// payment component is plausibly shared between both entry points, but this
	// exact string has NOT been confirmed to appear through the Buy quick
	// action specifically.
	private static final String ERROR_INSUFFICIENT_FUNDS = "Insufficient money, choose another payment method.";

	private static final long SCREEN_TRANSITION_TIMEOUT_MS = 15000;
	private static final long CATEGORY_EXPAND_TIMEOUT_MS = 5000;
	private static final long PAYMENT_RESULT_TIMEOUT_MS = 25000;

	@Override
	protected ScriptReturn run(Device device, IScriptContext context) throws Exception
	{
		// Action parameters:
		//   bundleName      -- exact "Choose a bundle" row text, e.g. "60 MB for 1 HOUR"
		//   cellNumber      -- the logged-in account's own number
		//   recipientNumber -- optional; if given and different from cellNumber, this
		//                      action fails rather than attempt the unconfirmed "Buy
		//                      for another" flow (see class doc)
		//   paymentMethod   -- optional, one of "Airtime"/"Bank Card"/"Wallet";
		//                      defaults to "Airtime" (the only one confirmed working
		//                      live, paid from a real Rand balance)
		final String bundleName = context.get("bundleName");
		if (bundleName == null || bundleName.trim().isEmpty())
			return fail("bundleName parameter was not set");
		final String cellNumber = context.get("cellNumber");
		final String recipientNumber = context.get("recipientNumber");
		if (recipientNumber != null && !recipientNumber.trim().isEmpty()
				&& cellNumber != null && !normalizeNumber(recipientNumber).equals(normalizeNumber(cellNumber)))
			return fail("recipientNumber (" + recipientNumber + ") differs from cellNumber (" + cellNumber
					+ ") -- \"Buy for another\" isn't implemented yet (never confirmed live), only buying for self");
		String paymentMethod = context.get("paymentMethod");
		if (paymentMethod == null || paymentMethod.trim().isEmpty())
			paymentMethod = "Airtime";
		final BundleComposition composition = BundleComposition.parse(bundleName);

		ObjectLevelApi api = device.getObjectLevelApi();
		api.experimental.startApplication(VODAPAY_PACKAGE);
		ScreenSync.waitFor(device, getCurrentContext(), SCREEN_TRANSITION_TIMEOUT_MS, BUY_QUICK_ACTION_LABEL);
		PopupDismisser.dismissKnownPopups(device, getCurrentContext());

		if (!tapByText(device, api, BUY_QUICK_ACTION_LABEL))
			return fail("Could not find the \"Buy\" quick action on Home");
		// The one-time CVV/security info screen (if this account/session hasn't
		// seen it before) sits in front of the actual Buy screen -- wait for
		// either it or the Buy screen itself, then let PopupDismisser clear the
		// info screen's "Got it" button if it showed up.
		ScreenSync.waitForAny(device, getCurrentContext(), SCREEN_TRANSITION_TIMEOUT_MS, BUY_FOR_ANOTHER_LABEL, "Got it");
		PopupDismisser.dismissKnownPopups(device, getCurrentContext());
		if (!ScreenSync.waitFor(device, getCurrentContext(), SCREEN_TRANSITION_TIMEOUT_MS, BUY_FOR_ANOTHER_LABEL))
			return fail("Buy screen (" + BUY_FOR_ANOTHER_LABEL + " not found) never loaded");

		// "Data" / "Voice" tab -- see class doc for why SMS isn't supported here yet.
		String productTab = composition.hasData() ? "Data" : composition.hasVoiceMinutes() ? "Voice" : null;
		if (productTab == null)
			return fail("Could not determine a supported product tab (Data/Voice) from bundleName: " + bundleName
					+ " -- SMS bundles aren't supported by this action yet, see class doc");
		if (!tapByText(device, api, productTab))
			return fail("Could not find product tab: " + productTab);
		if (!ScreenSync.waitFor(device, getCurrentContext(), SCREEN_TRANSITION_TIMEOUT_MS, CHOOSE_A_BUNDLE_LABEL))
			return fail("\"" + CHOOSE_A_BUNDLE_LABEL + "\" section never appeared after selecting the " + productTab + " tab");

		if (!expandCategoryAndFindBundle(device, api, bundleName))
			return fail("Could not find bundle \"" + bundleName + "\" under any validity category ("
					+ String.join(", ", VALIDITY_CATEGORIES) + ")");

		if (composition.getPrice() != null)
		{
			ObjectTree[] priceOnScreen = api.findObjectsByText("R" + composition.getPrice());
			if (priceOnScreen.length == 0)
				priceOnScreen = api.findObjectsByText("R " + composition.getPrice());
			if (priceOnScreen.length == 0)
				return fail("Expected price R" + composition.getPrice() + " not found next to bundle " + bundleName + " -- wrong bundle or price changed");
		}

		if (!tapByText(device, api, bundleName))
			return fail("Could not tap bundle: " + bundleName);
		if (!ScreenSync.waitFor(device, getCurrentContext(), SCREEN_TRANSITION_TIMEOUT_MS, PAYMENT_SUMMARY_TOTAL_LABEL))
			return fail("Payment summary screen (" + PAYMENT_SUMMARY_TOTAL_LABEL + ") never appeared after tapping the bundle");

		if (!tapByText(device, api, paymentMethod))
			return fail("Could not find payment method: " + paymentMethod);
		if (!ScreenSync.waitFor(device, getCurrentContext(), SCREEN_TRANSITION_TIMEOUT_MS, BUY_NOW_BUTTON_LABEL))
			return fail("\"" + BUY_NOW_BUTTON_LABEL + "\" button never appeared after selecting payment method " + paymentMethod);

		if (!tapByText(device, api, BUY_NOW_BUTTON_LABEL))
			return fail("Could not tap: " + BUY_NOW_BUTTON_LABEL);

		final long deadline = System.currentTimeMillis() + PAYMENT_RESULT_TIMEOUT_MS;
		while (api.findObjectsByRegex(THANK_YOU_REGEX).length == 0
				&& api.findObjectsByText(ERROR_INSUFFICIENT_FUNDS).length == 0
				&& System.currentTimeMillis() < deadline)
			device.serverWait(getCurrentContext(), 300);

		if (api.findObjectsByText(ERROR_INSUFFICIENT_FUNDS).length > 0)
			return fail(ERROR_INSUFFICIENT_FUNDS + " (payment method " + paymentMethod + " needs funding before this bundle can actually be bought)");

		ObjectTree[] thankYou = api.findObjectsByRegex(THANK_YOU_REGEX);
		if (thankYou.length == 0)
			return fail("Neither a \"Thank you\" confirmation nor a known error appeared within " + PAYMENT_RESULT_TIMEOUT_MS + "ms of tapping " + BUY_NOW_BUTTON_LABEL);

		ObjectTree[] orderLine = api.findObjectsByRegex(ORDER_PURCHASE_NO_REGEX);
		if (orderLine.length > 0)
		{
			String orderText = orderLine[0].getText();
			context.put("purchaseOrderNumber", orderText);
			System.out.println("Purchase confirmed: " + thankYou[0].getText() + " -- " + orderText);
		}
		else
		{
			System.out.println("Purchase confirmed: " + thankYou[0].getText());
		}

		return SUCCESS();
	}

	private ScriptReturn fail(String message)
	{
		return new ScriptReturn(getCurrentContext(), ScriptReturn.ScriptReturnCode.FAIL, message);
	}

	private String normalizeNumber(String number)
	{
		String digits = number.replaceAll("\\D", "");
		if (digits.startsWith("27"))
			digits = "0" + digits.substring(2);
		return digits;
	}

	/** Coordinate-taps the first object matching the given text (WebView content -- text nodes are frequently clickable="false" even when an ancestor is). */
	private boolean tapByText(Device device, ObjectLevelApi api, String text) throws Exception
	{
		ObjectTree[] matches = api.findObjectsByText(text);
		if (matches.length == 0)
			return false;
		Point center = new Point(matches[0].getX() + matches[0].getWidth() / 2, matches[0].getY() + matches[0].getHeight() / 2);
		device.sendTouchClick(center);
		return true;
	}

	/**
	 * The bundle's validity period isn't parsed from bundleName, so this
	 * expands each of the 5 known "Choose a bundle" categories in turn (they
	 * start collapsed) until bundleName's own row becomes visible, rather than
	 * guessing which one it belongs to. Categories already expanded (from an
	 * earlier attempt) are harmless to tap again -- confirmed live that
	 * re-tapping an expanded category's chevron just collapses it, so this
	 * only taps a category when the bundle isn't already visible.
	 */
	private boolean expandCategoryAndFindBundle(Device device, ObjectLevelApi api, String bundleName) throws Exception
	{
		if (api.findObjectsByText(bundleName).length > 0)
			return true;

		for (String category : VALIDITY_CATEGORIES)
		{
			ObjectTree[] categoryHeader = api.findObjectsByText(category);
			if (categoryHeader.length == 0)
				continue;

			Point center = new Point(categoryHeader[0].getX() + categoryHeader[0].getWidth() / 2, categoryHeader[0].getY() + categoryHeader[0].getHeight() / 2);
			device.sendTouchClick(center);
			ScreenSync.waitFor(device, getCurrentContext(), CATEGORY_EXPAND_TIMEOUT_MS, bundleName);

			if (api.findObjectsByText(bundleName).length > 0)
				return true;
		}
		return false;
	}

}
