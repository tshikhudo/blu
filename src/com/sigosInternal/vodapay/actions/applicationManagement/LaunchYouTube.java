package com.sigosInternal.vodapay.actions.applicationManagement;


import com.mc.api.action.Action;
import com.mc.api.device.Device;
import com.mc.api.device.ObjectLevelApi;
import com.mc.api.script.IScriptContext;
import com.mc.api.script.ScriptReturn;


public class LaunchYouTube extends Action
{
	/*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

	public static final LaunchYouTube instance = new LaunchYouTube();

	private LaunchYouTube()
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
