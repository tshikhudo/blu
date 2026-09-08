package com.sigos_internal.vodapay.actions.Application_Management;


import com.mc.api.action.Action;
import com.mc.api.device.Device;
import com.mc.api.device.ObjectLevelApi;
import com.mc.api.script.IScriptContext;
import com.mc.api.script.ScriptReturn;


public class Launch_YouTube extends Action
{
	/*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

	public static final Launch_YouTube instance = new Launch_YouTube();

	private Launch_YouTube()
	{
		super();
	}


	/*** END GENERATED CODE ***/

	private static final String YOUTUBE_PACKAGE = "com.google.android.youtube";

	@Override
	protected ScriptReturn run(Device device, IScriptContext context) throws Exception
	{
		ObjectLevelApi api = device.getObjectLevelApi();

		api.experimental.startApplication(YOUTUBE_PACKAGE);

		device.serverWait(getCurrentContext(), 5000);

		return SUCCESS();
	}

}
