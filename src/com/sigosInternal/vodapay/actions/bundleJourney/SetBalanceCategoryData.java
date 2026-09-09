package com.sigosInternal.vodapay.actions.bundleJourney;


import com.mc.api.action.Action;
import com.mc.api.device.Device;
import com.mc.api.script.IScriptContext;
import com.mc.api.script.ScriptReturn;


/**
 * Sets balanceCategory to "Data" before calling CheckDetailedBalanceRow. A
 * composite bundle can include several resources at once (per the user), so
 * a golden-path buy journey needs to check ALL THREE Detailed-balances
 * categories unconditionally, not just one picked by priority -- these three
 * trivial, zero-input setter Actions (see also ...Voice/...Sms) exist purely
 * so a purely linear/declarative Visual TestCase can hardcode which category
 * CheckDetailedBalanceRow reads next, since there's no confirmed way to set a
 * literal parameter value on a step in Studio's flow editor.
 */
public class SetBalanceCategoryData extends Action
{
	/*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

	public static final SetBalanceCategoryData instance = new SetBalanceCategoryData();

	private SetBalanceCategoryData()
	{
		super();
	}


	/*** END GENERATED CODE ***/

	@Override
	protected ScriptReturn run(Device device, IScriptContext context) throws Exception
	{
		context.put("balanceCategory", "Data");
		return SUCCESS();
	}

}
