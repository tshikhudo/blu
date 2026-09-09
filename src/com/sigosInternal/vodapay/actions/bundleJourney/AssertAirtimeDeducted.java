package com.sigosInternal.vodapay.actions.bundleJourney;


import com.mc.api.action.Action;
import com.mc.api.device.Device;
import com.mc.api.script.IScriptContext;
import com.mc.api.script.ScriptReturn;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * SOFT assertion: if the bundle is (at least partly) an airtime product
 * (bundleHasAirtime), confirms the real SIM airtime balance ("Anytime
 * airtime", read via CheckAllVodacomBalances) decreased by at least the
 * bundle's own price after the purchase. This is the Rand-value equivalent of
 * AssertDataProvisioned/Voice/Sms's row-diff check -- airtime isn't one of
 * the three Detailed-balances tabs (Data/Voice/SMS), it's a plain balance
 * figure, so "provisioned" here means "the payment was actually deducted",
 * not "a new row appeared".
 *
 * See AssertDataProvisioned's class doc for the full soft-assertion design
 * (never fails itself; records into softAssertionLog/softAssertionFailureCount;
 * ReportSoftAssertions is what actually fails the TestCase, at the very end).
 *
 * Assumes the purchase was paid via Airtime (PurchaseSocialBundle's default,
 * and the only payment method confirmed live) -- if a run actually pays via
 * Wallet or Bank Card instead, this balance won't move and this assertion
 * would incorrectly report a failure; not handled here since only Airtime
 * payment has ever been confirmed working end-to-end.
 */
public class AssertAirtimeDeducted extends Action
{
	/*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

	public static final AssertAirtimeDeducted instance = new AssertAirtimeDeducted();

	private AssertAirtimeDeducted()
	{
		super();
	}


	/*** END GENERATED CODE ***/

	private static final Pattern RAND_AMOUNT = Pattern.compile("(\\d+(?:[.,]\\d{1,2})?)");
	// Small tolerance for rounding/formatting differences between the price
	// parsed from the bundle name and the actual balance figures on screen.
	private static final double TOLERANCE = 0.05;

	@Override
	protected ScriptReturn run(Device device, IScriptContext context) throws Exception
	{
		if (!Boolean.parseBoolean(context.get("bundleHasAirtime")))
			return SUCCESS(); // bundle didn't include a plain airtime component -- nothing to check

		final String beforeStr = context.get("airtimeBalanceBefore");
		final String afterStr = context.get("balance_anytime_airtime");
		final String priceStr = context.get("bundlePrice");

		Double before = parseRand(beforeStr);
		Double after = parseRand(afterStr);
		Double price = (priceStr == null || priceStr.isEmpty()) ? null : parseRand(priceStr);

		boolean passed;
		String description;
		if (before == null || after == null)
		{
			passed = false;
			description = "Airtime balance deducted by at least the bundle price (before=" + beforeStr + ", after=" + afterStr + " -- could not read one or both as a Rand amount)";
		}
		else if (price == null)
		{
			// No price could be parsed from the bundle name -- fall back to
			// "did the balance decrease at all".
			passed = after < before;
			description = "Airtime balance decreased (before=" + beforeStr + ", after=" + afterStr + ", bundle price unknown)";
		}
		else
		{
			double actualDecrease = before - after;
			passed = actualDecrease >= (price - TOLERANCE);
			description = "Airtime balance decreased by at least R" + price + " (before=" + beforeStr + ", after=" + afterStr + ", actual decrease=" + String.format("%.2f", actualDecrease) + ")";
		}

		recordAssertion(context, description, passed);

		return SUCCESS();
	}

	private Double parseRand(String value)
	{
		if (value == null || value.isEmpty())
			return null;
		Matcher matcher = RAND_AMOUNT.matcher(value);
		if (!matcher.find())
			return null;
		return Double.parseDouble(matcher.group(1).replace(',', '.'));
	}

	private void recordAssertion(IScriptContext context, String description, boolean passed)
	{
		String outcome = passed ? "PASS" : "FAIL";
		String entry = "[" + outcome + "] " + description;
		System.out.println(entry);

		String existingLog = context.get("softAssertionLog");
		context.put("softAssertionLog", (existingLog == null || existingLog.isEmpty() ? "" : existingLog + "\n") + entry);

		if (!passed)
		{
			String countStr = context.get("softAssertionFailureCount");
			int count = (countStr == null || countStr.isEmpty()) ? 0 : Integer.parseInt(countStr);
			context.put("softAssertionFailureCount", String.valueOf(count + 1));
		}
	}

}
