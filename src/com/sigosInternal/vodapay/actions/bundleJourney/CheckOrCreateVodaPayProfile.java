package com.sigosInternal.vodapay.actions.bundleJourney;


import com.mc.api.action.Action;
import com.mc.api.device.Device;
import com.mc.api.device.ObjectLevelApi;
import com.mc.api.device.ObjectTree;
import com.mc.api.script.IScriptContext;
import com.mc.api.script.ScriptReturn;

import java.awt.Point;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Ensures the given cellNumber's VodaPay profile is usable before the bundle
 * journey starts:
 *  - not logged in at all -> registers (first time for this number) or logs in
 *    (a number already registered before) -- VodaPay uses one combined screen for
 *    both, so this doesn't need to know in advance which case it is
 *  - already logged in as a DIFFERENT number -> FAILs clearly rather than silently
 *    running the rest of the journey against the wrong account (VodaPay sessions
 *    persist across app restarts -- confirmed live by force-stopping and
 *    relaunching, which went straight back to Home with no re-auth)
 *  - already logged in as the right number -> no-op
 *
 * Every step below was actually driven and screenshotted live on a real device
 * (Huawei MGA-LX3, Android 10) on 2026-09-06, registering the team's real shared
 * test number -- see [[project-vodapay-bundle-testing]] in memory for which one.
 * The OTP is read straight off the native SMS app (com.android.mms): there's no
 * dedicated "read SMS" scripting API in this SDK, so this reads it the same way a
 * person would.
 */
public class CheckOrCreateVodaPayProfile extends Action
{
	/*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

	public static final CheckOrCreateVodaPayProfile instance = new CheckOrCreateVodaPayProfile();

	private CheckOrCreateVodaPayProfile()
	{
		super();
	}


	/*** END GENERATED CODE ***/

	private static final String VODAPAY_PACKAGE = "za.co.vodacom.vodapay";

	// "Log in or create profile" screen (native, has real resource-ids, unlike the
	// WebView-based shop screens).
	private static final String LOGGED_OUT_MARKER_LABEL = "Log in or create profile";
	private static final String CELL_NUMBER_FIELD_LABEL = "Enter your cell number";
	private static final String NEXT_BUTTON_LABEL = "Next";

	// OTP screen ("Sign up" for a new number). CONFIRMED live: title "Sign up",
	// heading "We've sent you an SMS", field hint "Enter your code here", link
	// "Resend SMS". No separate submit button was found -- entering 6 digits
	// auto-advances.
	private static final String OTP_FIELD_LABEL = "Enter your code here";
	private static final Pattern OTP_PATTERN = Pattern.compile("\\b\\d{4,8}\\b");

	// "Get started with VodaPay" profile screen -- only appears for a genuinely new
	// number. Name/Surname required, Email optional.
	private static final String NAME_FIELD_LABEL = "Enter name";
	private static final String SURNAME_FIELD_LABEL = "Enter surname";
	private static final String ACCEPT_BUTTON_LABEL = "Accept";

	// "Secure your profile" PIN screen -- 5 digits, one shared box-per-digit
	// layout for both "create" and "confirm". No visible field label to search by,
	// so this is entered via the same 5-box layout confirmed by getX/Y bounds of
	// the "Please create your PIN" text as an anchor.
	private static final String PIN_SECTION_LABEL = "Please create your PIN";
	private static final String CONFIRM_PIN_SECTION_LABEL = "Confirm your PIN";
	private static final String CONTINUE_BUTTON_LABEL = "Continue";

	// "My account" screen -- masked number shown as e.g. "065 *** 3962".
	private static final String PROFILE_ICON_DESC_OR_POSITION_NOTE = "top-left avatar icon on Home, no text/content-desc -- selected by position";
	private static final String MASKED_NUMBER_REGEX = "\\d{3} \\*\\*\\* \\d{4}";

	// Post-registration onboarding screens, confirmed live 2026-09-07: a
	// "Welcome to VodaPay!" carousel slide, then a "Don't miss out" notification
	// opt-in popup (dismissed via its own top-right X, catalogued in
	// PopupDismisser). Order isn't hard-relied on -- this just waits for
	// whichever shows up first before handing off to DismissKnownPopups.
	private static final String WELCOME_CAROUSEL_LABEL = "Welcome to VodaPay!";
	private static final String NOTIFICATION_OPTIN_LABEL = "Don't miss out";

	// Default ceilings for the smart waits below -- these are UPPER bounds, not
	// fixed sleeps: ScreenSync polls every ~300ms and returns as soon as the
	// target screen actually shows up, so a fast load doesn't pay the full
	// timeout. Screens genuinely do load at very different speeds (WebView
	// content over a live network vs. a native screen), which is exactly what a
	// single guessed Thread.sleep duration can't account for across many
	// different accounts/bundles in one Web-UI-driven batch run.
	private static final long SMS_DELIVERY_TIMEOUT_MS = 25000;
	private static final long SCREEN_TRANSITION_TIMEOUT_MS = 15000;
	private static final long SETTLE_TIMEOUT_MS = 8000;

	@Override
	protected ScriptReturn run(Device device, IScriptContext context) throws Exception
	{
		// Action parameters:
		//   cellNumber -- the number to register/log in with
		//   mpin       -- 5-digit PIN to set if this is a brand-new registration
		//                 (ignored if the number is already registered -- VodaPay
		//                 sessions persist, so no PIN re-entry has been observed)
		//   firstName, lastName -- only used for a brand-new registration's
		//                 "Get started with VodaPay" step
		final String cellNumber = context.get("cellNumber");

		ObjectLevelApi api = device.getObjectLevelApi();

		if (api.findObjectsByText(LOGGED_OUT_MARKER_LABEL).length == 0)
		{
			// Already logged in as *someone* -- confirm it's the right account before
			// treating this as a no-op. See "My account" (tap the profile avatar,
			// top-left of Home) for the masked number.
			return verifyLoggedInAccountMatches(device, context, api, cellNumber);
		}

		ObjectTree[] cellField = api.findObjectsByText(CELL_NUMBER_FIELD_LABEL);
		if (cellField.length == 0)
			return fail("Could not find field: " + CELL_NUMBER_FIELD_LABEL);
		cellField[0].clearTextBox();
		final String localNumber = cellNumber.startsWith("0") ? cellNumber.substring(1) : cellNumber;
		cellField[0].enterText(localNumber);

		ObjectTree[] nextBtn = api.findObjectsByText(NEXT_BUTTON_LABEL);
		if (nextBtn.length == 0)
			return fail("Could not find button: " + NEXT_BUTTON_LABEL);
		nextBtn[0].click();

		// Wait for the OTP screen itself rather than guessing how long SMS
		// delivery + the screen transition together take -- both vary a lot in
		// practice (network-dependent SMS delivery especially), and this is
		// exactly the kind of step a fixed sleep gets wrong across different
		// numbers/networks in a batch run.
		if (!waitForText(device, context, SMS_DELIVERY_TIMEOUT_MS, OTP_FIELD_LABEL))
			return fail("OTP screen (" + OTP_FIELD_LABEL + ") never appeared after submitting " + cellNumber);

		String otp = readOtpFromMessagesApp(device, context);
		if (otp == null)
			return fail("Could not find an OTP in the Messages app for " + cellNumber);

		// switch back to VodaPay -- reading the SMS above changed the foreground app
		api.experimental.startApplication(VODAPAY_PACKAGE);
		if (!waitForText(device, context, SETTLE_TIMEOUT_MS, OTP_FIELD_LABEL))
			return fail("OTP field wasn't ready after switching back to VodaPay");

		ObjectTree[] otpField = api.findObjectsByText(OTP_FIELD_LABEL);
		if (otpField.length == 0)
			return fail("Could not find field: " + OTP_FIELD_LABEL);
		otpField[0].clearTextBox();
		otpField[0].enterText(otp);

		// No separate submit button -- 6 digits auto-advances. Wait for whichever
		// next state actually shows up: a brand-new number continues to "Get
		// started"/"Secure your profile", an already-registered number logs
		// straight into Home (none of these three reappear), and a rejected OTP
		// stays on/returns to the logged-out screen.
		waitForText(device, context, SCREEN_TRANSITION_TIMEOUT_MS,
				NAME_FIELD_LABEL, PIN_SECTION_LABEL, LOGGED_OUT_MARKER_LABEL);

		if (api.findObjectsByText(LOGGED_OUT_MARKER_LABEL).length == 0
				&& api.findObjectsByText(PIN_SECTION_LABEL).length == 0
				&& api.findObjectsByText(NAME_FIELD_LABEL).length == 0)
		{
			System.out.println(cellNumber + " was already registered -- OTP logged straight in.");
			return SUCCESS();
		}

		if (api.findObjectsByText(LOGGED_OUT_MARKER_LABEL).length > 0)
			return fail("Still on the login screen after entering the OTP for " + cellNumber + " -- OTP was likely wrong or expired");

		// Brand-new number: "Get started with VodaPay"
		if (api.findObjectsByText(NAME_FIELD_LABEL).length > 0)
		{
			final String firstName = context.get("firstName");
			final String lastName = context.get("lastName");
			if (firstName == null || lastName == null)
				return fail("firstName/lastName parameters are required to register a brand-new number");

			ObjectTree[] nameField = api.findObjectsByText(NAME_FIELD_LABEL);
			nameField[0].enterText(firstName);
			ObjectTree[] surnameField = api.findObjectsByText(SURNAME_FIELD_LABEL);
			if (surnameField.length == 0)
				return fail("Could not find field: " + SURNAME_FIELD_LABEL);
			surnameField[0].enterText(lastName);

			ObjectTree[] acceptBtn = api.findObjectsByText(ACCEPT_BUTTON_LABEL);
			if (acceptBtn.length == 0)
				return fail("Could not find button: " + ACCEPT_BUTTON_LABEL);
			acceptBtn[0].click();
			if (!waitForText(device, context, SCREEN_TRANSITION_TIMEOUT_MS, PIN_SECTION_LABEL))
				return fail("PIN screen (" + PIN_SECTION_LABEL + ") never appeared after Accept");
		}

		// "Secure your profile" -- create + confirm a 5-digit PIN
		if (api.findObjectsByText(PIN_SECTION_LABEL).length > 0)
		{
			final String mpin = context.get("mpin");
			if (mpin == null || mpin.length() != 5)
				return fail("mpin parameter must be exactly 5 digits to register a brand-new number");

			if (!enterPinNear(device, api, PIN_SECTION_LABEL, mpin))
				return fail("Could not enter PIN under: " + PIN_SECTION_LABEL);
			if (!enterPinNear(device, api, CONFIRM_PIN_SECTION_LABEL, mpin))
				return fail("Could not enter PIN under: " + CONFIRM_PIN_SECTION_LABEL);

			ObjectTree[] continueBtn = api.findObjectsByText(CONTINUE_BUTTON_LABEL);
			if (continueBtn.length == 0)
				return fail("Could not find button: " + CONTINUE_BUTTON_LABEL);
			continueBtn[0].click();
			waitForText(device, context, SCREEN_TRANSITION_TIMEOUT_MS,
					WELCOME_CAROUSEL_LABEL, NOTIFICATION_OPTIN_LABEL);
		}

		// "Welcome to VodaPay!" onboarding carousel, then a notification opt-in
		// screen, then a native "Allow VodaPay to make and manage phone calls?"
		// permission dialog -- all one-time, all dismissed the same opportunistic
		// way rather than hard-coded steps, since their order/presence can vary.
		device.getObjectLevelApi().doSwipe(com.mc.api.device.SwipeSize.LARGE, com.mc.api.device.SwipeDirection.LEFT);
		device.serverWait(getCurrentContext(), 500); // swipe animation settle, not a network load
		device.execute(Action.get("com.sigosInternal.vodapay.actions.bundleJourney.DismissKnownPopups"));

		// The native call-permission dialog isn't WebView/VodaPay text -- it's a
		// system dialog, found the same way as any other object-level element.
		ObjectTree[] allowBtn = api.findObjectsByText("ALLOW");
		if (allowBtn.length > 0)
			allowBtn[0].click();

		device.serverWait(getCurrentContext(), 1000); // final settle before handing back to the caller

		return SUCCESS();
	}

	/**
	 * "My account" (tap the profile avatar, top-left on Home) shows the logged-in
	 * number masked as e.g. "065 *** 3962". Confirms the already-logged-in session
	 * actually belongs to the number this run needs, instead of assuming so.
	 */
	private ScriptReturn verifyLoggedInAccountMatches(Device device, IScriptContext context, ObjectLevelApi api, String cellNumber) throws Exception
	{
		final String local = cellNumber.startsWith("0") ? cellNumber.substring(1) : cellNumber;
		if (local.length() < 7)
			return fail("cellNumber too short to verify: " + cellNumber);
		final String expectedMasked = (cellNumber.startsWith("0") ? cellNumber.substring(0, 3) : "0" + local.substring(0, 2))
				+ " *** " + local.substring(local.length() - 4);

		// TODO: the avatar icon has no text/content-desc (same accessibility gap as
		// the network provider logos) -- this taps the first ImageView near the top
		// of Home as a best guess. Confirm with SpyCurrentScreen if this proves
		// unreliable on other devices.
		ObjectTree[] avatarCandidates = api.findObjectsByClassName("android.widget.ImageView");
		if (avatarCandidates.length == 0)
			return fail("Could not find the profile avatar icon on Home");
		ObjectTree avatar = avatarCandidates[0];
		device.sendTouchClick(new Point(avatar.getX() + avatar.getWidth() / 2, avatar.getY() + avatar.getHeight() / 2));
		// Wait for a masked number (any masked number, not necessarily the
		// expected one) to actually render before reading the screen -- confirms
		// the account screen loaded rather than assuming a fixed delay was enough.
		context.put("screenSyncRegex", MASKED_NUMBER_REGEX);
		context.put("screenSyncTimeoutMs", String.valueOf(SETTLE_TIMEOUT_MS));
		device.execute(Action.get("com.sigosInternal.vodapay.actions.bundleJourney.WaitForTextRegex"));

		String screenText = api.getCurrentScreen().getAllTextInSingleString();
		boolean matches = screenText.contains(expectedMasked);

		// back to Home either way
		device.sendKeys("[Back]", com.mc.api.device.helper.KeyMode.ALPHA);
		device.serverWait(getCurrentContext(), 500);

		if (!matches)
			return fail("VodaPay is logged in as a different account than " + cellNumber + " (expected masked number " + expectedMasked + " not found on My account)");

		System.out.println("VodaPay already logged in as " + cellNumber + ", skipping registration/login.");
		return SUCCESS();
	}

	/**
	 * Pushes a 5-digit PIN directly into the field just below the given anchor
	 * label, via ObjectTree.enterText() -- the same direct-set approach used for
	 * every other field in this action (cell number, OTP, name, surname), rather
	 * than simulating keystrokes into the screen.
	 *
	 * This replaces an earlier version that tapped near the anchor and used
	 * device.sendKeys() to simulate typing. That was dropped deliberately: live
	 * adb-based exploration of this exact 5-box PIN widget (see
	 * [[project-vodapay-bundle-testing]] in memory) found on-device keystroke
	 * injection unreliably duplicates or drops digits -- e.g. one keystroke
	 * landing as two digits, or one correction backspace deleting two characters
	 * instead of one. Pushing the whole value in one enterText() call sidesteps
	 * that class of bug entirely instead of working around it with read-back-and-
	 * retry loops. The visually separate 5 boxes are one underlying field, not 5
	 * -- confirmed the same way: raw digit entry advanced through all 5 as a
	 * single continuous cursor/value, not 5 independently-focused ones.
	 *
	 * TODO: this assumes the field is discoverable via the standard
	 * "android.widget.EditText" class name. Unconfirmed against the real BLU
	 * agent (this was only checked via raw adb on a personal phone, not through
	 * BLU itself) -- if this fails on a real run, use SpyCurrentScreen to find
	 * the field's actual class name and add it to the candidates below.
	 */
	private boolean enterPinNear(Device device, ObjectLevelApi api, String anchorLabel, String pin) throws Exception
	{
		ObjectTree[] anchor = api.findObjectsByText(anchorLabel);
		if (anchor.length == 0)
			return false;

		ObjectTree pinField = closestFieldBelow(api, anchor[0], "android.widget.EditText");
		if (pinField == null)
			return false;

		pinField.clearTextBox();
		pinField.enterText(pin);
		return true;
	}

	/**
	 * Finds the object of the given class name that sits directly below the
	 * anchor (smallest non-negative Y gap) -- used for fields that have no
	 * text/resource-id of their own to search by directly.
	 */
	private ObjectTree closestFieldBelow(ObjectLevelApi api, ObjectTree anchor, String className) throws Exception
	{
		ObjectTree best = null;
		int bestGap = Integer.MAX_VALUE;
		for (ObjectTree candidate : api.findObjectsByClassName(className))
		{
			int gap = candidate.getY() - anchor.getY();
			if (gap >= 0 && gap < bestGap)
			{
				best = candidate;
				bestGap = gap;
			}
		}
		return best;
	}

	/**
	 * Extracts the most recent 4-8 digit code visible in the Messages app.
	 */
	private String readOtpFromMessagesApp(Device device, IScriptContext context) throws Exception
	{
		device.execute(Action.get("com.sigosInternal.vodapay.actions.bundleJourney.ReadVisibleSmsText"));
		String screenText = context.get("smsVisibleText");
		Matcher matcher = OTP_PATTERN.matcher(screenText);

		String lastMatch = null;
		while (matcher.find())
			lastMatch = matcher.group();

		return lastMatch;
	}

	/** Thin wrapper around the WaitForText Action -- see its class doc for why this can't just be a direct method call. A single text is just a length-1 varargs call. */
	private boolean waitForText(Device device, IScriptContext context, long timeoutMs, String... texts) throws Exception
	{
		context.put("screenSyncTexts", WaitForText.joinCandidates(texts));
		context.put("screenSyncTimeoutMs", String.valueOf(timeoutMs));
		device.execute(Action.get("com.sigosInternal.vodapay.actions.bundleJourney.WaitForText"));
		return Boolean.parseBoolean(context.get("screenSyncFound"));
	}

	private ScriptReturn fail(String message)
	{
		return new ScriptReturn(getCurrentContext(), ScriptReturn.ScriptReturnCode.FAIL, message);
	}

}
