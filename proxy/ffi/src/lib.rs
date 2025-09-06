#![forbid(unsafe_code)]
#![warn(clippy::large_futures)]

mod error;
mod server;

uniffi::setup_scaffolding!("nostr_android_signer_proxy");
