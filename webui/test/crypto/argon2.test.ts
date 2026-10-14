import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

class MockWorker {
    static instances: MockWorker[] = [];

    onmessage: ((event: MessageEvent) => void) | null = null;
    onerror: ((event: ErrorEvent) => void) | null = null;

    postMessage = vi.fn();

    constructor() {
        MockWorker.instances.push(this);
    }
}

describe('deriveKek', () => {
    beforeEach(() => {
        vi.resetModules();
        MockWorker.instances = [];

        vi.stubGlobal(
            'Worker',
            MockWorker as unknown as typeof Worker,
        );
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('sends the derivation request and resolves the response', async () => {
        const { deriveKek } = await import('@/crypto/argon2.ts');

        const salt = new Uint8Array([1, 2, 3]);
        const kek = new Uint8Array([4, 5, 6]);

        const result = deriveKek('password', salt);

        const worker = MockWorker.instances[0];

        expect(worker).toBeDefined();
        expect(worker.postMessage).toHaveBeenCalledWith({
            id: 0,
            password: 'password',
            salt,
        });

        worker.onmessage?.(
            new MessageEvent('message', {
                data: { id: 0, kek },
            })
        );

        await expect(result).resolves.toEqual(kek);
    });

    it('uses the same worker for concurrent requests and matches responses by id', async () => {
        const { deriveKek } = await import('@/crypto/argon2.ts');

        const first = deriveKek('first', new Uint8Array([1]));
        const second = deriveKek('second', new Uint8Array([2]));

        expect(MockWorker.instances).toHaveLength(1);

        const worker = MockWorker.instances[0];

        expect(worker.postMessage).toHaveBeenNthCalledWith(1, {
            id: 0,
            password: 'first',
            salt: new Uint8Array([1]),
        });

        expect(worker.postMessage).toHaveBeenNthCalledWith(2, {
            id: 1,
            password: 'second',
            salt: new Uint8Array([2]),
        });

        worker.onmessage?.(
            new MessageEvent('message', {
                data: { id: 1, kek: new Uint8Array([20]) },
            })
        );

        worker.onmessage?.(
            new MessageEvent('message', {
                data: { id: 0, kek: new Uint8Array([10]) },
            })
        );

        await expect(first).resolves.toEqual(new Uint8Array([10]));
        await expect(second).resolves.toEqual(new Uint8Array([20]));
    });

    it('rejects when the worker returns an error', async () => {
        const { deriveKek } = await import('@/crypto/argon2.ts');

        const result = deriveKek('password', new Uint8Array([1]));

        const worker = MockWorker.instances[0];

        worker.onmessage?.(
            new MessageEvent('message', {
                data: { id: 0, error: 'Argon2 failed' },
            })
        );

        await expect(result).rejects.toThrow('Argon2 failed');
    });

    it('ignores responses for unknown request ids', async () => {
        const { deriveKek } = await import('@/crypto/argon2.ts');

        const result = deriveKek('password', new Uint8Array([1]));

        const worker = MockWorker.instances[0];

        expect(() => {
            worker.onmessage?.(
                new MessageEvent('message', {
                    data: { id: 999, kek: new Uint8Array([99]) },
                })
            );
        }).not.toThrow();

        worker.onmessage?.(
            new MessageEvent('message', {
                data: { id: 0, kek: new Uint8Array([10]) },
            })
        );

        await expect(result).resolves.toEqual(new Uint8Array([10]));
    });

    it('rejects all pending requests when the worker fails', async () => {
        const { deriveKek } = await import('@/crypto/argon2.ts');

        const first = deriveKek('first', new Uint8Array([1]));
        const second = deriveKek('second', new Uint8Array([2]));

        const worker = MockWorker.instances[0];

        worker.onerror?.(
            new ErrorEvent('error', { message: 'Worker exploded' })
        );

        await expect(first).rejects.toThrow('Worker exploded');
        await expect(second).rejects.toThrow('Worker exploded');
    });

    it('uses a fallback message when the worker fails without an error message', async () => {
        const { deriveKek } = await import('@/crypto/argon2.ts');

        const result = deriveKek('password', new Uint8Array([1]));

        const worker = MockWorker.instances[0];

        worker.onerror?.(
            new ErrorEvent('error', { message: '' })
        );

        await expect(result).rejects.toThrow('Argon2 worker failed');
    });
});
