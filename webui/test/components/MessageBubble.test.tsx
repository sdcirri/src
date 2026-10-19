import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { MessageDto } from '@/api/types.ts';

const { decryptMessage } = vi.hoisted(() => ({
    decryptMessage: vi.fn(),
}));

vi.mock('@/crypto/messaging.ts', () => ({
    decryptMessage,
}));

import MessageBubble from '@/components/MessageBubble.tsx';

const MY_PRIVATE_KEY = new Uint8Array([1, 2, 3]);
const THEIR_PUBLIC_KEY = new Uint8Array([4, 5, 6]);

const MESSAGE: MessageDto = {
    id: '3785eb96-d74b-4dd0-8307-f126fc59a20f',
    senderId: '6f1b2c9e-3a74-4d8f-9c21-5e7a63b0f412',
    chatId: 'a8d47f35-1c62-4be9-b7d3-92f6c10e548a',
    timestamp: Date.UTC(2026, 9, 15, 10, 30),
    data: 'AQID',
    iv: 'BAUG',
    direction: 'INCOMING',
};

describe('MessageBubble', () => {
    beforeEach(() => {
        decryptMessage.mockReset();
    });

    it('decrypts and renders the message plaintext', async () => {
        decryptMessage.mockResolvedValueOnce('hello');

        render(
            <MessageBubble
                message={MESSAGE}
                myPrivateX25519={MY_PRIVATE_KEY}
                theirPublicX25519={THEIR_PUBLIC_KEY}
            />
        );

        expect(await screen.findByText('hello')).toBeInTheDocument();
    });

    it('passes decoded encrypted payload and keys to decryptMessage', async () => {
        decryptMessage.mockResolvedValueOnce('hello');

        render(
            <MessageBubble
                message={MESSAGE}
                myPrivateX25519={MY_PRIVATE_KEY}
                theirPublicX25519={THEIR_PUBLIC_KEY}
            />
        );

        await waitFor(() => {
            expect(decryptMessage).toHaveBeenCalledOnce();
        });

        expect(decryptMessage).toHaveBeenCalledWith(
            {
                messageData: new Uint8Array([1, 2, 3]),
                messageIV: new Uint8Array([4, 5, 6]),
            },
            MY_PRIVATE_KEY,
            THEIR_PUBLIC_KEY,
        );
    });

    it.each([
        ['INCOMING', 'incoming'],
        ['OUTGOING', 'outgoing'],
    ] as const)(
        'uses the %s direction as CSS class',
        async (direction, cssClass) => {
            decryptMessage.mockResolvedValueOnce('hello');

            const { container } = render(
                <MessageBubble
                    message={{ ...MESSAGE, direction }}
                    myPrivateX25519={MY_PRIVATE_KEY}
                    theirPublicX25519={THEIR_PUBLIC_KEY}
                />
            );

            await screen.findByText('hello');

            expect(container.firstElementChild).toHaveClass('message-bubble', cssClass);
        }
    );

    it('renders the message timestamp', async () => {
        decryptMessage.mockResolvedValueOnce('hello');

        render(
            <MessageBubble
                message={MESSAGE}
                myPrivateX25519={MY_PRIVATE_KEY}
                theirPublicX25519={THEIR_PUBLIC_KEY}
            />
        );

        await screen.findByText('hello');

        const expectedTimestamp = new Date(MESSAGE.timestamp)
            .toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

        expect(screen.getByText(expectedTimestamp)).toBeInTheDocument();
    });

    it('decrypts again when the message changes', async () => {
        decryptMessage
            .mockResolvedValueOnce('first')
            .mockResolvedValueOnce('second');

        const { rerender } = render(
            <MessageBubble
                message={MESSAGE}
                myPrivateX25519={MY_PRIVATE_KEY}
                theirPublicX25519={THEIR_PUBLIC_KEY}
            />
        );

        expect(await screen.findByText('first')).toBeInTheDocument();

        rerender(
            <MessageBubble
                message={{ ...MESSAGE, id: 'second-id', data: 'BwgJ' }}
                myPrivateX25519={MY_PRIVATE_KEY}
                theirPublicX25519={THEIR_PUBLIC_KEY}
            />
        );

        expect(await screen.findByText('second')).toBeInTheDocument();
        expect(decryptMessage).toHaveBeenCalledTimes(2);
        expect(screen.queryByText('first')).not.toBeInTheDocument();
    });
});
