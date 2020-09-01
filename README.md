<p align="center"><a href="https://www.getmage.io/"><img width="660" src="https://uploads-ssl.webflow.com/5eb96fb23eccf7fcdeb3d89f/5ef20b997a17d70677effb6f_header.svg"></a></p>

# Mage android SDK

Distributing products globally should not be a one price fits all strategy. Get started with Mage to scale your products worldwide!

## Requirements
Please note that our SDK currently just works with min SDK Version 16.

## Installation

Mage is available through [JitPack](https://jitpack.io/). To install
it, simply do the following two steps:

### Add JitPack to repositories in your root build.gradle
```gradle
allprojects {
  repositories {
    ...
    maven { url 'https://jitpack.io' }
  }
}
```

### Add dependency to your App build.gradle
```gradle
dependencies {
  ...
  implementation 'com.github.getmageio:mage-android-sdk:1.0'
}
```

## How to use Mage in your android project

### 1) Import the Java Mage SDK

```java
import io.getmage.android.Mage;
```

### 2) Set the API Key

```java
try {
    Mage.setOptions(getApplicationContext(), new HashMap<String, Object>() {{
        // Set your API key
        put("apiKey", "YOUR_API_KEY");
        // Optional: strict mode. The SDK will crash when errors occur.
        // This way you can test if you set up the SDK correctly!
        // Default: false
        put("isStrict", true);
        // Indicate if your app is running on a consumer device.
        // Please note that production should not be set to true if your app runs on real testing devices!
        // Default: false
        put("isProduction", true);
    }});
} catch (Exception e) {
    // this will never be thrown except your config was wrong and isStrict is set to true
    e.printStackTrace();
}
```

### 3) Get your in app purchase IDs

Wherever you show in app purchases call `getIdFromProductName` to get the correct in app purchase ID. This could be, for example, somewhere in your ViewController for your store view / popup.

```java
// Get the correct in app purchase id to show to the user
// In some cases (i.e. user has no internet connection) the method won't return anything so defining a fallback is not a bad idea!
Mage.getIdFromProductName("myProduct", "com.myapp.myFallbackID");
```

### 4) Know what you sold

In some cases you might want to know what the user bought so you can send it to a CRM,
your own backend or for some custom logic inside your app. `getProductNameFromId` will help you out!

```java
// Get the correct in app purchase id to show to the user
// This method will return null if no product was found for the provided ID
String someProductName = Mage.getProductNameFromId("com.myapp.someIapID");
if (someProductName) {
    // do stuff..
}
```

### 5) Report purchases

Auto-purchase tracking is not yet implemented in our Android Java SDK. Whenever a user makes a purchase you need to report it.

```java
Mage.userPurchased("com.myapp.someIapID")
```


## License

Mage is available under the MIT license. See the LICENSE file for more info.
