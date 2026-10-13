import { Routes, Route, Navigate } from 'react-router';

import { useSession } from '@/session/useSession.ts';

import ResumeRegistrationPage from '@/pages/ResumeRegistrationPage.tsx';
import RegisterPage from '@/pages/RegisterPage.tsx';
import LoadingPage from '@/pages/LoadingPage.tsx';
import UnlockPage from '@/pages/UnlockPage.tsx';
import ErrorPage from '@/pages/ErrorPage.tsx';
import LoginPage from '@/pages/LoginPage.tsx';
import MainPage from '@/pages/MainPage.tsx';

import '@/css/main.css';

function App() {
    const { session } = useSession();

    switch (session.status) {
        case 'anonymous':
            return (
                <Routes>
                    <Route path='/login' element={<LoginPage />} />
                    <Route path='/register' element={<RegisterPage />} />
                    <Route path='*' element={<Navigate to='/login' replace />} />
                </Routes>
            );
        case 'error':
            return <ErrorPage />;
        case 'loading':
            return <LoadingPage />;
        case 'locked':
            return <UnlockPage />;
        case 'registration-incomplete':
            return <ResumeRegistrationPage />;
        default:
            return (
                <Routes>
                    <Route path='/' element={<MainPage />} />
                    <Route path='*' element={<Navigate to='/' replace />} />
                </Routes>
            );
    }
}

export default App
