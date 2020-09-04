package io.getmage.android;

import android.app.UiModeManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;

public class Mage {

    private static boolean verbose = false;

    private static final Mage OBJ = new Mage();

    // This is where all the magic happens
    String APIURLACCIO = "https://room-of-requirement.getmage.io/v1/accio";
    String APIURLLUMOS = "https://room-of-requirement.getmage.io/v1/lumos";

    HashMap<String, Object> currentState;
    ArrayList<HashMap<String, Object>> cachedProducts;
    String apiKey;
    boolean scheduledSaveStateInProgress;
    Context context;

    static ExecutorService executor;

    private Mage() {
        if (verbose) {
            Log.d("MageSDK", "initialized Singleton Mage");
        }
    }

    // to get Singleton Instance
    public static Mage sharedInstance() {
        return OBJ;
    }

    // available options:
    // apiKey = string
    // isStrict = bool, default false
    // production = bool, default false
    public static void setOptions(Context context, HashMap<String, Object> options) throws Exception {
        // if options not passed, options cant be set
        if (options == null) {
            return;
        }

        // set isProduction, default false
        Boolean tmp_isProduction = (Boolean) options.get("isProduction");
        if (tmp_isProduction == null) {
            tmp_isProduction = false;
        }

        // set isStrict, default false
        Boolean tmp_isStrict = (Boolean) options.get("isStrict");
        if (tmp_isStrict == null) {
            tmp_isStrict = false;
        }

        if (context == null){
            // just throw an exception when strict is true
            if(tmp_isStrict){
                throw new Exception("Mage Context Error: Mage needs Context to work. Call setOptions with a Context argument");
            }else{
                return;
            }
        }else{
            sharedInstance().context = context;
            if (verbose) {
                Log.d("MageSDK", "Mage recieved context");
            }
        }

        // if api key is passed, set api key
        String tmp_apiKey = (String) options.get("apiKey");
        if (tmp_apiKey != null) {
            sharedInstance().apiKey = tmp_apiKey;
            if (verbose) {
                Log.d("MageSDK", "Mage Api-Key set: " + sharedInstance().apiKey);
            }
        } else {
            // just throw an exception when strict is true
            if(tmp_isStrict){
                throw new Exception("Mage API Key Error: Mage needs an API key to work. Call setOptions with an apiKey argument in a dictionary");
            }else{
                return;
            }
        }

        // try to load state from cache
        try {
            sharedInstance().loadFromCache();
        } catch (JSONException | ClassCastException e) {
            e.printStackTrace();
            // TODO: test if that is fired when state is empty
            if (verbose) {
                Log.d("MageSDK", "loadFromCache went wrong");
            }
            sharedInstance().createCurrentState();
            sharedInstance().createCachedProducts();

        }

        // check if state is empty
        if(sharedInstance().currentState == null || sharedInstance().currentState.isEmpty()){
            sharedInstance().createCurrentState();
        }
        if(sharedInstance().cachedProducts == null){
            sharedInstance().createCachedProducts();
        }

        // update state
        sharedInstance().updateStateOnLaunch();

        sharedInstance().currentState.put("isStrict", tmp_isStrict);
        sharedInstance().currentState.put("isProduction", tmp_isProduction);

        sharedInstance().scheduledSaveStateInProgress = false;

        sharedInstance().scheduleSaveState();

        if (verbose) {
            Log.d("MageSDK", "currentState: " + sharedInstance().currentState);
            Log.d("MageSDK", "cachedProducts: " + sharedInstance().cachedProducts);
        }

        // initial API request for current price level
        // TODO check if initialization here is optimal & where to shutdown
        executor = Executors.newSingleThreadExecutor();
        executor.submit(new myRequest(sharedInstance().APIURLACCIO, sharedInstance().generateRequestObject(null), new Invoke() {
            @Override
            public void call(Exception error, HashMap<String, Object> response) {
                sharedInstance().setCachedProducts(error, response);
            }
        }));
    }

    public static String getProductNameFromId(String iapID){
        if (verbose) {
            Log.d("MageSDK", "getProductNameFromId: " + iapID);
        }
        if (sharedInstance().cachedProducts != null) {
            for (HashMap<String, Object> internalIapObj : sharedInstance().cachedProducts) {
                if (verbose) {
                    Log.d("MageSDK", "looking for : " + iapID + " -> " + internalIapObj.get("iapIdentifier"));
                }
                if (iapID.equals(internalIapObj.get("iapIdentifier"))) {
                    return (String) internalIapObj.get("productName");
                }
            }
        }
        return null;
    }

    public static String getIdFromProductName(String productName, String fallbackId){
        if (verbose) {
            Log.d("MageSDK", "getIdFromProductName: " + productName);
        }
        if (sharedInstance().cachedProducts != null) {
            for (HashMap<String, Object> internalIapObj : sharedInstance().cachedProducts) {
                if (verbose) {
                    Log.d("MageSDK", "looking for : " + productName + " -> " + internalIapObj.get("productName"));
                }
                if (productName.equals(internalIapObj.get("productName"))) {
                    return (String) internalIapObj.get("iapIdentifier");
                }
            }
        }
        return fallbackId;
    }

    private HashMap<String, Object> generateRequestObject(@Nullable String inAppPurchaseId){
        HashMap<String, Object> request = new HashMap<>();
        // assign state
        HashMap<String, Object> requestState = sharedInstance().currentState;
        requestState.put("time", getCurrentTimeStamp());
        request.put("state", requestState);
        request.put("products", sharedInstance().cachedProducts);

        if (inAppPurchaseId != null){
            if (verbose) {
                Log.d("MageSDK", "current cached products: " + sharedInstance().cachedProducts);
            }
            if (sharedInstance().cachedProducts != null) {
                HashMap<String, Object> foundProduct = null;
                for (HashMap<String, Object> internalIapObj : sharedInstance().cachedProducts) {
                    if (verbose) {
                        Log.d("MageSDK", "looking for : " + inAppPurchaseId + " -> " + internalIapObj.get("iapIdentifier"));
                    }
                    if (inAppPurchaseId.equals(internalIapObj.get("iapIdentifier"))) {
                        foundProduct = internalIapObj;
                        break;
                    }
                }
                if (foundProduct != null) {
                    request.put("product", foundProduct);
                } else {
                    request.put("product", null);
                }
            } else {
                request.put("product", null);
            }
        }

        if (verbose) {
            Log.d("MageSDK", "generateRequestObject: " + request);
        }

        return request;
    }

    public interface Invoke {
        void call(Exception error, HashMap<String, Object> response);
    }

    private void setCachedProducts(Exception error, HashMap<String, Object> response){
        executor.shutdown();
        if (verbose) {
            Log.d("MageSDK", "[Back to main] executor shutdown: " + executor.isShutdown());
        }
        if (error == null) {
            Object responseProducts = response.get("products");
            if (responseProducts == null) {
                sharedInstance().cachedProducts = new ArrayList<>();
            } else {
                try {
                    sharedInstance().cachedProducts = (ArrayList<HashMap<String, Object>>) responseProducts;
                } catch (ClassCastException e) {
                    e.printStackTrace();
                    Log.d("MageSDK", "parsing http response failed");
                    sharedInstance().cachedProducts = new ArrayList<>();
                }

            }
            if (verbose) {
                Log.d("MageSDK", "[Back to main] Response : " + sharedInstance().cachedProducts);
            }
        } else {
            if (verbose) {
                Log.d("MageSDK", "[Back to main] Exception occured : " + error);
            }
            error.printStackTrace();
        }
    }

    private static class myRequest implements Runnable {

        private HashMap<String, Object> requestContent;
        private Invoke func;
        private URL url;

        public myRequest(String url, HashMap<String, Object> requestContent, Invoke func){
            this.requestContent = requestContent;
            this.func = func;
            try {
                this.url = new URL(url);
            } catch (MalformedURLException e) {
                this.func.call(e, null);
            }
        }

        @Override
        public void run() {
            String response = "";
            HttpsURLConnection con;

            JSONObject jsonRequest = new JSONObject(requestContent);

            if (verbose) {
                Log.d("MageSDK", "[HTTP Thread ("+this.url+")] JSON : " + jsonRequest.toString());
            }
            try {
                con = (HttpsURLConnection) this.url.openConnection();
                // configure connection, set headers, set apikey
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Type", "application/json; utf-8");
                con.setRequestProperty("Accept", "application/json");
                con.setRequestProperty ("Token", sharedInstance().apiKey);

                OutputStream os = con.getOutputStream();
                OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");
                osw.write(jsonRequest.toString());
                osw.flush();
                osw.close();
                os.close();

                // start up connection
                con.connect();
                if (verbose) {
                    Log.d("MageSDK", "[HTTP Thread ("+this.url+")] Response Code : " + con.getResponseCode());
                }
                // read response
                BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String input;
                while ((input = br.readLine()) != null){
                    response += input;
                }
                br.close();
            } catch (IOException e) {
                this.func.call(e, null);
                return;
            }

            HashMap<String, Object> responseContent;
            try {
                JSONObject jsonResponse = new JSONObject(response);
                responseContent = jsonToMap(jsonResponse);
                if (verbose) {
                    Log.d("MageSDK", "[HTTP Thread ("+this.url+")] Response JSON : " + responseContent);
                }
            } catch (JSONException e) {
                this.func.call(e, null);
                return;
            }

            this.func.call(null, responseContent);
        }
    }

    // -------------------------------------------
    // STATE RELATED
    // -------------------------------------------
    void createCurrentState(){
        currentState = new HashMap<>();

        currentState.put("deviceId", getDeviceId());
        currentState.put("systemName", getSystemName());
        currentState.put("systemVersion", getSystemVersion());
        currentState.put("appName", getAppName());
        currentState.put("platform", "Google");
        currentState.put("deviceBrand", getDeviceBrand());
        currentState.put("deviceModel", getModel());
        currentState.put("deviceType", getDeviceTypeName());
    }

    void updateStateOnLaunch(){
        // device
        currentState.put("bundleId", getBundleId());
        currentState.put("appVersion", getAppVersion());
        currentState.put("buildNumber", getBuildNumber());
        currentState.put("isEmulator", isEmulator());
        // store
        currentState.put("storeCode", getStoreCode());
        currentState.put("countryCode", getCountryCode());
        currentState.put("currencyCode", getCurrencyCode());
        // time
        currentState.put("timeZone", getTimeZone());
        currentState.put("timeZoneCode", getTimeZoneCode());
        // production indicator
        currentState.put("isProduction", true);
        currentState.put("isStrict", true);
    }

    void createCachedProducts(){
        cachedProducts = new ArrayList<>();
    }

    void saveToCache(){
        if (verbose) {
            Log.d("MageSDK", "saveToCache..");
        }
        SharedPreferences preferences = context.getSharedPreferences("MagePreferences", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        JSONObject currentStateJson = new JSONObject(sharedInstance().currentState);
        editor.putString("currentState", currentStateJson.toString());
        HashMap<String, ArrayList<HashMap<String, Object>>> cachedProductsMap = new HashMap<>();
        cachedProductsMap.put("cachedProducts", sharedInstance().cachedProducts);
        JSONObject cachedProductsJson = new JSONObject(cachedProductsMap);
        editor.putString("cachedProducts", cachedProductsJson.toString());
        editor.apply();
        if (verbose) {
            Log.d("MageSDK", "saveToCache currentState: " + currentStateJson.toString());
            Log.d("MageSDK", "saveToCache cachedProducts: " + cachedProductsJson.toString());
        }
    }

    void loadFromCache() throws JSONException, ClassCastException {
        SharedPreferences preferences = context.getSharedPreferences("MagePreferences", Context.MODE_PRIVATE);

        //Userstate
        String currentStateString = preferences.getString("currentState", "");
        if (currentStateString != null) {
            if (verbose) {
                Log.d("MageSDK", "currentStateString: " + currentStateString);
            }
            JSONObject currentStateJson = new JSONObject(currentStateString);
            sharedInstance().currentState = jsonToMap(currentStateJson);
        } else {
            sharedInstance().createCurrentState();
        }

        //cachedProducts
        String cachedProductsString = preferences.getString("cachedProducts", "");
        if (cachedProductsString != null) {
            JSONObject cachedProductsJson = new JSONObject(cachedProductsString);
            HashMap<String, Object> cachedProductsMap = jsonToMap(cachedProductsJson);
            sharedInstance().cachedProducts = (ArrayList<HashMap<String, Object>>) cachedProductsMap.get("cachedProducts");
        } else {
            sharedInstance().createCachedProducts();
        }
    }

    public static HashMap<String, Object> jsonToMap(JSONObject json) throws JSONException {
        HashMap<String, Object> retMap = new HashMap<>();

        if(json != JSONObject.NULL) {
            retMap = toMap(json);
        }
        return retMap;
    }

    public static HashMap<String, Object> toMap(JSONObject object) throws JSONException {
        HashMap<String, Object> map = new HashMap<>();

        Iterator<String> keysItr = object.keys();
        while(keysItr.hasNext()) {
            String key = keysItr.next();
            Object value = object.get(key);

            if(value instanceof JSONArray) {
                value = toList((JSONArray) value);
            }

            else if(value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            map.put(key, value);
        }
        return map;
    }

    public static List<Object> toList(JSONArray array) throws JSONException {
        List<Object> list = new ArrayList<>();
        for(int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if(value instanceof JSONArray) {
                value = toList((JSONArray) value);
            }

            else if(value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            list.add(value);
        }
        return list;
    }

    void scheduleSaveState(){
        if(scheduledSaveStateInProgress){
            return;
        }
        if (verbose) {
            Log.d("MageSDK", "scheduleSaveState..");
        }
        scheduledSaveStateInProgress = true;
        new java.util.Timer().schedule(
                new java.util.TimerTask() {
                    @Override
                    public void run() {
                        saveToCache();
                        scheduledSaveStateInProgress = false;
                    }
                },
                2000
        );
    }

    // -------------------------------------------
    // PAYMENT HANDLER
    // -------------------------------------------
    public static void userPurchased(@Nullable String inAppId){
        if(inAppId != null){
            executor = Executors.newSingleThreadExecutor();
            executor.submit(new myRequest(sharedInstance().APIURLLUMOS, sharedInstance().generateRequestObject(inAppId), new Invoke() {
                @Override
                public void call(Exception error, HashMap<String, Object> response) {
                    // TODO implement stuff @mklb
                    if (verbose) {
                        Log.d("MageSDK", "userPurchased http callback: " + response);
                    }
                }
            }));
        }
    }

    // -------------------------------------------
    // ATTRIBUTE HELPERS
    // -------------------------------------------
    private static PackageInfo getPackageInfo() throws Exception {
        return sharedInstance().context.getPackageManager().getPackageInfo(sharedInstance().context.getPackageName(), 0);
    }

    // -------------------------------------------
    // ATTRIBUTE GETTERS
    // -------------------------------------------
    private static String getSystemName() {
        return "Android";
    }

    private static String getSystemVersion() {
        return Build.VERSION.RELEASE;
    }

    private static String getDeviceName() {
        if (Build.VERSION.SDK_INT >= 25) {
            String deviceName = Settings.Global.DEVICE_NAME;
            if (deviceName != null) {
                return deviceName;
            }
        }
        return "unknown";
    }

    private static String getAppName() {
        return sharedInstance().context.getApplicationInfo().loadLabel(sharedInstance().context.getPackageManager()).toString();
    }

    private static String getBundleId() {
        return sharedInstance().context.getPackageName();
    }

    private static String getAppVersion() {
        try {
            return getPackageInfo().versionName;
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static Integer getBuildNumber() {
        try {
            return getPackageInfo().versionCode;
        } catch (Exception e) {
            return null;
        }
    }

    private static String getModel() {
        return Build.MODEL;
    }

    private static String getBuildId() {
        return Build.ID;
    }

    private static String getDeviceId() {
        return Build.BOARD;
    }

    private static String getDeviceBrand(){
        return Build.BRAND;
    }

    private static boolean isEmulator() {
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.toLowerCase(Locale.ROOT).contains("droid4x")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.HARDWARE.contains("vbox86")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator")
                || Build.BOARD.toLowerCase(Locale.ROOT).contains("nox")
                || Build.BOOTLOADER.toLowerCase(Locale.ROOT).contains("nox")
                || Build.HARDWARE.toLowerCase(Locale.ROOT).contains("nox")
                || Build.PRODUCT.toLowerCase(Locale.ROOT).contains("nox")
                || Build.SERIAL.toLowerCase(Locale.ROOT).contains("nox")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"));
    }

    private static String getDeviceTypeName() {
        // Detect TVs
        if (sharedInstance().context.getPackageManager().hasSystemFeature("amazon.hardware.fire_tv")) {
            return "Tv";
        }

        UiModeManager uiManager = (UiModeManager) sharedInstance().context.getSystemService(Context.UI_MODE_SERVICE);
        if (uiManager != null && uiManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            return "Tv";
        }

        int smallestScreenWidthDp = sharedInstance().context.getResources().getConfiguration().smallestScreenWidthDp;

        if (smallestScreenWidthDp != Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED) {
            return smallestScreenWidthDp >= 600 ? "Tablet" : "Handset";
        }

        WindowManager windowManager = (WindowManager) sharedInstance().context.getSystemService(Context.WINDOW_SERVICE);

        if (windowManager == null) {
            return "Unknown";
        }

        // Get display metrics to see if we can differentiate handsets and tablets.
        DisplayMetrics metrics = new DisplayMetrics();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
        } else {
            windowManager.getDefaultDisplay().getMetrics(metrics);
        }

        // Calculate physical size.
        double widthInches = metrics.widthPixels / (double) metrics.xdpi;
        double heightInches = metrics.heightPixels / (double) metrics.ydpi;
        double diagonalSizeInches = Math.sqrt(Math.pow(widthInches, 2) + Math.pow(heightInches, 2));

        if (diagonalSizeInches >= 3.0 && diagonalSizeInches <= 6.9) {
            // Devices in a sane range for phones are considered to be Handsets.
            return "Handset";
        } else if (diagonalSizeInches > 6.9 && diagonalSizeInches <= 18.0) {
            // Devices larger than handset and in a sane range for tablets are tablets.
            return "Tablet";
        } else {
            // Otherwise, we don't know what device type we're on/
            return "Unknown";
        }
    }

    private static long getCurrentTimeStamp() {
        return System.currentTimeMillis() / 1000L;
    }

    private static String getStoreCode() {
        // because there is no way to tell in which store the user is we use the sims country code
        TelephonyManager teleMgr = (TelephonyManager)sharedInstance().context.getSystemService(Context.TELEPHONY_SERVICE);
        if (teleMgr != null){
            if (verbose) {
                Log.d("MageSDK", "teleMgr not null " + teleMgr);
            }
            String iso = teleMgr.getSimCountryIso();
            if(iso != null && !iso.isEmpty()){
                if (verbose) {
                    Log.d("MageSDK", "teleMgr iso not null " + iso);
                }
                // ISO 3166 country code (Aplha-2)
                return teleMgr.getSimCountryIso().toUpperCase();
            }
        }
        if (verbose) {
            Log.d("MageSDK", "teleMgr unknown");
        }
        return null;
    }

    private static String getCountryCode() {
        return sharedInstance().context.getResources().getConfiguration().locale.getISO3Country();
    }

    private static String getCurrencyCode() {
        Currency currency = Currency.getInstance(Locale.getDefault());
        return currency.getCurrencyCode();
    }

    private static String getTimeZone() {
        return TimeZone.getDefault().getID();
    }

    private static String getTimeZoneCode() {
        return TimeZone.getDefault().getDisplayName(true, TimeZone.SHORT);
    }

    public static void main(String[] args) {

    }
}
