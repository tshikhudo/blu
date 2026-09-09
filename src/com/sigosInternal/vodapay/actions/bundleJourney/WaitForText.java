package com.sigosInternal.vodapay.actions.bundleJourney;


import com.mc.api.action.Action;
import com.mc.api.device.Device;
import com.mc.api.device.ObjectLevelApi;
import com.mc.api.script.IScriptContext;
import com.mc.api.script.ScriptReturn;


/**
 * Polls for one or more candidate texts instead of sleeping a fixed
 * duration. Replaces the earlier plain-class ScreenSync.waitFor/waitForAny --
 * Studio only creates and manages typed Action/State/TestCase components (no
 * plain supporting classes), so a shared helper like this one has to be its
 * own Action, invoked via Action.get(...) + device.execute(...) like every
 * other step, with its arguments and result passed through IScriptContext
 * (which is a Map&lt;String,String&gt; -- confirmed via javap, not just assumed)
 * rather than as Java method parameters/return values.
 *
 * VodaPay's screens (mostly WebView-rendered, network-dependent) load in
 * wildly inconsistent times -- a fixed Thread.sleep either wastes time
 * waiting out a fast load, or (worse, and harder to notice until a run fails
 * intermittently) moves on to the next step before a slow one has actually
 * rendered.
 */
public class WaitForText extends Action
{
	/*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

	public static final WaitForText instance = new WaitForText();

	private WaitForText()
	{
		super();
	}


	/*** END GENERATED CODE ***/

	private static final long POLL_INTERVAL_MS = 300;
	static final String CANDIDATE_SEPARATOR = "###";

	@Override
	protected ScriptReturn run(Device device, IScriptContext context) throws Exception
	{
		// Action parameters:
		//   screenSyncTexts    -- one or more candidate texts to wait for, joined
		//                         with CANDIDATE_SEPARATOR (use joinCandidates());
		//                         waits until ANY of them appears
		//   screenSyncTimeoutMs -- how long to poll before giving up
		// Result (written back to context, never fails on timeout -- callers
		// decide what "not found" means for their own step):
		//   screenSyncFound       -- "true"/"false"
		//   screenSyncMatchedText -- whichever candidate matched first, "" if none
		final String candidatesRaw = context.get("screenSyncTexts");
		if (candidatesRaw == null || candidatesRaw.isEmpty())
			return fail("screenSyncTexts parameter was not set");
		final long timeoutMs = Long.parseLong(context.get("screenSyncTimeoutMs"));
		final String[] candidates = candidatesRaw.split(java.util.regex.Pattern.quote(CANDIDATE_SEPARATOR));

		ObjectLevelApi api = device.getObjectLevelApi();
		final long deadline = System.currentTimeMillis() + timeoutMs;

		while (true)
		{
			for (String candidate : candidates)
			{
				if (!candidate.isEmpty() && api.findObjectsByText(candidate).length > 0)
				{
					context.put("screenSyncFound", "true");
					context.put("screenSyncMatchedText", candidate);
					return SUCCESS();
				}
			}

			if (System.currentTimeMillis() >= deadline)
			{
				context.put("screenSyncFound", "false");
				context.put("screenSyncMatchedText", "");
				return SUCCESS();
			}

			device.serverWait(context, Math.min(POLL_INTERVAL_MS, deadline - System.currentTimeMillis()));
		}
	}

	/** Joins candidate texts with the internal separator for the screenSyncTexts parameter. */
	public static String joinCandidates(String... texts)
	{
		return String.join(CANDIDATE_SEPARATOR, texts);
	}

	private ScriptReturn fail(String message)
	{
		return new ScriptReturn(getCurrentContext(), ScriptReturn.ScriptReturnCode.FAIL, message);
	}

}
