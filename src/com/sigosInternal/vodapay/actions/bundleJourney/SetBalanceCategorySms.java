package com.sigosInternal.vodapay.actions.bundleJourney;


import com.mc.api.action.Action;
import com.mc.api.device.Device;
import com.mc.api.script.IScriptContext;
import com.mc.api.script.ScriptReturn;


/** Sets balanceCategory to "SMS" -- see SetBalanceCategoryData's class doc for why this exists. */
public class SetBalanceCategorySms extends Action
{
	/*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

	public static final SetBalanceCategorySms instance = new SetBalanceCategorySms();

	private SetBalanceCategorySms()
	{
		super();
	}


	/*** END GENERATED CODE ***/

	@Override
	protected ScriptReturn run(Device device, IScriptContext context) throws Exception
	{
		context.put("balanceCategory", "SMS");
		return SUCCESS();
	}

}
