import { ApiError } from '@/api/types.ts';

const BASE = import.meta.env.VITE_API_BASE ?? '';

function getCsrfToken(): string | undefined {
    return document.cookie
        .split('; ')
        .find(row => row.startsWith('XSRF-TOKEN='))
        ?.split('=')[1];
}

export async function request<T>(
    path: string,
    init: RequestInit = {},
): Promise<T> {
    const method = (init.method ?? 'GET').toUpperCase();
    const headers = new Headers(init.headers);

    if (!headers.has('Content-Type') && init.body) {
        headers.set('Content-Type', 'application/json');
    }
    if (method !== 'GET' && method !== 'HEAD') {
        const csrf = getCsrfToken();
        if (csrf) headers.set('X-XSRF-TOKEN', decodeURIComponent(csrf));
    }

    const res = await fetch(`${BASE}${path}`, {
        ...init,
        headers,
        credentials: 'include',
    });

    if (!res.ok) {
        throw new ApiError(res.status, await res.text());
    }
    if (res.status === 204) return undefined as T;
    return (await res.json()) as T;
}
