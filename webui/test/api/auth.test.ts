import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { UserCryptoDto, UserRegistrationRequest } from '@/api/types';

const { request, bootstrapUserCrypto, reEncryptSpecs } = vi.hoisted(() => ({
    request: vi.fn(),
    bootstrapUserCrypto: vi.fn(),
    reEncryptSpecs: vi.fn(),
}));

vi.mock('@/api/client.ts', () => ({ request }));
vi.mock('@/crypto/kek.ts', () => ({ bootstrapUserCrypto, reEncryptSpecs }));

import { changePassword, login, logout, refreshSession, register } from '@/api/auth';

const registration: UserRegistrationRequest = {
    username: 'alice',
    displayName: 'Alice',
    password: 'Password1!',
};

const bootCrypto = {
    kekSalt: 'salt',
    privateEd25519Crypto: 'ed-priv',
    privateEd25519IV: 'ed-iv',
    publicEd25519: 'ed-pub',
    privateX25519Crypto: 'x-priv',
    privateX25519IV: 'x-iv',
    publicX25519: 'x-pub',
};

const finalized: UserCryptoDto = {
    id: 'user-1',
    ...bootCrypto,
};

const oldCrypto: UserCryptoDto = {
    id: 'user-1',
    kekSalt: 'old-salt',
    privateEd25519Crypto: 'old-ed',
    privateEd25519IV: 'old-ed-iv',
    publicEd25519: 'ed-pub',
    privateX25519Crypto: 'old-x',
    privateX25519IV: 'old-x-iv',
    publicX25519: 'x-pub',
};

const newCrypto: UserCryptoDto = {
    ...oldCrypto,
    kekSalt: 'new-salt',
    privateEd25519Crypto: 'new-ed',
    privateEd25519IV: 'new-ed-iv',
    privateX25519Crypto: 'new-x',
    privateX25519IV: 'new-x-iv',
};

describe('login', () => {
    beforeEach(() => {
        request.mockReset();
    });

    it('posts credentials to /auth/login', async () => {
        const credentials = { username: 'alice', password: 'Password1!' };
        request.mockResolvedValue(undefined);

        await login(credentials);

        expect(request).toHaveBeenCalledWith('/auth/login', {
            method: 'POST',
            body: JSON.stringify(credentials),
        });
    });
});

describe('refreshSession', () => {
    beforeEach(() => {
        request.mockReset();
    });

    it('posts to /auth/refresh', async () => {
        request.mockResolvedValue(undefined);

        await refreshSession();

        expect(request).toHaveBeenCalledWith('/auth/refresh', {
            method: 'POST',
        });
    });
});

describe('logout', () => {
    beforeEach(() => {
        request.mockReset();
    });

    it('posts to /auth/logout', async () => {
        request.mockResolvedValue(undefined);

        await logout();

        expect(request).toHaveBeenCalledWith('/auth/logout', {
            method: 'POST',
        });
    });
});

describe('register', () => {
    beforeEach(() => {
        request.mockReset();
        bootstrapUserCrypto.mockReset();
        reEncryptSpecs.mockReset();
    });

    it('registers then finalizes with bootstrapped crypto', async () => {
        request.mockResolvedValueOnce(undefined).mockResolvedValueOnce(finalized);
        bootstrapUserCrypto.mockResolvedValue(bootCrypto);

        expect(await register(registration)).toEqual(finalized);

        expect(request).toHaveBeenNthCalledWith(1, '/auth/register', {
            method: 'POST',
            body: JSON.stringify(registration),
        });
        expect(bootstrapUserCrypto).toHaveBeenCalledWith(registration.password);
        expect(request).toHaveBeenNthCalledWith(2, '/auth/register/finalize', {
            method: 'POST',
            body: JSON.stringify(bootCrypto),
        });
    });

    it('does not bootstrap or finalize when registration fails', async () => {
        request.mockRejectedValueOnce(new Error('username taken'));

        await expect(register(registration)).rejects.toThrow('username taken');
        expect(bootstrapUserCrypto).not.toHaveBeenCalled();
        expect(request).toHaveBeenCalledTimes(1);
    });
});

describe('changePassword', () => {
    beforeEach(() => {
        request.mockReset();
        bootstrapUserCrypto.mockReset();
        reEncryptSpecs.mockReset();
    });

    it('re-encrypts specs and posts the remapped password payload', async () => {
        reEncryptSpecs.mockResolvedValue(newCrypto);
        request.mockResolvedValue(undefined);

        await changePassword('Password1!', 'NewPassw0rd!1', oldCrypto);

        expect(reEncryptSpecs).toHaveBeenCalledWith('Password1!', 'NewPassw0rd!1', oldCrypto);
        expect(request).toHaveBeenCalledWith('/auth/me/password', {
            method: 'POST',
            body: JSON.stringify({
                password: 'NewPassw0rd!1',
                newKekSalt: newCrypto.kekSalt,
                newPrivateEd25519: newCrypto.privateEd25519Crypto,
                newIvEd25519: newCrypto.privateEd25519IV,
                newPrivateX25519: newCrypto.privateX25519Crypto,
                newIvX25519: newCrypto.privateX25519IV,
            }),
        });
    });

    it('does not call the API when re-encryption fails', async () => {
        reEncryptSpecs.mockRejectedValue(new Error('wrong password'));

        await expect(changePassword('nope', 'NewPassw0rd!1', oldCrypto))
            .rejects
            .toThrow('wrong password');
        expect(request).not.toHaveBeenCalled();
    });
});
