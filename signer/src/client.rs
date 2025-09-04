use std::borrow::Cow;
use std::os::unix::net::UnixStream as StdUnixStream;
use std::sync::Arc;

use hyper_util::rt::TokioIo;
use nostr::prelude::*;
use nostr_android_signer_proto::android_signer_client::AndroidSignerClient;
use nostr_android_signer_proto::{
    GetPublicKeyReply, GetPublicKeyRequest, IsExternalSignerInstalledReply,
    IsExternalSignerInstalledRequest, Nip04DecryptReply, Nip04DecryptRequest, Nip04EncryptReply,
    Nip04EncryptRequest, SignEventReply, SignEventRequest,
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
    /// Current user public key
    public_key: OnceCell<PublicKey>,
}

impl AndroidSigner {
    #[inline]
    pub fn new(unique_name: &str) -> Result<Self, Error> {
        let name: String = format!("nip55_proxy_{unique_name}");

        Ok(Self {
            socket_addr: UnixSocketAddr::from_abstract(name.as_bytes())?,
            client: OnceCell::new(),
            public_key: OnceCell::new(),
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

    async fn _get_public_key(&self) -> Result<&PublicKey, Error> {
        self.public_key
            .get_or_try_init(|| async {
                // Get the client
                let client = self.client().await?;

                // Acquire the lock
                let mut client = client.lock().await;

                // Make the request
                let req: Request<GetPublicKeyRequest> = Request::new(GetPublicKeyRequest {});
                let res: Response<GetPublicKeyReply> = client.get_public_key(req).await?;

                // Unwrap the response
                let inner: GetPublicKeyReply = res.into_inner();
                Ok(PublicKey::parse(&inner.public_key)?)
            })
            .await
    }

    async fn _sign_event(&self, unsigned: UnsignedEvent) -> Result<Event, Error> {
        // Get the client
        let client = self.client().await?;

        // Acquire the lock
        let mut client = client.lock().await;

        // Make the request
        let req: Request<SignEventRequest> = Request::new(SignEventRequest {
            unsigned_event: unsigned.as_json(),
        });
        let res: Response<SignEventReply> = client.sign_event(req).await?;

        // Unwrap the response
        let inner: SignEventReply = res.into_inner();
        let event: Event = Event::from_json(&inner.event)?;

        // Verify
        event.verify()?;

        Ok(event)
    }

    async fn _nip04_encrypt(
        &self,
        current_user_public_key: &PublicKey,
        public_key: &PublicKey,
        plaintext: &str,
    ) -> Result<String, Error> {
        // Get the client
        let client = self.client().await?;

        // Acquire the lock
        let mut client = client.lock().await;

        // Make the request
        let req: Request<Nip04EncryptRequest> = Request::new(Nip04EncryptRequest {
            current_user_public_key: current_user_public_key.to_hex(),
            other_public_key: public_key.to_hex(),
            plaintext: plaintext.to_string(),
        });
        let res: Response<Nip04EncryptReply> = client.nip04_encrypt(req).await?;

        // Unwrap the response
        let inner: Nip04EncryptReply = res.into_inner();
        Ok(inner.ciphertext)
    }

    async fn _nip04_decrypt(
        &self,
        current_user_public_key: &PublicKey,
        public_key: &PublicKey,
        ciphertext: &str,
    ) -> Result<String, Error> {
        // Get the client
        let client = self.client().await?;

        // Acquire the lock
        let mut client = client.lock().await;

        // Make the request
        let req: Request<Nip04DecryptRequest> = Request::new(Nip04DecryptRequest {
            current_user_public_key: current_user_public_key.to_hex(),
            other_public_key: public_key.to_hex(),
            ciphertext: ciphertext.to_string(),
        });
        let res: Response<Nip04DecryptReply> = client.nip04_decrypt(req).await?;

        // Unwrap the response
        let inner: Nip04DecryptReply = res.into_inner();
        Ok(inner.plaintext)
    }
}

impl NostrSigner for AndroidSigner {
    fn backend(&self) -> SignerBackend {
        SignerBackend::Custom(Cow::Borrowed("android signer"))
    }

    fn get_public_key(&self) -> BoxedFuture<Result<PublicKey, SignerError>> {
        Box::pin(async move {
            self._get_public_key()
                .await
                .copied()
                .map_err(SignerError::backend)
        })
    }

    fn sign_event(&self, unsigned: UnsignedEvent) -> BoxedFuture<Result<Event, SignerError>> {
        Box::pin(async move {
            self._sign_event(unsigned)
                .await
                .map_err(SignerError::backend)
        })
    }

    fn nip04_encrypt<'a>(
        &'a self,
        public_key: &'a PublicKey,
        content: &'a str,
    ) -> BoxedFuture<'a, Result<String, SignerError>> {
        Box::pin(async move {
            let current_user_public_key =
                self._get_public_key().await.map_err(SignerError::backend)?;
            self._nip04_encrypt(current_user_public_key, public_key, content)
                .await
                .map_err(SignerError::backend)
        })
    }

    fn nip04_decrypt<'a>(
        &'a self,
        public_key: &'a PublicKey,
        encrypted_content: &'a str,
    ) -> BoxedFuture<'a, Result<String, SignerError>> {
        Box::pin(async move {
            let current_user_public_key =
                self._get_public_key().await.map_err(SignerError::backend)?;
            self._nip04_decrypt(current_user_public_key, public_key, encrypted_content)
                .await
                .map_err(SignerError::backend)
        })
    }

    fn nip44_encrypt<'a>(
        &'a self,
        _public_key: &'a PublicKey,
        _content: &'a str,
    ) -> BoxedFuture<'a, Result<String, SignerError>> {
        todo!()
    }

    fn nip44_decrypt<'a>(
        &'a self,
        _public_key: &'a PublicKey,
        _payload: &'a str,
    ) -> BoxedFuture<'a, Result<String, SignerError>> {
        todo!()
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
