import type { ChatDto } from '@/api/types.ts';

import ChatCard from '@/components/ChatCard.tsx';

function ChatList({ chats }: { chats: ChatDto[] }) {
    return (
        <div>
            {chats.map((chat) => (
                <ChatCard key={chat.chatId} chat={chat} />
            ))}
        </div>
    );
}

export default ChatList
