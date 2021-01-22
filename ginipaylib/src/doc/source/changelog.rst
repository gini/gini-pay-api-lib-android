=========
Changelog
=========

Important
=========

This page isn’t updated anymore. Please refer to our
`Releases on Github <https://github.com/gini/gini-sdk-android/releases>`_ for an up to date
changelog.

2.3.1 (2019-04-01)
==================

- Added document type hint for receipts.

2.3.0 (2019-01-16)
==================

- Added support for the Gini Accounting API.

2.2.0 (2018-10-31)
==================

- When uploading a document you may pass in predefined and/or custom metadata fields consisting
  of ASCII key-value pairs.

2.1.0 (2018-10-12)
==================

- Anonymous user credentials are encrypted by default. Existing user credentials are automatically
  encrypted on first usage. IMPORTANT: This change affects how credentials are stored only if no
  custom CredentialsStore implementation was set. If you require encryption either don't use a custom
  CredentialsStore or use the ``EncryptedCredentialsStore``.
- We raised the minimum Android SDK version to 19.

2.0.1 (2018-09-18)
==================

- Removed ``allowBackup`` from the manifest.

2.0.0 (2018-07-06)
==================

- Support for composite document analysis to analyse document's with multiple pages. A composite
  document consists of one or more partial documents. For each picture of a document's page a
  partial document has to be created. Using these a composite document can be created for which
  extractions may be retrieved after polling has finished.

1.5.0 (2018-02-08)
===================

- Min API Level increased to 15.
- Public key pinning with `Android Network Security Configuration and TrustKit
<guides/getting-started.html#public-key-pinning>`_. Previous public key pinning configuration was
removed.

1.4.3 (2018-02-05)
===================

Feature
-------

- Public key pinning.

1.4.2 (2018-01-29)
===================

Bugfix
------

- Using a custom cache doesn't prevent requests from being sent anymore.

1.4.1 (2018-01-15)
===================

Bugfixes
--------

- Added missing source classification types.
- Unknown source classification or unknown processing states don't cause exceptions anymore.

1.4.0 (2017-12-20)
===================

Feature
-------

- Certificate pinning.

1.3.92 (2017-04-13)
===================

Bugfixes
--------

- A new anonymous user will be generated if the existing one is invalid.
- Changing the email domain will be applied to existing anonymous users.

1.2.84 (2017-03-31)
===================

Feature
-------

- TLS is preferred over SSLv3 on API Levels 16 and later.

1.2.81 (2017-03-01)
===================

Feature
-------

- Volley caching can be customized by setting a com.android.volley.Cache implementation in the
  SdkBuilder.

1.1.73 (2016-12-21)
===================

Maintenance
-----------

- Updated Volley to com.android.Volley 1.0.0.

1.1.66 (2016-11-21)
===================

Bugfix
------

- Extraction candidates are now deserialized and returned as expected.

1.1.47 (2015-11-13)
===================

Feature
-------

- Documents can be uploaded as a byte array, too. Useful for texts (UTF-8 encoded) or PDFs.
- Document Type enum added and should be used instead of strings.
