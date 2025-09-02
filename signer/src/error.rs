use std::{fmt, io};

/// Android signer error.
#[derive(Debug)]
pub enum Error {
    /// I/O error
    IO(io::Error),
    /// Prost error
    Prost(prost::DecodeError),
}

impl std::error::Error for Error {}

impl fmt::Display for Error {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::IO(e) => e.fmt(f),
            Self::Prost(e) => e.fmt(f),
        }
    }
}

impl From<io::Error> for Error {
    fn from(e: io::Error) -> Self {
        Self::IO(e)
    }
}

impl From<prost::DecodeError> for Error {
    fn from(e: prost::DecodeError) -> Self {
        Self::Prost(e)
    }
}
