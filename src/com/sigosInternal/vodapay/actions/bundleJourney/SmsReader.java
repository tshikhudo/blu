package com.sigosInternal.vodapay.actions.bundleJourney;

import com.mc.api.device.Device;
import com.mc.api.device.ObjectLevelApi;
import com.mc.api.script.IScriptContext;

/**
 * Shared helper for reading SMS content off the device's native Messages app.
 * There is no dedicated "read SMS" scripting API in this SDK (checked
 * scripting-api.jar for anything under that name -- nothing), so this reads it
 * the same way a person would: open Messages and inspect the screen text. Used by
 * both OTP extraction (CheckOrCreateVodaPayProfile) and product SMS
 * verification (VerifySmsReceived), so the native-app quirks only need fixing
 * in one place once real device access confirms them.
 */
final class SmsReader
{
	// CONFIRMED 2026-09-06 on a real device (Huawei MGA-LX3, Android 10) via
	// `adb shell pm list packages`: the stock Messages app here is com.android.mms
	// (a Samsung device would instead be com.samsung.android.messaging -- this is
	// genuinely device-dependent, so re-verify per real test device model).
	private static final String MESSAGES_APP_PACKAGE = "com.android.mms";

	private SmsReader() {}

	/**
	 * Opens the native Messages app and returns all visible text from the
	 * conversation list screen (not a specific opened thread). CONFIRMED via a real
	 * `uiautomator dump` that this is sufficient: the list's per-conversation
	 * "subject" text node carries the full message body even though the on-screen
	 * display truncates it with "..." -- e.g. a message visually cut off as
	 * "Y'ello! Welcome to MTN. Your number is 06563..." came back in full in the
	 * accessibility tree. So no extra tap into a thread is needed to read a code or
	 * confirmation text, as long as it's within whatever the list currently shows.
	 * `context` is the calling Action/TestCase's IScriptContext, needed to wait for
	 * the app to actually load before reading.
	 */
	static String readVisibleMessagesText(Device device, IScriptContext context) throws Exception
	{
		ObjectLevelApi api = device.getObjectLevelApi();
		api.experimental.startApplication(MESSAGES_APP_PACKAGE);
		device.serverWait(context, 3000);
		return api.getCurrentScreen().getAllTextInSingleString();
	}
}
