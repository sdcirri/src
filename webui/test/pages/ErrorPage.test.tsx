import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const { retryBootstrap, useSession } = vi.hoisted(() => ({
    retryBootstrap: vi.fn(),
    useSession: vi.fn(),
}));

vi.mock('@/session/useSession.ts', () => ({ useSession }));

import ErrorPage from '@/pages/ErrorPage.tsx';

describe('ErrorPage', () => {
    beforeEach(() => {
        retryBootstrap.mockReset();
        retryBootstrap.mockResolvedValue(undefined);
        useSession.mockReturnValue({ retryBootstrap });
    });

    it('renders the bootstrap error', () => {
        render(<ErrorPage />);

        expect(screen.getByText('Something went wrong')).toBeInTheDocument();
        expect(screen.getByText('Unable to restore your session.')).toBeInTheDocument();
    });

    it('retries session bootstrap', () => {
        render(<ErrorPage />);

        fireEvent.click(screen.getByRole('button', { name: 'Retry' }));

        expect(retryBootstrap).toHaveBeenCalledOnce();
    });
});
