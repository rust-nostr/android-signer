use std::os::linux::net::SocketAddrExt;
use std::os::unix::net::{SocketAddr, UnixStream as StdUnixStream};

use prost::Message;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::UnixStream as TokioUnixStream;

use crate::error::Error;
use crate::proto::android_signer::{
    IsExternalSignerInstalledReply, IsExternalSignerInstalledRequest,
};

#[derive(Debug, Clone)]
pub struct AndroidSigner {
    /// UNIX socket address
    socket_addr: SocketAddr,
}

impl AndroidSigner {
    #[inline]
    pub fn new(unique_name: &str) -> Result<Self, Error> {
        let name: String = format!("nip55_proxy_{unique_name}");

        Ok(Self {
            socket_addr: SocketAddr::from_abstract_name(name.as_bytes())?,
        })
    }

    async fn connect(&self) -> Result<TokioUnixStream, Error> {
        // Connect to the abstract socket
        let std_stream: StdUnixStream = StdUnixStream::connect_addr(&self.socket_addr)?;

        // Moves the socket into nonblocking mode
        std_stream.set_nonblocking(true)?;

        // Convert to Tokio's async UnixStream
        Ok(TokioUnixStream::from_std(std_stream)?)
    }

    // Alternative method for more complex operations
    async fn send_request(&self, request_bytes: Vec<u8>) -> Result<Vec<u8>, Error> {
        let mut stream: TokioUnixStream = self.connect().await?;

        // Send request
        stream.write_all(&request_bytes).await?;
        stream.flush().await?;

        // Read response with a buffer
        let mut response: Vec<u8> = Vec::new();
        stream.read_to_end(&mut response).await?;

        Ok(response)
    }

    pub async fn is_external_signer_installed(&self) -> Result<bool, Error> {
        let request: IsExternalSignerInstalledRequest = IsExternalSignerInstalledRequest {};
        let response: Vec<u8> = self.send_request(request.encode_to_vec()).await?;
        let response: IsExternalSignerInstalledReply =
            IsExternalSignerInstalledReply::decode(&response[..])?;
        Ok(response.installed)
    }
}
