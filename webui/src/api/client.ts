import { ApiError } from '@/api/types.ts';

const BASE = import.meta.env.VITE_API_BASE ?? '';

function getCsrfToken(): string | undefined {
    return document.cookie
        .split('; ')
        .find(row => row.startsWith('XSRF-TOKEN='))
        ?.split('=')[1];
}

async function rawRequest<T>(
    path: string,
    init: RequestInit = {},
): Promise<T> {
    const method = (init.method ?? 'GET').toUpperCase();
    const headers = new Headers(init.headers);

    if (!headers.has('Content-Type') && init.body && !(init.body instanceof FormData))
        headers.set('Content-Type', 'application/json');

    if (method !== 'GET' && method !== 'HEAD') {
        const csrf = getCsrfToken();
        if (csrf) headers.set('X-XSRF-TOKEN', decodeURIComponent(csrf));
    }

    const res = await fetch(`${BASE}${path}`, {
        ...init,
        headers,
        credentials: 'include',
    });

    if (!res.ok)
        throw new ApiError(res.status, await res.text());

    const text = await res.text();
    if (text.length === 0) return undefined as T;
    return JSON.parse(text) as T;
}

let refreshPromise: Promise<void> | null = null;
let generation = 0;

async function recoverAuthentication(requestGeneration: number): Promise<void> {
    // someone already refreshed auth
    if (requestGeneration !== generation) return;

    if (!refreshPromise) {
        refreshPromise = rawRequest<void>('/auth/refresh', {
            method: 'POST',
        })
            .then(() => {
                generation++;
            })
            .finally(() => {
                refreshPromise = null;
            });
    }

    await refreshPromise;
}

type RequestOptions = RequestInit & {
    authenticated?: boolean;
};

export async function request<T>(
    path: string,
    { authenticated = true, ...init }: RequestOptions = {},
): Promise<T> {
    const requestGeneration = generation;

    try {
        return await rawRequest<T>(path, init);
    } catch (error) {
        if (!authenticated || !(error instanceof ApiError) || error.status !== 401)
            throw error;

        await recoverAuthentication(requestGeneration);
        return rawRequest<T>(path, init);
    }
}
