//! Android signer error

use std::{fmt, io};

use nostr::{event, key};
use tonic::Status;

/// Android signer error.
#[derive(Debug)]
pub enum Error {
    /// I/O error
    IO(io::Error),
    /// Tonic transport error
    Transport(tonic::transport::Error),
    /// Tonic status
    Status(Status),
    /// Keys error
    Keys(key::Error),
    /// Event error
    Event(event::Error),
    /// Timeout
    Timeout,
}

impl std::error::Error for Error {}

impl fmt::Display for Error {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::IO(e) => e.fmt(f),
            Self::Transport(e) => e.fmt(f),
            Self::Status(status) => f.write_str(status.message()),
            Self::Keys(e) => e.fmt(f),
            Self::Event(e) => e.fmt(f),
            Self::Timeout => f.write_str("Timeout"),
        }
    }
}

impl From<io::Error> for Error {
    fn from(e: io::Error) -> Self {
        Self::IO(e)
    }
}

impl From<tonic::transport::Error> for Error {
    fn from(e: tonic::transport::Error) -> Self {
        Self::Transport(e)
    }
}

impl From<Status> for Error {
    fn from(s: Status) -> Self {
        Self::Status(s)
    }
}

impl From<key::Error> for Error {
    fn from(e: key::Error) -> Self {
        Self::Keys(e)
    }
}

impl From<event::Error> for Error {
    fn from(e: event::Error) -> Self {
        Self::Event(e)
    }
}
