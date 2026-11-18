/** What the server takes as evidence of the SOL authorization (`PseResource.authorize`). */
export const EVIDENCE_MAX_BYTES = 2 * 1024 * 1024;

export const EVIDENCE_TYPES = ["application/pdf", "image/png", "image/jpeg"];

/** The file's bytes as base64, the shape `evidence_base64` expects. */
export const fileToBase64 = async (file: Blob): Promise<string> => {
  const bytes = new Uint8Array(await file.arrayBuffer());
  let binary = "";
  // Chunked: spreading a large array into fromCharCode overflows the call stack.
  for (let index = 0; index < bytes.length; index += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(index, index + 0x8000));
  }
  return btoa(binary);
};
