package com.sigos_internal.vodapay.actions.Bundle_Journey;


import com.mc.api.action.Action;
import com.mc.api.device.Device;
import com.mc.api.script.IScriptContext;
import com.mc.api.script.ScriptReturn;


/**
 * Generates real data usage for the data-depletion step by opening a
 * directly-provided video URL in the device's browser and letting it play for
 * a set duration -- confirmed via decompiling scripting-api.jar that
 * Device.OpenBrowser(String) sends a genuine device-level "open browser to
 * this URL" command (deviceInterface.sendOpenBrowserCommand(url)), not just a
 * signature guess.
 *
 * This replaces an earlier version that drove the native YouTube app's search
 * UI (tap search icon, type a term, tap the first result tile). That version
 * was never confirmed live -- every selector in it (the search icon's label,
 * the result tile's class name) was a placeholder, since this session's live
 * exploration only ever covered VodaPay, not YouTube. The URL+OpenBrowser
 * approach sidesteps that uncertainty entirely: given a direct video link,
 * there's no app-specific UI to find or guess at.
 *
 * There is no dedicated "stream simulator" API in this SDK -- per
 * ug_ext_SX_OTTTestcases.pdf, SIGOS's own built-in "YouTube" test case is a
 * fixed QoE benchmark (no video/duration parameters, just
 * DataConnectionType/RadioNetworkType/isRadioKPIsRequired common to every OTT
 * test case), not a reusable "watch this for X minutes" tool. The real
 * mechanism, here and in that built-in test case alike, is simply: cause a
 * real video to actually play, then wait real wall-clock time while it does.
 *
 * IMPORTANT, unconfirmed by this action: whether the device is on cellular
 * data or WiFi. If the device has WiFi connected, this generates real
 * streaming traffic that will NEVER touch the SIM's data bundle -- the
 * depletion step would run and look successful while proving nothing. Confirm
 * the device's connection type before trusting a depletion result; not
 * checked here since no confirmed API for reading/forcing that was found in
 * this SDK yet (HardwareHelper's RFSwitchHelper/DataCableHelper look like the
 * likeliest candidates -- not yet investigated).
 */
public class Play_Youtube_Video extends Action
{
	/*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

	public static final Play_Youtube_Video instance = new Play_Youtube_Video();

	private Play_Youtube_Video()
	{
		super();
	}


	/*** END GENERATED CODE ***/

	@Override
	protected ScriptReturn run(Device device, IScriptContext context) throws Exception
	{
		// Action parameters:
		//   videoUrl     -- direct link to the video to play, e.g. a
		//                   youtube.com/watch?v=... URL. Required.
		//   watchSeconds -- how long to let it play before returning, e.g. "600"
		final String videoUrl = context.get("videoUrl");
		if (videoUrl == null || videoUrl.trim().isEmpty())
			return fail("videoUrl parameter was not set");

		final String watchSecondsStr = context.get("watchSeconds");
		if (watchSecondsStr == null || watchSecondsStr.trim().isEmpty())
			return fail("watchSeconds parameter was not set");
		final int watchSeconds;
		try
		{
			watchSeconds = Integer.parseInt(watchSecondsStr.trim());
		}
		catch (NumberFormatException e)
		{
			return fail("watchSeconds must be a whole number of seconds, got: " + watchSecondsStr);
		}

		device.OpenBrowser(videoUrl);

		// Let the video play and consume the bundle's data allowance. No
		// on-screen marker to smart-wait for here -- this is genuinely a fixed
		// real-world duration (how long we want it to keep streaming), not a
		// screen-still-loading wait.
		device.serverWait(getCurrentContext(), (long) watchSeconds * 1000L);

		return SUCCESS();
	}

	private ScriptReturn fail(String message)
	{
		return new ScriptReturn(getCurrentContext(), ScriptReturn.ScriptReturnCode.FAIL, message);
	}

}
