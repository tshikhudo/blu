package com.sigosInternal.vodapay.test_cases;


import com.mc.api.action.Action;
import com.mc.api.device.Device;
import com.mc.api.device.DeviceStatus;
import com.mc.api.device.exception.DeviceExecutionException;
import com.mc.api.device.exception.NoSuchDeviceException;
import com.mc.api.device.helper.LockHelper;
import com.mc.api.script.IScriptContext;
import com.mc.api.script.ScriptReturn;
import com.mc.api.script.exception.ScriptFailureException;
import com.mc.api.script.result.ScriptResult;
import com.mc.api.testcase.TestCase;
import com.mc.api.testcase.helper.TestCaseHelper;
import com.sigosInternal.vodapay.BundleComposition;

import java.util.Map;


public class VodaPaySocialBundleDepletion extends TestCase
{
  /*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

  public static final VodaPaySocialBundleDepletion instance = new VodaPaySocialBundleDepletion();

  private VodaPaySocialBundleDepletion()
  {
    super();
  }

  /**
   * @param args
   * @throws ScriptFailureException
   */
  public static void main(String[] args) throws ScriptFailureException, InterruptedException
  {
    TestCase testCase = VodaPaySocialBundleDepletion.instance;

    // create our execution helper
    TestCaseHelper helper = getHelperFromArgs(args);

    // get the devices
    Device primaryDevice = helper.getPrimaryDevice();

    Map<String, Device> secondaryDevices = helper.getSecondaryDevices();

    // lock all devices
    primaryDevice.lock();

    for (Device device : secondaryDevices.values())
    {
      if (device != null)
        device.lock();
    }

    try
    {
      ScriptResult result = testCase.execute(helper);
      System.out.println(testCase.getClass().getName() + " Result is: " + result);
    }
    finally
    {
      // unlock all devices
      primaryDevice.unlock();

      for (Device device : secondaryDevices.values())
      {
        if (device != null)
          device.unlock();
      }
    }
  }


  /*** END GENERATED CODE ***/

  // SIM multiplexer iteration -- CONFIRMED by decompiling scripting-api.jar (CFR,
  // pulled via github.com/tshikhudo/cloud), not just reading method signatures:
  // com.mc.test.common.NewDeviceInterface.lock(LockHelper) (the real implementation
  // behind Device.lock(LockHelper)) checks "simCardPosition >= 1 && simCardPosition
  // <= 16" and, when set, calls deviceIF.lockSimcardSync(simCardPosition) to switch
  // the EB4's SIM multiplexer to that position, then auto-triggers a device reboot
  // via ResetDeviceRequest if the currently-connected SIM doesn't already match. So
  // this genuinely CAN be driven from a Java loop in this TestCase, not only from
  // Studio's Acquire-Device dialog / Monitor scheduling -- correcting an earlier,
  // shallower read of this SDK that assumed it was scheduling-only.
  //
  // Two things decompiling caught that the method signatures alone did not:
  //  - new LockHelper(LockHelper.LockType.TEST) does NOT call resetToDefault(), so
  //    getWaitTime() defaults to 0 unless set explicitly -- that leaves the lock
  //    retry loop almost no time to wait out a busy SIM position. Call
  //    resetToDefault() (or setWaitTime directly) before setSimCardPosition().
  //  - a failed SIM connect returns DeviceStatus.DeviceErrorCode.SIMCARD_LOCK_FAILURE
  //    rather than throwing, so that's what a per-position skip should check for.
  // Still unconfirmed: whether a real-SIM SMS provisioning confirmation can be read
  // from inside a script (found no such API in scripting-api.jar) -- ask BLU support
  // if that's needed rather than assuming it's possible.

  private static final int MIN_SIM_CARD_POSITION = 1;
  private static final int MAX_SIM_CARD_POSITION = 16;
  private static final long SIM_LOCK_WAIT_MS = 30000;
  // the framework's SIM switch can trigger an async device reboot (fire-and-forget
  // ResetDeviceRequest) before the new SIM is actually usable -- give it time.
  private static final long POST_SIM_SWITCH_WAIT_MS = 30000;

  @Override
  protected ScriptReturn execute(Device device, IScriptContext context) throws ScriptFailureException, InterruptedException, DeviceExecutionException
  {
    // The primary way this test case is meant to be run: scheduled from the
    // SITE Web UI with a LIST of SIM numbers and a LIST of bundles to run
    // through in one batch, not one hardcoded number+bundle per script. Two
    // input shapes are supported:
    //   cellNumbers + bundleNames -- semicolon-separated, paired by position
    //                                (cellNumbers[i] buys bundleNames[i]), e.g.
    //                                cellNumbers = "0662401991;0743011101"
    //                                bundleNames = "60 MB for 1 HOUR;100MB for 1 day"
    //                                Lists must be the same length.
    //   cellNumber + bundleName   -- singular fallback for a single run (kept
    //                                for scripts/scheduling that only need one
    //                                pair, or for local testing); ignored if
    //                                the plural lists above are given.
    // The bundle's own display text ("60 VC Min+100MB for 1 day - R10") already
    // tells us its price and which resources it contains -- see
    // BundleComposition -- so there's no separate product-type/price lookup to
    // maintain per bundle. Everything else below is test-infrastructure
    // configuration, not something a tester supplies per run:
    //   mpin                    -- test SIM's VodaPay PIN (shared across the
    //                              batch; only used for a brand-new registration,
    //                              an already-registered number never needs it)
    //   callDestinationNumber   -- required only if a bundle includes voice minutes;
    //                              another test line to call, so cost/traffic stays internal
    //   videoUrl, watchSeconds  -- data-depletion step: a direct video link opened via
    //                              Device.OpenBrowser (confirmed real API), played for
    //                              watchSeconds -- see PlayYouTubeVideo's own class doc
    //   deviceCandidates        -- optional, comma-separated mcd ids, e.g. "25011,25012" --
    //                              both real devices in this Vodacom SX implementation
    //                              (SAMSUNG_ANDRD_GALAXY_S23: "left"/"right"), confirmed via
    //                              a live SITE Web UI trace on 2026-09-07 (25012 is the one
    //                              actually holding the +27662401991 SIM used earlier this
    //                              project). The first candidate DeviceStatus.isAvailable()
    //                              reports free gets used instead of whatever TestCaseHelper
    //                              handed us -- only 25011 is registered in project.xml's
    //                              own <devices> block so far, since 25012's real
    //                              package_instance_id isn't confirmed yet (don't add it
    //                              there on a guess -- a wrong value looks valid but
    //                              silently breaks device resolution in Studio).
    //   simCardPositions        -- optional, e.g. "1-4" to run the WHOLE batch once per
    //                              SIM position on the multiplexer; omit to leave the SIM
    //                              alone (this is a physical-SIM-slot rotation, independent
    //                              of the cellNumbers list above, which is about which
    //                              VodaPay accounts/numbers to exercise on whatever SIM is
    //                              already active)

    final Device selectedDevice = selectAvailableDevice(device, context.get("deviceCandidates"));

    try
    {
      final String simRange = context.get("simCardPositions");

      if (simRange == null || simRange.trim().isEmpty())
      {
        runJourneyForAllPairs(selectedDevice, context);
        return SUCCESS();
      }

      final int dash = simRange.indexOf('-');
      final int startPos = dash < 0 ? Integer.parseInt(simRange.trim()) : Integer.parseInt(simRange.substring(0, dash).trim());
      final int endPos = dash < 0 ? startPos : Integer.parseInt(simRange.substring(dash + 1).trim());

      for (int simCardPosition = Math.max(startPos, MIN_SIM_CARD_POSITION);
           simCardPosition <= Math.min(endPos, MAX_SIM_CARD_POSITION);
           simCardPosition++)
      {
        LockHelper lockHelper = new LockHelper(LockHelper.LockType.TEST);
        lockHelper.resetToDefault();
        lockHelper.setWaitTime(SIM_LOCK_WAIT_MS);
        lockHelper.setSimCardPosition(simCardPosition);

        DeviceStatus status = selectedDevice.lock(lockHelper);
        if (status.getErrorCode() == DeviceStatus.DeviceErrorCode.SIMCARD_LOCK_FAILURE)
        {
          System.out.println("SIM position " + simCardPosition + " unavailable, skipping: " + status.getStatusMessage());
          continue;
        }

        selectedDevice.serverWait(getCurrentContext(), POST_SIM_SWITCH_WAIT_MS);

        System.out.println("Running bundle-depletion batch on SIM position " + simCardPosition);
        runJourneyForAllPairs(selectedDevice, context);
      }

      return SUCCESS();
    }
    finally
    {
      // If deviceCandidates picked a different physical device than the one
      // TestCaseHelper/main() already locked, main()'s generated unlock() only
      // knows about its own primaryDevice and won't release this one -- do it here.
      // When no candidates were given, selectedDevice IS device, and main()'s own
      // primaryDevice.unlock() already covers it, so nothing extra happens here.
      if (selectedDevice != device)
        selectedDevice.unlock();
    }
  }

  /**
   * Splits cellNumbers/bundleNames into paired lists and runs the full journey
   * once per pair, one after another on the same device -- the shape a Web-UI
   * batch run actually needs (a tester picks several numbers and bundles to
   * exercise in one go, rather than scheduling one run per pair by hand).
   * Falls back to the singular cellNumber/bundleName parameters for a single
   * run when the plural lists aren't supplied.
   *
   * Each pair is wrapped in its own try/catch, same resilience pattern as the
   * SIM-position loop above -- one bad number or an unavailable bundle
   * shouldn't abort every other pair already queued in the same batch.
   */
  private void runJourneyForAllPairs(Device device, IScriptContext context) throws ScriptFailureException, InterruptedException, DeviceExecutionException
  {
    final String cellNumbersList = context.get("cellNumbers");
    final String bundleNamesList = context.get("bundleNames");

    if (cellNumbersList == null || cellNumbersList.trim().isEmpty())
    {
      // Singular fallback -- a single pair, using whatever runJourney already
      // reads directly off context ("cellNumber"/"bundleName").
      runJourney(device, context);
      return;
    }

    if (bundleNamesList == null || bundleNamesList.trim().isEmpty())
      throw new ScriptFailureException(getCurrentContext(), "cellNumbers was given but bundleNames was not -- both lists are required together");

    final String[] cellNumbers = splitList(cellNumbersList);
    final String[] bundleNames = splitList(bundleNamesList);

    if (cellNumbers.length != bundleNames.length)
      throw new ScriptFailureException(getCurrentContext(), "cellNumbers (" + cellNumbers.length + " entries) and bundleNames ("
          + bundleNames.length + " entries) must be the same length -- each cell number is paired with the bundle at the same position");

    System.out.println("Running bundle-depletion batch for " + cellNumbers.length + " (cellNumber, bundleName) pair(s).");

    int succeeded = 0;
    int failed = 0;
    for (int i = 0; i < cellNumbers.length; i++)
    {
      final String cellNumber = cellNumbers[i];
      final String bundleName = bundleNames[i];

      System.out.println("--- Pair " + (i + 1) + "/" + cellNumbers.length + ": " + cellNumber + " -> " + bundleName + " ---");
      context.put("cellNumber", cellNumber);
      context.put("bundleName", bundleName);

      try
      {
        runJourney(device, context);
        succeeded++;
      }
      catch (ScriptFailureException e)
      {
        failed++;
        System.out.println("Pair " + cellNumber + " -> " + bundleName + " failed: " + e.getMessage());
      }
    }

    System.out.println("Batch complete: " + succeeded + " succeeded, " + failed + " failed, out of " + cellNumbers.length + " pair(s).");
  }

  /** Splits a semicolon-separated parameter list, trimming each entry and dropping empty ones from stray trailing separators. */
  private String[] splitList(String value)
  {
    java.util.List<String> entries = new java.util.ArrayList<>();
    for (String token : value.split(";"))
    {
      String trimmed = token.trim();
      if (!trimmed.isEmpty())
        entries.add(trimmed);
    }
    return entries.toArray(new String[0]);
  }

  /**
   * Picks which physical device to run against from a candidate mcd list, instead of
   * always using whatever TestCaseHelper/main() already locked as primary. Verified
   * via javap: Device.get(int) is a public static factory, and DeviceStatus.isAvailable()
   * reports whether a device can be acquired -- both usable here independent of the
   * primary/secondary device slots main() already sets up.
   */
  private Device selectAvailableDevice(Device fallbackDevice, String candidateMcdList) throws ScriptFailureException
  {
    if (candidateMcdList == null || candidateMcdList.trim().isEmpty())
      return fallbackDevice;

    for (String token : candidateMcdList.split(","))
    {
      final int mcd;
      try
      {
        mcd = Integer.parseInt(token.trim());
      }
      catch (NumberFormatException e)
      {
        continue;
      }

      try
      {
        Device candidate = Device.get(mcd);
        if (!candidate.getDeviceStatus().isAvailable())
        {
          System.out.println("Device mcd=" + mcd + " not available, trying next candidate.");
          continue;
        }

        LockHelper lockHelper = new LockHelper(LockHelper.LockType.TEST);
        lockHelper.resetToDefault();
        DeviceStatus lockStatus = candidate.lock(lockHelper);
        if (!lockStatus.isLocked())
        {
          System.out.println("Device mcd=" + mcd + " could not be locked, trying next candidate.");
          continue;
        }

        System.out.println("Selected device mcd=" + mcd + " from candidate list.");
        return candidate;
      }
      catch (NoSuchDeviceException e)
      {
        System.out.println("No such device mcd=" + mcd + ", trying next candidate.");
      }
    }

    throw new ScriptFailureException(getCurrentContext(), "None of the candidate devices (" + candidateMcdList + ") were available.");
  }

  private void runJourney(Device device, IScriptContext context) throws ScriptFailureException, InterruptedException, DeviceExecutionException
  {
    final String bundleName = context.get("bundleName");
    final BundleComposition composition = BundleComposition.parse(bundleName == null ? "" : bundleName);
    System.out.println("Parsed " + bundleName + " -> " + composition);

    Action installVodaPay = Action.get("com.sigosInternal.vodapay.actions.applicationManagement.InstallVodaPayApp");
    Action launchVodaPay = Action.get("com.sigosInternal.vodapay.actions.applicationManagement.LaunchVodaPay");
    Action checkOrCreateProfile = Action.get("com.sigosInternal.vodapay.actions.bundleJourney.CheckOrCreateVodaPayProfile");
    Action checkWalletBalance = Action.get("com.sigosInternal.vodapay.actions.bundleJourney.CheckWalletBalance");
    Action checkDetailedBalanceRow = Action.get("com.sigosInternal.vodapay.actions.bundleJourney.CheckDetailedBalanceRow");
    Action purchaseBundle = Action.get("com.sigosInternal.vodapay.actions.bundleJourney.PurchaseSocialBundle");
    Action verifyProvisioned = Action.get("com.sigosInternal.vodapay.actions.bundleJourney.VerifyBundleProvisioned");
    Action verifySms = Action.get("com.sigosInternal.vodapay.actions.bundleJourney.VerifySmsReceived");
    Action launchYouTube = Action.get("com.sigosInternal.vodapay.actions.applicationManagement.LaunchYouTube");
    Action playVideo = Action.get("com.sigosInternal.vodapay.actions.bundleJourney.PlayYouTubeVideo");
    Action depleteVoiceMinutes = Action.get("com.sigosInternal.vodapay.actions.bundleJourney.DepleteVoiceMinutes");
    Action verifyDepleted = Action.get("com.sigosInternal.vodapay.actions.bundleJourney.VerifyBundleDepleted");

    // Only actually installs when the app isn't already on the device -- skip this
    // action in Studio (or leave it unregistered) once devices are pre-provisioned,
    // to avoid re-uploading + rebooting on every run.
    if (installVodaPay != null)
      device.execute(installVodaPay);

    if (launchVodaPay != null)
      device.execute(launchVodaPay);

    if (checkOrCreateProfile != null)
      device.execute(checkOrCreateProfile);

    // Balance before/after the purchase -- lets a test confirm the wallet actually
    // moved by the bundle's price, not just that the purchase screen said success.
    // Also independently useful: a "just check the balance, don't buy anything"
    // scenario can run CheckWalletBalance alone without ever calling
    // PurchaseSocialBundle.
    String balanceBefore = null;
    if (checkWalletBalance != null)
    {
      device.execute(checkWalletBalance);
      balanceBefore = context.get("walletBalance");
      System.out.println("Wallet balance before purchase: " + balanceBefore);
    }

    // Snapshot the relevant My Vodacom detailed-balance rows (Data/Voice/SMS,
    // whichever the bundle's composition covers) BEFORE the purchase. Since a
    // newly bought bundle's row name in "Detailed balances" isn't guaranteed to
    // exactly match its shop tile name (per the user), the reliable way to
    // identify the new bundle is diffing before/after row lists rather than
    // assuming a name -- see CheckDetailedBalanceRow's context.get
    // ("detailedBalanceRows_<category>") for the before/after strings.
    String detailedCategory = composition.hasData() ? "Data" : composition.hasVoiceMinutes() ? "Voice" : composition.hasSms() ? "SMS" : null;
    String detailedRowsBefore = null;
    if (checkDetailedBalanceRow != null && detailedCategory != null)
    {
      context.put("balanceCategory", detailedCategory);
      device.execute(checkDetailedBalanceRow);
      detailedRowsBefore = context.get("detailedBalanceRows_" + detailedCategory);
    }

    if (purchaseBundle != null)
      device.execute(purchaseBundle);

    if (checkWalletBalance != null)
    {
      device.execute(checkWalletBalance);
      String balanceAfter = context.get("walletBalance");
      System.out.println("Wallet balance after purchase: " + balanceAfter + " (was " + balanceBefore + ")");
    }

    if (checkDetailedBalanceRow != null && detailedCategory != null)
    {
      context.put("balanceCategory", detailedCategory);
      device.execute(checkDetailedBalanceRow);
      String detailedRowsAfter = context.get("detailedBalanceRows_" + detailedCategory);
      System.out.println("New " + detailedCategory + " row(s) after purchase: " + diffNewRows(detailedRowsBefore, detailedRowsAfter));
    }

    if (verifyProvisioned != null)
      device.execute(verifyProvisioned);

    // Provisioning SMS check: the bundle's own price (and, when present, its data
    // component) is what the confirmation SMS should mention -- reuses the same
    // composition already parsed from bundleName, no separate expected-text input.
    if (verifySms != null && composition.getPrice() != null)
    {
      context.put("expectedSmsText", "R" + composition.getPrice());
      device.execute(verifySms);
    }

    if (composition.hasData())
    {
      if (launchYouTube != null)
        device.execute(launchYouTube);
      if (playVideo != null)
        device.execute(playVideo);
    }

    if (composition.hasVoiceMinutes())
    {
      final String callDestination = context.get("callDestinationNumber");
      if (callDestination == null || callDestination.trim().isEmpty())
      {
        System.out.println(bundleName + " includes " + composition.getVoiceMinutes()
            + " voice minutes, but no callDestinationNumber was supplied -- skipping voice depletion.");
      }
      else if (depleteVoiceMinutes != null)
      {
        context.put("voiceMinutes", String.valueOf(composition.getVoiceMinutes()));
        device.execute(depleteVoiceMinutes);
      }
    }

    if (composition.hasSms())
    {
      // No SMS-sending depletion action exists yet -- device.sendSMS(SMSHelper) is a
      // real API in this SDK, but its recipient-targeting semantics weren't confirmed
      // (SMSHelper only exposes applicationID + messageText, no explicit recipient
      // field), so building this needs more digging before it can be trusted.
      System.out.println(bundleName + " includes " + composition.getSmsCount() + " SMS -- SMS depletion not yet implemented.");
    }

    if (verifyDepleted != null)
      device.execute(verifyDepleted);
  }

  /**
   * Compares two semicolon-joined "name=value" row strings from
   * CheckDetailedBalanceRow and returns the row(s) present in "after" but not
   * in "before" -- i.e. whatever bundle the purchase actually added, without
   * needing to already know its exact Detailed-balances row name (which isn't
   * guaranteed to match the shop tile's name).
   */
  private String diffNewRows(String before, String after)
  {
    if (after == null || after.isEmpty())
      return "(no rows read)";
    java.util.Set<String> beforeSet = new java.util.HashSet<>();
    if (before != null)
      for (String row : before.split(";"))
        beforeSet.add(row);

    StringBuilder added = new StringBuilder();
    for (String row : after.split(";"))
    {
      if (row.isEmpty() || beforeSet.contains(row))
        continue;
      if (added.length() > 0)
        added.append(", ");
      added.append(row);
    }
    return added.length() > 0 ? added.toString() : "(none -- no new row appeared)";
  }

}
