package com.sigosInternal.vodapay.actions.bundleJourney;


import com.mc.api.device.Device;
import com.mc.api.device.ObjectLevelApi;
import com.mc.api.script.IScriptContext;

/**
 * Polls for a screen's marker text instead of sleeping a fixed duration.
 * VodaPay's screens (mostly WebView-rendered, network-dependent) load in
 * wildly inconsistent times -- a fixed Thread.sleep either wastes time
 * waiting out a fast load, or (worse, and harder to notice until a run fails
 * intermittently) moves on to the next step before a slow one has actually
 * rendered. This generalizes the poll loop already proven live in
 * VerifyBundleProvisioned/VerifySmsReceived into one shared helper so
 * every navigation step in the journey gets the same smart wait instead of
 * a guessed sleep duration.
 *
 * This matters more once one run works through a whole list of (cellNumber,
 * bundleName) pairs from the Web UI: a fixed per-step sleep tuned for one
 * account/bundle/network condition is exactly the kind of thing that becomes
 * flaky across a dozen different accounts and bundles in the same batch.
 */
public final class ScreenSync
{
	private static final long POLL_INTERVAL_MS = 300;

	private ScreenSync() {}

	/**
	 * Polls until any of the given candidate texts appears on screen, or
	 * timeoutMs elapses. Returns whichever candidate matched first, or null on
	 * timeout. Passing several candidates lets a caller wait for "whichever of
	 * these next screens actually shows up" (e.g. an error screen OR the happy
	 * path) instead of only being able to detect one specific outcome.
	 */
	public static String waitForAny(Device device, IScriptContext context, long timeoutMs, String... candidateTexts) throws Exception
	{
		ObjectLevelApi api = device.getObjectLevelApi();
		final long deadline = System.currentTimeMillis() + timeoutMs;

		while (true)
		{
			for (String candidate : candidateTexts)
			{
				if (candidate != null && api.findObjectsByText(candidate).length > 0)
					return candidate;
			}

			if (System.currentTimeMillis() >= deadline)
				return null;

			device.serverWait(context, Math.min(POLL_INTERVAL_MS, deadline - System.currentTimeMillis()));
		}
	}

	/** Polls until the given text appears on screen, or timeoutMs elapses. */
	public static boolean waitFor(Device device, IScriptContext context, long timeoutMs, String text) throws Exception
	{
		return waitForAny(device, context, timeoutMs, text) != null;
	}

	/**
	 * Polls until the given text DISAPPEARS from screen (e.g. a loading
	 * spinner's own label), or timeoutMs elapses. Returns true once it's gone,
	 * false if it was still present when the timeout hit.
	 */
	public static boolean waitWhileVisible(Device device, IScriptContext context, long timeoutMs, String text) throws Exception
	{
		ObjectLevelApi api = device.getObjectLevelApi();
		final long deadline = System.currentTimeMillis() + timeoutMs;

		while (api.findObjectsByText(text).length > 0)
		{
			if (System.currentTimeMillis() >= deadline)
				return false;
			device.serverWait(context, Math.min(POLL_INTERVAL_MS, deadline - System.currentTimeMillis()));
		}
		return true;
	}

	/** Same as {@link #waitFor}, but matches by regex (via ObjectLevelApi.findObjectsByRegex) for text that isn't a fixed literal, e.g. a masked phone number. */
	public static boolean waitForRegex(Device device, IScriptContext context, long timeoutMs, String regex) throws Exception
	{
		ObjectLevelApi api = device.getObjectLevelApi();
		final long deadline = System.currentTimeMillis() + timeoutMs;

		while (true)
		{
			if (api.findObjectsByRegex(regex).length > 0)
				return true;
			if (System.currentTimeMillis() >= deadline)
				return false;
			device.serverWait(context, Math.min(POLL_INTERVAL_MS, deadline - System.currentTimeMillis()));
		}
	}
}
