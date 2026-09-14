import { describe, expect, it } from 'vitest';
import { ed25519, x25519 } from '@noble/curves/ed25519.js';

import { bootstrapUserCrypto, decryptKeys, reEncryptSpecs } from '@/crypto/kek';

import {
    NEW_PASSWORD,
    TEST_PASSWORD,
    bootstrappedSpecs,
    expectBytes,
    fromBase64,
    specsFromKeys,
    toBase64,
    toUserCryptoDto,
} from './helpers';

describe('bootstrapUserCrypto', () => {
    it('returns a base64-encoded registration payload', async () => {
        const result = await bootstrapUserCrypto(TEST_PASSWORD);

        expect(fromBase64(result.kekSalt).byteLength).toBe(32);
        expect(fromBase64(result.publicEd25519).byteLength).toBe(32);
        expect(fromBase64(result.publicX25519).byteLength).toBe(32);
        expect(fromBase64(result.privateEd25519IV).byteLength).toBe(12);
        expect(fromBase64(result.privateX25519IV).byteLength).toBe(12);
        expect(fromBase64(result.privateEd25519Crypto).byteLength).toBeGreaterThan(0);
        expect(fromBase64(result.privateX25519Crypto).byteLength).toBeGreaterThan(0);
    });

    it('encrypts keys that decrypt with the same password', async () => {
        const boot = await bootstrapUserCrypto(TEST_PASSWORD);
        const decrypted = await decryptKeys(toUserCryptoDto('user-1', boot), TEST_PASSWORD);

        expect(decrypted.privateEd25519.byteLength).toBe(32);
        expect(decrypted.privateX25519.byteLength).toBe(32);
        expect(toBase64(decrypted.publicEd25519)).toBe(boot.publicEd25519);
        expect(toBase64(decrypted.publicX25519)).toBe(boot.publicX25519);
        expectBytes(ed25519.getPublicKey(decrypted.privateEd25519), decrypted.publicEd25519);
        expectBytes(x25519.getPublicKey(decrypted.privateX25519), decrypted.publicX25519);
    });

    it('produces different material on each call', async () => {
        const first = await bootstrapUserCrypto(TEST_PASSWORD);
        const second = await bootstrapUserCrypto(TEST_PASSWORD);

        expect(first.kekSalt).not.toBe(second.kekSalt);
        expect(first.publicEd25519).not.toBe(second.publicEd25519);
        expect(first.publicX25519).not.toBe(second.publicX25519);
    });
});

describe('decryptKeys', () => {
    it('decrypts private keys correctly', async () => {
        const { dto, material } = await specsFromKeys(TEST_PASSWORD);
        const result = await decryptKeys(dto, TEST_PASSWORD);

        expectBytes(result.privateEd25519, material.ed25519.privateKey);
        expectBytes(result.privateX25519, material.x25519.privateKey);
        expectBytes(result.publicEd25519, material.ed25519.publicKey);
        expectBytes(result.publicX25519, material.x25519.publicKey);
    });

    it('fails with wrong password', async () => {
        const specs = await bootstrappedSpecs();
        await expect(decryptKeys(specs, 'Wrong1!pass')).rejects.toThrow();
    });

    it('fails with tampered kek salt', async () => {
        const specs = await bootstrappedSpecs();
        const tampered = { ...specs, kekSalt: toBase64(crypto.getRandomValues(new Uint8Array(32))) };
        await expect(decryptKeys(tampered, TEST_PASSWORD)).rejects.toThrow();
    });
});

describe('reEncryptSpecs', () => {
    it('re-encrypts with a new password and preserves key material', async () => {
        const oldSpecs = await bootstrappedSpecs(TEST_PASSWORD, 'user-1');
        const before = await decryptKeys(oldSpecs, TEST_PASSWORD);

        const reEncrypted = await reEncryptSpecs(TEST_PASSWORD, NEW_PASSWORD, oldSpecs);

        expect(reEncrypted.id).toBe(oldSpecs.id);
        expect(reEncrypted.publicEd25519).toBe(oldSpecs.publicEd25519);
        expect(reEncrypted.publicX25519).toBe(oldSpecs.publicX25519);
        expect(reEncrypted.kekSalt).not.toBe(oldSpecs.kekSalt);
        expect(reEncrypted.privateEd25519Crypto).not.toBe(oldSpecs.privateEd25519Crypto);
        expect(reEncrypted.privateX25519Crypto).not.toBe(oldSpecs.privateX25519Crypto);

        const after = await decryptKeys(reEncrypted, NEW_PASSWORD);
        expectBytes(after.privateEd25519, before.privateEd25519);
        expectBytes(after.privateX25519, before.privateX25519);
        expectBytes(after.publicEd25519, before.publicEd25519);
        expectBytes(after.publicX25519, before.publicX25519);

        await expect(decryptKeys(reEncrypted, TEST_PASSWORD)).rejects.toThrow();
    });

    it('fails with wrong old password', async () => {
        const specs = await bootstrappedSpecs();
        await expect(reEncryptSpecs('Wrong1!pass', NEW_PASSWORD, specs)).rejects.toThrow();
    });
});
