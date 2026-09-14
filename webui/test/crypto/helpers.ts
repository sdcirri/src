import { expect } from 'vitest';
import { argon2id } from '@noble/hashes/argon2.js';

import type { UserCryptoDto } from '@/api/types';
import { aesEncrypt, fromBase64, toBase64 } from '@/crypto/common';
import { bootstrapUserCrypto } from '@/crypto/kek';
import { generateKeyMaterial } from '@/crypto/keys';

export { fromBase64, toBase64 };

export const TEST_PASSWORD = 'Password1!';
export const NEW_PASSWORD = 'NewPassw0rd!1';

export function expectBytes(actual: Uint8Array, expected: Uint8Array) {
    expect(Uint8Array.from(actual)).toEqual(Uint8Array.from(expected));
}

export function toUserCryptoDto(
    id: string,
    boot: Awaited<ReturnType<typeof bootstrapUserCrypto>>,
): UserCryptoDto {
    return { id, ...boot };
}

export async function bootstrappedSpecs(
    password = TEST_PASSWORD,
    id = 'test-user-id',
): Promise<UserCryptoDto> {
    return toUserCryptoDto(id, await bootstrapUserCrypto(password));
}

/** Builds a DTO from known key material, independently of bootstrapUserCrypto. */
export async function specsFromKeys(
    password: string,
    material: ReturnType<typeof generateKeyMaterial> = generateKeyMaterial(),
    id = 'test-user-id',
): Promise<{ dto: UserCryptoDto; material: ReturnType<typeof generateKeyMaterial> }> {
    const salt = crypto.getRandomValues(new Uint8Array(32));
    const hash = argon2id(password, salt, { t: 3, m: 65536, p: 4, dkLen: 32 }) as Uint8Array;
    const encEd25519 = await aesEncrypt(hash, material.ed25519.privateKey);
    const encX25519 = await aesEncrypt(hash, material.x25519.privateKey);

    return {
        material,
        dto: {
            id,
            kekSalt: toBase64(salt),
            privateEd25519IV: toBase64(encEd25519.iv),
            privateEd25519Crypto: toBase64(encEd25519.cipherText),
            privateX25519IV: toBase64(encX25519.iv),
            privateX25519Crypto: toBase64(encX25519.cipherText),
            publicEd25519: toBase64(material.ed25519.publicKey),
            publicX25519: toBase64(material.x25519.publicKey),
        },
    };
}
