package com.sigosInternal.vodapay.actions.bundleJourney;


import com.mc.api.action.Action;
import com.mc.api.device.Device;
import com.mc.api.script.IScriptContext;
import com.mc.api.script.ScriptReturn;

import java.util.HashSet;
import java.util.Set;


/**
 * SOFT assertion: if the bundle includes SMS (bundleHasSms), confirms a new
 * "Detailed balances" row appeared under the SMS category after the
 * purchase. See AssertDataProvisioned's class doc for the full explanation of
 * the soft-assertion design (never fails itself; records into
 * softAssertionLog/softAssertionFailureCount; ReportSoftAssertions is what
 * actually fails the TestCase, at the very end).
 */
public class AssertSmsProvisioned extends Action
{
	/*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

	public static final AssertSmsProvisioned instance = new AssertSmsProvisioned();

	private AssertSmsProvisioned()
	{
		super();
	}


	/*** END GENERATED CODE ***/

	@Override
	protected ScriptReturn run(Device device, IScriptContext context) throws Exception
	{
		if (!Boolean.parseBoolean(context.get("bundleHasSms")))
			return SUCCESS(); // bundle didn't include SMS -- nothing to check

		final String before = context.get("detailedBalanceRowsSmsBefore");
		final String after = context.get("detailedBalanceRows_SMS");

		boolean passed = hasNewRow(before, after);
		recordAssertion(context, "SMS bundle provisioned (new Detailed balances row under SMS)", passed);

		return SUCCESS();
	}

	private boolean hasNewRow(String before, String after)
	{
		if (after == null || after.isEmpty())
			return false;
		Set<String> beforeSet = new HashSet<>();
		if (before != null)
			for (String row : before.split(";"))
				beforeSet.add(row);
		for (String row : after.split(";"))
			if (!row.isEmpty() && !beforeSet.contains(row))
				return true;
		return false;
	}

	private void recordAssertion(IScriptContext context, String description, boolean passed)
	{
		String outcome = passed ? "PASS" : "FAIL";
		String entry = "[" + outcome + "] " + description;
		System.out.println(entry);

		String existingLog = context.get("softAssertionLog");
		context.put("softAssertionLog", (existingLog == null || existingLog.isEmpty() ? "" : existingLog + "\n") + entry);

		if (!passed)
		{
			String countStr = context.get("softAssertionFailureCount");
			int count = (countStr == null || countStr.isEmpty()) ? 0 : Integer.parseInt(countStr);
			context.put("softAssertionFailureCount", String.valueOf(count + 1));
		}
	}

}
