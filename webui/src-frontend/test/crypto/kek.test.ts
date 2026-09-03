import { describe, it, expect } from 'vitest';

import { aesEncrypt } from '@/crypto/common';
import { generateArgonSalt, decryptKeys } from '@/crypto/kek';
import type { UserCryptoDto } from '@/api/types';

const toBase64 = (u8: Uint8Array): string =>
    btoa(String.fromCharCode(...u8));

const makeDto = async (password: string, salt: Uint8Array): Promise<UserCryptoDto> => {
    const { argon2id } = await import('@noble/hashes/argon2.js');
    const hash = argon2id(password, salt, { t: 3, m: 65536, p: 4, dkLen: 32 }) as Uint8Array;

    const ed25519Private = crypto.getRandomValues(new Uint8Array(32));
    const x25519Private = crypto.getRandomValues(new Uint8Array(32));
    const ed25519Public = crypto.getRandomValues(new Uint8Array(32));
    const x25519Public = crypto.getRandomValues(new Uint8Array(32));

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
};

describe('generateArgonSalt', () => {
    it('returns 32 bytes', () => {
        expect(generateArgonSalt().byteLength).toBe(32);
    });

    it('returns different values each call', () => {
        expect(Uint8Array.from(generateArgonSalt())).not.toEqual(Uint8Array.from(generateArgonSalt()));
    });
});

describe('decryptKeys', () => {
    it('decrypts private keys correctly', async () => {
        const password = 'Password1!';
        const salt = generateArgonSalt();
        const { argon2id } = await import('@noble/hashes/argon2.js');
        const hash = argon2id(password, salt, { t: 3, m: 65536, p: 4, dkLen: 32 }) as Uint8Array;

        const ed25519Private = crypto.getRandomValues(new Uint8Array(32));
        const x25519Private = crypto.getRandomValues(new Uint8Array(32));
        const ed25519Public = crypto.getRandomValues(new Uint8Array(32));
        const x25519Public = crypto.getRandomValues(new Uint8Array(32));

        const encEd25519 = await aesEncrypt(hash, ed25519Private);
        const encX25519 = await aesEncrypt(hash, x25519Private);

        const dto: UserCryptoDto = {
            id: 'test-user-id',
            kekSalt: toBase64(salt),
            privateEd25519IV: toBase64(encEd25519.iv),
            privateEd25519Crypto: toBase64(encEd25519.cipherText),
            privateX25519IV: toBase64(encX25519.iv),
            privateX25519Crypto: toBase64(encX25519.cipherText),
            publicEd25519: toBase64(ed25519Public),
            publicX25519: toBase64(x25519Public),
        };

        const result = await decryptKeys(dto, password, salt);

        expect(Uint8Array.from(result.privateEd25519)).toEqual(Uint8Array.from(ed25519Private));
        expect(Uint8Array.from(result.privateX25519)).toEqual(Uint8Array.from(x25519Private));
        expect(Uint8Array.from(result.publicEd25519)).toEqual(Uint8Array.from(ed25519Public));
        expect(Uint8Array.from(result.publicX25519)).toEqual(Uint8Array.from(x25519Public));
    });

    it('fails with wrong password', async () => {
        const salt = generateArgonSalt();
        const dto = await makeDto('Password1!', salt);
        await expect(decryptKeys(dto, 'Wrong1!pass', salt)).rejects.toThrow();
    });

    it('fails with wrong salt', async () => {
        const salt = generateArgonSalt();
        const dto = await makeDto('Password1!', salt);
        await expect(decryptKeys(dto, 'Password1!', generateArgonSalt())).rejects.toThrow();
    });
});
