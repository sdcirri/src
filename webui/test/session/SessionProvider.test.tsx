import { expect, it, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { act } from 'react';

import type { MessageDto, UserCryptoDto, UserDto } from '@/api/types.ts';
import type { DecryptedCryptoSpecs } from '@/crypto/kek.ts';
import { SessionProvider } from '@/session/SessionProvider';
import { useSession } from '@/session/useSession';

const fetchMock = vi.fn();

function json(status: number, body?: unknown) {
    return {
        ok: status >= 200 && status < 300,
        status,
        text: async () => body === undefined ? '' : JSON.stringify(body),
    };
}

const USER_ID = '7b2d9f1c-4a63-4d8e-9f27-1c6b5a0e3d91';

const MOCK_CRYPTO: UserCryptoDto = {
    id: USER_ID,
    kekSalt: 'salt',
    privateEd25519Crypto: 'ed-priv',
    privateEd25519IV: 'ed-iv',
    publicEd25519: 'ed-pub',
    privateX25519Crypto: 'x-priv',
    privateX25519IV: 'x-iv',
    publicX25519: 'x-pub',
};

const MOCK_USER: UserDto = {
    id: USER_ID,
    username: 'tester',
    displayName: 'Tester',
    proPic: null
};

const MOCK_KEYS: DecryptedCryptoSpecs = {
    privateEd25519: new Uint8Array(32).fill(1),
    publicEd25519: new Uint8Array(32).fill(2),
    privateX25519: new Uint8Array(32).fill(3),
    publicX25519: new Uint8Array(32).fill(4),
};

const { decryptKeys, bootstrapUserCrypto, connectMessages } = vi.hoisted(() => ({
    decryptKeys: vi.fn(),
    bootstrapUserCrypto: vi.fn(),
    connectMessages: vi.fn(),
}));

vi.mock('@/crypto/kek.ts', () => ({ decryptKeys, bootstrapUserCrypto }));
vi.mock('@/api/ws.ts', () => ({ connectMessages }));

beforeEach(() => {
    fetchMock.mockReset();
    decryptKeys.mockReset();
    connectMessages.mockReset();
    connectMessages.mockReturnValue(vi.fn());
});

afterEach(() => {
    vi.unstubAllGlobals();
});

it('bootstrap restores an existing session as locked', async () => {
    vi.stubGlobal('fetch', fetchMock);
    fetchMock
        .mockResolvedValueOnce(json(200, MOCK_CRYPTO))
        .mockResolvedValueOnce(json(200, MOCK_USER));

    const { result } = renderHook(() => useSession(), {
        wrapper: SessionProvider,
    });

    expect(result.current.session.status).toBe('loading');

    await waitFor(() => {
        expect(result.current.session.status).toBe('locked');
    });
    expect(result.current.session.user).toEqual(MOCK_USER);
    expect(result.current.session.crypto).toEqual(MOCK_CRYPTO);
    expect(result.current.session.keys).toBeNull();
    expect(decryptKeys).not.toHaveBeenCalled();
});

it('bootstrap tries refresh when access token expired', async () => {
    vi.stubGlobal('fetch', fetchMock);
    fetchMock
        .mockResolvedValueOnce(json(401, 'Unauthorized'))
        .mockResolvedValueOnce(json(204))
        .mockResolvedValueOnce(json(200, MOCK_CRYPTO))
        .mockResolvedValueOnce(json(200, MOCK_USER));

    const { result } = renderHook(() => useSession(), {
        wrapper: SessionProvider,
    });

    expect(result.current.session.status).toBe('loading');

    await waitFor(() => {
        expect(result.current.session.status).toBe('locked');
    });
    expect(result.current.session.user).toEqual(MOCK_USER);
    expect(result.current.session.crypto).toEqual(MOCK_CRYPTO);
    expect(result.current.session.keys).toBeNull();
    expect(decryptKeys).not.toHaveBeenCalled();
    expect(fetchMock).toHaveBeenCalledTimes(4);
    expect(fetchMock).toHaveBeenNthCalledWith(
        2,
        expect.stringContaining('refresh'),
        expect.anything(),
    );
});

it('becomes anonymous when session lookup and refresh fail', async () => {
    vi.stubGlobal('fetch', fetchMock);
    fetchMock
        .mockResolvedValueOnce(json(401, 'Unauthorized'))
        .mockResolvedValueOnce(json(401, 'Unauthorized'));

    const { result } = renderHook(() => useSession(), {
        wrapper: SessionProvider,
    });

    expect(result.current.session.status).toBe('loading');

    await waitFor(() => {
        expect(result.current.session.status).toBe('anonymous');
    });

    expect(result.current.session.user).toBeNull();
    expect(result.current.session.crypto).toBeNull();
    expect(result.current.session.keys).toBeNull();
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining('refresh'),
        expect.anything(),
    );
});

it('sign in succeeds and unlocks crypto', async () => {
    vi.stubGlobal('fetch', fetchMock);
    decryptKeys.mockResolvedValueOnce(MOCK_KEYS);
    fetchMock
        // bootstrap fails and user is prompted to login
        .mockResolvedValueOnce(json(401, 'Unauthorized'))
        .mockResolvedValueOnce(json(401, 'Unauthorized'))
        // user logs in and bootstrap succeeds
        .mockResolvedValueOnce(json(204))
        .mockResolvedValueOnce(json(200, MOCK_CRYPTO))
        .mockResolvedValueOnce(json(200, MOCK_USER));

    const { result } = renderHook(() => useSession(), {
        wrapper: SessionProvider,
    });

    await waitFor(() => {
        expect(result.current.session.status).toBe('anonymous');
    });

    await act(async () => {
        await result.current.signIn('tester', 'password');
    });

    expect(result.current.session.status).toBe('unlocked');
    expect(result.current.session.user).toEqual(MOCK_USER);
    expect(result.current.session.crypto).toEqual(MOCK_CRYPTO);
    expect(result.current.session.keys).toEqual(MOCK_KEYS);
    expect(decryptKeys).toHaveBeenCalledWith(MOCK_CRYPTO, 'password');
    expect(fetchMock).toHaveBeenCalledTimes(5);
    expect(fetchMock).toHaveBeenNthCalledWith(
        3,
        expect.stringContaining('login'),
        expect.anything(),
    );
});

it('failed login preserves anonymous state and propagates 403', async () => {
    vi.stubGlobal('fetch', fetchMock);
    fetchMock
        // bootstrap fails and user is prompted to login
        .mockResolvedValueOnce(json(401, 'Unauthorized'))
        .mockResolvedValueOnce(json(401, 'Unauthorized'))
        // user inputs wrong credentials
        .mockResolvedValueOnce(json(403, 'Forbidden'));

    const { result } = renderHook(() => useSession(), {
        wrapper: SessionProvider,
    });

    await waitFor(() => {
        expect(result.current.session.status).toBe('anonymous');
    });

    await expect(
        result.current.signIn('tester', 'password')
    ).rejects.toMatchObject({ name: 'ApiError', status: 403 });

    expect(result.current.session.status).toBe('anonymous');
    expect(result.current.session.user).toBeNull();
    expect(result.current.session.crypto).toBeNull();
    expect(result.current.session.keys).toBeNull();
    expect(decryptKeys).not.toHaveBeenCalled();
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock).toHaveBeenNthCalledWith(
        3,
        expect.stringContaining('login'),
        expect.anything(),
    );
});

it('unlock decrypts keys from an existing locked session', async () => {
    vi.stubGlobal('fetch', fetchMock);
    decryptKeys.mockResolvedValueOnce(MOCK_KEYS);
    fetchMock
        .mockResolvedValueOnce(json(200, MOCK_CRYPTO))
        .mockResolvedValueOnce(json(200, MOCK_USER));

    const { result } = renderHook(() => useSession(), {
        wrapper: SessionProvider,
    });

    await waitFor(() => {
        expect(result.current.session.status).toBe('locked');
    });

    await act(async () => {
        await result.current.unlock('password')
    });

    expect(result.current.session.status).toBe('unlocked');
    expect(result.current.session.user).toEqual(MOCK_USER);
    expect(result.current.session.crypto).toEqual(MOCK_CRYPTO);
    expect(result.current.session.keys).toEqual(MOCK_KEYS);
    expect(decryptKeys).toHaveBeenCalledOnce();
    expect(decryptKeys).toHaveBeenCalledWith(MOCK_CRYPTO, 'password');
    expect(fetchMock).toHaveBeenCalledTimes(2);
});

it('sign up creates and unlocks a new session', async () => {
    vi.stubGlobal('fetch', fetchMock);
    decryptKeys.mockResolvedValueOnce(MOCK_KEYS);
    fetchMock
        // bootstrap → anonymous
        .mockResolvedValueOnce(json(401, 'Unauthorized'))
        .mockResolvedValueOnce(json(401, 'Unauthorized'))
        // user signs up (/register → 201 no content + /register/finalize → 200 with UserCryptoDto)
        .mockResolvedValueOnce(json(201))
        .mockResolvedValueOnce(json(200, MOCK_CRYPTO))
        .mockResolvedValueOnce(json(200, MOCK_USER));

    const { result } = renderHook(() => useSession(), {
        wrapper: SessionProvider,
    });

    await waitFor(() => {
        expect(result.current.session.status).toBe('anonymous');
    });

    await act(async () => {
        await result.current.signUp('tester', 'Tester', 'password')
    });

    expect(result.current.session.status).toBe('unlocked');
    expect(result.current.session.user).toEqual(MOCK_USER);
    expect(result.current.session.crypto).toEqual(MOCK_CRYPTO);
    expect(result.current.session.keys).toEqual(MOCK_KEYS);
    expect(decryptKeys).toHaveBeenCalledOnce();
    expect(decryptKeys).toHaveBeenCalledWith(MOCK_CRYPTO, 'password');
    expect(fetchMock).toHaveBeenCalledTimes(5);
    expect(fetchMock).toHaveBeenNthCalledWith(
        3,
        expect.stringContaining('register'),
        expect.anything(),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
        4,
        expect.stringContaining('register/finalize'),
        expect.anything(),
    );
});

it('sign out resets state to anonymous', async () => {
    vi.stubGlobal('fetch', fetchMock);
    decryptKeys.mockResolvedValueOnce(MOCK_KEYS);
    fetchMock
        .mockResolvedValueOnce(json(200, MOCK_CRYPTO))
        .mockResolvedValueOnce(json(200, MOCK_USER))
        .mockResolvedValueOnce(json(204));

    const { result } = renderHook(() => useSession(), {
        wrapper: SessionProvider,
    });

    await waitFor(() => {
        expect(result.current.session.status).toBe('locked');
    });

    await act(async () => {
        await result.current.unlock('password');
    });

    expect(result.current.session.status).toBe('unlocked');
    expect(fetchMock).toHaveBeenCalledTimes(2);

    await act(async () => {
        await result.current.signOut();
    })

    expect(result.current.session.status).toBe('anonymous');
    expect(result.current.session.user).toBeNull();
    expect(result.current.session.crypto).toBeNull();
    expect(result.current.session.keys).toBeNull();
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock).toHaveBeenNthCalledWith(
        3,
        expect.stringContaining('logout'),
        expect.anything(),
    );
})

it('failed sign out preserves the current session', async () => {
    vi.stubGlobal('fetch', fetchMock);
    decryptKeys.mockResolvedValueOnce(MOCK_KEYS);

    fetchMock
        .mockResolvedValueOnce(json(200, MOCK_CRYPTO))
        .mockResolvedValueOnce(json(200, MOCK_USER))
        // logout fails
        .mockResolvedValueOnce(json(500, 'Internal Server Error'));

    const { result } = renderHook(() => useSession(), {
        wrapper: SessionProvider,
    });

    await waitFor(() => {
        expect(result.current.session.status).toBe('locked');
    });

    await act(async () => {
        await result.current.unlock('password');
    });


    await expect(
        result.current.signOut()
    ).rejects.toMatchObject({ name: 'ApiError', status: 500 });

    expect(result.current.session.status).toBe('unlocked');
    expect(result.current.session.user).toEqual(MOCK_USER);
    expect(result.current.session.crypto).toEqual(MOCK_CRYPTO);
    expect(result.current.session.keys).toEqual(MOCK_KEYS);
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock).toHaveBeenNthCalledWith(
        3,
        expect.stringContaining('logout'),
        expect.anything(),
    );
});

it('updateUser replaces the current user preserving the session', async () => {
    vi.stubGlobal('fetch', fetchMock);

    fetchMock
        .mockResolvedValueOnce(json(200, MOCK_CRYPTO))
        .mockResolvedValueOnce(json(200, MOCK_USER));

    const { result } = renderHook(() => useSession(), {
        wrapper: SessionProvider,
    });

    await waitFor(() => {
        expect(result.current.session.status).toBe('locked');
    });

    const updatedUser: UserDto = {
        ...MOCK_USER,
        displayName: 'Updated Tester',
    };

    act(() => {
        result.current.updateUser(updatedUser);
    });

    expect(result.current.session.user).toEqual(updatedUser);
    expect(result.current.session.status).toBe('locked');
    expect(result.current.session.crypto).toEqual(MOCK_CRYPTO);
    expect(result.current.session.keys).toBeNull();
});

it('becomes anonymous when refresh succeeds but session lookup still fails', async () => {
    vi.stubGlobal('fetch', fetchMock);
    fetchMock
        .mockResolvedValueOnce(json(401, 'Unauthorized'))
        .mockResolvedValueOnce(json(204))
        // session lookup after refresh still fails
        .mockResolvedValueOnce(json(401, 'Unauthorized'));

    const { result } = renderHook(() => useSession(), {
        wrapper: SessionProvider,
    });

    await waitFor(() => {
        expect(result.current.session.status).toBe('anonymous');
    });

    expect(result.current.session.user).toBeNull();
    expect(result.current.session.crypto).toBeNull();
    expect(result.current.session.keys).toBeNull();
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock).toHaveBeenNthCalledWith(
        2,
        expect.stringContaining('refresh'),
        expect.anything(),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
        3,
        expect.stringContaining('me/crypto'),
        expect.anything(),
    );
});

it('dispatches websocket messages to subscribed listeners', async () => {
    vi.stubGlobal('fetch', fetchMock);

    decryptKeys.mockResolvedValueOnce(MOCK_KEYS);

    fetchMock
        .mockResolvedValueOnce(json(200, MOCK_CRYPTO))
        .mockResolvedValueOnce(json(200, MOCK_USER));

    let onMessage!: (message: MessageDto) => void;

    connectMessages.mockImplementation(callback => {
        onMessage = callback;
        return vi.fn();
    });

    const { result } = renderHook(() => useSession(), {
        wrapper: SessionProvider,
    });

    await waitFor(() => {
        expect(result.current.session.status).toBe('locked');
    });

    await act(async () => {
        await result.current.unlock('password');
    });

    await waitFor(() => {
        expect(connectMessages).toHaveBeenCalledOnce();
    });

    const listener = vi.fn();
    result.current.wsSubscribe(listener);

    const message: MessageDto = {
        id: '3785eb96-d74b-4dd0-8307-f126fc59a20f',
        senderId: '6f1b2c9e-3a74-4d8f-9c21-5e7a63b0f412',
        chatId: 'a8d47f35-1c62-4be9-b7d3-92f6c10e548a',
        timestamp: 1,
        data: 'wire-data',
        iv: 'wire-iv',
        direction: 'OUTGOING',
    };

    act(() => { onMessage(message); });

    expect(listener).toHaveBeenCalledOnce();
    expect(listener).toHaveBeenCalledWith(message);
});

it('does not dispatch websocket messages after unsubscribe', async () => {
    vi.stubGlobal('fetch', fetchMock);

    decryptKeys.mockResolvedValueOnce(MOCK_KEYS);

    fetchMock
        .mockResolvedValueOnce(json(200, MOCK_CRYPTO))
        .mockResolvedValueOnce(json(200, MOCK_USER));

    let onMessage: ((message: MessageDto) => void) | undefined;

    connectMessages.mockImplementation(callback => {
        onMessage = callback;
        return vi.fn();
    });

    const { result } = renderHook(() => useSession(), {
        wrapper: SessionProvider,
    });

    await waitFor(() => {
        expect(result.current.session.status).toBe('locked');
    });
    await act(async () => {
        await result.current.unlock('password');
    });
    await waitFor(() => {
        expect(connectMessages).toHaveBeenCalledOnce();
    });

    expect(onMessage).toBeTypeOf('function');

    const listener = vi.fn();
    const unsubscribe = result.current.wsSubscribe(listener);
    unsubscribe();

    const message: MessageDto = {
        id: '3785eb96-d74b-4dd0-8307-f126fc59a20f',
        senderId: '6f1b2c9e-3a74-4d8f-9c21-5e7a63b0f412',
        chatId: 'a8d47f35-1c62-4be9-b7d3-92f6c10e548a',
        timestamp: 1,
        data: 'wire-data',
        iv: 'wire-iv',
        direction: 'OUTGOING',
    };
    act(() => { onMessage!(message); });

    expect(listener).not.toHaveBeenCalled();
});

it('disconnects websocket on unmount', async () => {
    vi.stubGlobal('fetch', fetchMock);

    decryptKeys.mockResolvedValueOnce(MOCK_KEYS);

    fetchMock
        .mockResolvedValueOnce(json(200, MOCK_CRYPTO))
        .mockResolvedValueOnce(json(200, MOCK_USER));

    const disconnect = vi.fn();

    connectMessages.mockReturnValue(disconnect);

    const { result, unmount } = renderHook(() => useSession(), {
        wrapper: SessionProvider,
    });

    await waitFor(() => {
        expect(result.current.session.status).toBe('locked');
    });

    await act(async () => {
        await result.current.unlock('password');
    });

    await waitFor(() => {
        expect(connectMessages).toHaveBeenCalledOnce();
    });

    unmount();

    expect(disconnect).toHaveBeenCalledOnce();
});
