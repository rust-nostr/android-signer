use std::io::Result;

fn main() -> Result<()> {
    tonic_prost_build::compile_protos("src/android_signer.proto")?;
    Ok(())
}
