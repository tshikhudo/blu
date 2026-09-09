package com.sigosInternal.vodapay.actions.bundleJourney;


import com.mc.api.action.Action;
import com.mc.api.device.Device;
import com.mc.api.script.IScriptContext;
import com.mc.api.script.ScriptReturn;


/** Sets balanceCategory to "Voice" -- see SetBalanceCategoryData's class doc for why this exists. */
public class SetBalanceCategoryVoice extends Action
{
	/*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

	public static final SetBalanceCategoryVoice instance = new SetBalanceCategoryVoice();

	private SetBalanceCategoryVoice()
	{
		super();
	}


	/*** END GENERATED CODE ***/

	@Override
	protected ScriptReturn run(Device device, IScriptContext context) throws Exception
	{
		context.put("balanceCategory", "Voice");
		return SUCCESS();
	}

}
