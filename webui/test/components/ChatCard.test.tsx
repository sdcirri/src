import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { ChatDto, ContactCryptoDto, UserDto } from '@/api/types.ts';

const { getUserInfo, getContactCryptoSpecs, decryptMessage, useSession } = vi.hoisted(() => ({
    getUserInfo: vi.fn(),
    getContactCryptoSpecs: vi.fn(),
    decryptMessage: vi.fn(),
    useSession: vi.fn(),
}));

vi.mock('@/api/users.ts', () => ({
    getUserInfo,
    getContactCryptoSpecs,
}));

vi.mock('@/crypto/messaging.ts', () => ({
    decryptMessage,
}));

vi.mock('@/session/useSession.ts', () => ({
    useSession,
}));

import ChatCard from '@/components/ChatCard.tsx';

const PRIVATE_X25519 = new Uint8Array([1, 2, 3]);

const CONTACT: UserDto = {
    id: 'contact-id',
    username: 'alice',
    displayName: 'Alice',
    proPic: null,
};

const CONTACT_CRYPTO: ContactCryptoDto = {
    publicEd25519: 'AQID',
    publicX25519: 'BwgJ',
};

const CHAT: ChatDto = {
    chatId: 'chat-id',
    contactId: CONTACT.id,
    lastMessage: {
        id: 'message-id',
        senderId: CONTACT.id,
        chatId: 'chat-id',
        timestamp: 1,
        data: 'AQID',
        iv: 'BAUG',
        direction: 'INCOMING',
    },
};

describe('ChatCard', () => {
    beforeEach(() => {
        getUserInfo.mockReset();
        getContactCryptoSpecs.mockReset();
        decryptMessage.mockReset();
        useSession.mockReset();

        getUserInfo.mockResolvedValue(CONTACT);
        getContactCryptoSpecs.mockResolvedValue(CONTACT_CRYPTO);
        decryptMessage.mockResolvedValue('hello');
        useSession.mockReturnValue({
            session: {
                status: 'unlocked',
                keys: {
                    privateX25519: PRIVATE_X25519,
                },
            },
        });
    });

    it('loads and renders the contact', async () => {
        render(<ChatCard chat={CHAT} onSelect={vi.fn()} />);

        expect(await screen.findByText('Alice (@alice)')).toBeInTheDocument();
        expect(getUserInfo).toHaveBeenCalledWith(CONTACT.id);
        expect(getContactCryptoSpecs).toHaveBeenCalledWith(CONTACT.id);
    });

    it('renders only the username when display name is missing', async () => {
        getUserInfo.mockResolvedValueOnce({ ...CONTACT, displayName: null });

        render(<ChatCard chat={CHAT} onSelect={vi.fn()} />);

        expect(await screen.findByText('@alice')).toBeInTheDocument();
    });

    it('renders the profile picture when present', async () => {
        getUserInfo.mockResolvedValueOnce({ ...CONTACT, proPic: 'base64-picture' });

        render(<ChatCard chat={CHAT} onSelect={vi.fn()} />);

        expect(await screen.findByRole('img', { name: "alice's propic" }))
            .toHaveAttribute('src', 'data:image/jpg;base64,base64-picture');
    });

    it('renders the fallback icon when profile picture is missing', async () => {
        const { container } = render(<ChatCard chat={CHAT} onSelect={vi.fn()} />);

        await screen.findByText('Alice (@alice)');

        expect(screen.queryByRole('img')).not.toBeInTheDocument();
        expect(container.querySelector('svg')).not.toBeNull();
    });

    it('decrypts and renders an incoming message preview', async () => {
        render(<ChatCard chat={CHAT} onSelect={vi.fn()} />);

        expect(await screen.findByText('→ hello')).toBeInTheDocument();
        expect(decryptMessage).toHaveBeenCalledWith(
            {
                messageData: new Uint8Array([1, 2, 3]),
                messageIV: new Uint8Array([4, 5, 6]),
            },
            PRIVATE_X25519,
            new Uint8Array([7, 8, 9]),
        );
    });

    it('renders an outgoing message preview', async () => {
        const chat: ChatDto = {
            ...CHAT,
            lastMessage: { ...CHAT.lastMessage!, direction: 'OUTGOING' },
        };

        render(<ChatCard chat={chat} onSelect={vi.fn()} />);

        expect(await screen.findByText('← hello')).toBeInTheDocument();
    });

    it('truncates previews longer than 20 characters', async () => {
        decryptMessage.mockResolvedValueOnce('12345678901234567890EXTRA');

        render(<ChatCard chat={CHAT} onSelect={vi.fn()} />);

        expect(await screen.findByText('→ 12345678901234567890...')).toBeInTheDocument();
    });

    it('does not truncate previews up to 20 characters', async () => {
        decryptMessage.mockResolvedValueOnce('12345678901234567890');

        render(<ChatCard chat={CHAT} onSelect={vi.fn()} />);

        expect(await screen.findByText('→ 12345678901234567890')).toBeInTheDocument();
    });

    it('does not decrypt when there is no last message', async () => {
        const { container } = render(
            <ChatCard chat={{ ...CHAT, lastMessage: null }} onSelect={vi.fn()} />
        );

        await screen.findByText('Alice (@alice)');

        expect(decryptMessage).not.toHaveBeenCalled();
        expect(container.querySelector('.card-preview')).toHaveTextContent('');
    });

    it('does not decrypt when session keys are unavailable', async () => {
        useSession.mockReturnValue({
            session: {
                status: 'locked',
                keys: null,
            },
        });

        const { container } = render(<ChatCard chat={CHAT} onSelect={vi.fn()} />);

        await screen.findByText('Alice (@alice)');

        expect(decryptMessage).not.toHaveBeenCalled();
        expect(container.querySelector('.card-preview')).toHaveTextContent('');
    });

    it('calls onSelect with the chat when clicked', async () => {
        const onSelect = vi.fn();

        render(<ChatCard chat={CHAT} onSelect={onSelect} />);

        fireEvent.click(await screen.findByRole('button'));

        expect(onSelect).toHaveBeenCalledOnce();
        expect(onSelect).toHaveBeenCalledWith(CHAT);
    });

    it('reloads when the contact changes', async () => {
        const { rerender } = render(<ChatCard chat={CHAT} onSelect={vi.fn()} />);

        await screen.findByText('Alice (@alice)');

        getUserInfo.mockResolvedValueOnce({
            ...CONTACT,
            id: 'other-contact',
            username: 'bob',
            displayName: 'Bob',
        });

        rerender(
            <ChatCard
                chat={{ ...CHAT, contactId: 'other-contact' }}
                onSelect={vi.fn()}
            />
        );

        expect(await screen.findByText('Bob (@bob)')).toBeInTheDocument();
        expect(getUserInfo).toHaveBeenLastCalledWith('other-contact');
    });

    it('does not continue loading after unmount', async () => {
        let resolveUser!: (user: UserDto) => void;

        getUserInfo.mockReturnValueOnce(new Promise(resolve => {
            resolveUser = resolve;
        }));

        const { unmount } = render(<ChatCard chat={CHAT} onSelect={vi.fn()} />);

        unmount();
        resolveUser(CONTACT);
        await Promise.resolve();

        expect(decryptMessage).not.toHaveBeenCalled();
    });

    it('does not update the preview after unmount', async () => {
        let resolveDecrypt!: (value: string) => void;

        decryptMessage.mockReturnValueOnce(new Promise(resolve => {
            resolveDecrypt = resolve;
        }));

        const { unmount } = render(<ChatCard chat={CHAT} onSelect={vi.fn()} />);

        await waitFor(() => {
            expect(decryptMessage).toHaveBeenCalledOnce();
        });

        unmount();
        resolveDecrypt('hello');
        await Promise.resolve();
    });
});
