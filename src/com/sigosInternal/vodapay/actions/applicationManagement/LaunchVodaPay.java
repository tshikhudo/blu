package com.sigosInternal.vodapay.actions.applicationManagement;


import com.mc.api.action.Action;
import com.mc.api.device.Device;
import com.mc.api.device.ObjectLevelApi;
import com.mc.api.script.IScriptContext;
import com.mc.api.script.ScriptReturn;
import com.sigosInternal.vodapay.actions.bundleJourney.WaitForText;


public class LaunchVodaPay extends Action
{
	/*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

	public static final LaunchVodaPay instance = new LaunchVodaPay();

	private LaunchVodaPay()
	{
		super();
	}


	/*** END GENERATED CODE ***/

	// CONFIRMED 2026-09-06 via `adb shell pm list packages` on a real device.
	private static final String VODAPAY_PACKAGE = "za.co.vodacom.vodapay";
	// "My Vodacom" is part of the persistent native bottom nav bar, present on
	// Home regardless of account balance state -- a reliable "Home has loaded"
	// marker. "Log in or create profile" covers the logged-out landing screen
	// instead, for a session that isn't already signed in.
	private static final String HOME_MARKER_LABEL = "My Vodacom";
	private static final String LOGGED_OUT_MARKER_LABEL = "Log in or create profile";
	private static final long LAUNCH_TIMEOUT_MS = 15000;

	@Override
	protected ScriptReturn run(Device device, IScriptContext context) throws Exception
	{
		ObjectLevelApi api = device.getObjectLevelApi();

		api.experimental.startApplication(VODAPAY_PACKAGE);

		// Wait for the app to actually reach a recognisable landing screen
		// (either Home or the logged-out screen) rather than a fixed guess --
		// cold-start time varies a lot by device/account state.
		context.put("screenSyncTexts", WaitForText.joinCandidates(HOME_MARKER_LABEL, LOGGED_OUT_MARKER_LABEL));
		context.put("screenSyncTimeoutMs", String.valueOf(LAUNCH_TIMEOUT_MS));
		device.execute(Action.get("com.sigosInternal.vodapay.actions.bundleJourney.WaitForText"));

		return SUCCESS();
	}

}
