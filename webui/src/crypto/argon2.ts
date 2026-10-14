

type DeriveKekResponse =
    | { id: number; kek: Uint8Array }
    | { id: number; error: string };

type PendingRequest = {
    resolve: (kek: Uint8Array) => void;
    reject: (error: Error) => void;
};

let worker: Worker | null = null;
let nextId = 0;

const pending = new Map<number, PendingRequest>();

function getWorker(): Worker {
    if (worker)
        return worker;

    worker = new Worker(
        new URL('./argon2.worker.ts', import.meta.url),
        { type: 'module' }
    );

    worker.onmessage = (event: MessageEvent<DeriveKekResponse>) => {
        const response = event.data;
        const request = pending.get(response.id);
        if (!request) return;

        pending.delete(response.id);

        if ('error' in response)
            request.reject(new Error(response.error));
        else
            request.resolve(response.kek);
    };

    worker.onerror = event => {
        const error = new Error(event.message || 'Argon2 worker failed');

        pending.forEach(request => request.reject(error));
        pending.clear();
    };

    return worker;
}

export function deriveKek(password: string, salt: Uint8Array): Promise<Uint8Array> {
    const id = nextId++;

    return new Promise((resolve, reject) => {
        pending.set(id, { resolve, reject });
        getWorker().postMessage({
            id,
            password,
            salt,
        });
    });
}
