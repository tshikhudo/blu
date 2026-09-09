package com.sigosInternal.vodapay.actions.bundleJourney;


import com.mc.api.action.Action;
import com.mc.api.device.Device;
import com.mc.api.script.IScriptContext;
import com.mc.api.script.ScriptReturn;


/**
 * Preserves every "before" balance reading under its own fixed key, so the
 * later "after" readings (from a second call to CheckWalletBalance/
 * CheckAllVodacomBalances/CheckDetailedBalanceRow later in the same sequence)
 * don't overwrite them first. Covers all four possible bundle resources
 * (a bundle can be a single one or any combination, per the user):
 *  - Airtime: balance_anytime_airtime (from CheckAllVodacomBalances)
 *  - Data: detailedBalanceRows_Data (from CheckDetailedBalanceRow, category Data)
 *  - Voice: detailedBalanceRows_Voice
 *  - SMS: detailedBalanceRows_SMS
 * Also preserves walletBalance (the separate VodaPay Entry Wallet, distinct
 * from real SIM airtime) in case a run pays via Wallet instead of Airtime.
 *
 * Exists specifically for a Visual/declarative TestCase built in Studio's own
 * flow editor: a hand-coded Java TestCase can just stash "before" values in
 * local variables, but a purely declarative sequence of steps only has the
 * shared IScriptContext to work with -- see WaitForText's class doc for the
 * fuller explanation of why every shared helper here is its own Action.
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
		copy(context, "walletBalance", "walletBalanceBefore");
		copy(context, "balance_anytime_airtime", "airtimeBalanceBefore");
		copy(context, "detailedBalanceRows_Data", "detailedBalanceRowsDataBefore");
		copy(context, "detailedBalanceRows_Voice", "detailedBalanceRowsVoiceBefore");
		copy(context, "detailedBalanceRows_SMS", "detailedBalanceRowsSmsBefore");

		return SUCCESS();
	}

	/** Missing values are copied as "" rather than skipped -- a legitimately empty "before" state (e.g. zero existing rows) is still a valid snapshot. */
	private void copy(IScriptContext context, String sourceKey, String destKey)
	{
		String value = context.get(sourceKey);
		context.put(destKey, value == null ? "" : value);
	}

}
