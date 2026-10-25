import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { ChatDto, MessageDto, UserDto } from '@/api/types.ts';

const {
    navigate,
    signOut,
    wsSubscribe,
    unsubscribe,
    getChats,
    searchUsers,
    useSession,
    chatList,
    chatBox,
} = vi.hoisted(() => ({
    navigate: vi.fn(),
    signOut: vi.fn(),
    wsSubscribe: vi.fn(),
    unsubscribe: vi.fn(),
    getChats: vi.fn(),
    searchUsers: vi.fn(),
    useSession: vi.fn(),
    chatList: vi.fn(),
    chatBox: vi.fn(),
}));

vi.mock('react-router', () => ({ useNavigate: () => navigate }));
vi.mock('@/api/chat.ts', () => ({ getChats }));
vi.mock('@/api/users.ts', () => ({ searchUsers }));
vi.mock('@/session/useSession.ts', () => ({ useSession }));
vi.mock('@/components/AccountPopover.tsx', () => ({ default: () => <div>Account</div> }));
vi.mock('@/components/ChatList.tsx', () => ({
    default: ({ chats, onSelect }: { chats: ChatDto[]; onSelect: (chat: ChatDto) => void }) => {
        chatList({ chats, onSelect });
        return (
            <div aria-label='Chat list'>
                {chats.map(chat => (
                    <button key={chat.chatId || chat.contactId} onClick={() => onSelect(chat)}>
                        {chat.chatId || `fake:${chat.contactId}`}
                    </button>
                ))}
            </div>
        );
    },
}));
vi.mock('@/components/ChatBox.tsx', () => ({
    default: ({ chat }: { chat: ChatDto | null }) => {
        chatBox({ chat });
        return <div aria-label='Chat box'>{chat?.contactId ?? 'none'}</div>;
    },
}));

import MainPage from '@/pages/MainPage.tsx';

const CHAT_1: ChatDto = {
    chatId: 'chat-1',
    contactId: 'contact-1',
    lastMessage: null,
};

const CHAT_2: ChatDto = {
    chatId: 'chat-2',
    contactId: 'contact-2',
    lastMessage: null,
};

const USER: UserDto = {
    id: 'contact-3',
    username: 'alice',
    displayName: 'Alice',
    proPic: null,
};

function message(chatId: string, id = 'message-1'): MessageDto {
    return {
        id,
        senderId: 'sender-id',
        chatId,
        timestamp: 1,
        data: 'AQID',
        iv: 'BAUG',
        direction: 'INCOMING',
    };
}

let wsHandler: ((message: MessageDto) => void) | null;

describe('MainPage', () => {
    beforeEach(() => {
        navigate.mockReset();
        signOut.mockReset();
        wsSubscribe.mockReset();
        unsubscribe.mockReset();
        getChats.mockReset();
        searchUsers.mockReset();
        useSession.mockReset();
        chatList.mockClear();
        chatBox.mockClear();

        wsHandler = null;
        signOut.mockResolvedValue(undefined);
        getChats.mockResolvedValue([CHAT_1, CHAT_2]);
        searchUsers.mockResolvedValue([]);
        wsSubscribe.mockImplementation((handler: (message: MessageDto) => void) => {
            wsHandler = handler;
            return unsubscribe;
        });
        useSession.mockReturnValue({ signOut, wsSubscribe });
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it('loads and renders chats', async () => {
        render(<MainPage />);

        expect(await screen.findByRole('button', { name: 'chat-1' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'chat-2' })).toBeInTheDocument();
        expect(getChats).toHaveBeenCalledOnce();
        expect(screen.getByLabelText('Chat box')).toHaveTextContent('none');
    });

    it('selects a chat', async () => {
        render(<MainPage />);

        fireEvent.click(await screen.findByRole('button', { name: 'chat-2' }));

        expect(screen.getByLabelText('Chat box')).toHaveTextContent('contact-2');
    });

    it('signs out and navigates to login', async () => {
        render(<MainPage />);

        fireEvent.click(screen.getByRole('button', { name: 'Log out' }));

        await waitFor(() => {
            expect(signOut).toHaveBeenCalledOnce();
            expect(navigate).toHaveBeenCalledWith('/login', { replace: true });
        });
    });

    it('moves an existing chat to the top when a websocket message arrives', async () => {
        render(<MainPage />);
        await screen.findByRole('button', { name: 'chat-1' });

        const incoming = message('chat-2');

        act(() => {
            wsHandler!(incoming);
        });

        const props = chatList.mock.calls.at(-1)![0] as { chats: ChatDto[] };
        expect(props.chats.map(chat => chat.chatId)).toEqual(['chat-2', 'chat-1']);
        expect(props.chats[0].lastMessage).toEqual(incoming);
        expect(getChats).toHaveBeenCalledOnce();
    });

    it('refreshes chats when websocket message belongs to an unknown chat', async () => {
        const refreshed: ChatDto = {
            chatId: 'chat-3',
            contactId: 'contact-3',
            lastMessage: message('chat-3'),
        };

        getChats
            .mockResolvedValueOnce([CHAT_1])
            .mockResolvedValueOnce([refreshed, CHAT_1]);

        render(<MainPage />);
        await screen.findByRole('button', { name: 'chat-1' });

        act(() => {
            wsHandler!(message('chat-3'));
        });

        await waitFor(() => {
            expect(screen.getByRole('button', { name: 'chat-3' })).toBeInTheDocument();
        });

        expect(getChats).toHaveBeenCalledTimes(2);
    });

    it('replaces a selected fake chat with the persisted chat after websocket refresh', async () => {
        vi.useFakeTimers();

        getChats
            .mockResolvedValueOnce([])
            .mockResolvedValueOnce([{
                chatId: 'chat-3',
                contactId: USER.id,
                lastMessage: message('chat-3'),
            }]);
        searchUsers.mockResolvedValueOnce([USER]);

        render(<MainPage />);
        await act(async () => { await Promise.resolve(); });

        fireEvent.change(screen.getByPlaceholderText('Search for users...'), {
            target: { value: 'alice' },
        });

        await act(async () => {
            vi.advanceTimersByTime(300);
            await Promise.resolve();
        });

        fireEvent.click(screen.getByRole('button', { name: `fake:${USER.id}` }));
        expect(screen.getByLabelText('Chat box')).toHaveTextContent(USER.id);

        act(() => {
            wsHandler!(message('chat-3'));
        });

        await act(async () => { await Promise.resolve(); });

        const props = chatBox.mock.calls.at(-1)![0] as { chat: ChatDto | null };
        expect(props.chat?.chatId).toBe('chat-3');
        expect(props.chat?.contactId).toBe(USER.id);
    });

    it('does not replace a persisted current chat during unknown-chat refresh', async () => {
        getChats
            .mockResolvedValueOnce([CHAT_1])
            .mockResolvedValueOnce([CHAT_1, CHAT_2]);

        render(<MainPage />);
        fireEvent.click(await screen.findByRole('button', { name: 'chat-1' }));

        act(() => {
            wsHandler!(message('unknown-chat'));
        });

        await waitFor(() => {
            expect(getChats).toHaveBeenCalledTimes(2);
        });

        expect(screen.getByLabelText('Chat box')).toHaveTextContent(CHAT_1.contactId);
    });

    it('does not search queries shorter than three trimmed characters', async () => {
        vi.useFakeTimers();
        render(<MainPage />);
        await act(async () => { await Promise.resolve(); });

        fireEvent.change(screen.getByPlaceholderText('Search for users...'), {
            target: { value: ' ab ' },
        });

        await act(async () => {
            vi.advanceTimersByTime(300);
            await Promise.resolve();
        });

        expect(searchUsers).not.toHaveBeenCalled();
        expect(screen.queryByRole('button', { name: 'chat-1' })).not.toBeInTheDocument();
    });

    it('searches users after debounce and renders fake chats', async () => {
        vi.useFakeTimers();
        searchUsers.mockResolvedValueOnce([USER]);

        render(<MainPage />);
        await act(async () => { await Promise.resolve(); });

        fireEvent.change(screen.getByPlaceholderText('Search for users...'), {
            target: { value: '  alice  ' },
        });

        await act(async () => {
            vi.advanceTimersByTime(300);
            await Promise.resolve();
        });

        expect(searchUsers).toHaveBeenCalledWith('alice', 0);
        expect(screen.getByRole('button', { name: `fake:${USER.id}` })).toBeInTheDocument();
    });

    it('clears search results when search fails', async () => {
        vi.useFakeTimers();
        searchUsers.mockRejectedValueOnce(new Error('search failed'));

        render(<MainPage />);
        await act(async () => { await Promise.resolve(); });

        fireEvent.change(screen.getByPlaceholderText('Search for users...'), {
            target: { value: 'alice' },
        });

        await act(async () => {
            vi.advanceTimersByTime(300);
            await Promise.resolve();
        });

        expect(searchUsers).toHaveBeenCalledOnce();
        expect(screen.getByLabelText('Chat list')).toBeEmptyDOMElement();
    });

    it('cancels the previous debounced search when the query changes', async () => {
        vi.useFakeTimers();
        render(<MainPage />);
        await act(async () => { await Promise.resolve(); });

        const input = screen.getByPlaceholderText('Search for users...');
        fireEvent.change(input, { target: { value: 'alice' } });

        act(() => {
            vi.advanceTimersByTime(200);
        });

        fireEvent.change(input, { target: { value: 'bob' } });

        await act(async () => {
            vi.advanceTimersByTime(300);
            await Promise.resolve();
        });

        expect(searchUsers).toHaveBeenCalledOnce();
        expect(searchUsers).toHaveBeenCalledWith('bob', 0);
    });

    it('unsubscribes websocket listener on unmount', () => {
        const { unmount } = render(<MainPage />);

        unmount();

        expect(unsubscribe).toHaveBeenCalledOnce();
    });

    it('does not apply initial chats after unmount', async () => {
        let resolve!: (chats: ChatDto[]) => void;
        getChats.mockReturnValueOnce(new Promise(r => { resolve = r; }));

        const { unmount } = render(<MainPage />);
        unmount();

        await act(async () => {
            resolve([CHAT_1]);
        });

        expect(chatList.mock.calls.at(-1)![0].chats).toEqual([]);
    });
});
