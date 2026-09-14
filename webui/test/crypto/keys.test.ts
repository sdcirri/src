import { describe, expect, it } from 'vitest';
import { ed25519, x25519 } from '@noble/curves/ed25519.js';

import { generateEd25519KeyPair, generateKeyMaterial, generateX25519KeyPair } from '@/crypto/keys';

import { expectBytes } from './helpers';

describe('generateEd25519KeyPair', () => {
    it('returns 32-byte keys whose public key matches the private key', () => {
        const pair = generateEd25519KeyPair();
        expect(pair.privateKey.byteLength).toBe(32);
        expect(pair.publicKey.byteLength).toBe(32);
        expectBytes(pair.publicKey, ed25519.getPublicKey(pair.privateKey));
    });

    it('produces different material on each call', () => {
        const first = generateEd25519KeyPair();
        const second = generateEd25519KeyPair();
        expect(Uint8Array.from(first.privateKey)).not.toEqual(Uint8Array.from(second.privateKey));
        expect(Uint8Array.from(first.publicKey)).not.toEqual(Uint8Array.from(second.publicKey));
    });
});

describe('generateX25519KeyPair', () => {
    it('returns 32-byte keys whose public key matches the private key', () => {
        const pair = generateX25519KeyPair();
        expect(pair.privateKey.byteLength).toBe(32);
        expect(pair.publicKey.byteLength).toBe(32);
        expectBytes(pair.publicKey, x25519.getPublicKey(pair.privateKey));
    });

    it('produces different material on each call', () => {
        const first = generateX25519KeyPair();
        const second = generateX25519KeyPair();
        expect(Uint8Array.from(first.privateKey)).not.toEqual(Uint8Array.from(second.privateKey));
        expect(Uint8Array.from(first.publicKey)).not.toEqual(Uint8Array.from(second.publicKey));
    });
});

describe('generateKeyMaterial', () => {
    it('returns independent ed25519 and x25519 pairs', () => {
        const material = generateKeyMaterial();
        expectBytes(material.ed25519.publicKey, ed25519.getPublicKey(material.ed25519.privateKey));
        expectBytes(material.x25519.publicKey, x25519.getPublicKey(material.x25519.privateKey));
        expect(Uint8Array.from(material.ed25519.privateKey)).not.toEqual(
            Uint8Array.from(material.x25519.privateKey),
        );
    });
});
