use std::os::unix::net::UnixStream as StdUnixStream;
use std::sync::Arc;

use hyper_util::rt::TokioIo;
use nostr_android_signer_proto::android_signer_client::AndroidSignerClient;
use nostr_android_signer_proto::{
    IsExternalSignerInstalledReply, IsExternalSignerInstalledRequest,
};
use tokio::net::UnixStream as TokioUnixStream;
use tokio::sync::{Mutex, OnceCell};
use tonic::transport::{Channel, Endpoint, Uri};
use tonic::{Request, Response};
use tower::service_fn;
use uds::{UnixSocketAddr, UnixStreamExt};

use crate::error::Error;

#[derive(Debug, Clone)]
pub struct AndroidSigner {
    /// UNIX socket address
    socket_addr: UnixSocketAddr,
    /// Timeout for requests
    client: OnceCell<Arc<Mutex<AndroidSignerClient<Channel>>>>,
}

impl AndroidSigner {
    #[inline]
    pub fn new(unique_name: &str) -> Result<Self, Error> {
        let name: String = format!("nip55_proxy_{unique_name}");

        Ok(Self {
            socket_addr: UnixSocketAddr::from_abstract(name.as_bytes())?,
            client: OnceCell::new(),
        })
    }

    async fn client(&self) -> Result<&Arc<Mutex<AndroidSignerClient<Channel>>>, Error> {
        self.client
            .get_or_try_init(|| async {
                let socket_addr: UnixSocketAddr = self.socket_addr;

                // We will ignore this uri because uds do not use it
                let channel: Channel = Endpoint::try_from("unix://fake_uri")?
                    .connect_with_connector(service_fn(move |_: Uri| async move {
                        let stream: TokioUnixStream = connect(&socket_addr)?;

                        Ok::<_, Error>(TokioIo::new(stream))
                    }))
                    .await?;

                // Construct client
                let client: AndroidSignerClient<Channel> = AndroidSignerClient::new(channel);

                Ok(Arc::new(Mutex::new(client)))
            })
            .await
    }

    pub async fn is_external_signer_installed(&self) -> Result<bool, Error> {
        // Get the client
        let client = self.client().await?;

        // Acquire the lock
        let mut client = client.lock().await;

        // Make the request
        let req: Request<IsExternalSignerInstalledRequest> =
            Request::new(IsExternalSignerInstalledRequest {});
        let res: Response<IsExternalSignerInstalledReply> =
            client.is_external_signer_installed(req).await?;

        // Unwrap the response
        let inner: IsExternalSignerInstalledReply = res.into_inner();
        Ok(inner.installed)
    }
}

fn connect(socket_addr: &UnixSocketAddr) -> Result<TokioUnixStream, Error> {
    // Connect to the abstract socket
    let std_stream: StdUnixStream = StdUnixStream::connect_to_unix_addr(socket_addr)?;

    // Moves the socket into nonblocking mode
    std_stream.set_nonblocking(true)?;

    // Convert to Tokio's async UnixStream
    Ok(TokioUnixStream::from_std(std_stream)?)
}
