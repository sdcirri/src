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
    const { session, wsSubscribe } = useSession();

    const [contact, setContact] = useState<UserDto | null>(null);
    const [contactCrypto, setContactCrypto] = useState<ContactCryptoDto | null>(null);
    const [messages, setMessages] = useState<MessageDto[]>([]);
    const [draft, setDraft] = useState('');
    const [page, setPage] = useState(0);
    const [hasMore, setHasMore] = useState<boolean>(false);
    const [loadingMore, setLoadingMore] = useState<boolean>(false);

    const contactId = chat?.contactId;

    async function send() {
        const text = draft.trim();
        if (!chat || !contact || !contactCrypto || !session.keys || !text) return;

        const sent = await sendMessage(contact.id, text, session.keys, contactCrypto);
        setMessages(prev => [...prev, sent]);
        setDraft('');
    }

    function onLoadMore() {
        setLoadingMore(true);
        setPage(p => p + 1);
    }

    useEffect(() => {
        return wsSubscribe((message: MessageDto) => {
            if (message.senderId !== contactId) return;
            setMessages(prev => [...prev, message]);
        });
    }, [contactId, wsSubscribe]);

    useEffect(() => {
        let cancelled = false;

        async function load() {
            if (contactId != null) {
                const [user, crypto] = await Promise.all([
                    getUserInfo(contactId),
                    getContactCryptoSpecs(contactId)
                ]);

                setLoadingMore(true);
                let history: MessageDto[];
                try {
                    history = await getChat(user.id, page);
                    setHasMore(history.length === 30);
                } catch {
                    history = [];
                } finally {
                    setLoadingMore(false);
                }

                if (cancelled) return;

                setContact(user);
                setContactCrypto(crypto);
                const older = history.toReversed();
                if (page === 0) setMessages(older);
                else setMessages(prev => [...older, ...prev]);
            }
        }

        load();
        return () => { cancelled = true; };
    }, [contactId, page]);

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
            {
                contactCrypto?.publicX25519 && session.keys &&
                    <MessageHistory
                        messages={messages}
                        hasMore={hasMore}
                        onLoadMore={onLoadMore}
                        loadingMore={loadingMore}
                        myPrivateX25519={session.keys.privateX25519}
                        theirPublicX25519={fromBase64(contactCrypto.publicX25519)}
                    />
            }
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
