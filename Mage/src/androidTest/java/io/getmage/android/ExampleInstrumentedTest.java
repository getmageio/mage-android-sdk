package io.getmage.android;

import android.content.Context;
import android.util.Log;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 * http://junit.sourceforge.net/javadoc/org/junit/Assert.html
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */

@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {

    @BeforeClass
    public static void setOptions(){
        try {
            // This is an API key just for the SDK demo. (Real API keys look different!)
            Mage.setOptions(InstrumentationRegistry.getInstrumentation().getTargetContext(), new HashMap<String, Object>() {{
                put("apiKey", "87ßkjasdjnas7z3ji3zusajk38");
                put("isStrict", false);
                put("isProduction", true);
            }});

        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    @Test
    public void canLoadAProduct() {
        Log.d("MageSDKTEST", "loadproduct");
        String someProduct = Mage.getIdFromProductName("premium_static", "io.getmage.demo_app_static.premium_static_1_25");
        assertNotSame(someProduct, "io.getmage.demo_app_static.premium_static");
    }

    @Test
    public void fallsBackToDefaultProduct() {
        Log.d("MageSDKTEST", "testfallback");
        String someProduct = Mage.getIdFromProductName("NOTEXISTING", "io.getmage.demo_app_static.premium_static_1_25");
        assertEquals(someProduct, "io.getmage.demo_app_static.premium_static_1_25");
    }

    @Test
    public void canLookupAProductNameViaAnIapId() {
        Log.d("MageSDKTEST", "lookupname");
        String someProductName = Mage.getProductNameFromId("io.getmage.demo_app_static.premium_static");
        Log.d("MageSDKTEST", ""+someProductName);
        assertEquals(someProductName, "premium_static");
    }

    @Test
    public void willReturnNullDuringAProductNameLookupWithANoneExistingId() {
        Log.d("MageSDKTEST", "wrongID");
        String someProductName = Mage.getProductNameFromId("io.getmage.demo_app_static.NOTEXISTING");
        assertEquals(someProductName, null);
    }
}