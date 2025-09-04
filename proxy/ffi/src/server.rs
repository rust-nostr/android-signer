use std::os::unix::net::UnixListener as StdUnixListener;
use std::sync::Arc;

use nostr_android_signer_proto::android_signer_server::{AndroidSigner, AndroidSignerServer};
use nostr_android_signer_proto::{
    GetPublicKeyReply, GetPublicKeyRequest, IsExternalSignerInstalledReply,
    IsExternalSignerInstalledRequest, SignEventReply, SignEventRequest,
};
use tokio::net::UnixListener as TokioUnixListener;
use tokio_stream::wrappers::UnixListenerStream;
use tonic::transport::Server;
use tonic::{Request, Response, Status};
use uds::{UnixListenerExt, UnixSocketAddr};
use uniffi::Object;

use crate::error::AndroidSignerProxyError;

pub struct SignerAdapter {
    callback: Arc<dyn NostrAndroidSignerProxyCallback>,
}

#[tonic::async_trait]
impl AndroidSigner for SignerAdapter {
    async fn is_external_signer_installed(
        &self,
        _request: Request<IsExternalSignerInstalledRequest>,
    ) -> Result<Response<IsExternalSignerInstalledReply>, Status> {
        let res: bool = self.callback.is_external_signer_installed().await?;
        Ok(Response::new(IsExternalSignerInstalledReply {
            installed: res,
        }))
    }

    async fn get_public_key(
        &self,
        _request: Request<GetPublicKeyRequest>,
    ) -> Result<Response<GetPublicKeyReply>, Status> {
        let public_key: String = self.callback.get_public_key().await?;
        Ok(Response::new(GetPublicKeyReply { public_key }))
    }

    async fn sign_event(
        &self,
        request: Request<SignEventRequest>,
    ) -> Result<Response<SignEventReply>, Status> {
        let req: SignEventRequest = request.into_inner();
        let event: String = self.callback.sign_event(req.unsigned_event).await?;
        Ok(Response::new(SignEventReply { event }))
    }
}

#[derive(Object)]
pub struct NostrAndroidSignerProxy {
    /// UNIX socket address
    socket_addr: UnixSocketAddr,
    callback: Arc<dyn NostrAndroidSignerProxyCallback>,
}

#[uniffi::export(async_runtime = "tokio")]
impl NostrAndroidSignerProxy {
    #[uniffi::constructor]
    pub fn new(
        unique_name: &str,
        callback: Arc<dyn NostrAndroidSignerProxyCallback>,
    ) -> Result<Self, AndroidSignerProxyError> {
        let name: String = format!("nip55_proxy_{unique_name}");

        Ok(Self {
            socket_addr: UnixSocketAddr::from_abstract(name.as_bytes())?,
            callback,
        })
    }

    /// Run the proxy
    pub async fn run(&self) -> Result<(), AndroidSignerProxyError> {
        let signer = SignerAdapter {
            callback: self.callback.clone(),
        };

        let listener: TokioUnixListener = bind_socket(&self.socket_addr)?;
        let stream: UnixListenerStream = UnixListenerStream::new(listener);

        Server::builder()
            .add_service(AndroidSignerServer::new(signer))
            .serve_with_incoming(stream)
            .await?;

        Ok(())
    }
}

fn bind_socket(socket_addr: &UnixSocketAddr) -> Result<TokioUnixListener, AndroidSignerProxyError> {
    // Bind abstract socket
    let listener = StdUnixListener::bind_unix_addr(socket_addr)?;

    // Moves the socket into nonblocking mode
    listener.set_nonblocking(true)?;

    // Convert to Tokio's async UnixListener
    Ok(TokioUnixListener::from_std(listener)?)
}

#[uniffi::export(with_foreign)]
#[async_trait::async_trait]
pub trait NostrAndroidSignerProxyCallback: Send + Sync {
    async fn is_external_signer_installed(&self) -> Result<bool, AndroidSignerProxyError>;

    async fn get_public_key(&self) -> Result<String, AndroidSignerProxyError>;

    async fn sign_event(&self, unsigned: String) -> Result<String, AndroidSignerProxyError>;
}
