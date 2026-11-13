import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';

import type { ChatDto, UserDto } from '@/api/types.ts';
import { searchUsers } from '@/api/users.ts';
import { getChats } from '@/api/chat.ts';

import Security from '@material-symbols/svg-400/rounded/security.svg?react';
import Logout from '@material-symbols/svg-400/outlined/logout.svg?react';
import Search from '@material-symbols/svg-400/rounded/search.svg?react';

import { useSession } from '@/session/useSession.ts';

import AccountPopover from '@/components/AccountPopover.tsx';
import ChatList from '@/components/ChatList.tsx';
import ChatBox from '@/components/ChatBox.tsx';

import '@/css/sidebar.css';
import '@/css/topbar.css';
import '@/css/main.css';

function asFakeChats(users: UserDto[]): ChatDto[] {
    return users.map(u => {
        return { chatId: '', contactId: u.id, lastMessage: null, unreadCount: 0 }
    });
}

function MainPage() {
    const navigate = useNavigate();
    const { signOut, wsSubscribe } = useSession();

    const [currentChat, setCurrentChat] = useState<ChatDto | null>(null);
    const [chats, setChats] = useState<ChatDto[]>([]);
    const [users, setUsers] = useState<UserDto[]>([]);
    const [query, setQuery] = useState('');

    const chatsRef = useRef<ChatDto[]>([]);
    const openChatRef = useRef<string | null>(null);

    async function onSignOut() {
        await signOut();
        navigate('/login', { replace: true });
    }

    function onSelectChat(chat: ChatDto) {
        openChatRef.current = chat.contactId;
        setCurrentChat(chat);
        setChats(prev => prev.map(c =>
            c.contactId === chat.contactId ? { ...c, unreadCount: 0 } : c
        ));
    }

    useEffect(() => {
        chatsRef.current = chats;
    }, [chats]);

    useEffect(() => {
        let cancelled = false;
        getChats().then((list) => { if (!cancelled) setChats(list); });
        return () => { cancelled = true; };
    }, []);

    useEffect(() => {
        return wsSubscribe((message) => {
            const existing = chatsRef.current.find(
                c => c.chatId === message.chatId
            );

            if (existing) {
                setChats(prev => {
                    const chat = prev.find(c => c.chatId === message.chatId);
                    if (!chat) return prev;
                    const isOpen = chat.contactId === openChatRef.current;
                    const unreadCount = isOpen ? 0 : (chat.unreadCount ?? 0) + (message.direction === 'INCOMING' ? 1 : 0);

                    return [
                        {
                            ...chat,
                            unreadCount,
                            lastMessage: message,
                        },
                        ...prev.filter(c => c.chatId !== message.chatId),
                    ];
                });
            } else {
                getChats().then(list => {
                    setChats(list);
                    setCurrentChat(current => {
                        if (!current || current.chatId) return current;
                        return list.find(
                            chat => chat.contactId === current.contactId
                        ) ?? current;
                    });
                });
            }
        });
    }, [wsSubscribe]);

    useEffect(() => {
        const q = query.trim();
        if (q.length < 3) return;

        let cancelled = false;
        const timeout = setTimeout(async () => {
            try {
                const results = await searchUsers(q, 0);
                if (!cancelled) setUsers(results);
            } catch {
                if (!cancelled) setUsers([]);
            }
        }, 300);

        return () => {
            cancelled = true;
            clearTimeout(timeout);
        };
    }, [query]);

    const q = query.trim();
    const results = q.length >= 3 ? users : [];

    return (
        <div id='root-container'>
            <div id='topbar'>
                <div id='topbar-title'><Security /><h1>S R C</h1></div>
                <span id='topbar-spacer'/>
                <AccountPopover />
                <button
                    type='button'
                    className='topbar-button'
                    aria-label='Log out'
                    title='Log out'
                    onClick={onSignOut}
                >
                    <Logout />
                </button>
            </div>
            <div id='app-container'>
                <div id='sidebar'>
                    <div id='searchbar'>
                        <Search />
                        <input
                            type='text'
                            id='user-search'
                            placeholder='Search for users...'
                            value={query}
                            onChange={(e) => setQuery(e.target.value)}
                        />
                    </div>
                    <ChatList chats={q === '' ? chats : asFakeChats(results)} onSelect={onSelectChat} />
                </div>
                <ChatBox key={currentChat?.contactId ?? 'no-chat'} chat={currentChat} />
            </div>
        </div>
    )
}

export default MainPage
