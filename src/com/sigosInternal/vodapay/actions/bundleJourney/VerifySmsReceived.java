package com.sigosInternal.vodapay.actions.bundleJourney;


import com.mc.api.action.Action;
import com.mc.api.device.Device;
import com.mc.api.script.IScriptContext;
import com.mc.api.script.ScriptReturn;


/**
 * Generic SMS-content check, reused for two different points in the product
 * journey: the initial provisioning SMS ("you bought X for R10, valid 1 day") and
 * the usage-threshold notification SMS sent while a product is partway depleted
 * ("you've used 50% of your bundle"). Both are just "does a message containing
 * this text show up in Messages within this timeout" -- only the expected text
 * and when it's called differs, so one action covers both instead of two
 * near-duplicates.
 */
public class VerifySmsReceived extends Action
{
	/*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

	public static final VerifySmsReceived instance = new VerifySmsReceived();

	private VerifySmsReceived()
	{
		super();
	}


	/*** END GENERATED CODE ***/

	private static final long DEFAULT_TIMEOUT_MS = 60000;
	private static final long POLL_INTERVAL_MS = 5000;

	@Override
	protected ScriptReturn run(Device device, IScriptContext context) throws Exception
	{
		// Action parameters:
		//   expectedSmsText -- substring(s) that must appear, e.g. a price ("R10"),
		//                      a bundle component ("100MB"), or a threshold phrase
		//                      ("50%") -- comma-separated to require several at once
		//   timeoutMs       -- optional, how long to wait for the SMS to arrive
		final String expectedSmsTextParam = context.get("expectedSmsText");
		if (expectedSmsTextParam == null || expectedSmsTextParam.trim().isEmpty())
			return new ScriptReturn(getCurrentContext(), ScriptReturn.ScriptReturnCode.FAIL, "expectedSmsText parameter was not set");

		final String[] expectedFragments = expectedSmsTextParam.split(",");

		final String timeoutParam = context.get("timeoutMs");
		final long timeoutMs = (timeoutParam == null || timeoutParam.trim().isEmpty())
				? DEFAULT_TIMEOUT_MS : Long.parseLong(timeoutParam.trim());

		long waited = 0;
		while (waited <= timeoutMs)
		{
			device.execute(Action.get("com.sigosInternal.vodapay.actions.bundleJourney.ReadVisibleSmsText"));
			String screenText = context.get("smsVisibleText");

			boolean allFound = true;
			for (String fragment : expectedFragments)
			{
				if (!screenText.contains(fragment.trim()))
				{
					allFound = false;
					break;
				}
			}

			if (allFound)
			{
				System.out.println("Found expected SMS content: " + expectedSmsTextParam);
				return SUCCESS();
			}

			device.serverWait(getCurrentContext(), POLL_INTERVAL_MS);
			waited += POLL_INTERVAL_MS;
		}

		return new ScriptReturn(getCurrentContext(), ScriptReturn.ScriptReturnCode.FAIL,
				"No SMS containing [" + expectedSmsTextParam + "] arrived within " + timeoutMs + "ms");
	}

}
