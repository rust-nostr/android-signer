# Android signer (NIP-55)

## Description

Nostr Android signer implementation ([NIP-55](https://github.com/nostr-protocol/nips/blob/master/55.md)).

## Project structure

- [proxy]: Android Kotlin implementation that acts as a proxy/bridge for NIP-55 communication.
  - [proxy/ffi]: FFI bindings for the [proxy].
- [signer]: Rust implementation of a NIP-55 signer that works through the [proxy].

[proxy]: proxy
[proxy/ffi]: proxy/ffi
[signer]: signer

## Getting started

### Kotlin

Add the following to your `build.gradle.kts` file:

```kotlin
repositories {
    mavenCentral()
}

dependencies { 
    implementation("org.rust-nostr:android-signer-proxy:<version>")
}
```

You can find the latest version at <https://central.sonatype.com/artifact/org.rust-nostr/android-signer-proxy>.

Then, in your Android code:

```kotlin
package com.example.yourpackage

// ...
import android.os.Bundle
import rust.nostr.android.signer.proxy.NostrAndroidSignerProxyServer

class MainActivity : <ComponentActivity>() {
    private var proxy: NostrAndroidSignerProxyServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup proxy
        // THE SAME UNIQUE NAME MUST BE USED IN THE RUST SIDE!
        val localProxy = NostrAndroidSignerProxyServer(context = applicationContext, this, "<unique-name>")
        localProxy.start()        
        proxy = localProxy
    }

    override fun onDestroy() {
        super.onDestroy()

        proxy?.stop()
    }
}
```

The `<ComponentActivity>()`, in the case of Flutter, is `FlutterFragmentActivity()`.

### Rust

Add the following to your `Cargo.toml` file:

```toml
[dependencies]
nostr-android-signer = "<version>"
```

You can find the latest version at <https://crates.io/crates/nostr-android-signer>.

## State

**These libraries are in ALPHA state**, things that are implemented generally work but the API will change in breaking ways.

## Donations

`rust-nostr` is free and open-source. This means we do not earn any revenue by selling it. Instead, we rely on your financial support. If you actively use any of the `rust-nostr` libs/software/services, then please [donate](https://rust-nostr.org/donate).

## License

This project is distributed under the MIT software license - see the [LICENSE](LICENSE) file for details
