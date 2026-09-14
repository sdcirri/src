import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { ContactCryptoDto, UserCryptoDto, UserDto } from '@/api/types';

const { request } = vi.hoisted(() => ({
    request: vi.fn(),
}));

vi.mock('@/api/client.ts', () => ({ request }));

import {
    changeDisplayName,
    changeProPic,
    changeUsername,
    getContactCryptoSpecs,
    getMyCryptoSpecs,
    getUserInfo,
    searchUsers,
} from '@/api/users';

const alice: UserDto = {
    id: 'user-1',
    username: 'alice',
    displayName: 'Alice',
    proPic: '',
};

describe('searchUsers', () => {
    beforeEach(() => {
        request.mockReset();
    });

    it('returns [] without calling the API when the query is shorter than 3', async () => {
        expect(await searchUsers('ab', 0)).toEqual([]);
        expect(await searchUsers('', 0)).toEqual([]);
        expect(request).not.toHaveBeenCalled();
    });

    it('returns [] without calling the API when the page is negative', async () => {
        expect(await searchUsers('alice', -1)).toEqual([]);
        expect(request).not.toHaveBeenCalled();
    });

    it('defaults a null page to 0', async () => {
        request.mockResolvedValue([alice]);

        expect(await searchUsers('alice', null)).toEqual([alice]);
        expect(request).toHaveBeenCalledWith('/users/search?q=alice&n=0');
    });

    it('requests the search endpoint when query and page are valid', async () => {
        request.mockResolvedValue([alice]);

        expect(await searchUsers('alice', 2)).toEqual([alice]);
        expect(request).toHaveBeenCalledWith('/users/search?q=alice&n=2');
    });
});

describe('getUserInfo', () => {
    beforeEach(() => {
        request.mockReset();
    });

    it('gets /users/:id', async () => {
        request.mockResolvedValue(alice);

        expect(await getUserInfo('user-1')).toEqual(alice);
        expect(request).toHaveBeenCalledWith('/users/user-1');
    });
});

describe('getMyCryptoSpecs', () => {
    beforeEach(() => {
        request.mockReset();
    });

    it('gets /users/me/crypto', async () => {
        const specs: UserCryptoDto = {
            id: 'user-1',
            kekSalt: 'salt',
            privateEd25519Crypto: 'ed',
            privateEd25519IV: 'ed-iv',
            publicEd25519: 'ed-pub',
            privateX25519Crypto: 'x',
            privateX25519IV: 'x-iv',
            publicX25519: 'x-pub',
        };
        request.mockResolvedValue(specs);

        expect(await getMyCryptoSpecs()).toEqual(specs);
        expect(request).toHaveBeenCalledWith('/users/me/crypto');
    });
});

describe('getContactCryptoSpecs', () => {
    beforeEach(() => {
        request.mockReset();
    });

    it('gets /users/:id/crypto', async () => {
        const specs: ContactCryptoDto = {
            publicEd25519: 'ed-pub',
            publicX25519: 'x-pub',
        };
        request.mockResolvedValue(specs);

        expect(await getContactCryptoSpecs('contact-1')).toEqual(specs);
        expect(request).toHaveBeenCalledWith('/users/contact-1/crypto');
    });
});

describe('changeDisplayName', () => {
    beforeEach(() => {
        request.mockReset();
    });

    it('puts the display name', async () => {
        request.mockResolvedValue({ ...alice, displayName: 'Alicia' });

        expect(await changeDisplayName('Alicia')).toEqual({ ...alice, displayName: 'Alicia' });
        expect(request).toHaveBeenCalledWith('/users/me/display_name', {
            method: 'PUT',
            body: JSON.stringify({ display_name: 'Alicia' }),
        });
    });
});

describe('changeUsername', () => {
    beforeEach(() => {
        request.mockReset();
    });

    it('puts the username', async () => {
        request.mockResolvedValue({ ...alice, username: 'alicia' });

        expect(await changeUsername('alicia')).toEqual({ ...alice, username: 'alicia' });
        expect(request).toHaveBeenCalledWith('/users/me/username', {
            method: 'PUT',
            body: JSON.stringify({ username: 'alicia' }),
        });
    });
});

describe('changeProPic', () => {
    beforeEach(() => {
        request.mockReset();
    });

    it('sends the image as multipart form data', async () => {
        const image = new Blob(['png'], { type: 'image/png' });
        request.mockResolvedValue(alice);

        expect(await changeProPic(image)).toEqual(alice);
        expect(request).toHaveBeenCalledWith('/users/me/propic', {
            method: 'PUT',
            body: expect.any(FormData),
        });

        const body = request.mock.calls[0]?.[1]?.body as FormData;
        expect(body.get('image')).toBeInstanceOf(Blob);
    });
});
