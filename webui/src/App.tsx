import { useSession } from '@/session/useSession.ts';
import UnlockPage from '@/pages/UnlockPage.tsx';
import LoginPage from '@/pages/LoginPage.tsx';
import '@/css/main.css';

function App() {
    const { session } = useSession();

    if (session.status === 'loading') return <p>Loading…</p>;
    if (session.status === 'anonymous') return <LoginPage />;
    if (session.status === 'locked') return <UnlockPage />;

    return (
        <div id='root-container'>
            <div id='top-bar'><h1>S R C</h1></div>
            <div id='app-container'>
                <div id='sidebar'></div>
                <div id='chat-container'></div>
            </div>
        </div>
    )
}

export default App
