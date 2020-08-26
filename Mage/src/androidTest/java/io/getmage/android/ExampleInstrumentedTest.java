package io.getmage.android;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;

import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 * http://junit.sourceforge.net/javadoc/org/junit/Assert.html
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {

    @Before
    public void setOptions(){
        try {
            // This is an API key just for the SDK demo. (Real API keys look different!)
            Mage.setOptions(InstrumentationRegistry.getInstrumentation().getTargetContext(), new HashMap<String, Object>() {{
                put("apiKey", "749392738494832672820");
                put("isStrict", false);
                put("isProduction", true);
            }});
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void canLoadAProduct() {
        String someProduct = Mage.getIdFromProductName("io.getmage.demo_app.premium_plus", "io.getmage.demo_app.premium_plus_1_25");
        assertNotSame(someProduct, "io.getmage.demo_app.premium_plus_1_25");
    }

    @Test
    public void fallsBackToDefaultProduct() {
        String someProduct = Mage.getIdFromProductName("io.getmage.demo_app.NOTEXISTING", "io.getmage.demo_app.premium_plus_1_25");
        assertEquals(someProduct, "io.getmage.demo_app.premium_plus_1_25");
    }

    @Test
    public void canLookupAProductNameViaAnIapId() {
        String someProductName = Mage.getProductNameFromId("io.getmage.demo_app.premium_plus_1_25");
        assertEquals(someProductName, "io.getmage.demo_app.premium_plus");
    }

    @Test
    public void willReturnNullDuringAProductNameLookupWithANoneExistingId() {
        String someProductName = Mage.getProductNameFromId("io.getmage.demo_app.NOTEXISTING");
        assertEquals(someProductName, null);
    }
}