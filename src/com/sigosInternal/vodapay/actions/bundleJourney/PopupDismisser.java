package com.sigosInternal.vodapay.actions.bundleJourney;

import com.mc.api.device.Device;
import com.mc.api.device.ObjectLevelApi;
import com.mc.api.device.ObjectTree;
import com.mc.api.script.IScriptContext;

import java.awt.Point;

/**
 * Best-effort dismissal of the marketing/tip popups VodaPay shows opportunistically
 * (a notification opt-in screen, "physical keyboard enabled" and "Support" tips,
 * a full-screen "VodaPay Club" promo, a one-time "For added security, you may be
 * asked for your CVV/Security Code" info screen on the Buy quick action's first
 * use, etc -- confirmed live 2026-09-06/07). These
 * aren't tied to a specific screen in the journey, so every action that navigates
 * VodaPay should call this after each screen transition rather than assume a
 * clean screen. Non-fatal: if nothing matches, this simply does nothing.
 *
 * Two distinct dismiss mechanisms are needed, catalogued separately:
 *  - Text-labelled dismiss buttons (KNOWN_DISMISS_LABELS) -- found and tapped by
 *    their own text, e.g. "GOT IT", "No thanks".
 *  - Full-screen promo overlays with an unlabelled "X" close icon (no text or
 *    content-desc on the icon itself, confirmed live on the "VodaPay Club" promo)
 *    -- these are instead detected by distinctive text elsewhere on the promo
 *    (KNOWN_PROMO_MARKERS), then dismissed by tapping the ImageView/ImageButton
 *    closest to the top-right of the screen, where every promo overlay's close
 *    icon has been positioned so far.
 */
final class PopupDismisser
{
	// TODO: both lists are only what's been seen live so far -- add to them as new
	// popups turn up rather than assuming either is exhaustive.
	private static final String[] KNOWN_DISMISS_LABELS = {
			"GOT IT", "Got it", "No thanks", "NO THANKS", "Later", "LATER", "Skip", "Not now", "X"
	};
	private static final String[] KNOWN_PROMO_MARKERS = {
			"VodaPay Club", "Unlock", "Red Hot"
	};
	// Close icons seen so far sit within roughly this many px of the top of a
	// 720x1559 screen -- scale if a different device resolution proves wrong.
	private static final int PROMO_CLOSE_ICON_MAX_Y = 200;

	private PopupDismisser() {}

	private static final long DISMISS_SETTLE_TIMEOUT_MS = 5000;

	static void dismissKnownPopups(Device device, IScriptContext context) throws Exception
	{
		ObjectLevelApi api = device.getObjectLevelApi();

		for (String label : KNOWN_DISMISS_LABELS)
		{
			ObjectTree[] matches = api.findObjectsByText(label);
			if (matches.length == 0)
				continue;

			tapCenter(device, matches[0]);
			// Wait for the dismissed label to actually go away rather than a fixed
			// settle time -- the popup's own close animation/transition duration
			// isn't consistent across popup types or devices.
			ScreenSync.waitWhileVisible(device, context, DISMISS_SETTLE_TIMEOUT_MS, label);
		}

		for (String marker : KNOWN_PROMO_MARKERS)
		{
			if (api.findObjectsByText(marker).length == 0)
				continue;

			ObjectTree closeIcon = findTopRightIcon(api);
			if (closeIcon == null)
				continue;

			tapCenter(device, closeIcon);
			ScreenSync.waitWhileVisible(device, context, DISMISS_SETTLE_TIMEOUT_MS, marker);
		}
	}

	/** The rightmost ImageView/ImageButton within PROMO_CLOSE_ICON_MAX_Y of the top of the screen. */
	private static ObjectTree findTopRightIcon(ObjectLevelApi api) throws Exception
	{
		ObjectTree best = null;
		for (String className : new String[]{"android.widget.ImageView", "android.widget.ImageButton"})
		{
			for (ObjectTree candidate : api.findObjectsByClassName(className))
			{
				if (candidate.getY() > PROMO_CLOSE_ICON_MAX_Y)
					continue;
				if (best == null || candidate.getX() > best.getX())
					best = candidate;
			}
		}
		return best;
	}

	private static void tapCenter(Device device, ObjectTree node) throws Exception
	{
		Point center = new Point(node.getX() + node.getWidth() / 2, node.getY() + node.getHeight() / 2);
		device.sendTouchClick(center);
	}
}
