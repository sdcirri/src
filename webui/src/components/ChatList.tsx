import type { ChatDto } from '@/api/types.ts';

import ChatCard from '@/components/ChatCard.tsx';

type ChatListProps = {
    chats: ChatDto[];
    onSelect: (chat: ChatDto) => void;
}

function ChatList({ chats, onSelect }: ChatListProps) {
    return (
        <div>
            {chats.map((chat) => (
                <ChatCard key={chat.chatId} chat={chat} onSelect={onSelect} />
            ))}
        </div>
    );
}

export default ChatList
