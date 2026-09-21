import { useEffect, useState } from 'react';

import { getContactCryptoSpecs, getUserInfo } from'@/api/users.ts';
import type { ChatDto, UserDto } from '@/api/types.ts';

import { decryptMessage } from '@/crypto/messaging.ts';
import { fromBase64 } from '@/crypto/common.ts';

import AccountCircleFill from '@material-symbols/svg-400/rounded/account_circle-fill.svg?react';
import { useSession } from '@/session/useSession.ts';

import '@/css/card.css';
import '@/css/main.css';

type ChatCardProps = {
    chat: ChatDto;
    onSelect: (chat: ChatDto) => void;
}

function ChatCard({ chat, onSelect }: ChatCardProps) {
    const { session } = useSession();
    const [contact, setContact] = useState<UserDto | null>(null);
    const [messagePreview, setMessagePreview] = useState('');

    useEffect(() => {
        let cancelled = false;

        async function load() {
            const [user, contactCrypto] = await Promise.all([
                getUserInfo(chat.contactId),
                getContactCryptoSpecs(chat.contactId),
            ]);
            if (cancelled) return;
            setContact(user);

            if (!chat.lastMessage || !session.keys) {
                setMessagePreview('');
                return;
            }

            const decrypted = await decryptMessage(
                {
                    messageData: fromBase64(chat.lastMessage.data),
                    messageIV: fromBase64(chat.lastMessage.iv),
                },
                session.keys.privateX25519,
                fromBase64(contactCrypto.publicX25519)
            );
            if (cancelled) return;
            setMessagePreview(
                decrypted.length > 20 ? decrypted.substring(0, 20) + '...' : decrypted
            );
        }

        load();
        return () => { cancelled = true; };
    }, [chat.contactId, chat.lastMessage, session.keys]);

    if (!contact) return null;

    return (
        <button
            type='button'
            className='card-container'
            onClick={() => { onSelect(chat) }}
        >
            {
                contact.proPic &&
                    <img
                        src={`data:image/jpg;base64,${contact.proPic}`}
                        alt={`${contact.username}'s propic`}
                    />
                ||  <AccountCircleFill />
            }
            <div className='contact-info'>
                <p>{contact.displayName ?? contact.username}</p>
                <p>{messagePreview}</p>
            </div>
        </button>
    );
}

export default ChatCard
