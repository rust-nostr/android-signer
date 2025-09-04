use std::fmt;

use tonic::Status;
use uniffi::Error;

#[derive(Debug, Error)]
pub enum AndroidSignerProxyError {
    IO(String),
    Transport(String),
    Callback(String),
}

impl fmt::Display for AndroidSignerProxyError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::IO(e) => f.write_str(e),
            Self::Transport(e) => f.write_str(e),
            Self::Callback(e) => f.write_str(e),
        }
    }
}

impl From<std::io::Error> for AndroidSignerProxyError {
    fn from(e: std::io::Error) -> Self {
        Self::IO(e.to_string())
    }
}

impl From<tonic::transport::Error> for AndroidSignerProxyError {
    fn from(e: tonic::transport::Error) -> Self {
        Self::Transport(e.to_string())
    }
}

impl From<AndroidSignerProxyError> for Status {
    fn from(e: AndroidSignerProxyError) -> Self {
        Status::internal(e.to_string())
    }
}
