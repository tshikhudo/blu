package com.sigosInternal.vodapay.actions.bundleJourney;


import com.mc.api.action.Action;
import com.mc.api.device.Device;
import com.mc.api.device.ObjectLevelApi;
import com.mc.api.device.ObjectTree;
import com.mc.api.script.IScriptContext;
import com.mc.api.script.ScriptReturn;

import java.awt.Point;


/**
 * Reads the row-by-row "Detailed balances" screen (My Vodacom -> tap any summary
 * balance card -> "Detailed balances") -- CONFIRMED live 2026-09-06 on a real
 * Vodacom-linked postpaid account. This is the actual per-bundle breakdown behind
 * the aggregate "Anytime data"/"Anytime voice"/"Anytime sms" summary figures --
 * e.g. this account's "22.53GB" Anytime data summary is really the sum of several
 * separate bundles (SP Incentive Recurring 20GB, two "RED Data 7.5GB" grants --
 * one of which had genuinely reached "0KB left" -- and Night Owl RED 7.5GB), each
 * with its own name, expiry date, and remaining amount. This is the real
 * mechanism for verifying a SPECIFIC purchased bundle depleted, rather than just
 * watching the aggregate number move.
 *
 * The screen has three tabs (Data/Voice/SMS), each with the same row shape: a
 * name, optionally "Recurring" + "Expires DD/MM/YYYY" (only on non-aggregate
 * rows), then a value and the literal word "left" -- all as separate WebView text
 * nodes, not one combined string like the summary cards use.
 */
public class CheckDetailedBalanceRow extends Action
{
	/*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

	public static final CheckDetailedBalanceRow instance = new CheckDetailedBalanceRow();

	private CheckDetailedBalanceRow()
	{
		super();
	}


	/*** END GENERATED CODE ***/

	private static final String VODAPAY_PACKAGE = "za.co.vodacom.vodapay";
	private static final String MY_VODACOM_TAB_LABEL = "My Vodacom";
	private static final String DATA_SUMMARY_CARD_LABEL = "Anytime data";
	private static final String VOICE_SUMMARY_CARD_LABEL = "Anytime voice";
	private static final String SMS_SUMMARY_CARD_LABEL = "Anytime sms";
	private static final String DETAILED_BALANCES_MENU_LABEL = "Detailed balances";
	private static final String LEFT_MARKER = "left";
	private static final String RECURRING_MARKER = "Recurring";
	private static final String EXPIRES_PREFIX = "Expires";
	private static final long SCREEN_TRANSITION_TIMEOUT_MS = 15000;

	@Override
	protected ScriptReturn run(Device device, IScriptContext context) throws Exception
	{
		// Action parameters:
		//   balanceCategory -- "Data", "Voice", or "SMS" -- which tab to read
		//   rowName         -- optional exact row name to look up (e.g. a bundle
		//                      name just purchased); if omitted, every row found is
		//                      just logged, useful for a "what's actually here"
		//                      exploration run
		final String category = context.get("balanceCategory");
		if (category == null || category.trim().isEmpty())
			return fail("balanceCategory parameter (Data/Voice/SMS) was not set");
		final String rowName = context.get("rowName");

		String summaryCardLabel = category.equalsIgnoreCase("Data") ? DATA_SUMMARY_CARD_LABEL
				: category.equalsIgnoreCase("Voice") ? VOICE_SUMMARY_CARD_LABEL
				: category.equalsIgnoreCase("SMS") ? SMS_SUMMARY_CARD_LABEL : null;
		if (summaryCardLabel == null)
			return fail("Unknown balanceCategory: " + category + " (expected Data, Voice, or SMS)");

		ObjectLevelApi api = device.getObjectLevelApi();
		api.experimental.startApplication(VODAPAY_PACKAGE);
		waitForText(device, context, SCREEN_TRANSITION_TIMEOUT_MS, MY_VODACOM_TAB_LABEL);
		device.execute(Action.get("com.sigosInternal.vodapay.actions.bundleJourney.DismissKnownPopups"));

		if (!tapFirst(device, api, MY_VODACOM_TAB_LABEL))
			return fail("Could not find nav tab: " + MY_VODACOM_TAB_LABEL);
		if (!waitForText(device, context, SCREEN_TRANSITION_TIMEOUT_MS, summaryCardLabel))
			return fail("Balance card " + summaryCardLabel + " never appeared on My Vodacom");

		if (!tapFirst(device, api, summaryCardLabel))
			return fail("Could not find balance card: " + summaryCardLabel);
		if (!waitForText(device, context, SCREEN_TRANSITION_TIMEOUT_MS, DETAILED_BALANCES_MENU_LABEL))
			return fail("Menu item " + DETAILED_BALANCES_MENU_LABEL + " never appeared");

		if (!tapFirst(device, api, DETAILED_BALANCES_MENU_LABEL))
			return fail("Could not find menu item: " + DETAILED_BALANCES_MENU_LABEL);
		// Don't hard-fail on timeout here -- a category can legitimately have zero
		// rows (e.g. no SMS balance at all on a data-only account), in which case
		// no "left" text will ever appear and that's a valid outcome, not an error.
		if (!waitForText(device, context, SCREEN_TRANSITION_TIMEOUT_MS, LEFT_MARKER))
			System.out.println("No \"" + LEFT_MARKER + "\" rows appeared for " + category + " within the wait -- proceeding, this may be a legitimately empty category.");

		// The Detailed balances screen opens on whichever tab matches the card that
		// was tapped, so no separate tab-switch is needed here.

		ObjectTree[] textNodes = api.findObjectsByClassName("android.widget.TextView");
		boolean foundRequestedRow = false;
		// Every row found gets collected here as "name=value" pairs (semicolon
		// separated) and stashed in context, regardless of whether a specific
		// rowName was requested. The intended pattern for identifying a newly
		// purchased bundle without knowing its exact row name in advance: call this
		// action for a category BEFORE a purchase and save context.get
		// ("detailedBalanceRows_<category>") aside, call it again AFTER, then diff
		// the two strings -- whatever row is in "after" but not "before" is the
		// bundle that was just bought.
		StringBuilder allRows = new StringBuilder();
		for (int i = 0; i < textNodes.length; i++)
		{
			String text = textNodes[i].getText();
			if (text == null || !text.equals(LEFT_MARKER) || i == 0)
				continue;

			String value = textNodes[i - 1].getText();
			int nameIndex = i - 2;
			while (nameIndex >= 0)
			{
				String candidate = textNodes[nameIndex].getText();
				if (candidate != null && (candidate.equals(RECURRING_MARKER) || candidate.startsWith(EXPIRES_PREFIX)))
				{
					nameIndex--;
					continue;
				}
				break;
			}
			if (nameIndex < 0)
				continue;
			String name = textNodes[nameIndex].getText();

			System.out.println(category + " row: " + name + " -> " + value);
			if (allRows.length() > 0)
				allRows.append(";");
			allRows.append(name).append("=").append(value);

			if (rowName != null && name != null && name.trim().equalsIgnoreCase(rowName.trim()))
			{
				context.put("detailedBalanceValue", value);
				foundRequestedRow = true;
			}
		}

		context.put("detailedBalanceRows_" + category, allRows.toString());
		// Also written under this fixed, category-independent key -- some
		// callers (e.g. SnapshotBalancesBefore) want "whatever category this
		// call just checked" without needing to know in advance which one that
		// was, since a Visual TestCase runs this action three times in a row
		// (once per category, via SetBalanceCategoryData/Voice/Sms) and reads
		// each result back through its own specific "detailedBalanceRows_"
		// + category key rather than this shared one.
		context.put("detailedBalanceRowsSnapshot", allRows.toString());

		if (rowName != null && !foundRequestedRow)
			return fail("No row named \"" + rowName + "\" found under " + category + " detailed balances");

		return SUCCESS();
	}

	private boolean tapFirst(Device device, ObjectLevelApi api, String text) throws Exception
	{
		ObjectTree[] matches = api.findObjectsByText(text);
		if (matches.length == 0)
			return false;
		Point center = new Point(matches[0].getX() + matches[0].getWidth() / 2, matches[0].getY() + matches[0].getHeight() / 2);
		device.sendTouchClick(center);
		return true;
	}

	/** Thin wrapper around the WaitForText Action -- see its class doc for why this can't just be a direct method call. */
	private boolean waitForText(Device device, IScriptContext context, long timeoutMs, String text) throws Exception
	{
		context.put("screenSyncTexts", WaitForText.joinCandidates(text));
		context.put("screenSyncTimeoutMs", String.valueOf(timeoutMs));
		device.execute(Action.get("com.sigosInternal.vodapay.actions.bundleJourney.WaitForText"));
		return Boolean.parseBoolean(context.get("screenSyncFound"));
	}

	private ScriptReturn fail(String message)
	{
		return new ScriptReturn(getCurrentContext(), ScriptReturn.ScriptReturnCode.FAIL, message);
	}

}
