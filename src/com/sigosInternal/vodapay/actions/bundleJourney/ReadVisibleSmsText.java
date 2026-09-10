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

	// Genuinely device-dependent, confirmed two different ways so far:
	// - Personal Huawei MGA-LX3 (Android 10), via `adb shell pm list packages`
	//   2026-09-06: com.android.mms (bare package name was sufficient there --
	//   this was explored with plain adb, before the real device farm's
	//   startApplication() was found to need the full package/Activity form).
	// - Real BLU device farm's Samsung Galaxy S23 [mcd 25011], confirmed
	//   directly by the user 2026-09-10: Google's own Messages app, full
	//   component "com.google.android.apps.messaging/com.google.android.apps.messaging.ui.ConversationListActivity"
	//   -- NOT com.samsung.android.messaging as an earlier guess here assumed;
	//   Samsung devices don't necessarily use Samsung's own Messages app.
	// This project's real test device is the Samsung one, so that's what's used.
	private static final String MESSAGES_APP_PACKAGE = "com.google.android.apps.messaging/com.google.android.apps.messaging.ui.ConversationListActivity";

	/**
	 * Opens the native Messages app and writes all visible text from the
	 * conversation list screen (not a specific opened thread) to context as
	 * smsVisibleText.
	 *
	 * CONFIRMED via a real `uiautomator dump` on `com.android.mms` (the
	 * personal Huawei phone) that reading the LIST screen, without opening a
	 * thread, is sufficient: the list's per-conversation "subject" text node
	 * carries the full message body even though the on-screen display
	 * truncates it with "..." -- e.g. a message visually cut off as "Y'ello!
	 * Welcome to MTN. Your number is 06563..." came back in full in the
	 * accessibility tree.
	 *
	 * UNCONFIRMED for Google's Messages app (com.google.android.apps.messaging
	 * -- the real BLU device farm's actual Messages app, per the user). Its UI
	 * implementation is different from com.android.mms and may genuinely
	 * truncate the accessibility text too, not just the visual display -- if
	 * OTP/SMS reading comes back empty or cut short on a real run, the fix is
	 * to open the relevant conversation thread first (tap it) before reading
	 * the screen, rather than assuming the list view alone is enough here too.
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
