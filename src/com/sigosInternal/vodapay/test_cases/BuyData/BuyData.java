package com.sigosInternal.vodapay.test_cases.BuyData;

import com.mc.api.device.Device;
import com.mc.api.script.exception.ScriptFailureException;
import com.mc.api.script.result.ScriptResult;
import com.mc.api.testcase.TestCase;
import com.mc.api.testcase.TestCaseInterpreter;
import com.mc.api.testcase.helper.TestCaseHelper;
import java.util.Collection;
import java.util.Map;

public class BuyData extends TestCaseInterpreter
{
  /*** GENERATED CODE -- DO NOT MODIFY (ANY CHANGES WILL BE OVERWRITTEN) ***/

  public static final BuyData instance = new BuyData();

  private BuyData()
  {
    super();
  }

  /**
   * @param args
   * @throws ScriptFailureException
   */
  public static void main(String[] args) throws ScriptFailureException, InterruptedException
  {
    TestCase testCase = BuyData.instance;

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

  @Override
  protected String getTestCaseFilename()
  {
    return "TestCase.xml";
  }


  /*** END GENERATED CODE ***/
}
