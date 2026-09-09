package com.sigosInternal.vodapay.actions.bundleJourney;


import com.mc.api.action.Action;
import com.mc.api.device.Device;
import com.mc.api.script.IScriptContext;
import com.mc.api.script.ScriptReturn;

import java.util.HashSet;
import java.util.Set;


/**
 * SOFT assertion: if the bundle includes a data component (bundleHasData),
 * confirms a new "Detailed balances" row appeared under the Data category
 * after the purchase -- comparing detailedBalanceRowsDataBefore (from
 * SnapshotBalancesBefore) against the current detailedBalanceRows_Data
 * (re-read after the purchase). A newly bought bundle's row name isn't
 * guaranteed to match its shop tile name (confirmed live), so diffing beats
 * assuming a name.
 *
 * "Soft" per the user's explicit request: this NEVER returns FAIL itself,
 * regardless of outcome -- it records PASS/FAIL into a shared log
 * (softAssertionLog/softAssertionFailureCount) and always returns SUCCESS(),
 * so the rest of the sequence keeps running even if this one check fails.
 * ReportSoftAssertions is the one step that turns an accumulated failure into
 * an actual TestCase failure, at the very end, after every other check has
 * had a chance to run. If the bundle doesn't include data at all, this is a
 * no-op (nothing to assert) rather than a false failure.
 */
public class AssertDataProvisioned extends Action
{
	/*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

	public static final AssertDataProvisioned instance = new AssertDataProvisioned();

	private AssertDataProvisioned()
	{
		super();
	}


	/*** END GENERATED CODE ***/

	@Override
	protected ScriptReturn run(Device device, IScriptContext context) throws Exception
	{
		if (!Boolean.parseBoolean(context.get("bundleHasData")))
			return SUCCESS(); // bundle didn't include data -- nothing to check

		final String before = context.get("detailedBalanceRowsDataBefore");
		final String after = context.get("detailedBalanceRows_Data");

		boolean passed = hasNewRow(before, after);
		recordAssertion(context, "Data bundle provisioned (new Detailed balances row under Data)", passed);

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

	/**
	 * Appends this outcome to the shared soft-assertion log and, on failure,
	 * increments the shared failure counter -- NEVER throws/fails this Action
	 * itself. Duplicated identically in each Assert* action rather than shared
	 * via a plain utility class, since Studio has no support for those (see
	 * WaitForText's class doc) and this is small enough that duplicating it is
	 * simpler and safer than inventing another Action just to hold it.
	 */
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
