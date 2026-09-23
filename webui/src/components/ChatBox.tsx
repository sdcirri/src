import { useEffect, useState } from 'react';

import type { ChatDto, ContactCryptoDto, MessageDto, UserDto } from '@/api/types.ts';
import { getContactCryptoSpecs, getUserInfo } from '@/api/users.ts';
import { getChat, sendMessage } from '@/api/chat.ts';

import { fromBase64 } from '@/crypto/common.ts';

import AccountCircleFill from '@material-symbols/svg-400/rounded/account_circle-fill.svg?react';
import SendFill from '@material-symbols/svg-400/rounded/send-fill.svg?react';

import MessageHistory from '@/components/MessageHistory.tsx';
import { useSession } from '@/session/useSession.ts';

import '@/css/main.css';
import '@/css/chat.css';

function ChatBox({ chat }: { chat: ChatDto | null }) {
    const { session } = useSession();

    const [contact, setContact] = useState<UserDto | null>(null);
    const [contactCrypto, setContactCrypto] = useState<ContactCryptoDto | null>(null);
    const [messages, setMessages] = useState<MessageDto[]>([]);
    const [draft, setDraft] = useState('');
    const [page, setPage] = useState(0);

    async function send() {
        const text = draft.trim();
        if (!chat || !contact || !contactCrypto || !session.keys || !text) return;

        const sent = await sendMessage(contact.id, text, session.keys, contactCrypto);
        setMessages(prev => [...prev, sent]);
        setDraft('');
    }

    useEffect(() => {
        let cancelled = false;

        async function load() {
            if (chat) {
                const [user, crypto] = await Promise.all([
                    getUserInfo(chat.contactId),
                    getContactCryptoSpecs(chat.contactId)
                ]);

                let history: MessageDto[];
                try {
                    history = await getChat(user.id, page);
                } catch {
                    history = [];
                }

                if (cancelled) return;

                setContact(user);
                setContactCrypto(crypto);
                setMessages(history.toReversed());
            }
        }

        load();
        return () => { cancelled = true; };
    }, [chat, page]);

    return (
        <div id='chat-container'>
            <div id='chat-header'>
                {
                    contact && (
                        contact.proPic &&
                            <img
                                src={`data:image/jpg;base64,${contact.proPic}`}
                                alt={`${contact.username}'s propic`}
                            />
                        ||  <AccountCircleFill />
                    ) || ''
                }
                { contact && <p id='chat-user'>{contact.displayName ?? contact.username}</p> }
            </div>
            <div id='chat-body'>
                {
                    contactCrypto?.publicX25519 && session.keys &&
                        <MessageHistory
                            messages={messages}
                            myPrivateX25519={session.keys.privateX25519}
                            theirPublicX25519={fromBase64(contactCrypto.publicX25519)}
                        />
                }
            </div>
            {
                contact &&
                    <div id='message-box'>
                        <input
                            type='text'
                            id='message-input'
                            value={draft}
                            placeholder='Write something...'
                            onChange={e => setDraft(e.target.value)}
                            onKeyDown={e => { if (e.key === 'Enter') void send(); }}
                        />
                        <button id='send-button' onClick={send}>
                            <SendFill />
                        </button>
                    </div>
            }
        </div>
    );
}

export default ChatBox
