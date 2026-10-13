import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

type MockResponseInit = {
    ok: boolean;
    status: number;
    body?: string;
    json?: unknown;
};

function mockResponse(init: MockResponseInit) {
    const text = init.body ?? (init.json === undefined ? '' : JSON.stringify(init.json));
    return {
        ok: init.ok,
        status: init.status,
        text: vi.fn().mockResolvedValue(text),
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
        vi.stubEnv('VITE_API_BASE', '');
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

    function deferred<T>() {
        let resolve!: (value: T | PromiseLike<T>) => void;
        let reject!: (reason?: unknown) => void;

        const promise = new Promise<T>((res, rej) => {
            resolve = res;
            reject = rej;
        });

        return { promise, resolve, reject };
    }

    it('defaults API base to empty when unset', async () => {
        vi.unstubAllEnvs();
        vi.stubEnv('VITE_API_BASE', undefined as unknown as string);

        fetchMock.mockResolvedValue(mockResponse({
            ok: true,
            status: 204,
        }));

        const { request } = await loadClient();
        await request('/auth/logout', { method: 'POST' });

        expect(fetchMock).toHaveBeenCalledWith('/auth/logout', expect.any(Object));
    });

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

    it('refreshes on 401 and retries once', async () => {
        fetchMock
            .mockResolvedValueOnce(mockResponse({
                ok: false,
                status: 401,
                body: 'Unauthorized',
            }))
            .mockResolvedValueOnce(mockResponse({
                ok: true,
                status: 204,
            }))
            .mockResolvedValueOnce(mockResponse({
                ok: true,
                status: 200,
                json: { id: 'user-1' },
            }));

        const { request } = await loadClient();

        await expect(request<{ id: string }>('/users/me'))
            .resolves.toEqual({ id: 'user-1' });

        expect(fetchMock).toHaveBeenCalledTimes(3);
        expect(fetchMock.mock.calls[0]?.[0]).toBe('/users/me');
        expect(fetchMock.mock.calls[1]?.[0]).toBe('/auth/refresh');
        expect(fetchMock.mock.calls[2]?.[0]).toBe('/users/me');
    });

    it('does not refresh unauthenticated requests', async () => {
        fetchMock.mockResolvedValue(mockResponse({
            ok: false,
            status: 401,
            body: 'Unauthorized',
        }));

        const { request } = await loadClient();

        await expect(request('/auth/login', {
            method: 'POST',
            body: '{}',
            authenticated: false,
        })).rejects.toMatchObject({ status: 401 });

        expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    it('does not refresh non-401 API errors', async () => {
        fetchMock.mockResolvedValue(mockResponse({
            ok: false,
            status: 403,
            body: 'Forbidden',
        }));

        const { request } = await loadClient();

        await expect(request('/users/me')).rejects.toMatchObject({ status: 403 });

        expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    it('does not refresh non-ApiError failures', async () => {
        const networkError = new TypeError('Network failure');

        fetchMock.mockRejectedValue(networkError);

        const { request } = await loadClient();

        await expect(request('/users/me')).rejects.toBe(networkError);

        expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    it('shares one refresh across concurrent 401 responses', async () => {
        const refresh = deferred<ReturnType<typeof mockResponse>>();
        const attempts = new Map<string, number>();

        fetchMock.mockImplementation((url: string) => {
            if (url === '/auth/refresh')
                return refresh.promise;

            const attempt = attempts.get(url) ?? 0;
            attempts.set(url, attempt + 1);

            return Promise.resolve(
                attempt === 0
                    ? mockResponse({
                        ok: false,
                        status: 401,
                        body: 'Unauthorized',
                    })
                    : mockResponse({
                        ok: true,
                        status: 200,
                        json: { url },
                    })
            );
        });

        const { request } = await loadClient();

        const first = request<{ url: string }>('/a');
        const second = request<{ url: string }>('/b');

        await vi.waitFor(() => {
            expect(
                fetchMock.mock.calls.filter(
                    call => call[0] === '/auth/refresh'
                )
            ).toHaveLength(1);
        });

        refresh.resolve(mockResponse({
            ok: true,
            status: 204,
        }));

        await expect(Promise.all([first, second]))
            .resolves.toEqual([
                { url: '/a' },
                { url: '/b' },
            ]);

        expect(
            fetchMock.mock.calls.filter(call => call[0] === '/auth/refresh')
        ).toHaveLength(1);
    });

    it('does not refresh a late 401 from the previous auth version', async () => {
        const refresh = deferred<ReturnType<typeof mockResponse>>();
        const late401 = deferred<ReturnType<typeof mockResponse>>();

        let aAttempts = 0, bAttempts = 0;

        fetchMock.mockImplementation((url: string) => {
            if (url === '/auth/refresh')
                return refresh.promise;

            if (url === '/a') {
                aAttempts++;

                return Promise.resolve(
                    aAttempts === 1
                        ? mockResponse({
                            ok: false,
                            status: 401,
                            body: 'Unauthorized',
                        })
                        : mockResponse({
                            ok: true,
                            status: 200,
                            json: { id: 'a' },
                        })
                );
            }

            if (url === '/b') {
                bAttempts++;
                if (bAttempts === 1) return late401.promise;

                return Promise.resolve(mockResponse({
                    ok: true,
                    status: 200,
                    json: { id: 'b' },
                }));
            }

            throw new Error(`Unexpected URL: ${url}`);
        });

        const { request } = await loadClient();

        const first = request<{ id: string }>('/a');
        const second = request<{ id: string }>('/b');

        await vi.waitFor(() => {
            expect(
                fetchMock.mock.calls.filter(
                    call => call[0] === '/auth/refresh'
                )
            ).toHaveLength(1);
        });

        refresh.resolve(mockResponse({
            ok: true,
            status: 204,
        }));

        await expect(first)
            .resolves.toEqual({ id: 'a' });

        // B's response belongs to the old auth version, but arrives only after A has already refreshed.
        late401.resolve(mockResponse({
            ok: false,
            status: 401,
            body: 'Unauthorized',
        }));

        await expect(second).resolves.toEqual({ id: 'b' });

        expect(
            fetchMock.mock.calls.filter(call => call[0] === '/auth/refresh')
        ).toHaveLength(1);
    });
});
