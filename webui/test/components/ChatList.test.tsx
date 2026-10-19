import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import type { ChatDto } from '@/api/types.ts';

vi.mock('@/components/ChatCard.tsx', () => ({
    default: ({ chat, onSelect }: { chat: ChatDto; onSelect: (chat: ChatDto) => void }) => (
        <button onClick={() => onSelect(chat)}>{chat.contactId}</button>
    ),
}));

import ChatList from '@/components/ChatList.tsx';

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

describe('ChatList', () => {
    it('renders no chat cards when the list is empty', () => {
        render(<ChatList chats={[]} onSelect={vi.fn()} />);

        expect(screen.queryByRole('button')).not.toBeInTheDocument();
    });

    it('renders all chats', () => {
        render(<ChatList chats={[CHAT_1, CHAT_2]} onSelect={vi.fn()} />);

        expect(screen.getByText('contact-1')).toBeInTheDocument();
        expect(screen.getByText('contact-2')).toBeInTheDocument();
        expect(screen.getAllByRole('button')).toHaveLength(2);
    });

    it('passes the selected chat to onSelect', () => {
        const onSelect = vi.fn();

        render(<ChatList chats={[CHAT_1, CHAT_2]} onSelect={onSelect} />);

        fireEvent.click(screen.getByText('contact-2'));

        expect(onSelect).toHaveBeenCalledOnce();
        expect(onSelect).toHaveBeenCalledWith(CHAT_2);
    });

    it('renders a chat without chatId using contactId as fallback key', () => {
        const fakeChat: ChatDto = {
            chatId: '',
            contactId: 'contact-1',
            lastMessage: null,
        };

        render(<ChatList chats={[fakeChat]} onSelect={vi.fn()} />);

        expect(screen.getByText('contact-1')).toBeInTheDocument();
    });
});
