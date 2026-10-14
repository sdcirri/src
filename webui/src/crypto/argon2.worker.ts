import { argon2id } from '@noble/hashes/argon2.js';

type DeriveKekRequest = {
    id: number;
    password: string;
    salt: Uint8Array;
};

type DeriveKekResponse =
    | {
    id: number;
    kek: Uint8Array;
}
    | {
    id: number;
    error: string;
};

self.onmessage = (event: MessageEvent<DeriveKekRequest>) => {
    const { id, password, salt } = event.data;

    try {
        const kek = argon2id(password, salt, {
            t: 3,
            m: 65536,
            p: 4,
            dkLen: 32,
        });

        const response: DeriveKekResponse = { id, kek };
        self.postMessage(response);
    } catch (e) {
        const response: DeriveKekResponse = {
            id,
            error: e instanceof Error ? e.message : 'Argon2 failed',
        };

        self.postMessage(response);
    }
};
