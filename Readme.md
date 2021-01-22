Gini Android SDK
================

An SDK for integrating Gini technology into other apps. With this SDK you will be able to extract semantic information
from various types of documents.


Dependencies
------------

The Gini Android SDK has the following dependencies:

* [Volley from Google](http://developer.android.com/training/volley/index.html) ([AOSP Repository](https://android.googlesource.com/platform/frameworks/volley))
* [Bolts from facebook](https://github.com/BoltsFramework/Bolts-Android)
* [TrustKit from DataTheorem](https://github.com/datatheorem/TrustKit-Android)

Integration
-----------

You can easily integrate the Gini Android SDK into your app using Gradle and our Maven repository.

```
    repositories {
        maven {
            url "https://repo.gini.net/nexus/content/repositories/public"
        }
        ...
    }
    
    dependencies {
        compile ('net.gini:gini-pay-api-lib-android:1.0.0@aar'){
            transitive = true
        }
        ...
    }

```

See the [integration guide](http://developer.gini.net/gini-sdk-android/) for detailed guidance how to 
integrate the Gini SDK into your app.

See the [Gini Android SDK documentation](http://developer.gini.net/gini-sdk-android/java-docs-release/net/gini/android/DocumentTaskManager.html)
for more details how to use the `DocumentTaskManager`.


Copyright (c) 2014-2019, [Gini GmbH](https://www.gini.net/)
