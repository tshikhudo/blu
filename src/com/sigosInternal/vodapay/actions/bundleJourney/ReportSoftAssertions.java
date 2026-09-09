package com.sigosInternal.vodapay.actions.bundleJourney;


import com.mc.api.action.Action;
import com.mc.api.device.Device;
import com.mc.api.script.IScriptContext;
import com.mc.api.script.ScriptReturn;


/**
 * Prints the full accumulated soft-assertion log (see the Assert* actions:
 * AssertDataProvisioned/AssertVoiceProvisioned/AssertSmsProvisioned/
 * AssertAirtimeDeducted) and turns any recorded failure into an actual
 * TestCase failure -- but only here, at the very end of the sequence. Every
 * individual assertion runs regardless of whether an earlier one failed
 * (that's the "soft" part, matching the AssertJ SoftAssertions pattern used
 * in the team's own earlier scripts, though this project doesn't have that
 * library available -- see [[project-vodapay-bundle-testing]] in memory for
 * why this was built from scratch instead of depending on it). This should
 * be the LAST step in any sequence that uses the Assert* actions.
 */
public class ReportSoftAssertions extends Action
{
	/*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

	public static final ReportSoftAssertions instance = new ReportSoftAssertions();

	private ReportSoftAssertions()
	{
		super();
	}


	/*** END GENERATED CODE ***/

	@Override
	protected ScriptReturn run(Device device, IScriptContext context) throws Exception
	{
		final String log = context.get("softAssertionLog");
		final String countStr = context.get("softAssertionFailureCount");
		final int failureCount = (countStr == null || countStr.isEmpty()) ? 0 : Integer.parseInt(countStr);

		System.out.println("=== Soft assertion summary ===");
		System.out.println(log == null || log.isEmpty() ? "(no assertions ran)" : log);

		if (failureCount > 0)
			return new ScriptReturn(getCurrentContext(), ScriptReturn.ScriptReturnCode.FAIL,
					failureCount + " assertion(s) failed -- see the log above for details");

		return SUCCESS();
	}

}
