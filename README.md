# Android Signer (NIP-55)

Nostr Android signer implementation ([NIP-55](https://github.com/nostr-protocol/nips/blob/master/55.md)).

## Project structure

- [proto]: Protobuf definitions, used by the [proxy/ffi] and [signer].
- [proxy]: Android/Kotlin implementation that acts as a bridge for NIP-55 communication (Intents and Content resolver).
  - [proxy/ffi]: Implementation of gRPC over UDS for the [proxy].
- [signer]: Rust implementation of the NIP-55 signer that communicates via the [proxy].

## Architecture

- Transport: gRPC over Unix Domain Sockets (UDS)
- Roles:
  - Rust signer: gRPC client
  - Proxy FFI (Rust, exposed to Kotlin via UniFFI): gRPC server
  - Kotlin layer: integrates with Android Intents and Content Resolver to fulfill NIP-55 operations
- Protobuf: gRPC services and messages are defined in the [proto] crate.
- Flow (request/response):
  1) Rust signer (client) sends a request over UDS using gRPC
  2) FFI server receives it and forwards the request to the Kotlin layer
  3) Kotlin performs the action using Intents or Content Resolver
  4) Kotlin returns the result to the FFI server
  5) FFI replies to the Rust client with the response

Diagram:

```
Rust app <──> signer (client) <── gRPC/UDS ──> FFI (server) <──> Kotlin (Intents/ContentResolver)
```

## Quick start

Below are the minimal steps to try the full integration: an Android app that starts the proxy and a Rust binary/library that connects to the proxy.

### Android side (Kotlin)

Add the following to your app module `build.gradle.kts`:

```kotlin
repositories {
    // Releases
    mavenCentral()
    // Snapshots (optional)
    maven("https://central.sonatype.com/repository/maven-snapshots/")
}

dependencies {
    implementation("org.rust-nostr:android-signer-proxy:<version>")
}
```

Latest version: https://central.sonatype.com/artifact/org.rust-nostr/android-signer-proxy

In your Android code, start the proxy and bind it to a shared unique name:

```kotlin
package com.example.yourpackage

import android.os.Bundle
import androidx.activity.ComponentActivity // or FlutterFragmentActivity for Flutter
import rust.nostr.android.signer.proxy.NostrAndroidSignerProxyServer

class MainActivity : ComponentActivity() {
    private var proxy: NostrAndroidSignerProxyServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Use the same unique name on the Rust side too!
        val localProxy = NostrAndroidSignerProxyServer(
            context = applicationContext,
            activity = this,
            uniqueName = "<unique-name>"
        )
        localProxy.start()
        proxy = localProxy
    }

    override fun onDestroy() {
        super.onDestroy()
        proxy?.stop()
    }
}
```

Flutter note: replace `ComponentActivity` with `FlutterFragmentActivity()`.

Finally, add support for the `nostrsigner:` scheme to your `AndroidManifest.xml`: 

```xml
<manifest>
    <!-- ... -->
    <queries>
        <!-- ... -->
        <intent>
            <action android:name="android.intent.action.VIEW" />
            <category android:name="android.intent.category.BROWSABLE" />
            <data android:scheme="nostrsigner" />
        </intent>
    </queries>
</manifest>
```

### Rust side

Add the following to your `Cargo.toml`:

```toml
[dependencies]
nostr-android-signer = "<version>"
```

Latest version: https://crates.io/crates/nostr-android-signer

Example usage to interact with the NIP-55 signer:

```rust
use nostr_android_signer::prelude::*;

// Must match the unique name used on Android
const UNIQUE_NAME: &str = "<unique-name>";

#[tokio::main]
async fn main() -> Result<()> {
    // 1) Construct the signer instance
    let signer = AndroidSigner::new(UNIQUE_NAME)?;

    // 2) Get the signer's public key
    let public_key = signer.get_public_key().await?;
    println!("Public key: {public_key}");

    Ok(())
}
```

## Important notes

- Unique name: it's the identifier of the local channel; if it doesn't match between Android and Rust, the connection will fail.
- Lifecycle: call `start()` on the proxy in `onCreate` and `stop()` in `onDestroy` (or an equivalent lifecycle point) to avoid leaking resources.
- `nostrsigner` scheme: required to handle NIP-55 flows via Intent/URI.

## State

**These libraries are in ALPHA state**, things that are implemented generally work but the API will change in breaking ways.

## Donations

`rust-nostr` is free and open-source. This means we do not earn any revenue by selling it. Instead, we rely on your financial support. If you actively use any of the `rust-nostr` libs/software/services, then please [donate](https://rust-nostr.org/donate).

## License

This project is distributed under the MIT software license - see the [LICENSE](LICENSE) file for details

[proto]: proto
[proxy]: proxy
[proxy/ffi]: proxy/ffi
[signer]: signer
