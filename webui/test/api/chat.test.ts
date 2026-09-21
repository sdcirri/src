import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { ChatDto, ContactCryptoDto, MessageDto } from '@/api/types';
import type { DecryptedCryptoSpecs } from '@/crypto/kek';
import { fromBase64, toBase64 } from '@/crypto/common';
import { generateX25519KeyPair } from '@/crypto/keys';
import { decryptMessage } from '@/crypto/messaging';

const { request } = vi.hoisted(() => ({
    request: vi.fn(),
}));

vi.mock('@/api/client.ts', () => ({ request }));

import {getChat, getChats, sendMessage} from '@/api/chat';

function ownSpecs(privateX25519: Uint8Array, publicX25519: Uint8Array): DecryptedCryptoSpecs {
    return {
        privateEd25519: new Uint8Array(32),
        publicEd25519: new Uint8Array(32),
        privateX25519,
        publicX25519,
    };
}

describe('getChat', () => {
    beforeEach(() => {
        request.mockReset();
    });

    it('posts the contact history with the given page', async () => {
        const messages: MessageDto[] = [{
            timestamp: 1,
            data: 'wire-data',
            iv: 'wire-iv',
            direction: 'INCOMING',
        }];
        request.mockResolvedValue(messages);

        expect(await getChat('contact-1', 2)).toEqual(messages);
        expect(request).toHaveBeenCalledWith('/chats/contact-1?pageNumber=2', {
            method: 'POST',
        });
    });

    it('defaults pageNumber to 0', async () => {
        request.mockResolvedValue([]);

        expect(await getChat('contact-1')).toEqual([]);
        expect(request).toHaveBeenCalledWith('/chats/contact-1?pageNumber=0', {
            method: 'POST',
        });
    });
});

describe('getChats', () => {
    beforeEach(() => {
        request.mockReset();
    });

    it('gets the current user chat list', async () => {
        const chats: ChatDto[] = [{
            chatId: 'chat-1',
            contactId: 'contact-1',
            lastMessage: {
                timestamp: 1,
                data: 'wire-data',
                iv: 'wire-iv',
                direction: 'INCOMING',
            },
        }];
        request.mockResolvedValue(chats);

        expect(await getChats()).toEqual(chats);
        expect(request).toHaveBeenCalledWith('/chats');
    });
});

describe('sendMessage', () => {
    beforeEach(() => {
        request.mockReset();
    });

    it('posts ciphertext and iv, not the plaintext', async () => {
        const alice = generateX25519KeyPair();
        const bob = generateX25519KeyPair();
        const sent: MessageDto = {
            timestamp: 1,
            data: 'wire-data',
            iv: 'wire-iv',
            direction: 'OUTGOING',
        };
        request.mockResolvedValue(sent);

        const contact: ContactCryptoDto = {
            publicEd25519: toBase64(new Uint8Array(32)),
            publicX25519: toBase64(bob.publicKey),
        };

        expect(
            await sendMessage('contact-1', 'hello from alice', ownSpecs(alice.privateKey, alice.publicKey), contact),
        ).toEqual(sent);

        expect(request).toHaveBeenCalledWith('/chats/contact-1', {
            method: 'POST',
            body: expect.any(String),
        });

        const body = JSON.parse(request.mock.calls[0]?.[1]?.body as string) as {
            messageData: string;
            messageIV: string;
        };
        expect(body.messageData).not.toContain('hello');
        expect(fromBase64(body.messageIV).byteLength).toBe(12);

        const decrypted = await decryptMessage(
            { messageData: fromBase64(body.messageData), messageIV: fromBase64(body.messageIV) },
            bob.privateKey,
            alice.publicKey,
        );
        expect(decrypted).toBe('hello from alice');
    });
});
