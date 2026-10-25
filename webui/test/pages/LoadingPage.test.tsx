import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

vi.mock('@alerix/m3-loading-indicator/react', () => ({
    M3LoadingIndicator: ({ size }: { size: number }) => <div role='status' data-size={size}>Loading</div>,
}));

import LoadingPage from '@/pages/LoadingPage.tsx';

describe('LoadingPage', () => {
    it('renders the loading indicator', () => {
        const { container } = render(<LoadingPage />);

        expect(screen.getByRole('status')).toHaveAttribute('data-size', '128');
        expect(container.firstElementChild).toHaveClass('center');
    });
});
