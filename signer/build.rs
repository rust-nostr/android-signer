use std::io::Result;

fn main() -> Result<()> {
    prost_build::compile_protos(&["src/proto/android_signer.proto"], &["src/proto"])?;
    Ok(())
}
