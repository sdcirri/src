import { x25519 } from '@noble/curves/ed25519.js';
import { hkdf } from '@noble/hashes/hkdf.js';
import { sha512 } from '@noble/hashes/sha2.js';

import { aesEncrypt, aesDecrypt } from './common.ts';

export type EncryptedMessage = {
    messageData: Uint8Array;
    messageIV: Uint8Array;
};

function deriveMessageKey(myPrivateX25519: Uint8Array, theirPublicX25519: Uint8Array): Uint8Array {
    const secret = x25519.getSharedSecret(myPrivateX25519, theirPublicX25519);
    return hkdf(sha512, secret, undefined, new TextEncoder().encode('src/v1/messaging-key'), 32);
}

export async function encryptMessage(
    plaintext: string,
    myPrivateX25519: Uint8Array,
    theirPublicX25519: Uint8Array
): Promise<EncryptedMessage> {
    const enc = new TextEncoder();
    const aesKey = deriveMessageKey(myPrivateX25519, theirPublicX25519);
    const encryptedMessage = await aesEncrypt(aesKey, enc.encode(plaintext));

    return {
        messageData: encryptedMessage.cipherText,
        messageIV: encryptedMessage.iv
    };
}

export async function decryptMessage(
    encryptedMessage: EncryptedMessage,
    myPrivateX25519: Uint8Array,
    theirPublicX25519: Uint8Array
): Promise<string> {
    const dec = new TextDecoder();
    const aesKey = deriveMessageKey(myPrivateX25519, theirPublicX25519);
    return dec.decode(await aesDecrypt(aesKey, { cipherText: encryptedMessage.messageData, iv: encryptedMessage.messageIV }));
}
