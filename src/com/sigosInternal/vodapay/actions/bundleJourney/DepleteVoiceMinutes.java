package com.sigosInternal.vodapay.actions.bundleJourney;


import com.mc.api.action.Action;
import com.mc.api.device.Device;
import com.mc.api.device.ObjectLevelApi;
import com.mc.api.device.ObjectTree;
import com.mc.api.script.IScriptContext;
import com.mc.api.script.ScriptReturn;

import java.awt.Point;


/**
 * Burns down a voice-minute allocation with one outbound call held for the full
 * duration -- e.g. a "60 VC Min" component means one ~60 minute call. There's no
 * native "place a call" scripting API in this SDK (checked scripting-api.jar --
 * nothing beyond sendSMS for telephony), so this drives the native dialer's UI.
 *
 * CONFIRMED 2026-09-06 on a real device (Huawei MGA-LX3, Android 10, dialer is
 * bundled into com.huawei.contacts): the dialpad's digit keys are custom-drawn --
 * their nodes report clickable="false" in the accessibility tree, so
 * ObjectTree.click() would likely do nothing. A real coordinate tap does work
 * (verified live via `adb shell input tap` on a digit's bounds, which produced the
 * expected digit in the number display) -- so this uses device.sendTouchClick(Point)
 * against each digit TextView's bounds instead of click(). If a different test
 * device has a normal EditText-based dialer, findObjectsByClassName + enterText
 * would be simpler and should be tried first via SpyCurrentScreen.
 */
public class DepleteVoiceMinutes extends Action
{
	/*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

	public static final DepleteVoiceMinutes instance = new DepleteVoiceMinutes();

	private DepleteVoiceMinutes()
	{
		super();
	}


	/*** END GENERATED CODE ***/

	// CONFIRMED 2026-09-06 via `adb shell pm resolve-activity -a android.intent.action.DIAL`
	// on a real device: dialer functionality is bundled into the Contacts app.
	private static final String DIALER_APP_PACKAGE = "com.huawei.contacts";
	// TODO: still PLACEHOLDER -- have not actually placed a call yet (needs a real
	// callDestinationNumber and a go-ahead first, since it has a real cost/side
	// effect). Confirm the in-call screen's end-call control with SpyCurrentScreen
	// once that's authorized.
	private static final String END_CALL_BUTTON_LABEL = "End call"; // PLACEHOLDER -- verify
	private static final long CALL_CONNECT_WAIT_MS = 10000;

	@Override
	protected ScriptReturn run(Device device, IScriptContext context) throws Exception
	{
		// Action parameters:
		//   callDestinationNumber -- the number to call (another test line, so the
		//                            cost/traffic stays internal to the team)
		//   voiceMinutes           -- how many minutes to burn, from BundleComposition
		final String destinationNumber = context.get("callDestinationNumber");
		if (destinationNumber == null || destinationNumber.trim().isEmpty())
			return new ScriptReturn(getCurrentContext(), ScriptReturn.ScriptReturnCode.FAIL, "callDestinationNumber parameter was not set");

		final int voiceMinutes = Integer.parseInt(context.get("voiceMinutes"));

		ObjectLevelApi api = device.getObjectLevelApi();
		api.experimental.startApplication(DIALER_APP_PACKAGE);
		device.serverWait(getCurrentContext(), 2000);

		for (char digit : destinationNumber.toCharArray())
		{
			if (!Character.isDigit(digit))
				continue;

			ObjectTree[] key = api.findObjectsByText(String.valueOf(digit));
			if (key.length == 0)
				return new ScriptReturn(getCurrentContext(), ScriptReturn.ScriptReturnCode.FAIL, "Could not find dialpad key for digit: " + digit);

			Point center = new Point(key[0].getX() + key[0].getWidth() / 2, key[0].getY() + key[0].getHeight() / 2);
			device.sendTouchClick(center);
			device.serverWait(getCurrentContext(), 300);
		}

		// TODO: the call/dial control's content-desc is "dial" but its text is empty
		// and its own node is disabled until digits are entered -- once authorized to
		// actually place a call, confirm whether a coordinate tap (same technique as
		// the digits above) is needed here too, the same way it was for the digits.
		ObjectTree[] callButton = api.findObjectsByClassName("android.widget.ImageButton");
		if (callButton.length == 0)
			return new ScriptReturn(getCurrentContext(), ScriptReturn.ScriptReturnCode.FAIL, "Could not find the call/dial button");
		Point callCenter = new Point(callButton[0].getX() + callButton[0].getWidth() / 2, callButton[0].getY() + callButton[0].getHeight() / 2);
		device.sendTouchClick(callCenter);

		device.serverWait(getCurrentContext(), CALL_CONNECT_WAIT_MS);

		System.out.println("Holding call to " + destinationNumber + " for " + voiceMinutes + " minute(s) to deplete the voice allocation.");
		device.serverWait(getCurrentContext(), (long) voiceMinutes * 60000);

		ObjectTree[] endCallButton = api.findObjectsByText(END_CALL_BUTTON_LABEL);
		if (endCallButton.length > 0)
			endCallButton[0].click();

		return SUCCESS();
	}

}
