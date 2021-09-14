# Release Process

This document describes the release process for a new version of the Gini Pay API lib for Android.

* Add new features only in separate `feature` branches
* Merge `feature` branches into `develop` using a PR
* Create a `release-x.y.z` branch where `x.y.z` is the new version number 
  * Update `version` in `ginipaylib/gradle.properties`
  * Update the version in the snippet `ginipaylib/src/doc/source/guides/getting-started.rst`
* Push the `release-x.y.z` branch and wait for the build to pass
* Merge the `release-x.y.z` branch into `master` using a PR
* Tag `master` branch with the new version number
* Push all branches to remote including tags
* Wait for the build to pass and confirm the release
* Create a new release on GitHub with a changelog for the release tag
* Merge the `release-x.y.z` branch into `develop`
* Delete `release-x.y.z`
* Done!
