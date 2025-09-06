//! Prelude

#![allow(unknown_lints)]
#![allow(ambiguous_glob_reexports)]
#![doc(hidden)]

pub use nostr::prelude::*;

pub use crate::client::{self, *};
pub use crate::error::{self, *};
