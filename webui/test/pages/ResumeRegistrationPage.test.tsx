import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/types.ts';

const { resumeRegistration, useSession } = vi.hoisted(() => ({
    resumeRegistration: vi.fn(),
    useSession: vi.fn(),
}));

vi.mock('@/session/useSession.ts', () => ({ useSession }));
vi.mock('@alerix/m3-loading-indicator/react', () => ({
    M3LoadingIndicator: () => <div role='status'>Loading</div>,
}));

import ResumeRegistrationPage from '@/pages/ResumeRegistrationPage.tsx';

describe('ResumeRegistrationPage', () => {
    beforeEach(() => {
        resumeRegistration.mockReset();
        useSession.mockReturnValue({ resumeRegistration });
        vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => {
            callback(0);
            return 0;
        });
    });

    it('resumes registration with the supplied password', async () => {
        resumeRegistration.mockResolvedValueOnce(undefined);
        render(<ResumeRegistrationPage />);

        fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password' } });
        fireEvent.submit(document.querySelector('#login-form')!);

        await waitFor(() => {
            expect(resumeRegistration).toHaveBeenCalledWith('password');
        });
    });

    it('shows pending state while finalizing registration', async () => {
        let resolve!: () => void;
        resumeRegistration.mockReturnValueOnce(new Promise<void>(r => { resolve = r; }));
        render(<ResumeRegistrationPage />);

        fireEvent.submit(document.querySelector('#login-form')!);

        expect(screen.getByRole('button', { name: 'Finalize registration' })).toBeDisabled();
        expect(screen.getByRole('status')).toBeInTheDocument();

        resolve();

        await waitFor(() => {
            expect(screen.getByRole('button', { name: 'Finalize registration' })).not.toBeDisabled();
        });
    });

    it('shows wrong password on 403', async () => {
        resumeRegistration.mockRejectedValueOnce(new ApiError(403, 'Forbidden'));
        render(<ResumeRegistrationPage />);

        fireEvent.submit(document.querySelector('#login-form')!);

        expect(await screen.findByText('Wrong password')).toBeInTheDocument();
    });

    it.each([
        new ApiError(500, 'Server error'),
        new Error('Network error'),
    ])('shows generic error for other failures', async error => {
        resumeRegistration.mockRejectedValueOnce(error);
        render(<ResumeRegistrationPage />);

        fireEvent.submit(document.querySelector('#login-form')!);

        expect(await screen.findByText('Unable to complete registration')).toBeInTheDocument();
    });

});
