use std::{fmt, io};

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
    /// Timeout
    Timeout,
}

impl std::error::Error for Error {}

impl fmt::Display for Error {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::IO(e) => e.fmt(f),
            Self::Transport(e) => e.fmt(f),
            Self::Status(s) => s.fmt(f),
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
