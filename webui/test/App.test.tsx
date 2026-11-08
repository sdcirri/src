import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const { useSession } = vi.hoisted(() => ({
    useSession: vi.fn(),
}));

vi.mock('@/session/useSession.ts', () => ({
    useSession,
}));

vi.mock('@/pages/LoginPage.tsx', () => ({
    default: () => <div>Login page</div>,
}));

vi.mock('@/pages/RegisterPage.tsx', () => ({
    default: () => <div>Register page</div>,
}));

vi.mock('@/pages/LoadingPage.tsx', () => ({
    default: () => <div>Loading page</div>,
}));

vi.mock('@/pages/ErrorPage.tsx', () => ({
    default: () => <div>Error page</div>,
}));

vi.mock('@/pages/UnlockPage.tsx', () => ({
    default: () => <div>Unlock page</div>,
}));

vi.mock('@/pages/ResumeRegistrationPage.tsx', () => ({
    default: () => <div>Resume registration page</div>,
}));

vi.mock('@/pages/MainPage.tsx', () => ({
    default: () => <div>Main page</div>,
}));

import App from '@/App.tsx';

function mockStatus(status: string) {
    useSession.mockReturnValue({
        session: { status },
    });
}

function renderApp(path = '/') {
    return render(
        <MemoryRouter initialEntries={[path]}>
            <App />
        </MemoryRouter>
    );
}

describe('App', () => {
    beforeEach(() => {
        useSession.mockReset();
    });

    it('renders the loading page', () => {
        mockStatus('loading');
        renderApp();
        expect(screen.getByText('Loading page')).toBeInTheDocument();
    });

    it('renders the error page', () => {
        mockStatus('error');
        renderApp();
        expect(screen.getByText('Error page')).toBeInTheDocument();
    });

    it('renders the unlock page', () => {
        mockStatus('locked');
        renderApp();
        expect(screen.getByText('Unlock page')).toBeInTheDocument();
    });

    it('renders the resume registration page', () => {
        mockStatus('registration-incomplete');
        renderApp();
        expect(screen.getByText('Resume registration page')).toBeInTheDocument();
    });

    it('renders login for anonymous users', () => {
        mockStatus('anonymous');
        renderApp('/login');
        expect(screen.getByText('Login page')).toBeInTheDocument();
    });

    it('renders register for anonymous users', () => {
        mockStatus('anonymous');
        renderApp('/register');
        expect(screen.getByText('Register page')).toBeInTheDocument();
    });

    it('redirects unknown anonymous routes to login', async () => {
        mockStatus('anonymous');
        renderApp('/whatever');
        expect(await screen.findByText('Login page')).toBeInTheDocument();
    });

    it('renders the main page for unlocked users', () => {
        mockStatus('unlocked');
        renderApp('/');
        expect(screen.getByText('Main page')).toBeInTheDocument();
    });

    it('redirects unknown authenticated routes to main', async () => {
        mockStatus('unlocked');
        renderApp('/whatever');
        expect(await screen.findByText('Main page')).toBeInTheDocument();
    });
});
