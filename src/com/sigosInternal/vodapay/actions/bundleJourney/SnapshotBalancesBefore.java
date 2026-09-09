package com.sigosInternal.vodapay.actions.bundleJourney;


import com.mc.api.action.Action;
import com.mc.api.device.Device;
import com.mc.api.script.IScriptContext;
import com.mc.api.script.ScriptReturn;


/**
 * Preserves the "before" wallet balance and Detailed-balances row snapshot
 * under their own fixed keys, so the later "after" readings (from a second
 * call to CheckWalletBalance/CheckDetailedBalanceRow later in the same
 * sequence) don't overwrite them first.
 *
 * Exists specifically for a Visual/declarative TestCase built in Studio's own
 * flow editor (e.g. a golden-path buy journey): a hand-coded Java TestCase
 * can just stash the "before" value in a local variable before calling the
 * same action again, but a purely declarative sequence of steps has no local
 * variables -- only the shared IScriptContext, and no confirmed way to set a
 * literal per-node parameter on a step in that editor (the one example
 * exported from it shows zero such configuration on any node). So this uses
 * fixed, hardcoded key names on both ends rather than a generic/configurable
 * copy, to avoid needing any node-level configuration at all -- every step in
 * the visual flow just runs as-is, reading/writing well-known context keys.
 */
public class SnapshotBalancesBefore extends Action
{
	/*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

	public static final SnapshotBalancesBefore instance = new SnapshotBalancesBefore();

	private SnapshotBalancesBefore()
	{
		super();
	}


	/*** END GENERATED CODE ***/

	@Override
	protected ScriptReturn run(Device device, IScriptContext context) throws Exception
	{
		// Reads walletBalance (from CheckWalletBalance) and
		// detailedBalanceRowsSnapshot (from CheckDetailedBalanceRow), writes
		// walletBalanceBefore and detailedBalanceRowsBefore. Missing values are
		// copied as "" rather than failing -- a legitimately empty "before" state
		// (e.g. a category with zero existing rows) is still a valid snapshot.
		String walletBalance = context.get("walletBalance");
		context.put("walletBalanceBefore", walletBalance == null ? "" : walletBalance);

		String detailedRows = context.get("detailedBalanceRowsSnapshot");
		context.put("detailedBalanceRowsBefore", detailedRows == null ? "" : detailedRows);

		return SUCCESS();
	}

}
