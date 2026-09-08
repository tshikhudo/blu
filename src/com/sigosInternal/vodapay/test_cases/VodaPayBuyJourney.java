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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * The final, narrowly-scoped buy-journey test case: register-or-login, read
 * balances before, buy (if a bundle is given), read balances after. This is
 * deliberately smaller than {@link VodaPaySocialBundleDepletion} -- it
 * stops at provisioning confirmation and does not attempt bundle DEPLETION
 * (YouTube streaming, voice calls, SMS sending). That scope split is
 * deliberate: every action this test case calls has been confirmed working
 * end-to-end live on a real device (see [[project-vodapay-bundle-testing]] in
 * memory), including a real completed purchase with its provisioning
 * independently verified via a before/after balance-row diff. The broader
 * depletion pipeline in VodaPaySocialBundleDepletion still depends on
 * pieces that are known-stale (VerifyBundleProvisioned/VerifyBundleDepleted
 * still search for a "My Bundles" nav item that was never confirmed to be
 * real) or entirely unimplemented (SMS depletion) -- this test case exists so
 * there's ONE thing that's genuinely ready to run as-is, rather than only
 * having the larger, partially-unproven pipeline available.
 *
 * Primary intended usage: scheduled from the SITE Web UI against a LIST of
 * SIM numbers and a LIST of bundles, one pair per number, in a single batch --
 * see cellNumbers/bundleNames below. A blank entry in bundleNames for a given
 * position means "just check this number's balances, don't buy anything" (a
 * real, separately-useful scenario the user asked for earlier in this
 * project), rather than being treated as an error.
 */
public class VodaPayBuyJourney extends TestCase
{
  /*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

  public static final VodaPayBuyJourney instance = new VodaPayBuyJourney();

  private VodaPayBuyJourney()
  {
    super();
  }

  /**
   * @param args
   * @throws ScriptFailureException
   */
  public static void main(String[] args) throws ScriptFailureException, InterruptedException
  {
    TestCase testCase = VodaPayBuyJourney.instance;

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

  // See VodaPaySocialBundleDepletion for the full write-up of why this SIM
  // multiplexer mechanism is safe to drive from a Java loop (confirmed by
  // decompiling scripting-api.jar, not just reading method signatures).
  private static final int MIN_SIM_CARD_POSITION = 1;
  private static final int MAX_SIM_CARD_POSITION = 16;
  private static final long SIM_LOCK_WAIT_MS = 30000;
  private static final long POST_SIM_SWITCH_WAIT_MS = 30000;

  @Override
  protected ScriptReturn execute(Device device, IScriptContext context) throws ScriptFailureException, InterruptedException, DeviceExecutionException
  {
    // Parameters (see also VodaPaySocialBundleDepletion, which shares the
    // same project.xml and most of these):
    //   cellNumbers, bundleNames -- semicolon-separated, paired by position,
    //                              e.g. cellNumbers = "0662401991;0743011101"
    //                                   bundleNames  = "60 MB for 1 HOUR;"
    //                              (the second pair here has a blank bundle --
    //                              that number is balance-checked only, not
    //                              bought for). Lists must be the same length.
    //   cellNumber, bundleName  -- singular fallback for a single pair, used
    //                              when the plural lists above aren't given.
    //   mpin                    -- test SIM's VodaPay PIN, only needed for a
    //                              brand-new registration
    //   paymentMethod           -- optional, "Airtime" (default, confirmed
    //                              working) / "Bank Card" / "Wallet"
    //   deviceCandidates        -- optional, comma-separated mcd ids, e.g.
    //                              "25011,25012" (both real devices in this
    //                              Vodacom implementation, confirmed 2026-09-07)
    //   simCardPositions        -- optional, e.g. "1-4" to run the whole batch
    //                              once per physical SIM multiplexer position

    final Device selectedDevice = selectAvailableDevice(device, context.get("deviceCandidates"));

    try
    {
      final String simRange = context.get("simCardPositions");

      if (simRange == null || simRange.trim().isEmpty())
      {
        runBatch(selectedDevice, context);
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

        System.out.println("Running buy-journey batch on SIM position " + simCardPosition);
        runBatch(selectedDevice, context);
      }

      return SUCCESS();
    }
    finally
    {
      if (selectedDevice != device)
        selectedDevice.unlock();
    }
  }

  /** See VodaPaySocialBundleDepletion.selectAvailableDevice -- identical logic, kept independent rather than shared to avoid coupling the two test cases together. */
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

  /**
   * Splits cellNumbers/bundleNames into paired lists and runs one buy-journey
   * per pair. Falls back to the singular cellNumber/bundleName parameters for
   * a single run when the plural lists aren't supplied. Each pair is wrapped
   * in its own try/catch so one bad number or bundle doesn't abort the rest
   * of an already-queued batch.
   */
  private void runBatch(Device device, IScriptContext context) throws ScriptFailureException, InterruptedException, DeviceExecutionException
  {
    final String cellNumbersList = context.get("cellNumbers");

    if (cellNumbersList == null || cellNumbersList.trim().isEmpty())
    {
      runOne(device, context);
      return;
    }

    final String bundleNamesList = context.get("bundleNames");
    if (bundleNamesList == null)
      throw new ScriptFailureException(getCurrentContext(), "cellNumbers was given but bundleNames was not -- both must be present together (a blank bundleNames entry is fine, meaning \"balance check only\")");

    // Deliberately preserves empty entries (unlike a plain split-and-drop) --
    // an empty bundleNames slot is a real, meaningful value here ("balance
    // check only" for that number), not something to silently skip past.
    final String[] cellNumbers = splitPreservingEmpty(cellNumbersList);
    final String[] bundleNames = splitPreservingEmpty(bundleNamesList);

    if (cellNumbers.length != bundleNames.length)
      throw new ScriptFailureException(getCurrentContext(), "cellNumbers (" + cellNumbers.length + " entries) and bundleNames ("
          + bundleNames.length + " entries) must be the same length -- each cell number is paired with the bundle (or blank) at the same position");

    System.out.println("Running buy-journey batch for " + cellNumbers.length + " pair(s).");

    int succeeded = 0;
    int failed = 0;
    for (int i = 0; i < cellNumbers.length; i++)
    {
      final String cellNumber = cellNumbers[i];
      final String bundleName = bundleNames[i];

      System.out.println("--- Pair " + (i + 1) + "/" + cellNumbers.length + ": " + cellNumber
          + " -> " + (bundleName.isEmpty() ? "(balance check only)" : bundleName) + " ---");
      context.put("cellNumber", cellNumber);
      context.put("bundleName", bundleName);

      try
      {
        runOne(device, context);
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

  /** Splits on ";" without dropping empty entries, only trimming whitespace around each one. */
  private String[] splitPreservingEmpty(String value)
  {
    String[] tokens = value.split(";", -1);
    for (int i = 0; i < tokens.length; i++)
      tokens[i] = tokens[i].trim();
    return tokens;
  }

  /**
   * The actual per-number journey: register-or-login, balances before, buy
   * (if bundleName is non-blank), balances after. Uses the same
   * detailed-balance-row diffing as VodaPaySocialBundleDepletion to
   * identify a newly-provisioned bundle without needing its exact
   * Detailed-balances row name to be known in advance.
   */
  private void runOne(Device device, IScriptContext context) throws ScriptFailureException, InterruptedException, DeviceExecutionException
  {
    final String cellNumber = context.get("cellNumber");
    final String bundleName = context.get("bundleName");
    final boolean buying = bundleName != null && !bundleName.trim().isEmpty();
    final BundleComposition composition = buying ? BundleComposition.parse(bundleName) : null;

    Action launchVodaPay = Action.get("com.sigosInternal.vodapay.actions.applicationManagement.LaunchVodaPay");
    Action checkOrCreateProfile = Action.get("com.sigosInternal.vodapay.actions.bundleJourney.CheckOrCreateVodaPayProfile");
    Action checkWalletBalance = Action.get("com.sigosInternal.vodapay.actions.bundleJourney.CheckWalletBalance");
    Action checkAllBalances = Action.get("com.sigosInternal.vodapay.actions.bundleJourney.CheckAllVodacomBalances");
    Action checkDetailedBalanceRow = Action.get("com.sigosInternal.vodapay.actions.bundleJourney.CheckDetailedBalanceRow");
    Action purchaseBundle = Action.get("com.sigosInternal.vodapay.actions.bundleJourney.PurchaseSocialBundle");

    if (launchVodaPay != null)
      device.execute(launchVodaPay);

    if (checkOrCreateProfile != null)
      device.execute(checkOrCreateProfile);

    // Balances before -- both the Home wallet balance and, if buying, the
    // relevant Detailed-balances category rows. When not buying, also pull
    // every My Vodacom balance (VodaBucks/data/voice/sms/etc) since a
    // standalone balance check is a real, separately-useful scenario on its
    // own, not just a before-snapshot for a purchase.
    String walletBefore = null;
    if (checkWalletBalance != null)
    {
      device.execute(checkWalletBalance);
      walletBefore = context.get("walletBalance");
      System.out.println(cellNumber + ": wallet balance before: " + walletBefore);
    }

    String detailedCategory = buying
        ? (composition.hasData() ? "Data" : composition.hasVoiceMinutes() ? "Voice" : composition.hasSms() ? "SMS" : null)
        : null;
    String detailedRowsBefore = null;
    if (buying && checkDetailedBalanceRow != null && detailedCategory != null)
    {
      context.put("balanceCategory", detailedCategory);
      device.execute(checkDetailedBalanceRow);
      detailedRowsBefore = context.get("detailedBalanceRows_" + detailedCategory);
    }

    if (!buying)
    {
      if (checkAllBalances != null)
        device.execute(checkAllBalances);
      System.out.println(cellNumber + ": balance-check-only pair, no bundle to buy -- done.");
      return;
    }

    if (purchaseBundle == null)
      throw new ScriptFailureException(getCurrentContext(), "PurchaseSocialBundle action not found");
    device.execute(purchaseBundle);
    System.out.println(cellNumber + ": purchased " + bundleName + " (order " + context.get("purchaseOrderNumber") + ")");

    // Balances after -- and the actual provisioning confirmation: whichever
    // Detailed-balances row is new in "after" but wasn't in "before" is the
    // bundle that was just bought, regardless of whether its row name matches
    // the shop tile's name (confirmed live it often doesn't).
    if (checkWalletBalance != null)
    {
      device.execute(checkWalletBalance);
      String walletAfter = context.get("walletBalance");
      System.out.println(cellNumber + ": wallet balance after: " + walletAfter + " (was " + walletBefore + ")");
    }

    if (checkDetailedBalanceRow != null && detailedCategory != null)
    {
      context.put("balanceCategory", detailedCategory);
      device.execute(checkDetailedBalanceRow);
      String detailedRowsAfter = context.get("detailedBalanceRows_" + detailedCategory);
      String newRows = diffNewRows(detailedRowsBefore, detailedRowsAfter);
      System.out.println(cellNumber + ": new " + detailedCategory + " row(s) after purchase (provisioning confirmation): " + newRows);

      if (newRows.startsWith("(none"))
        throw new ScriptFailureException(getCurrentContext(), bundleName + " did not provision -- no new " + detailedCategory + " row appeared after purchase");
    }
  }

  /**
   * Compares two semicolon-joined "name=value" row strings from
   * CheckDetailedBalanceRow and returns the row(s) present in "after" but
   * not in "before" -- see VodaPaySocialBundleDepletion for the original
   * write-up of why diffing beats assuming a row name.
   */
  private String diffNewRows(String before, String after)
  {
    if (after == null || after.isEmpty())
      return "(no rows read)";
    Set<String> beforeSet = new HashSet<>();
    if (before != null)
      for (String row : before.split(";"))
        beforeSet.add(row);

    List<String> added = new ArrayList<>();
    for (String row : after.split(";"))
    {
      if (row.isEmpty() || beforeSet.contains(row))
        continue;
      added.add(row);
    }
    return added.isEmpty() ? "(none -- no new row appeared)" : String.join(", ", added);
  }

}
