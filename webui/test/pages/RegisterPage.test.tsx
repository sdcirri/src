import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/types.ts';

const { navigate, signUp, useSession } = vi.hoisted(() => ({
    navigate: vi.fn(),
    signUp: vi.fn(),
    useSession: vi.fn(),
}));

vi.mock('react-router', () => ({ useNavigate: () => navigate }));
vi.mock('@/session/useSession.ts', () => ({ useSession }));
vi.mock('@alerix/m3-loading-indicator/react', () => ({
    M3LoadingIndicator: () => <div role='status'>Loading</div>,
}));

import RegisterPage from '@/pages/RegisterPage.tsx';

function fill(
    username = 'tester',
    displayName = '',
    password = 'password',
    passwordConfirm = password,
) {
    fireEvent.change(screen.getByLabelText('Username'), { target: { value: username } });
    fireEvent.change(screen.getByLabelText('Display name (optional)'), { target: { value: displayName } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: password } });
    fireEvent.change(screen.getByLabelText('Confirm password'), { target: { value: passwordConfirm } });
}

function submit() {
    fireEvent.submit(document.querySelector('#login-form')!);
}

describe('RegisterPage', () => {
    beforeEach(() => {
        navigate.mockReset();
        signUp.mockReset();
        useSession.mockReturnValue({ signUp });
        vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => {
            callback(0);
            return 0;
        });
    });

    it('registers without an optional display name and navigates home', async () => {
        signUp.mockResolvedValueOnce(undefined);
        render(<RegisterPage />);
        fill();

        submit();

        await waitFor(() => {
            expect(signUp).toHaveBeenCalledWith('tester', null, 'password');
            expect(navigate).toHaveBeenCalledWith('/', { replace: true });
        });
    });

    it('registers with a display name', async () => {
        signUp.mockResolvedValueOnce(undefined);
        render(<RegisterPage />);
        fill('tester', 'Test User');

        submit();

        await waitFor(() => {
            expect(signUp).toHaveBeenCalledWith('tester', 'Test User', 'password');
        });
    });

    it.each([
        ['bad display name', 'tester', 'x'.repeat(256), 'password', 'password', 'Bad display name'],
        ['bad username', 'no spaces', '', 'password', 'password', 'Bad username'],
        ['short password', 'tester', '', 'short', 'short', 'Password must be between 8 and 255 characters'],
        ['password mismatch', 'tester', '', 'password', 'different', "Passwords don't match"],
    ])('rejects %s locally', (_, username, displayName, password, passwordConfirm, expected) => {
        render(<RegisterPage />);
        fill(username, displayName, password, passwordConfirm);

        submit();

        expect(screen.getByText(expected)).toBeInTheDocument();
        expect(signUp).not.toHaveBeenCalled();
    });

    it('shows pending state while registering', async () => {
        let resolve!: () => void;
        signUp.mockReturnValueOnce(new Promise<void>(r => { resolve = r; }));
        render(<RegisterPage />);
        fill();

        submit();

        expect(screen.getByRole('button', { name: 'Register' })).toBeDisabled();
        expect(screen.getByRole('status')).toBeInTheDocument();

        resolve();

        await waitFor(() => {
            expect(screen.getByRole('button', { name: 'Register' })).not.toBeDisabled();
        });
    });

    it.each([
        [new ApiError(409, 'Conflict'), 'Username already taken'],
        [new ApiError(400, 'Weak password'), 'Password too weak'],
        [new ApiError(500, 'Server error'), 'Registration failed'],
        [new Error('Network error'), 'Registration failed'],
    ])('shows the expected registration error', async (error, expected) => {
        signUp.mockRejectedValueOnce(error);
        render(<RegisterPage />);
        fill();

        submit();

        expect(await screen.findByText(expected)).toBeInTheDocument();
        expect(navigate).not.toHaveBeenCalled();
    });

});
