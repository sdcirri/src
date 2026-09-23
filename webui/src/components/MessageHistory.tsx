import type { MessageDto } from '@/api/types.ts';

import MessageBubble from '@/components/MessageBubble.tsx';

import '@/css/chat.css';

type MessageHistoryProps = {
    messages: MessageDto[];
    myPrivateX25519: Uint8Array;
    theirPublicX25519: Uint8Array;
}

function MessageHistory({ messages, myPrivateX25519, theirPublicX25519 }: MessageHistoryProps) {
    return (
        <div id='messages-container'>
            {messages.map((message) => (
                <MessageBubble
                    key={message.iv}
                    message={message}
                    myPrivateX25519={myPrivateX25519}
                    theirPublicX25519={theirPublicX25519}
                />
            ))}
        </div>
    );
}

export default MessageHistory
