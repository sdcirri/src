import { describe, it, expect } from 'vitest';
import { ed25519, x25519 } from '@noble/curves/ed25519.js';
import { argon2id } from '@noble/hashes/argon2.js';

import { aesEncrypt } from '@/crypto/common';
import { bootstrapUserCrypto, decryptKeys, reEncryptSpecs } from '@/crypto/kek';
import type { UserCryptoDto } from '@/api/types';

const toBase64 = (u8: Uint8Array): string =>
    btoa(String.fromCharCode(...u8));

const fromBase64 = (b64: string): Uint8Array =>
    Uint8Array.from(atob(b64), c => c.charCodeAt(0));

const randomSalt = (): Uint8Array => crypto.getRandomValues(new Uint8Array(32));

async function makeDto(
    password: string,
    salt: Uint8Array,
    ed25519Private: Uint8Array,
    x25519Private: Uint8Array,
    ed25519Public: Uint8Array,
    x25519Public: Uint8Array,
): Promise<UserCryptoDto> {
    const hash = argon2id(password, salt, { t: 3, m: 65536, p: 4, dkLen: 32 }) as Uint8Array;
    const encEd25519 = await aesEncrypt(hash, ed25519Private);
    const encX25519 = await aesEncrypt(hash, x25519Private);

    return {
        id: 'test-user-id',
        kekSalt: toBase64(salt),
        privateEd25519IV: toBase64(encEd25519.iv),
        privateEd25519Crypto: toBase64(encEd25519.cipherText),
        privateX25519IV: toBase64(encX25519.iv),
        privateX25519Crypto: toBase64(encX25519.cipherText),
        publicEd25519: toBase64(ed25519Public),
        publicX25519: toBase64(x25519Public),
    };
}

function toUserCryptoDto(id: string, boot: Awaited<ReturnType<typeof bootstrapUserCrypto>>): UserCryptoDto {
    return { id, ...boot };
}

describe('bootstrapUserCrypto', () => {
    it('returns a base64-encoded registration payload', async () => {
        const result = await bootstrapUserCrypto('Password1!');

        expect(fromBase64(result.kekSalt).byteLength).toBe(32);
        expect(fromBase64(result.publicEd25519).byteLength).toBe(32);
        expect(fromBase64(result.publicX25519).byteLength).toBe(32);
        expect(fromBase64(result.privateEd25519IV).byteLength).toBe(12);
        expect(fromBase64(result.privateX25519IV).byteLength).toBe(12);
        expect(fromBase64(result.privateEd25519Crypto).byteLength).toBeGreaterThan(0);
        expect(fromBase64(result.privateX25519Crypto).byteLength).toBeGreaterThan(0);
    });

    it('encrypts keys that decrypt with the same password', async () => {
        const password = 'Password1!';
        const boot = await bootstrapUserCrypto(password);
        const dto = toUserCryptoDto('user-1', boot);

        const decrypted = await decryptKeys(dto, password);

        expect(decrypted.privateEd25519.byteLength).toBe(32);
        expect(decrypted.privateX25519.byteLength).toBe(32);
        expect(toBase64(decrypted.publicEd25519)).toBe(boot.publicEd25519);
        expect(toBase64(decrypted.publicX25519)).toBe(boot.publicX25519);
    });

    it('produces different material on each call', async () => {
        const first = await bootstrapUserCrypto('Password1!');
        const second = await bootstrapUserCrypto('Password1!');

        expect(first.kekSalt).not.toBe(second.kekSalt);
        expect(first.publicEd25519).not.toBe(second.publicEd25519);
        expect(first.publicX25519).not.toBe(second.publicX25519);
    });
});

describe('decryptKeys', () => {
    it('decrypts private keys correctly', async () => {
        const password = 'Password1!';
        const salt = randomSalt();
        const ed25519Private = ed25519.utils.randomSecretKey();
        const x25519Private = x25519.utils.randomSecretKey();
        const ed25519Public = ed25519.getPublicKey(ed25519Private);
        const x25519Public = x25519.getPublicKey(x25519Private);
        const dto = await makeDto(
            password,
            salt,
            ed25519Private,
            x25519Private,
            ed25519Public,
            x25519Public,
        );

        const result = await decryptKeys(dto, password);

        expect(Uint8Array.from(result.privateEd25519)).toEqual(Uint8Array.from(ed25519Private));
        expect(Uint8Array.from(result.privateX25519)).toEqual(Uint8Array.from(x25519Private));
        expect(Uint8Array.from(result.publicEd25519)).toEqual(Uint8Array.from(ed25519Public));
        expect(Uint8Array.from(result.publicX25519)).toEqual(Uint8Array.from(x25519Public));
    });

    it('fails with wrong password', async () => {
        const password = 'Password1!';
        const salt = randomSalt();
        const ed25519Private = ed25519.utils.randomSecretKey();
        const x25519Private = x25519.utils.randomSecretKey();
        const dto = await makeDto(
            password,
            salt,
            ed25519Private,
            x25519Private,
            ed25519.getPublicKey(ed25519Private),
            x25519.getPublicKey(x25519Private),
        );

        await expect(decryptKeys(dto, 'Wrong1!pass')).rejects.toThrow();
    });

    it('fails with tampered kek salt', async () => {
        const password = 'Password1!';
        const salt = randomSalt();
        const ed25519Private = ed25519.utils.randomSecretKey();
        const x25519Private = x25519.utils.randomSecretKey();
        const dto = await makeDto(
            password,
            salt,
            ed25519Private,
            x25519Private,
            ed25519.getPublicKey(ed25519Private),
            x25519.getPublicKey(x25519Private),
        );

        const tampered = { ...dto, kekSalt: toBase64(randomSalt()) };
        await expect(decryptKeys(tampered, password)).rejects.toThrow();
    });
});

describe('reEncryptSpecs', () => {
    it('re-encrypts with a new password and preserves key material', async () => {
        const oldPassword = 'Password1!';
        const newPassword = 'NewPassw0rd!1';
        const oldSpecs = toUserCryptoDto('user-1', await bootstrapUserCrypto(oldPassword));
        const before = await decryptKeys(oldSpecs, oldPassword);

        const reEncrypted = await reEncryptSpecs(oldPassword, newPassword, oldSpecs);

        expect(reEncrypted.id).toBe(oldSpecs.id);
        expect(reEncrypted.publicEd25519).toBe(oldSpecs.publicEd25519);
        expect(reEncrypted.publicX25519).toBe(oldSpecs.publicX25519);
        expect(reEncrypted.kekSalt).not.toBe(oldSpecs.kekSalt);
        expect(reEncrypted.privateEd25519Crypto).not.toBe(oldSpecs.privateEd25519Crypto);
        expect(reEncrypted.privateX25519Crypto).not.toBe(oldSpecs.privateX25519Crypto);

        const after = await decryptKeys(reEncrypted, newPassword);
        expect(Uint8Array.from(after.privateEd25519)).toEqual(Uint8Array.from(before.privateEd25519));
        expect(Uint8Array.from(after.privateX25519)).toEqual(Uint8Array.from(before.privateX25519));
        expect(Uint8Array.from(after.publicEd25519)).toEqual(Uint8Array.from(before.publicEd25519));
        expect(Uint8Array.from(after.publicX25519)).toEqual(Uint8Array.from(before.publicX25519));

        await expect(decryptKeys(reEncrypted, oldPassword)).rejects.toThrow();
    });

    it('fails with wrong old password', async () => {
        const specs = toUserCryptoDto('user-1', await bootstrapUserCrypto('Password1!'));

        await expect(reEncryptSpecs('Wrong1!pass', 'NewPassw0rd!1', specs)).rejects.toThrow();
    });
});
