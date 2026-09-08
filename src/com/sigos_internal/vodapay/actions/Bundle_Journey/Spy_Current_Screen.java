package com.sigos_internal.vodapay.actions.Bundle_Journey;


import com.mc.api.action.Action;
import com.mc.api.device.Device;
import com.mc.api.device.ObjectLevelApi;
import com.mc.api.device.ObjectTree;
import com.mc.api.script.IScriptContext;
import com.mc.api.script.ScriptReturn;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;


/**
 * Diagnostic action, not part of the bundle-depletion journey: dumps the current
 * screen's object tree (element text/class/position) to the Script output console
 * and saves a screenshot to disk, so the real VodaPay/YouTube element names and
 * labels can be captured and relayed back for filling in the PLACEHOLDER values in
 * Purchase_Social_Bundle, Verify_Bundle_Provisioned, Verify_Bundle_Depleted, and
 * Play_Youtube_Video. Run this with the device sitting on whichever screen needs
 * to be inspected, then copy the printed XML (or send the saved PNG) back.
 */
public class Spy_Current_Screen extends Action
{
	/*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

	public static final Spy_Current_Screen instance = new Spy_Current_Screen();

	private Spy_Current_Screen()
	{
		super();
	}


	/*** END GENERATED CODE ***/

	@Override
	protected ScriptReturn run(Device device, IScriptContext context) throws Exception
	{
		// Optional parameter: label -- a short name to tag this screenshot with,
		// e.g. "vodapay_login" or "youtube_search_results".
		String label = context.get("label");
		if (label == null || label.trim().isEmpty())
			label = "screen";

		ObjectLevelApi api = device.getObjectLevelApi();
		ObjectTree root = api.getCurrentScreen();

		System.out.println("=== Spy_Current_Screen [" + label + "] object tree ===");
		System.out.println(root.toXmlString());
		System.out.println("=== end object tree ===");

		BufferedImage screenshot = device.getCurrentImage();
		File outFile = new File(System.getProperty("java.io.tmpdir"), "vodapay_spy_" + label + "_" + System.currentTimeMillis() + ".png");
		ImageIO.write(screenshot, "png", outFile);
		System.out.println("Screenshot saved to: " + outFile.getAbsolutePath());

		return SUCCESS();
	}

}
