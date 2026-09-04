import { ed25519, x25519 } from '@noble/curves/ed25519.js';

export type KeyPair = {
    privateKey: Uint8Array;
    publicKey: Uint8Array;
};

export function generateEd25519KeyPair(): KeyPair {
    const privateKey = ed25519.utils.randomSecretKey(); // 32 byte
    const publicKey = ed25519.getPublicKey(privateKey);  // 32 byte
    return { privateKey, publicKey };
}

export function generateX25519KeyPair(): KeyPair {
    const privateKey = x25519.utils.randomSecretKey();
    const publicKey = x25519.getPublicKey(privateKey);
    return { privateKey, publicKey };
}

export function generateKeyMaterial(): {
    ed25519: KeyPair;
    x25519: KeyPair;
} {
    return {
        ed25519: generateEd25519KeyPair(),
        x25519: generateX25519KeyPair(),
    };
}
