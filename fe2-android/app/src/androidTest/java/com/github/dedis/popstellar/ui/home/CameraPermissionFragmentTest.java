package com.github.dedis.popstellar.ui.home;

import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static com.github.dedis.popstellar.pages.qrcode.CameraPermissionPageObject.allowCameraButton;
import static com.github.dedis.popstellar.pages.qrcode.CameraPermissionPageObject.getRequestKey;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;

import androidx.test.espresso.ViewInteraction;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;

import com.github.dedis.popstellar.testutils.FragmentScenarioRule;
import com.github.dedis.popstellar.testutils.MockResultRegistry;
import com.github.dedis.popstellar.testutils.ResultReceiver;
import com.github.dedis.popstellar.ui.qrcode.CameraPermissionFragment;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeoutException;

/** Test for the CameraPermissionFragment */
@RunWith(AndroidJUnit4ClassRunner.class)
public class CameraPermissionFragmentTest {

  private final MockResultRegistry mockRegistry = new MockResultRegistry();

  @Rule
  public final FragmentScenarioRule<CameraPermissionFragment> fragmentRule =
      FragmentScenarioRule.launchInContainer(
          CameraPermissionFragment.class, () -> CameraPermissionFragment.newInstance(mockRegistry));

  @Test
  public void allowButtonMakesPermissionRequestAndProducesResult()
      throws TimeoutException, InterruptedException {
    setupAllowPermission();
    ResultReceiver<Bundle> receiver =
        ResultReceiver.createFakeListener(fragmentRule.getScenario(), getRequestKey());

    ViewInteraction allowButton = allowCameraButton().check(matches(ViewMatchers.isClickable()));
    allowButton.perform(click());

    assertTrue("No response were received", receiver.received());
    assertEquals("The response is not valid", Bundle.EMPTY, receiver.get(500));
  }

  @Test
  public void denyPermissionDoesNothing() {
    setupDenyPermission();
    ResultReceiver<Bundle> receiver =
        ResultReceiver.createFakeListener(fragmentRule.getScenario(), getRequestKey());

    ViewInteraction allowButton = allowCameraButton().check(matches(ViewMatchers.isClickable()));
    allowButton.perform(click());

    assertFalse("A response was received when none was expected", receiver.received());
  }

  /** Permission request will be allowed after this call */
  private void setupAllowPermission() {
    mockRegistry.setResultProvider(r -> true);
  }

  /** Permission request will be allowed after this call */
  private void setupDenyPermission() {
    mockRegistry.setResultProvider(r -> false);
  }
}
