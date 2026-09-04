
export type AesCrypto = {
    cipherText: Uint8Array;
    iv: Uint8Array;
};

function toArrayBuffer(u8: Uint8Array): ArrayBuffer {
    const buf = new ArrayBuffer(u8.byteLength);
    new Uint8Array(buf).set(u8);
    return buf;
}

export async function aesEncrypt(key: Uint8Array, plaintext: Uint8Array): Promise<AesCrypto> {
    const cryptoKey = await crypto.subtle.importKey(
        'raw', toArrayBuffer(key), { name: 'AES-GCM' }, false, ['encrypt']
    );
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const cipherText = await crypto.subtle.encrypt(
        { name: 'AES-GCM', iv: toArrayBuffer(iv) },
        cryptoKey,
        toArrayBuffer(plaintext)
    );
    return { iv, cipherText: new Uint8Array(cipherText) };
}

export async function aesDecrypt(key: Uint8Array, aesCrypto: AesCrypto): Promise<Uint8Array> {
    const cryptoKey = await crypto.subtle.importKey(
        'raw', toArrayBuffer(key), { name: 'AES-GCM' }, false, ['decrypt']
    );
    const plaintext = await crypto.subtle.decrypt(
        { name: 'AES-GCM', iv: toArrayBuffer(aesCrypto.iv) },
        cryptoKey,
        toArrayBuffer(aesCrypto.cipherText)
    );
    return new Uint8Array(plaintext);
}
