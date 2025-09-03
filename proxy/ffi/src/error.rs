use std::fmt;

use uniffi::Error;

#[derive(Debug, Error)]
pub enum AndroidSignerProxyError {
    IO(String),
    Transport(String),
}

impl fmt::Display for AndroidSignerProxyError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::IO(e) => f.write_str(e),
            Self::Transport(e) => f.write_str(e),
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
