import { argon2id } from '@noble/hashes/argon2.js';

import type { UserCryptoDto, UserRegistrationFinalizationRequest } from '@/api/types.ts';
import { type AesCrypto, aesDecrypt, aesEncrypt } from '@/crypto/common.ts';
import { generateKeyMaterial } from "@/crypto/keys.ts";

export type DecryptedCryptoSpecs = {
    privateEd25519: Uint8Array;
    publicEd25519: Uint8Array;
    privateX25519: Uint8Array;
    publicX25519: Uint8Array;
};

function fromBase64(b64: string): Uint8Array {
    return Uint8Array.from(atob(b64), c => c.charCodeAt(0));
}

function toBase64(bytes: Uint8Array): string {
    return btoa(String.fromCharCode(...bytes));
}

function argon2idForKek(password: string, salt: Uint8Array): Uint8Array {
    return argon2id(password as string, salt, { t: 3, m: 65536, p: 4, dkLen: 32 });
}

function generateArgonSalt(): Uint8Array {
    return crypto.getRandomValues(new Uint8Array(32));
}

async function encryptKeyWithKek(password: string, salt: Uint8Array, key: Uint8Array): Promise<AesCrypto> {
    const kek = argon2idForKek(password, salt);
    return await aesEncrypt(kek, key);
}

export async function bootstrapUserCrypto(password: string): Promise<UserRegistrationFinalizationRequest> {
    const keyMaterial = generateKeyMaterial();
    const kekSalt = generateArgonSalt();

    const ed25519Crypto = await encryptKeyWithKek(password, kekSalt, keyMaterial.ed25519.privateKey);
    const x25519Crypto = await encryptKeyWithKek(password, kekSalt, keyMaterial.x25519.privateKey);

    return {
        kekSalt: toBase64(kekSalt),
        privateEd25519Crypto: toBase64(ed25519Crypto.cipherText),
        privateEd25519IV: toBase64(ed25519Crypto.iv),
        publicEd25519: toBase64(keyMaterial.ed25519.publicKey),
        privateX25519Crypto: toBase64(x25519Crypto.cipherText),
        privateX25519IV: toBase64(x25519Crypto.iv),
        publicX25519: toBase64(keyMaterial.x25519.publicKey),
    };
}

export async function decryptKeys(mySpecs: UserCryptoDto, password: string): Promise<DecryptedCryptoSpecs> {
    const hash = argon2idForKek(password, fromBase64(mySpecs.kekSalt));
    const plainEd25519 = await aesDecrypt(
        hash, { iv: fromBase64(mySpecs.privateEd25519IV), cipherText: fromBase64(mySpecs.privateEd25519Crypto) }
    );
    const plainX25519 = await aesDecrypt(
        hash, { iv: fromBase64(mySpecs.privateX25519IV), cipherText: fromBase64(mySpecs.privateX25519Crypto) }
    );

    return {
        privateEd25519: plainEd25519,
        publicEd25519: fromBase64(mySpecs.publicEd25519),
        privateX25519: plainX25519,
        publicX25519: fromBase64(mySpecs.publicX25519),
    };
}

export async function reEncryptSpecs(oldPassword: string, newPassword: string, oldSpecs: UserCryptoDto): Promise<UserCryptoDto> {
    const decryptedCryptoSpecs = await decryptKeys(oldSpecs, oldPassword);

    const newSalt = generateArgonSalt();
    const newEd25519Crypto = await encryptKeyWithKek(newPassword, newSalt, decryptedCryptoSpecs.privateEd25519);
    const newX25519Crypto = await encryptKeyWithKek(newPassword, newSalt, decryptedCryptoSpecs.privateX25519);

    return {
        id: oldSpecs.id,
        kekSalt: toBase64(newSalt),
        privateEd25519Crypto: toBase64(newEd25519Crypto.cipherText),
        privateEd25519IV: toBase64(newEd25519Crypto.iv),
        publicEd25519: oldSpecs.publicEd25519,
        privateX25519Crypto: toBase64(newX25519Crypto.cipherText),
        privateX25519IV: toBase64(newX25519Crypto.iv),
        publicX25519: oldSpecs.publicX25519,
    }
}
