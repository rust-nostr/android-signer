use std::os::unix::net::UnixStream as StdUnixStream;
use std::time::Duration;

use prost::Message;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::UnixStream as TokioUnixStream;
use tokio::time;
use uds::{UnixSocketAddr, UnixStreamExt};

use crate::error::Error;
use crate::proto::android_signer::{
    IsExternalSignerInstalledReply, IsExternalSignerInstalledRequest,
};

#[derive(Debug, Clone)]
pub struct AndroidSigner {
    /// UNIX socket address
    socket_addr: UnixSocketAddr,
    /// Timeout for requests
    timeout: Duration,
}

impl AndroidSigner {
    #[inline]
    pub fn new(unique_name: &str) -> Result<Self, Error> {
        let name: String = format!("nip55_proxy_{unique_name}");

        Ok(Self {
            socket_addr: UnixSocketAddr::from_abstract(name.as_bytes())?,
            timeout: Duration::from_secs(30),
        })
    }

    async fn connect(&self) -> Result<TokioUnixStream, Error> {
        // Connect to the abstract socket
        let std_stream: StdUnixStream = StdUnixStream::connect_to_unix_addr(&self.socket_addr)?;

        // Moves the socket into nonblocking mode
        std_stream.set_nonblocking(true)?;

        // Convert to Tokio's async UnixStream
        Ok(TokioUnixStream::from_std(std_stream)?)
    }

    async fn send_request<T>(&self, request: T) -> Result<Vec<u8>, Error>
    where
        T: Message,
    {
        let stream: TokioUnixStream = self.connect().await?;

        let (mut read_half, mut write_half) = stream.into_split();

        let bytes: Vec<u8> = request.encode_length_delimited_to_vec();

        // Send request
        write_half.write_all(&bytes).await?;
        write_half.flush().await?;

        // Shutdown the write half to send EOF
        write_half.shutdown().await?;

        // Read response with a buffer
        let mut response: Vec<u8> = Vec::new();
        read_half.read_to_end(&mut response).await?;

        Ok(response)
    }

    async fn send_request_with_timeout<T>(&self, request: T) -> Result<Vec<u8>, Error>
    where
        T: Message,
    {
        time::timeout(self.timeout, self.send_request(request))
            .await
            .map_err(|_| Error::Timeout)?
    }

    pub async fn is_external_signer_installed(&self) -> Result<bool, Error> {
        let request: IsExternalSignerInstalledRequest = IsExternalSignerInstalledRequest {};

        let response: Vec<u8> = self.send_request_with_timeout(request).await?;

        let response: IsExternalSignerInstalledReply =
            IsExternalSignerInstalledReply::decode(&response[..])?;

        Ok(response.installed)
    }
}
