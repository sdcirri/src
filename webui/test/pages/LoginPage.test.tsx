import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/types.ts';

const { navigate, signIn, useSession } = vi.hoisted(() => ({
    navigate: vi.fn(),
    signIn: vi.fn(),
    useSession: vi.fn(),
}));

vi.mock('react-router', () => ({
    useNavigate: () => navigate,
    Link: ({ to, children }: { to: string; children: React.ReactNode }) => <a href={to}>{children}</a>,
}));

vi.mock('@/session/useSession.ts', () => ({ useSession }));
vi.mock('@alerix/m3-loading-indicator/react', () => ({
    M3LoadingIndicator: () => <div role='status'>Loading</div>,
}));

import LoginPage from '@/pages/LoginPage.tsx';

function fill(username = 'tester', password = 'password') {
    fireEvent.change(screen.getByLabelText('Username'), { target: { value: username } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: password } });
}

function submit() {
    fireEvent.submit(document.querySelector('#login-form')!);
}

describe('LoginPage', () => {
    beforeEach(() => {
        navigate.mockReset();
        signIn.mockReset();
        useSession.mockReturnValue({ signIn });
        vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => {
            callback(0);
            return 0;
        });
    });

    it('renders the login form and registration link', () => {
        render(<LoginPage />);

        expect(screen.getByRole('heading', { name: 'Login' })).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'New here? Register!' })).toHaveAttribute('href', '/register');
    });

    it.each([
        ['te', 'password'],
        ['tester', 'short'],
    ])('rejects invalid credentials without calling signIn', (username, password) => {
        render(<LoginPage />);
        fill(username, password);

        submit();

        expect(screen.getByText('Username or password is wrong')).toBeInTheDocument();
        expect(document.querySelector('#login-form')).toHaveClass('form-shake');
        expect(signIn).not.toHaveBeenCalled();
    });


    it('signs in and navigates home', async () => {
        signIn.mockResolvedValueOnce(undefined);
        render(<LoginPage />);
        fill();

        submit();

        await waitFor(() => {
            expect(signIn).toHaveBeenCalledWith('tester', 'password');
            expect(navigate).toHaveBeenCalledWith('/', { replace: true });
        });
    });

    it('shows pending state while signing in', async () => {
        let resolve!: () => void;
        signIn.mockReturnValueOnce(new Promise<void>(r => { resolve = r; }));
        render(<LoginPage />);
        fill();

        submit();

        expect(screen.getByRole('button', { name: 'Login' })).toBeDisabled();
        expect(screen.getByRole('status')).toBeInTheDocument();

        resolve();

        await waitFor(() => {
            expect(screen.getByRole('button', { name: 'Login' })).not.toBeDisabled();
        });
    });

    it('shows credential error on 401', async () => {
        signIn.mockRejectedValueOnce(new ApiError(401, 'Unauthorized'));
        render(<LoginPage />);
        fill();

        submit();

        expect(await screen.findByText('Username or password is wrong')).toBeInTheDocument();
        expect(navigate).not.toHaveBeenCalled();
    });

    it.each([
        new ApiError(500, 'Server error'),
        new Error('Network error'),
    ])('shows generic login error on unexpected failure', async error => {
        signIn.mockRejectedValueOnce(error);
        render(<LoginPage />);
        fill();

        submit();

        expect(await screen.findByText('Login failed')).toBeInTheDocument();
        expect(navigate).not.toHaveBeenCalled();
    });
});
