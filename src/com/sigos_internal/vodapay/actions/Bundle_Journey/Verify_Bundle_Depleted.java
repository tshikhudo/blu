package com.sigos_internal.vodapay.actions.Bundle_Journey;


import com.mc.api.action.Action;
import com.mc.api.device.Device;
import com.mc.api.device.ObjectLevelApi;
import com.mc.api.device.ObjectTree;
import com.mc.api.device.helper.KeyMode;
import com.mc.api.script.IScriptContext;
import com.mc.api.script.ScriptReturn;


public class Verify_Bundle_Depleted extends Action
{
	/*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

	public static final Verify_Bundle_Depleted instance = new Verify_Bundle_Depleted();

	private Verify_Bundle_Depleted()
	{
		super();
	}


	/*** END GENERATED CODE ***/

	// TODO: replace with VodaPay's real depleted/expired-bundle labels.
	// Checking for an explicit "depleted" state is more robust than parsing a
	// numeric remaining-balance value, since object-level text matching returns
	// whether a string is present rather than reading arbitrary field contents.
	private static final String MY_BUNDLES_NAV_LABEL = "My Bundles"; // PLACEHOLDER
	private static final String DEPLETED_LABEL = "Depleted";         // PLACEHOLDER, e.g. could be "0MB left" / "Expired"

	@Override
	protected ScriptReturn run(Device device, IScriptContext context) throws Exception
	{
		final String bundleName = context.get("bundleName");

		// bring VodaPay back to the foreground -- YouTube was the active app
		device.sendKeys("[Return]", KeyMode.ALPHA); // TODO: confirm real "go home" / app-switch key for this device
		device.serverWait(getCurrentContext(), 2000);

		ObjectLevelApi api = device.getObjectLevelApi();
		api.experimental.startApplication("za.co.vodacom.vodapay"); // PLACEHOLDER -- keep in sync with Launch_VodaPay
		device.serverWait(getCurrentContext(), 3000);

		ObjectTree[] nav = api.findObjectsByText(MY_BUNDLES_NAV_LABEL);
		if (nav.length == 0)
			return new ScriptReturn(getCurrentContext(), ScriptReturn.ScriptReturnCode.FAIL, "Could not find nav item: " + MY_BUNDLES_NAV_LABEL);
		nav[0].click();
		device.serverWait(getCurrentContext(), 2000);

		ObjectTree[] bundleEntry = api.findObjectsByText(bundleName);
		ObjectTree[] depleted = api.findObjectsByText(DEPLETED_LABEL);

		if (depleted.length > 0)
		{
			System.out.println(bundleName + " correctly shows as depleted.");
			return SUCCESS();
		}

		if (bundleEntry.length > 0)
			return new ScriptReturn(getCurrentContext(), ScriptReturn.ScriptReturnCode.FAIL, bundleName + " is still active/not depleted after the usage step.");

		return new ScriptReturn(getCurrentContext(), ScriptReturn.ScriptReturnCode.FAIL, "Could not locate " + bundleName + " on the bundles screen at all.");
	}

}
