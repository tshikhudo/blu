package com.sigos_internal.vodapay;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a VodaPay bundle's display text into its component resources. VodaPay
 * bundles are frequently composite -- e.g. "60 VC Min+100MB for 1 day - R10" is
 * voice minutes AND data in one product -- so a single purchased bundle can need
 * more than one depletion action run against it (calls for the minutes, streaming
 * for the data, etc). Parsing the display text directly means the test only needs
 * cellNumber + bundleName as input; no separate product-type lookup table is
 * required, since the bundle name the team already gives testers is itself the
 * full description (as shown by the team's own examples: "30 VC Min + 1GB",
 * "60 VC Min+100MB for 1 day - R10", "100 VC Min+200MB for 1 day - R15").
 */
public final class BundleComposition
{
	private static final Pattern VOICE_MINUTES = Pattern.compile("(\\d+)\\s*VC\\s*Min", Pattern.CASE_INSENSITIVE);
	private static final Pattern SMS_COUNT = Pattern.compile("(\\d+)\\s*SMS", Pattern.CASE_INSENSITIVE);
	private static final Pattern DATA_AMOUNT = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(GB|MB)", Pattern.CASE_INSENSITIVE);
	private static final Pattern VALIDITY_DAYS = Pattern.compile("(\\d+)\\s*day", Pattern.CASE_INSENSITIVE);
	private static final Pattern PRICE = Pattern.compile("R\\s*(\\d+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE);

	private final Integer voiceMinutes;
	private final Integer smsCount;
	private final Integer dataMB;
	private final Integer validityDays;
	private final String price;

	private BundleComposition(Integer voiceMinutes, Integer smsCount, Integer dataMB, Integer validityDays, String price)
	{
		this.voiceMinutes = voiceMinutes;
		this.smsCount = smsCount;
		this.dataMB = dataMB;
		this.validityDays = validityDays;
		this.price = price;
	}

	public static BundleComposition parse(String bundleDisplayText)
	{
		Integer voiceMinutes = firstGroupAsInt(VOICE_MINUTES, bundleDisplayText);
		Integer smsCount = firstGroupAsInt(SMS_COUNT, bundleDisplayText);
		Integer validityDays = firstGroupAsInt(VALIDITY_DAYS, bundleDisplayText);

		Integer dataMB = null;
		Matcher dataMatcher = DATA_AMOUNT.matcher(bundleDisplayText);
		if (dataMatcher.find())
		{
			double amount = Double.parseDouble(dataMatcher.group(1));
			boolean isGB = dataMatcher.group(2).equalsIgnoreCase("GB");
			dataMB = (int) Math.round(isGB ? amount * 1024 : amount);
		}

		String price = null;
		Matcher priceMatcher = PRICE.matcher(bundleDisplayText);
		if (priceMatcher.find())
			price = priceMatcher.group(1);

		return new BundleComposition(voiceMinutes, smsCount, dataMB, validityDays, price);
	}

	private static Integer firstGroupAsInt(Pattern pattern, String text)
	{
		Matcher matcher = pattern.matcher(text);
		return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
	}

	public boolean hasVoiceMinutes() { return voiceMinutes != null; }
	public boolean hasSms() { return smsCount != null; }
	public boolean hasData() { return dataMB != null; }

	public Integer getVoiceMinutes() { return voiceMinutes; }
	public Integer getSmsCount() { return smsCount; }
	public Integer getDataMB() { return dataMB; }
	public Integer getValidityDays() { return validityDays; }
	public String getPrice() { return price; }

	@Override
	public String toString()
	{
		return "BundleComposition{voiceMinutes=" + voiceMinutes + ", smsCount=" + smsCount
				+ ", dataMB=" + dataMB + ", validityDays=" + validityDays + ", price=" + price + "}";
	}
}
