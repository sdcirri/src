import { useState, useEffect } from 'react';

import { decryptMessage } from '@/crypto/messaging.ts';
import { fromBase64 } from '@/crypto/common.ts';

import type { MessageDto } from '@/api/types.ts';

import '@/css/main.css';
import '@/css/chat.css';

type MessageBubbleProps = {
    message: MessageDto;
    myPrivateX25519: Uint8Array;
    theirPublicX25519: Uint8Array;
}

export function MessageBubble({ message, myPrivateX25519, theirPublicX25519 }: MessageBubbleProps) {
    const [plaintext, setPlaintext] = useState('');

    useEffect(() => {
        async function decrypt() {
            const plain = await decryptMessage(
                { messageData: fromBase64(message.data), messageIV: fromBase64(message.iv) },
                myPrivateX25519,
                theirPublicX25519
            )
            setPlaintext(plain);
        }

        decrypt();
    }, [message, myPrivateX25519, theirPublicX25519]);

    const timestamp = new Date(message.timestamp * 1000).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    return (
        <div className={'message-bubble ' + message.direction.toLowerCase()}>
            <div className='bubble-inner'>
                <div>{plaintext}</div>
                <div className='bubble-ts'>{timestamp}</div>
            </div>
        </div>
    );
}

export default MessageBubble
