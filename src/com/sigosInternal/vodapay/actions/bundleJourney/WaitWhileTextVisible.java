package com.sigosInternal.vodapay.actions.bundleJourney;


import com.mc.api.action.Action;
import com.mc.api.device.Device;
import com.mc.api.device.ObjectLevelApi;
import com.mc.api.script.IScriptContext;
import com.mc.api.script.ScriptReturn;


/**
 * Polls until the given text DISAPPEARS from screen (e.g. a dismissed
 * popup's own label), or timeoutMs elapses. Companion to {@link WaitForText};
 * kept separate since "wait for" and "wait while visible" are different
 * polling conditions, not variants of the same one.
 */
public class WaitWhileTextVisible extends Action
{
	/*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

	public static final WaitWhileTextVisible instance = new WaitWhileTextVisible();

	private WaitWhileTextVisible()
	{
		super();
	}


	/*** END GENERATED CODE ***/

	private static final long POLL_INTERVAL_MS = 300;

	@Override
	protected ScriptReturn run(Device device, IScriptContext context) throws Exception
	{
		// Action parameters:
		//   screenSyncText      -- text expected to disappear
		//   screenSyncTimeoutMs -- how long to poll before giving up
		// Result (never fails on timeout -- this is a best-effort settle wait):
		//   screenSyncGone -- "true"/"false"
		final String text = context.get("screenSyncText");
		if (text == null || text.isEmpty())
			return fail("screenSyncText parameter was not set");
		final long timeoutMs = Long.parseLong(context.get("screenSyncTimeoutMs"));

		ObjectLevelApi api = device.getObjectLevelApi();
		final long deadline = System.currentTimeMillis() + timeoutMs;

		while (api.findObjectsByText(text).length > 0)
		{
			if (System.currentTimeMillis() >= deadline)
			{
				context.put("screenSyncGone", "false");
				return SUCCESS();
			}
			device.serverWait(context, Math.min(POLL_INTERVAL_MS, deadline - System.currentTimeMillis()));
		}

		context.put("screenSyncGone", "true");
		return SUCCESS();
	}

	private ScriptReturn fail(String message)
	{
		return new ScriptReturn(getCurrentContext(), ScriptReturn.ScriptReturnCode.FAIL, message);
	}

}
