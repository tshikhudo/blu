package com.sigosInternal.vodapay.actions.bundleJourney;


import com.mc.api.action.Action;
import com.mc.api.device.Device;
import com.mc.api.device.ObjectLevelApi;
import com.mc.api.script.IScriptContext;
import com.mc.api.script.ScriptReturn;


/**
 * Same idea as {@link WaitForText}, but matches by regex (via
 * ObjectLevelApi.findObjectsByRegex) for text that isn't a fixed literal,
 * e.g. a masked phone number. Kept as its own Action rather than folded into
 * WaitForText since a regex candidate isn't safely joinable/splittable with
 * plain-text candidates using a fixed separator.
 */
public class WaitForTextRegex extends Action
{
	/*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

	public static final WaitForTextRegex instance = new WaitForTextRegex();

	private WaitForTextRegex()
	{
		super();
	}


	/*** END GENERATED CODE ***/

	private static final long POLL_INTERVAL_MS = 300;

	@Override
	protected ScriptReturn run(Device device, IScriptContext context) throws Exception
	{
		// Action parameters:
		//   screenSyncRegex     -- regex to match against on-screen text
		//   screenSyncTimeoutMs -- how long to poll before giving up
		// Result (never fails on timeout):
		//   screenSyncFound -- "true"/"false"
		final String regex = context.get("screenSyncRegex");
		if (regex == null || regex.isEmpty())
			return fail("screenSyncRegex parameter was not set");
		final long timeoutMs = Long.parseLong(context.get("screenSyncTimeoutMs"));

		ObjectLevelApi api = device.getObjectLevelApi();
		final long deadline = System.currentTimeMillis() + timeoutMs;

		while (true)
		{
			if (api.findObjectsByRegex(regex).length > 0)
			{
				context.put("screenSyncFound", "true");
				return SUCCESS();
			}
			if (System.currentTimeMillis() >= deadline)
			{
				context.put("screenSyncFound", "false");
				return SUCCESS();
			}
			device.serverWait(context, Math.min(POLL_INTERVAL_MS, deadline - System.currentTimeMillis()));
		}
	}

	private ScriptReturn fail(String message)
	{
		return new ScriptReturn(getCurrentContext(), ScriptReturn.ScriptReturnCode.FAIL, message);
	}

}
