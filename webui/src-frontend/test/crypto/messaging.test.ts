import { describe, it, expect } from 'vitest';
import { x25519 } from '@noble/curves/ed25519.js';

import { encryptMessage, decryptMessage } from '@/crypto/messaging';

function generateX25519KeyPair() {
    const privateKey = x25519.utils.randomSecretKey();
    return { privateKey, publicKey: x25519.getPublicKey(privateKey) };
}

describe('encryptMessage / decryptMessage', () => {
    it('roundtrip between two parties', async () => {
        const alice = generateX25519KeyPair();
        const bob = generateX25519KeyPair();
        const plaintext = 'hello from alice';

        const encrypted = await encryptMessage(plaintext, alice.privateKey, bob.publicKey);
        const decrypted = await decryptMessage(encrypted, bob.privateKey, alice.publicKey);

        expect(decrypted).toBe(plaintext);
    });

    it('returns ciphertext and a 12-byte iv', async () => {
        const alice = generateX25519KeyPair();
        const bob = generateX25519KeyPair();

        const encrypted = await encryptMessage('ping', alice.privateKey, bob.publicKey);

        expect(encrypted.messageIV.byteLength).toBe(12);
        expect(encrypted.messageData.byteLength).toBeGreaterThan(0);
        expect(Uint8Array.from(encrypted.messageData)).not.toEqual(
            Uint8Array.from(new TextEncoder().encode('ping'))
        );
    });

    it('supports empty plaintext', async () => {
        const alice = generateX25519KeyPair();
        const bob = generateX25519KeyPair();

        const encrypted = await encryptMessage('', alice.privateKey, bob.publicKey);
        const decrypted = await decryptMessage(encrypted, bob.privateKey, alice.publicKey);

        expect(decrypted).toBe('');
    });

    it('supports unicode plaintext', async () => {
        const alice = generateX25519KeyPair();
        const bob = generateX25519KeyPair();
        const plaintext = 'ciao 🚀 — messaggio sicuro';

        const encrypted = await encryptMessage(plaintext, alice.privateKey, bob.publicKey);
        const decrypted = await decryptMessage(encrypted, bob.privateKey, alice.publicKey);

        expect(decrypted).toBe(plaintext);
    });

    it('fails to decrypt with the wrong peer public key', async () => {
        const alice = generateX25519KeyPair();
        const bob = generateX25519KeyPair();
        const eve = generateX25519KeyPair();

        const encrypted = await encryptMessage('secret', alice.privateKey, bob.publicKey);

        await expect(
            decryptMessage(encrypted, bob.privateKey, eve.publicKey)
        ).rejects.toThrow();
    });

    it('fails to decrypt tampered ciphertext', async () => {
        const alice = generateX25519KeyPair();
        const bob = generateX25519KeyPair();

        const encrypted = await encryptMessage('secret', alice.privateKey, bob.publicKey);
        const tamperedData = Uint8Array.from(encrypted.messageData);
        tamperedData[0] ^= 0xff;

        await expect(
            decryptMessage(
                { messageData: tamperedData, messageIV: encrypted.messageIV },
                bob.privateKey,
                alice.publicKey
            )
        ).rejects.toThrow();
    });

    it('fails to decrypt with a tampered iv', async () => {
        const alice = generateX25519KeyPair();
        const bob = generateX25519KeyPair();

        const encrypted = await encryptMessage('secret', alice.privateKey, bob.publicKey);
        const tamperedIv = Uint8Array.from(encrypted.messageIV);
        tamperedIv[0] ^= 0xff;

        await expect(
            decryptMessage(
                { messageData: encrypted.messageData, messageIV: tamperedIv },
                bob.privateKey,
                alice.publicKey
            )
        ).rejects.toThrow();
    });
});
