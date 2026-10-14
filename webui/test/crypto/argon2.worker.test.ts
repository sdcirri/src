import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const { argon2id } = vi.hoisted(() => ({
    argon2id: vi.fn(),
}));

vi.mock('@noble/hashes/argon2.js', () => ({
    argon2id,
}));

type WorkerRequest = {
    id: number;
    password: string;
    salt: Uint8Array;
};

describe('argon2 worker', () => {
    const postMessage = vi.fn();

    let workerScope: {
        postMessage: typeof postMessage;
        onmessage: ((event: MessageEvent<WorkerRequest>) => void) | null;
    };

    beforeEach(async () => {
        vi.resetModules();

        argon2id.mockReset();
        postMessage.mockReset();

        workerScope = { postMessage, onmessage: null };

        vi.stubGlobal('self', workerScope);

        await import('@/crypto/argon2.worker.ts');
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('derives the KEK and posts the result', () => {
        const salt = new Uint8Array([1, 2, 3]);
        const kek = new Uint8Array([4, 5, 6]);

        argon2id.mockReturnValue(kek);

        workerScope.onmessage?.(
            new MessageEvent('message', {
                data: { id: 42, password: 'password', salt },
            })
        );

        expect(argon2id).toHaveBeenCalledWith(
            'password',
            salt,
            { t: 3, m: 65536, p: 4, dkLen: 32 }
        );

        expect(postMessage).toHaveBeenCalledWith({ id: 42, kek });
    });

    it('posts the error when Argon2 fails', () => {
        argon2id.mockImplementation(() => {
            throw new Error('boom');
        });

        workerScope.onmessage?.(
            new MessageEvent('message', {
                data: { id: 42, password: 'password', salt: new Uint8Array([1]) },
            })
        );

        expect(postMessage).toHaveBeenCalledWith({ id: 42, error: 'boom' });
    });

    it('handles non-Error failures', () => {
        argon2id.mockImplementation(() => {
            throw 'boom';
        });

        workerScope.onmessage?.(
            new MessageEvent('message', {
                data: { id: 42, password: 'password', salt: new Uint8Array([1]) },
            })
        );

        expect(postMessage).toHaveBeenCalledWith({ id: 42, error: 'Argon2 failed' });
    });
});
