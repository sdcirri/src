import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { act } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { ChatDto, ContactCryptoDto, MessageDto, UserDto } from '@/api/types.ts';

type MessageHistoryProps = {
    messages: MessageDto[];
    hasMore: boolean;
    loadingMore: boolean;
    onLoadMore: () => void;
    myPrivateX25519: Uint8Array;
    theirPublicX25519: Uint8Array;
};

const {
    getUserInfo,
    getContactCryptoSpecs,
    getChat,
    sendMessage,
    fromBase64,
    useSession,
    wsSubscribe,
    unsubscribe,
    messageHistory,
} = vi.hoisted(() => ({
    getUserInfo: vi.fn(),
    getContactCryptoSpecs: vi.fn(),
    getChat: vi.fn(),
    sendMessage: vi.fn(),
    fromBase64: vi.fn(),
    useSession: vi.fn(),
    wsSubscribe: vi.fn(),
    unsubscribe: vi.fn(),
    messageHistory: vi.fn(),
}));

vi.mock('@/api/users.ts', () => ({
    getUserInfo,
    getContactCryptoSpecs,
}));

vi.mock('@/api/chat.ts', () => ({
    getChat,
    sendMessage,
}));

vi.mock('@/crypto/common.ts', () => ({
    fromBase64,
}));

vi.mock('@/session/useSession.ts', () => ({
    useSession,
}));

vi.mock('@/components/MessageHistory.tsx', () => ({
    default: (props: MessageHistoryProps) => {
        messageHistory(props);

        return (
            <div
                aria-label='Message history'
                data-has-more={props.hasMore}
                data-loading-more={props.loadingMore}
            >
                <span data-testid='message-ids'>{props.messages.map(m => m.id).join(',')}</span>
                {props.hasMore && (
                    <button
                        type='button'
                        disabled={props.loadingMore}
                        onClick={props.onLoadMore}
                    >
                        {props.loadingMore ? 'Loading' : 'Load more'}
                    </button>
                )}
            </div>
        );
    },
}));

import ChatBox from '@/components/ChatBox.tsx';

const MY_PRIVATE_KEY = new Uint8Array([1, 2, 3]);
const THEIR_PUBLIC_KEY = new Uint8Array([4, 5, 6]);

const CONTACT: UserDto = {
    id: 'contact-id',
    username: 'alice',
    displayName: 'Alice',
    proPic: null,
};

const CONTACT_CRYPTO: ContactCryptoDto = {
    publicEd25519: 'AQID',
    publicX25519: 'BAUG',
};

const CHAT: ChatDto = {
    chatId: 'chat-id',
    contactId: CONTACT.id,
    lastMessage: null,
};

function message(id: string, timestamp: number, chatId = CHAT.chatId): MessageDto {
    return {
        id,
        senderId: CONTACT.id,
        chatId,
        timestamp,
        data: 'AQID',
        iv: 'BAUG',
        direction: 'INCOMING',
    };
}

const MESSAGE_1 = message('message-1', 1);
const MESSAGE_2 = message('message-2', 2);
const SENT_MESSAGE = message('sent-message', 3);

let wsHandler: ((message: MessageDto) => void) | null;

describe('ChatBox', () => {
    beforeEach(() => {
        getUserInfo.mockReset();
        getContactCryptoSpecs.mockReset();
        getChat.mockReset();
        sendMessage.mockReset();
        fromBase64.mockReset();
        useSession.mockReset();
        wsSubscribe.mockReset();
        unsubscribe.mockReset();
        messageHistory.mockClear();

        wsHandler = null;

        getUserInfo.mockResolvedValue(CONTACT);
        getContactCryptoSpecs.mockResolvedValue(CONTACT_CRYPTO);
        getChat.mockResolvedValue([]);
        fromBase64.mockReturnValue(THEIR_PUBLIC_KEY);

        wsSubscribe.mockImplementation((handler: (message: MessageDto) => void) => {
            wsHandler = handler;
            return unsubscribe;
        });

        useSession.mockReturnValue({
            session: {
                keys: {
                    privateX25519: MY_PRIVATE_KEY,
                },
            },
            wsSubscribe,
        });
    });

    it('renders an empty chat when no chat is selected', () => {
        render(<ChatBox chat={null} />);

        expect(getUserInfo).not.toHaveBeenCalled();
        expect(getContactCryptoSpecs).not.toHaveBeenCalled();
        expect(getChat).not.toHaveBeenCalled();
        expect(screen.queryByPlaceholderText('Write something...')).not.toBeInTheDocument();
        expect(screen.queryByLabelText('Message history')).not.toBeInTheDocument();
    });

    it('loads contact, crypto and history', async () => {
        getChat.mockResolvedValueOnce([MESSAGE_2, MESSAGE_1]);

        render(<ChatBox chat={CHAT} />);

        expect(await screen.findByText('Alice')).toBeInTheDocument();
        expect(getUserInfo).toHaveBeenCalledWith(CONTACT.id);
        expect(getContactCryptoSpecs).toHaveBeenCalledWith(CONTACT.id);
        expect(getChat).toHaveBeenCalledWith(CONTACT.id, undefined);
        expect(screen.getByTestId('message-ids')).toHaveTextContent('message-1,message-2');
        expect(fromBase64).toHaveBeenCalledWith(CONTACT_CRYPTO.publicX25519);

        const props = messageHistory.mock.calls.at(-1)![0] as MessageHistoryProps;
        expect(props.myPrivateX25519).toBe(MY_PRIVATE_KEY);
        expect(props.theirPublicX25519).toBe(THEIR_PUBLIC_KEY);
        expect(props.hasMore).toBe(false);
        expect(props.loadingMore).toBe(false);
    });

    it('renders username and profile picture when display name is missing', async () => {
        getUserInfo.mockResolvedValueOnce({
            ...CONTACT,
            displayName: null,
            proPic: 'picture',
        });

        render(<ChatBox chat={CHAT} />);

        expect(await screen.findByText('alice')).toBeInTheDocument();
        expect(screen.getByRole('img', { name: "alice's propic" }))
            .toHaveAttribute('src', 'data:image/jpg;base64,picture');
    });

    it('renders the fallback avatar when profile picture is missing', async () => {
        render(<ChatBox chat={CHAT} />);

        await screen.findByText('Alice');

        expect(document.querySelector('#chat-header svg')).not.toBeNull();
    });

    it('keeps the chat usable when history loading fails', async () => {
        getChat.mockRejectedValueOnce(new Error('history failed'));

        render(<ChatBox chat={CHAT} />);

        expect(await screen.findByPlaceholderText('Write something...')).toBeInTheDocument();
        expect(screen.getByTestId('message-ids')).toHaveTextContent('');

        const props = messageHistory.mock.calls.at(-1)![0] as MessageHistoryProps;
        expect(props.loadingMore).toBe(false);
        expect(props.hasMore).toBe(false);
    });

    it('filters websocket messages by chat and deduplicates them', async () => {
        render(<ChatBox chat={CHAT} />);

        await screen.findByLabelText('Message history');

        const otherChatMessage = message('other-message', 1, 'other-chat');

        act(() => {
            wsHandler!(otherChatMessage);
            wsHandler!(MESSAGE_1);
            wsHandler!(MESSAGE_1);
        });

        expect(screen.getByTestId('message-ids')).toHaveTextContent('message-1');
    });

    it('merges websocket messages received while history is loading', async () => {
        let resolveHistory!: (messages: MessageDto[]) => void;

        getChat.mockReturnValueOnce(new Promise(resolve => {
            resolveHistory = resolve;
        }));

        render(<ChatBox chat={CHAT} />);

        await waitFor(() => {
            expect(getChat).toHaveBeenCalledOnce();
            expect(wsHandler).not.toBeNull();
        });

        act(() => {
            wsHandler!(MESSAGE_2);
        });

        await act(async () => {
            resolveHistory([MESSAGE_2, MESSAGE_1]);
        });

        await waitFor(() => {
            expect(screen.getByTestId('message-ids')).toHaveTextContent('message-1,message-2');
        });
    });

    it('sends a trimmed message, appends it and clears the draft', async () => {
        sendMessage.mockResolvedValueOnce(SENT_MESSAGE);

        render(<ChatBox chat={CHAT} />);

        const input = await screen.findByPlaceholderText('Write something...');

        fireEvent.change(input, { target: { value: '  hello  ' } });
        fireEvent.click(document.querySelector('#send-button')!);

        await waitFor(() => {
            expect(sendMessage).toHaveBeenCalledWith(
                CONTACT.id,
                'hello',
                { privateX25519: MY_PRIVATE_KEY },
                CONTACT_CRYPTO,
            );
        });

        expect(input).toHaveValue('');
        expect(screen.getByTestId('message-ids')).toHaveTextContent('sent-message');

        act(() => {
            wsHandler!(SENT_MESSAGE);
        });

        expect(screen.getByTestId('message-ids').textContent).toBe('sent-message');
    });

    it('sends on Enter but not on other keys', async () => {
        sendMessage.mockResolvedValueOnce(SENT_MESSAGE);

        render(<ChatBox chat={CHAT} />);

        const input = await screen.findByPlaceholderText('Write something...');

        fireEvent.change(input, { target: { value: 'hello' } });
        fireEvent.keyDown(input, { key: 'Escape' });

        expect(sendMessage).not.toHaveBeenCalled();

        fireEvent.keyDown(input, { key: 'Enter' });

        await waitFor(() => {
            expect(sendMessage).toHaveBeenCalledOnce();
        });
    });

    it('does not send an empty message', async () => {
        render(<ChatBox chat={CHAT} />);

        await screen.findByPlaceholderText('Write something...');

        fireEvent.click(document.querySelector('#send-button')!);

        expect(sendMessage).not.toHaveBeenCalled();
    });

    it('does not send when session keys are unavailable', async () => {
        useSession.mockReturnValue({
            session: { keys: null },
            wsSubscribe,
        });

        render(<ChatBox chat={CHAT} />);

        const input = await screen.findByPlaceholderText('Write something...');

        fireEvent.change(input, { target: { value: 'hello' } });
        fireEvent.click(document.querySelector('#send-button')!);

        expect(sendMessage).not.toHaveBeenCalled();
        expect(screen.queryByLabelText('Message history')).not.toBeInTheDocument();
    });

    it('loads older messages using the oldest message as cursor', async () => {
        const firstPage = Array.from(
            { length: 30 },
            (_, i) => message(`message-${30 - i}`, 30 - i),
        );
        const older = message('older-message', 0);

        getChat
            .mockResolvedValueOnce(firstPage)
            .mockResolvedValueOnce([older]);

        render(<ChatBox chat={CHAT} />);

        const loadMore = await screen.findByRole('button', { name: 'Load more' });

        expect(screen.getByTestId('message-ids').textContent)
            .toBe(Array.from({ length: 30 }, (_, i) => `message-${i + 1}`).join(','));

        fireEvent.click(loadMore);

        await waitFor(() => {
            expect(getChat).toHaveBeenNthCalledWith(2, CONTACT.id, 'message-1');
        });

        await waitFor(() => {
            expect(screen.getByTestId('message-ids').textContent)
                .toBe(`older-message,${Array.from({ length: 30 }, (_, i) => `message-${i + 1}`).join(',')}`);
        });

        expect(screen.queryByRole('button', { name: 'Load more' })).not.toBeInTheDocument();
    });

    it('unsubscribes and subscribes again when the selected chat changes', async () => {
        const otherContact: UserDto = {
            ...CONTACT,
            id: 'other-contact',
            username: 'bob',
            displayName: 'Bob',
        };

        getUserInfo
            .mockResolvedValueOnce(CONTACT)
            .mockResolvedValueOnce(otherContact);

        const { rerender } = render(<ChatBox chat={CHAT} />);

        await screen.findByText('Alice');
        expect(wsSubscribe).toHaveBeenCalledOnce();

        rerender(
            <ChatBox
                chat={{
                    chatId: 'other-chat',
                    contactId: otherContact.id,
                    lastMessage: null,
                }}
            />
        );

        await screen.findByText('Bob');

        expect(unsubscribe).toHaveBeenCalledOnce();
        expect(wsSubscribe).toHaveBeenCalledTimes(2);
    });

    it('does not apply a completed load after unmount', async () => {
        let resolveHistory!: (messages: MessageDto[]) => void;

        getChat.mockReturnValueOnce(new Promise(resolve => {
            resolveHistory = resolve;
        }));

        const { unmount } = render(<ChatBox chat={CHAT} />);

        await waitFor(() => {
            expect(getChat).toHaveBeenCalledOnce();
        });

        unmount();

        await act(async () => {
            resolveHistory([MESSAGE_1]);
        });

        expect(messageHistory).not.toHaveBeenCalled();
    });

    it('does not load more when there are no messages', async () => {
        render(<ChatBox chat={CHAT} />);

        await screen.findByLabelText('Message history');

        const props = messageHistory.mock.calls.at(-1)![0] as MessageHistoryProps;

        act(() => {
            props.onLoadMore();
        });

        expect(getChat).toHaveBeenCalledOnce();
    });

    it('does not load more while another page is loading', async () => {
        const firstPage = Array.from(
            { length: 30 },
            (_, i) => message(`message-${30 - i}`, 30 - i),
        );

        let resolveOlder!: (messages: MessageDto[]) => void;

        getChat
            .mockResolvedValueOnce(firstPage)
            .mockReturnValueOnce(new Promise(resolve => {
                resolveOlder = resolve;
            }));

        render(<ChatBox chat={CHAT} />);

        fireEvent.click(await screen.findByRole('button', { name: 'Load more' }));

        await waitFor(() => {
            const props = messageHistory.mock.calls.at(-1)![0] as MessageHistoryProps;
            expect(props.loadingMore).toBe(true);
        });

        const props = messageHistory.mock.calls.at(-1)![0] as MessageHistoryProps;

        act(() => {
            props.onLoadMore();
        });

        expect(getChat).toHaveBeenCalledTimes(2);

        await act(async () => {
            resolveOlder([]);
        });
    });
});
