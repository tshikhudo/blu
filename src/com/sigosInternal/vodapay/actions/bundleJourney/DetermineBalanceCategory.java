package com.sigosInternal.vodapay.actions.bundleJourney;


import com.mc.api.action.Action;
import com.mc.api.device.Device;
import com.mc.api.script.IScriptContext;
import com.mc.api.script.ScriptReturn;


/**
 * Picks which Detailed-balances tab (Data/Voice/SMS) a bundle belongs to,
 * from ParseBundleComposition's own output (bundleHasData/bundleHasVoiceMinutes/
 * bundleHasSms). Exists as its own Action -- rather than a Branch node with a
 * condition per flag -- specifically so a Visual/declarative TestCase (built
 * in Studio's own flow editor, e.g. BuyData) can stay a plain linear sequence
 * of steps instead of needing a working Branch/Condition setup, which hasn't
 * been confirmed against Studio's real schema (only ever seen one EMPTY,
 * unconfigured Branch node exported, never a working one). A hand-coded Java
 * TestCase can still just do the equivalent ternary directly if it prefers --
 * this Action is for the declarative style, not a replacement requirement.
 */
public class DetermineBalanceCategory extends Action
{
	/*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

	public static final DetermineBalanceCategory instance = new DetermineBalanceCategory();

	private DetermineBalanceCategory()
	{
		super();
	}


	/*** END GENERATED CODE ***/

	@Override
	protected ScriptReturn run(Device device, IScriptContext context) throws Exception
	{
		// Input: bundleHasData / bundleHasVoiceMinutes / bundleHasSms (from
		// ParseBundleComposition), each "true"/"false".
		// Output: balanceCategory -- "Data", "Voice", "SMS", or "" if none of the
		// three flags is true. Left empty rather than failed here -- whatever
		// downstream step actually needs a category (e.g. CheckDetailedBalanceRow)
		// already fails with its own clear message on an unrecognised/blank one,
		// so there's no value in duplicating that validation here.
		final boolean hasData = Boolean.parseBoolean(context.get("bundleHasData"));
		final boolean hasVoiceMinutes = Boolean.parseBoolean(context.get("bundleHasVoiceMinutes"));
		final boolean hasSms = Boolean.parseBoolean(context.get("bundleHasSms"));

		String category = hasData ? "Data" : hasVoiceMinutes ? "Voice" : hasSms ? "SMS" : "";
		context.put("balanceCategory", category);

		return SUCCESS();
	}

}
