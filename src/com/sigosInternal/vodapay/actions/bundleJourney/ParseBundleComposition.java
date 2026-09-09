package com.sigosInternal.vodapay.actions.bundleJourney;


import com.mc.api.action.Action;
import com.mc.api.device.Device;
import com.mc.api.script.IScriptContext;
import com.mc.api.script.ScriptReturn;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Parses a VodaPay bundle's display text into its component resources and
 * writes each piece back to context as a plain string. VodaPay bundles are
 * frequently composite -- e.g. "60 VC Min+100MB for 1 day - R10" is voice
 * minutes AND data in one product -- so a single purchased bundle can need
 * more than one depletion action run against it (calls for the minutes,
 * streaming for the data, etc). Parsing the display text directly means the
 * test only needs cellNumber + bundleName as input; no separate
 * product-type lookup table is required, since the bundle name the team
 * already gives testers is itself the full description (as shown by the
 * team's own examples: "30 VC Min + 1GB", "60 VC Min+100MB for 1 day - R10",
 * "100 VC Min+200MB for 1 day - R15").
 *
 * Was a plain public value-holder class (BundleComposition, with an object
 * returned from a static parse() method and getter methods on it) until
 * Studio's lack of support for untyped classes forced every shared helper in
 * this project to become its own Action -- see WaitForText's class doc for
 * the fuller explanation. Since IScriptContext is a Map&lt;String,String&gt;
 * (confirmed via javap), there's no way to hand back a composite Java object
 * through it either -- every field is written out individually as a string.
 */
public class ParseBundleComposition extends Action
{
	/*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

	public static final ParseBundleComposition instance = new ParseBundleComposition();

	private ParseBundleComposition()
	{
		super();
	}


	/*** END GENERATED CODE ***/

	private static final Pattern VOICE_MINUTES = Pattern.compile("(\\d+)\\s*VC\\s*Min", Pattern.CASE_INSENSITIVE);
	private static final Pattern SMS_COUNT = Pattern.compile("(\\d+)\\s*SMS", Pattern.CASE_INSENSITIVE);
	private static final Pattern DATA_AMOUNT = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(GB|MB)", Pattern.CASE_INSENSITIVE);
	private static final Pattern VALIDITY_DAYS = Pattern.compile("(\\d+)\\s*day", Pattern.CASE_INSENSITIVE);
	private static final Pattern PRICE = Pattern.compile("R\\s*(\\d+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE);

	@Override
	protected ScriptReturn run(Device device, IScriptContext context) throws Exception
	{
		// Input: bundleName (the bundle's raw display text, e.g. "60 VC
		// Min+100MB for 1 day - R10"; blank/absent is valid -- everything just
		// comes back empty/false).
		// Outputs, all plain strings, "" meaning "not present in the bundle text":
		//   bundleHasVoiceMinutes / bundleHasSms / bundleHasData -- "true"/"false"
		//   bundleVoiceMinutes / bundleSmsCount / bundleDataMB / bundleValidityDays -- numbers as strings, or ""
		//   bundlePrice -- e.g. "10", or ""
		final String bundleDisplayText = context.get("bundleName");
		final String text = bundleDisplayText == null ? "" : bundleDisplayText;

		String voiceMinutes = firstGroup(VOICE_MINUTES, text);
		String smsCount = firstGroup(SMS_COUNT, text);
		String validityDays = firstGroup(VALIDITY_DAYS, text);

		String dataMB = "";
		Matcher dataMatcher = DATA_AMOUNT.matcher(text);
		if (dataMatcher.find())
		{
			double amount = Double.parseDouble(dataMatcher.group(1));
			boolean isGB = dataMatcher.group(2).equalsIgnoreCase("GB");
			dataMB = String.valueOf(Math.round(isGB ? amount * 1024 : amount));
		}

		String price = "";
		Matcher priceMatcher = PRICE.matcher(text);
		if (priceMatcher.find())
			price = priceMatcher.group(1);

		context.put("bundleVoiceMinutes", voiceMinutes);
		context.put("bundleSmsCount", smsCount);
		context.put("bundleDataMB", dataMB);
		context.put("bundleValidityDays", validityDays);
		context.put("bundlePrice", price);
		context.put("bundleHasVoiceMinutes", String.valueOf(!voiceMinutes.isEmpty()));
		context.put("bundleHasSms", String.valueOf(!smsCount.isEmpty()));
		context.put("bundleHasData", String.valueOf(!dataMB.isEmpty()));

		return SUCCESS();
	}

	private String firstGroup(Pattern pattern, String text)
	{
		Matcher matcher = pattern.matcher(text);
		return matcher.find() ? matcher.group(1) : "";
	}

}
