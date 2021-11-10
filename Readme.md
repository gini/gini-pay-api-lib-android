Gini Pay API Library
====================

**Deprecation Notice**
----------------------

The Gini Pay API Library was split into industry specific API libraries: 
[Gini Bank API Library](https://github.com/gini/gini-mobile-android/blob/main/bank-api-library/) and 
[Gini Health API Library](https://github.com/gini/gini-mobile-android/blob/main/health-api-library/).

This library won't be developed further and we kindly ask you to switch to one of the new libraries.

### Gini Bank API Library

If you have a banking related app you should switch to the Gini Bank API Library. Migration entails only a few steps
which you can find in this 
[migration guide](https://github.com/gini/gini-mobile-android/blob/main/bank-api-library/migrate-from-pay-api-lib.md).

### Gini Health API Library

If you have a health insurance related app you should switch to the Gini Health API Library. Migration entails only a
few steps which you can find in this 
[migration guide](https://github.com/gini/gini-mobile-android/blob/main/health-api-library/migrate-from-pay-api-lib.md).

Introduction
------------

A library for communicating with the [Gini Pay API](https://pay-api.gini.net/documentation/). It allows you to easily add
[payment information extraction](https://pay-api.gini.net/documentation/#document-extractions-for-payment) capabilities
to your app. It also enables your app to create or resolve [payment requests](https://pay-api.gini.net/documentation/#payments).

The Gini Pay API provides an information extraction service for analyzing invoices. Specifically it extracts information
such as the document sender or the payment relevant information (amount to pay, IBAN, BIC, payment reference, etc.).
It also provides secure payment information sharing between clients via payment requests.

Documentation
-------------

See the [integration guide](https://developer.gini.net/gini-pay-api-lib-android/) for detailed guidance on how to
integrate the Gini Pay API Library into your app.

Dependencies
------------

The Gini Pay API Library has the following dependencies:

* [Volley from Google](http://developer.android.com/training/volley/index.html) ([AOSP Repository](https://android.googlesource.com/platform/frameworks/volley))
* [Bolts from facebook](https://github.com/BoltsFramework/Bolts-Android)
* [TrustKit from DataTheorem](https://github.com/datatheorem/TrustKit-Android)

License
-------

The Gini Pay API Library is available under the MIT license. See the LICENSE file for details.
