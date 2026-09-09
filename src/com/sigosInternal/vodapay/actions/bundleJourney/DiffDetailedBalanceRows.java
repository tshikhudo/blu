package com.sigosInternal.vodapay.actions.bundleJourney;


import com.mc.api.action.Action;
import com.mc.api.device.Device;
import com.mc.api.script.IScriptContext;
import com.mc.api.script.ScriptReturn;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Compares detailedBalanceRowsBefore (written by SnapshotBalancesBefore) against
 * the LATEST detailedBalanceRowsSnapshot (written by a second, later call to
 * CheckDetailedBalanceRow) and reports the row(s) present "after" but not
 * "before" -- the actual provisioning confirmation for a purchase, since a
 * newly bought bundle's Detailed-balances row name isn't guaranteed to match
 * its shop tile name (confirmed live), so diffing beats assuming a name.
 * FAILS if nothing new appeared, since that means the bundle didn't actually
 * provision.
 *
 * Uses fixed, hardcoded context keys rather than configurable parameters --
 * see SnapshotBalancesBefore's class doc for why, in this project's Visual/
 * declarative TestCase style. Was private helper logic duplicated in both
 * hand-coded TestCases (VodaPayBuyJourney/VodaPaySocialBundleDepletion) --
 * pulled out as its own Action so the declarative style gets the same check.
 */
public class DiffDetailedBalanceRows extends Action
{
	/*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

	public static final DiffDetailedBalanceRows instance = new DiffDetailedBalanceRows();

	private DiffDetailedBalanceRows()
	{
		super();
	}


	/*** END GENERATED CODE ***/

	@Override
	protected ScriptReturn run(Device device, IScriptContext context) throws Exception
	{
		// Reads detailedBalanceRowsBefore and detailedBalanceRowsSnapshot.
		// Writes newDetailedBalanceRows -- the newly-added row(s), comma-joined.
		final String before = context.get("detailedBalanceRowsBefore");
		final String after = context.get("detailedBalanceRowsSnapshot");

		if (after == null || after.isEmpty())
		{
			context.put("newDetailedBalanceRows", "");
			return fail("No rows were read for the \"after\" snapshot -- cannot confirm provisioning");
		}

		Set<String> beforeSet = new HashSet<>();
		if (before != null)
			for (String row : before.split(";"))
				beforeSet.add(row);

		List<String> added = new ArrayList<>();
		for (String row : after.split(";"))
		{
			if (row.isEmpty() || beforeSet.contains(row))
				continue;
			added.add(row);
		}

		if (added.isEmpty())
		{
			context.put("newDetailedBalanceRows", "");
			return fail("No new Detailed-balances row appeared after the purchase -- it did not provision");
		}

		String joined = String.join(", ", added);
		context.put("newDetailedBalanceRows", joined);
		System.out.println("New row(s) confirming provisioning: " + joined);

		return SUCCESS();
	}

	private ScriptReturn fail(String message)
	{
		return new ScriptReturn(getCurrentContext(), ScriptReturn.ScriptReturnCode.FAIL, message);
	}

}
