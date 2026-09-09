package com.sigosInternal.vodapay.actions.bundleJourney;


import com.mc.api.action.Action;
import com.mc.api.device.Device;
import com.mc.api.device.ObjectLevelApi;
import com.mc.api.script.IScriptContext;
import com.mc.api.script.ScriptReturn;


/**
 * Shared step for reading SMS content off the device's native Messages app.
 * There is no dedicated "read SMS" scripting API in this SDK (checked
 * scripting-api.jar for anything under that name -- nothing), so this reads it
 * the same way a person would: open Messages and inspect the screen text. Used by
 * both OTP extraction (CheckOrCreateVodaPayProfile) and product SMS
 * verification (VerifySmsReceived), so the native-app quirks only need fixing
 * in one place once real device access confirms them.
 *
 * Was a plain package-private utility class (SmsReader) until Studio's lack
 * of support for untyped classes forced every shared helper in this project
 * to become its own Action -- see WaitForText's class doc for the fuller
 * explanation.
 */
public class ReadVisibleSmsText extends Action
{
	/*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

	public static final ReadVisibleSmsText instance = new ReadVisibleSmsText();

	private ReadVisibleSmsText()
	{
		super();
	}


	/*** END GENERATED CODE ***/

	// CONFIRMED 2026-09-06 on a real device (Huawei MGA-LX3, Android 10) via
	// `adb shell pm list packages`: the stock Messages app here is com.android.mms
	// (a Samsung device would instead be com.samsung.android.messaging -- this is
	// genuinely device-dependent, so re-verify per real test device model).
	private static final String MESSAGES_APP_PACKAGE = "com.android.mms";

	/**
	 * Opens the native Messages app and writes all visible text from the
	 * conversation list screen (not a specific opened thread) to context as
	 * smsVisibleText. CONFIRMED via a real `uiautomator dump` that this is
	 * sufficient: the list's per-conversation "subject" text node carries the
	 * full message body even though the on-screen display truncates it with
	 * "..." -- e.g. a message visually cut off as "Y'ello! Welcome to MTN.
	 * Your number is 06563..." came back in full in the accessibility tree. So
	 * no extra tap into a thread is needed to read a code or confirmation
	 * text, as long as it's within whatever the list currently shows.
	 */
	@Override
	protected ScriptReturn run(Device device, IScriptContext context) throws Exception
	{
		ObjectLevelApi api = device.getObjectLevelApi();
		api.experimental.startApplication(MESSAGES_APP_PACKAGE);
		device.serverWait(context, 3000);
		context.put("smsVisibleText", api.getCurrentScreen().getAllTextInSingleString());
		return SUCCESS();
	}

}
