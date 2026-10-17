# Third-party notices

## AndroidX Media3 and AndroidX Annotation

Copyright The Android Open Source Project.

Licensed under the Apache License, Version 2.0. A copy is provided in
[third_party/licenses/Apache-2.0.txt](third_party/licenses/Apache-2.0.txt).

These components are used as dependencies. Their source is available from the
[AndroidX Media repository](https://github.com/androidx/media) and
[AndroidX repository](https://github.com/androidx/androidx).

## NanoHTTPD 2.3.1

Copyright (C) 2012 - 2015 nanohttpd.

Licensed under BSD-3-Clause. The full notice is provided in
[third_party/licenses/NanoHTTPD.txt](third_party/licenses/NanoHTTPD.txt), retained
in the source header, and bundled in the APK at `assets/nanohttpd-license.txt`.

The vendored source comes from the official Maven Central source artifact
`org.nanohttpd:nanohttpd:2.3.1`. The only functional change adds `SUBSCRIBE`,
`UNSUBSCRIBE`, and `NOTIFY` to the HTTP method enum for DLNA GENA support.
