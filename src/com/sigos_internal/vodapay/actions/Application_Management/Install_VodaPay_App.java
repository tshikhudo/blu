package com.sigos_internal.vodapay.actions.Application_Management;


import com.mc.api.action.Action;
import com.mc.api.device.Device;
import com.mc.api.device.helper.UploadApplicationHelper;
import com.mc.api.script.IScriptContext;
import com.mc.api.script.ScriptReturn;


public class Install_VodaPay_App extends Action
{
	/*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

	public static final Install_VodaPay_App instance = new Install_VodaPay_App();

	private Install_VodaPay_App()
	{
		super();
	}


	/*** END GENERATED CODE ***/

	// TODO: confirm this matches exactly what's registered in the DeviceAnywhere/BLU
	// application repository (Studio -> Application Manager) -- name/type/version
	// must match an entry already uploaded there, per the Upload_Application example
	// in javaexamples_giri.
	private static final String VODAPAY_APP_NAME = "VodaPay.apk";     // PLACEHOLDER -- verify against the repository
	private static final String VODAPAY_APP_TYPE = "ANDROID_APK";
	private static final String VODAPAY_APP_VERSION = "latest";       // PLACEHOLDER -- verify

	@Override
	protected ScriptReturn run(Device device, IScriptContext context) throws Exception
	{
		UploadApplicationHelper helper = new UploadApplicationHelper();
		helper.setApplicationName(VODAPAY_APP_NAME);
		helper.setApplicationType(VODAPAY_APP_TYPE);
		helper.setApplicationVersion(VODAPAY_APP_VERSION);

		device.uploadApplication(helper);

		// allow the upload to fully write to the device before rebooting into it
		device.serverWait(getCurrentContext(), 10000);

		// required on Android/most platforms to complete installation (see
		// Upload_Application.java in javaexamples_giri)
		device.reset();

		device.serverWait(getCurrentContext(), 30000);

		return SUCCESS();
	}

}
