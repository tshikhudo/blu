package com.sigos_internal.vodapay.actions.Bundle_Journey;


import com.mc.api.action.Action;
import com.mc.api.device.Device;
import com.mc.api.device.ObjectLevelApi;
import com.mc.api.device.ObjectTree;
import com.mc.api.script.IScriptContext;
import com.mc.api.script.ScriptReturn;


public class Verify_Bundle_Provisioned extends Action
{
	/*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

	public static final Verify_Bundle_Provisioned instance = new Verify_Bundle_Provisioned();

	private Verify_Bundle_Provisioned()
	{
		super();
	}


	/*** END GENERATED CODE ***/

	// TODO: replace with the real "My Bundles" / balance-screen labels
	private static final String MY_BUNDLES_NAV_LABEL = "My Bundles";  // PLACEHOLDER
	private static final String ACTIVE_STATUS_LABEL = "Active";       // PLACEHOLDER
	private static final int PROVISIONING_TIMEOUT_MS = 30000;
	private static final int POLL_INTERVAL_MS = 3000;

	@Override
	protected ScriptReturn run(Device device, IScriptContext context) throws Exception
	{
		final String bundleName = context.get("bundleName");

		ObjectLevelApi api = device.getObjectLevelApi();

		ObjectTree[] nav = api.findObjectsByText(MY_BUNDLES_NAV_LABEL);
		if (nav.length == 0)
			return new ScriptReturn(getCurrentContext(), ScriptReturn.ScriptReturnCode.FAIL, "Could not find nav item: " + MY_BUNDLES_NAV_LABEL);
		nav[0].click();
		device.serverWait(getCurrentContext(), 2000);

		// provisioning can lag the purchase response, so poll instead of a single check
		int waited = 0;
		while (waited < PROVISIONING_TIMEOUT_MS)
		{
			ObjectTree[] bundleEntry = api.findObjectsByText(bundleName);
			ObjectTree[] activeStatus = api.findObjectsByText(ACTIVE_STATUS_LABEL);

			if (bundleEntry.length > 0 && activeStatus.length > 0)
			{
				System.out.println(bundleName + " is provisioned and active.");
				return SUCCESS();
			}

			device.serverWait(getCurrentContext(), POLL_INTERVAL_MS);
			waited += POLL_INTERVAL_MS;
		}

		return new ScriptReturn(getCurrentContext(), ScriptReturn.ScriptReturnCode.FAIL, bundleName + " did not show as provisioned within " + PROVISIONING_TIMEOUT_MS + "ms");
	}

}
