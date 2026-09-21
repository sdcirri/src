import { useEffect, useState } from 'react';

import type { ChatDto, UserDto } from '@/api/types.ts';
import { getUserInfo } from '@/api/users.ts';

import AccountCircleFill from '@material-symbols/svg-400/rounded/account_circle-fill.svg?react';
import SendFill from '@material-symbols/svg-400/rounded/send-fill.svg?react';

import '@/css/main.css';
import '@/css/chat.css';

function ChatBox({ chat }: { chat: ChatDto | null }) {
    const [contact, setContact] = useState<UserDto | null>(null);

    useEffect(() => {
        let cancelled = false;

        async function load() {
            if (chat) {
                const user = await getUserInfo(chat.contactId);
                if (cancelled) return;
                setContact(user);
            }
        }

        load();
        return () => { cancelled = true; };
    }, [chat]);

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
            <div id='chat-body'></div>
            <div id='message-box'>
                <input type='text' id='message-input' placeholder='Write something...' />
                <button id='send-button'>
                    <SendFill />
                </button>
            </div>
        </div>
    );
}

export default ChatBox
