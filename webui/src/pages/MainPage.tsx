import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';

import type { ChatDto, UserDto } from '@/api/types.ts';
import { searchUsers } from '@/api/users.ts';
import { getChats } from '@/api/chat.ts';

import Security from '@material-symbols/svg-400/rounded/security.svg?react';
import Logout from '@material-symbols/svg-400/outlined/logout.svg?react';
import Search from '@material-symbols/svg-400/rounded/search.svg?react';

import { useSession } from '@/session/useSession.ts';
import ChatList from '@/components/ChatList.tsx';
import ChatBox from '@/components/ChatBox.tsx';

import '@/css/sidebar.css';
import '@/css/topbar.css';
import '@/css/main.css';
import AccountPopover from "@/components/AccountPopover.tsx";

function asFakeChats(users: UserDto[]): ChatDto[] {
    return users.map(u => {
        return { chatId: '', contactId: u.id, lastMessage: null }
    });
}

function MainPage() {
    const navigate = useNavigate();
    const { signOut } = useSession();

    const [currentChat, setCurrentChat] = useState<ChatDto | null>(null);
    const [chats, setChats] = useState<ChatDto[]>([]);
    const [users, setUsers] = useState<UserDto[]>([]);
    const [query, setQuery] = useState('');

    async function onSignOut() {
        await signOut();
        navigate('/login', { replace: true });
    }

    useEffect(() => {
        let cancelled = false;
        getChats().then((list) => { if (!cancelled) setChats(list); });
        return () => { cancelled = true; };
    }, []);

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
                <div id='topbar-title'><Security/><h1>S R C</h1></div>
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
                    <ChatList chats={q === '' ? chats : asFakeChats(results)} onSelect={setCurrentChat} />
                </div>
                <ChatBox key={currentChat?.contactId ?? 'no-chat'} chat={currentChat} />
            </div>
        </div>
    )
}

export default MainPage
