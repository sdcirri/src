import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/api/types.ts';

const { unlock, useSession } = vi.hoisted(() => ({
    unlock: vi.fn(),
    useSession: vi.fn(),
}));

vi.mock('@/session/useSession.ts', () => ({ useSession }));
vi.mock('@alerix/m3-loading-indicator/react', () => ({
    M3LoadingIndicator: () => <div role='status'>Loading</div>,
}));

import UnlockPage from '@/pages/UnlockPage.tsx';

describe('UnlockPage', () => {
    beforeEach(() => {
        unlock.mockReset();
        useSession.mockReturnValue({ unlock });
        vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => {
            callback(0);
            return 0;
        });
    });

    it('unlocks with the supplied password', async () => {
        unlock.mockResolvedValueOnce(undefined);
        render(<UnlockPage />);

        fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password' } });
        fireEvent.submit(document.querySelector('#login-form')!);

        await waitFor(() => {
            expect(unlock).toHaveBeenCalledWith('password');
        });
    });

    it('shows pending state while unlocking', async () => {
        let resolve!: () => void;
        unlock.mockReturnValueOnce(new Promise<void>(r => { resolve = r; }));
        render(<UnlockPage />);

        fireEvent.submit(document.querySelector('#login-form')!);

        expect(screen.getByRole('button', { name: 'Unlock' })).toBeDisabled();
        expect(screen.getByRole('status')).toBeInTheDocument();

        resolve();

        await waitFor(() => {
            expect(screen.getByRole('button', { name: 'Unlock' })).not.toBeDisabled();
        });
    });

    it('shows wrong password on 403', async () => {
        unlock.mockRejectedValueOnce(new ApiError(403, 'Forbidden'));
        render(<UnlockPage />);

        fireEvent.submit(document.querySelector('#login-form')!);

        expect(await screen.findByText('Wrong password')).toBeInTheDocument();
    });

    it.each([
        new ApiError(500, 'Server error'),
        new Error('Network error'),
    ])('shows generic error for other failures', async error => {
        unlock.mockRejectedValueOnce(error);
        render(<UnlockPage />);

        fireEvent.submit(document.querySelector('#login-form')!);

        expect(await screen.findByText('Unable to complete registration')).toBeInTheDocument();
    });

});
