package com.sigosInternal.vodapay.actions.bundleJourney;


import com.mc.api.action.Action;
import com.mc.api.device.Device;
import com.mc.api.device.ObjectLevelApi;
import com.mc.api.script.IScriptContext;
import com.mc.api.script.ScriptReturn;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Reads "Your wallet balance" off the VodaPay Home screen. Standalone-usable (some
 * test scenarios just check the balance and never buy anything), and also meant to
 * be called both before and after PurchaseSocialBundle to confirm the balance
 * actually moved by the right amount, not just that the purchase screen said
 * success.
 *
 * CONFIRMED 2026-09-06 on a real device: the Home screen is a hybrid -- native
 * bottom nav bar, but the wallet balance card itself is WebView content (no
 * resource-id), so this reads it the same text-search way as the shop screens.
 */
public class CheckWalletBalance extends Action
{
	/*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

	public static final CheckWalletBalance instance = new CheckWalletBalance();

	private CheckWalletBalance()
	{
		super();
	}


	/*** END GENERATED CODE ***/

	private static final String VODAPAY_PACKAGE = "za.co.vodacom.vodapay";
	private static final String WALLET_BALANCE_LABEL = "Your wallet balance";
	private static final Pattern RAND_AMOUNT = Pattern.compile("R\\s?\\d+(?:[.,]\\d{2})?");
	private static final long SCREEN_TRANSITION_TIMEOUT_MS = 15000;

	@Override
	protected ScriptReturn run(Device device, IScriptContext context) throws Exception
	{
		ObjectLevelApi api = device.getObjectLevelApi();
		api.experimental.startApplication(VODAPAY_PACKAGE);
		context.put("screenSyncTexts", WaitForText.joinCandidates(WALLET_BALANCE_LABEL));
		context.put("screenSyncTimeoutMs", String.valueOf(SCREEN_TRANSITION_TIMEOUT_MS));
		device.execute(Action.get("com.sigosInternal.vodapay.actions.bundleJourney.WaitForText"));

		if (api.findObjectsByText(WALLET_BALANCE_LABEL).length == 0)
			return new ScriptReturn(getCurrentContext(), ScriptReturn.ScriptReturnCode.FAIL, "Not on the Home screen -- could not find: " + WALLET_BALANCE_LABEL);

		String screenText = api.getCurrentScreen().getAllTextInSingleString();
		Matcher matcher = RAND_AMOUNT.matcher(screenText);
		if (!matcher.find())
			return new ScriptReturn(getCurrentContext(), ScriptReturn.ScriptReturnCode.FAIL, "Found the wallet balance label but no Rand amount near it");

		String balance = matcher.group();
		System.out.println("Wallet balance: " + balance);
		// Callers can read this back via context.get("walletBalance") for a
		// before/after comparison around a purchase.
		context.put("walletBalance", balance);

		return SUCCESS();
	}

}
