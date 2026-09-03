import { argon2id } from '@noble/hashes/argon2.js';

import type { UserCryptoDto } from '@/api/types.ts';
import {aesDecrypt} from './common.ts';

export type DecryptedCryptoSpecs = {
    privateEd25519: Uint8Array;
    publicEd25519: Uint8Array;
    privateX25519: Uint8Array;
    publicX25519: Uint8Array;
};

function fromBase64(b64: string): Uint8Array {
    return Uint8Array.from(atob(b64), c => c.charCodeAt(0));
}

function argon2idForKek(password: string, salt: Uint8Array): Uint8Array {
    return argon2id(password as string, salt, { t: 3, m: 65536, p: 4, dkLen: 32 });
}

export function generateArgonSalt(): Uint8Array {
    return crypto.getRandomValues(new Uint8Array(32));
}

export async function decryptKeys(mySpecs: UserCryptoDto, password: string, salt: Uint8Array): Promise<DecryptedCryptoSpecs> {
    const hash = argon2idForKek(password, salt);
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
