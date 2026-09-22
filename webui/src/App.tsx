import { Routes, Route, Navigate } from 'react-router';

import { useSession } from '@/session/useSession.ts';
import RegisterPage from '@/pages/RegisterPage.tsx';
import UnlockPage from '@/pages/UnlockPage.tsx';
import LoginPage from '@/pages/LoginPage.tsx';
import MainPage from '@/pages/MainPage.tsx';

import '@/css/main.css';

function App() {
    const { session } = useSession();

    if (session.status === 'anonymous') return (
        <Routes>
            <Route path='/login' element={<LoginPage />} />
            <Route path='/register' element={<RegisterPage />} />
            <Route path='*' element={<Navigate to='/login' replace />} />
        </Routes>
    );

    if (session.status === 'locked') return <UnlockPage />;

    return (
        <Routes>
            <Route path='/' element={<MainPage />} />
            <Route path='*' element={<Navigate to='/' replace />} />
        </Routes>
    );
}

export default App
