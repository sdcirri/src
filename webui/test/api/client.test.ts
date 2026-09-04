import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

type MockResponseInit = {
    ok: boolean;
    status: number;
    body?: string;
    json?: unknown;
};

function mockResponse(init: MockResponseInit) {
    return {
        ok: init.ok,
        status: init.status,
        text: vi.fn().mockResolvedValue(init.body ?? ''),
        json: vi.fn().mockResolvedValue(init.json),
    };
}

function setCookie(name: string, value: string) {
    document.cookie = `${name}=${encodeURIComponent(value)}; path=/`;
}

function clearCookies() {
    for (const part of document.cookie.split(';')) {
        const name = part.split('=')[0]?.trim();
        if (name) {
            document.cookie = `${name}=; Max-Age=0; path=/`;
        }
    }
}

describe('request', () => {
    const fetchMock = vi.fn();

    beforeEach(() => {
        vi.resetModules();
        vi.stubGlobal('fetch', fetchMock);
        clearCookies();
        fetchMock.mockReset();
    });

    afterEach(() => {
        vi.unstubAllGlobals();
        vi.unstubAllEnvs();
        clearCookies();
    });

    async function loadClient() {
        return import('@/api/client');
    }

    function lastFetchInit(): RequestInit {
        return fetchMock.mock.calls.at(-1)?.[1] as RequestInit;
    }

    function lastFetchHeaders(): Headers {
        return lastFetchInit().headers as Headers;
    }

    it('performs GET and parses JSON responses', async () => {
        fetchMock.mockResolvedValue(mockResponse({
            ok: true,
            status: 200,
            json: { id: 'user-1' },
        }));

        const { request } = await loadClient();
        const result = await request<{ id: string }>('/users/me');

        expect(fetchMock).toHaveBeenCalledWith('/users/me', expect.objectContaining({
            credentials: 'include',
        }));
        expect(lastFetchHeaders().has('Content-Type')).toBe(false);
        expect(lastFetchHeaders().has('X-XSRF-TOKEN')).toBe(false);
        expect(result).toEqual({ id: 'user-1' });
    });

    it('returns undefined for 204 responses', async () => {
        fetchMock.mockResolvedValue(mockResponse({
            ok: true,
            status: 204,
        }));

        const { request } = await loadClient();
        const result = await request<void>('/auth/login', { method: 'POST', body: '{}' });

        expect(result).toBeUndefined();
    });

    it('sets JSON content type for mutating requests with a body', async () => {
        fetchMock.mockResolvedValue(mockResponse({
            ok: true,
            status: 204,
        }));

        const { request } = await loadClient();
        await request('/auth/login', {
            method: 'POST',
            body: JSON.stringify({ username: 'user1', password: 'Password1!' }),
        });

        expect(lastFetchHeaders().get('Content-Type')).toBe('application/json');
    });

    it('preserves an explicit content type header', async () => {
        fetchMock.mockResolvedValue(mockResponse({
            ok: true,
            status: 204,
        }));

        const { request } = await loadClient();
        await request('/upload', {
            method: 'POST',
            headers: { 'Content-Type': 'text/plain' },
            body: 'raw-body',
        });

        expect(lastFetchHeaders().get('Content-Type')).toBe('text/plain');
    });

    it('adds the decoded CSRF header for mutating requests', async () => {
        setCookie('other', 'value');
        setCookie('XSRF-TOKEN', 'csrf%token');
        fetchMock.mockResolvedValue(mockResponse({
            ok: true,
            status: 204,
        }));

        const { request } = await loadClient();
        await request('/auth/logout', { method: 'POST' });

        expect(lastFetchHeaders().get('X-XSRF-TOKEN')).toBe('csrf%token');
    });

    it('does not add CSRF for GET requests even when the cookie exists', async () => {
        setCookie('XSRF-TOKEN', 'abc123');
        fetchMock.mockResolvedValue(mockResponse({
            ok: true,
            status: 200,
            json: [],
        }));

        const { request } = await loadClient();
        await request('/users/search?q=alice');

        expect(lastFetchHeaders().has('X-XSRF-TOKEN')).toBe(false);
    });

    it('does not add CSRF when the token cookie is missing', async () => {
        setCookie('other', 'value');
        fetchMock.mockResolvedValue(mockResponse({
            ok: true,
            status: 204,
        }));

        const { request } = await loadClient();
        await request('/auth/logout', { method: 'POST' });

        expect(lastFetchHeaders().has('X-XSRF-TOKEN')).toBe(false);
    });

    it('throws ApiError with status and response text on failure', async () => {
        fetchMock.mockResolvedValue(mockResponse({
            ok: false,
            status: 401,
            body: 'Unauthorized',
        }));

        const [{ request }, { ApiError }] = await Promise.all([
            loadClient(),
            import('@/api/types'),
        ]);

        await expect(request('/auth/login', { method: 'POST', body: '{}' }))
            .rejects
            .toSatisfy((error: unknown) => {
                expect(error).toBeInstanceOf(ApiError);
                expect(error).toMatchObject({
                    status: 401,
                    message: 'Unauthorized',
                    name: 'ApiError',
                });
                return true;
            });
    });

    it('uses the configured API base URL', async () => {
        vi.stubEnv('VITE_API_BASE', 'http://api.test');

        fetchMock.mockResolvedValue(mockResponse({
            ok: true,
            status: 200,
            json: { ok: true },
        }));

        const { request } = await loadClient();
        await request('/health');

        expect(fetchMock).toHaveBeenCalledWith('http://api.test/health', expect.any(Object));
    });
});
