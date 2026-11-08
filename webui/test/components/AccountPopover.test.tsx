import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const { changeDisplayName, changeUsername, useSession, updateUser } = vi.hoisted(() => ({
    changeDisplayName: vi.fn(),
    changeUsername: vi.fn(),
    useSession: vi.fn(),
    updateUser: vi.fn(),
}));

vi.mock('@/api/users.ts', () => ({
    changeDisplayName,
    changeUsername,
}));

vi.mock('@/session/useSession.ts', () => ({
    useSession,
}));

import AccountPopover from '@/components/AccountPopover.tsx';

const USER = {
    id: 'user-id',
    username: 'alice',
    displayName: 'Alice',
};

describe('AccountPopover', () => {
    beforeEach(() => {
        changeDisplayName.mockReset();
        changeUsername.mockReset();
        useSession.mockReset();
        updateUser.mockReset();

        useSession.mockReturnValue({
            session: { user: USER },
            updateUser,
        });
    });

    it('renders the current account data', () => {
        render(<AccountPopover />);

        expect(screen.getByLabelText('Username')).toHaveValue('alice');
        expect(screen.getByLabelText('Display name')).toHaveValue('Alice');

        const dialog = screen.getByRole('dialog', { hidden: true });
        expect(dialog).toHaveAttribute('aria-hidden', 'true');
        expect(dialog).not.toHaveClass('is-open');
    });

    it('opens and closes the popover', () => {
        render(<AccountPopover />);

        const button = screen.getByRole('button', { name: 'My account' });
        const dialog = screen.getByRole('dialog', { hidden: true });

        fireEvent.click(button);

        expect(button).toHaveAttribute('aria-expanded', 'true');
        expect(dialog).toHaveAttribute('aria-hidden', 'false');
        expect(dialog).toHaveClass('is-open');

        fireEvent.click(button);

        expect(button).toHaveAttribute('aria-expanded', 'false');
        expect(dialog).toHaveAttribute('aria-hidden', 'true');
    });

    it('closes the popover when Cancel is clicked', () => {
        render(<AccountPopover />);

        fireEvent.click(screen.getByRole('button', { name: 'My account' }));
        fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));

        expect(screen.getByRole('button', { name: 'My account' }))
            .toHaveAttribute('aria-expanded', 'false');
    });

    it('does not render account details when there is no user', () => {
        useSession.mockReturnValue({
            session: { user: null },
            updateUser,
        });

        render(<AccountPopover />);

        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'My account' })).toBeInTheDocument();
    });

    it('does nothing when submitting unchanged data', async () => {
        render(<AccountPopover />);

        fireEvent.click(screen.getByRole('button', { name: 'My account' }));
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() => {
            expect(changeUsername).not.toHaveBeenCalled();
            expect(changeDisplayName).not.toHaveBeenCalled();
            expect(updateUser).not.toHaveBeenCalled();
        });

        expect(screen.getByRole('button', { name: 'My account' }))
            .toHaveAttribute('aria-expanded', 'false');
    });

    it('changes the username', async () => {
        const updatedUser = { ...USER, username: 'bob' };
        changeUsername.mockResolvedValueOnce(updatedUser);

        render(<AccountPopover />);

        fireEvent.click(screen.getByRole('button', { name: 'My account' }));
        fireEvent.change(screen.getByLabelText('Username'), {
            target: { value: 'bob' },
        });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() => {
            expect(changeUsername).toHaveBeenCalledWith('bob');
        });

        expect(changeDisplayName).not.toHaveBeenCalled();
        expect(updateUser).toHaveBeenCalledWith(updatedUser);
    });

    it('changes the display name', async () => {
        const updatedUser = { ...USER, displayName: 'Alice Cooper' };
        changeDisplayName.mockResolvedValueOnce(updatedUser);

        render(<AccountPopover />);

        fireEvent.click(screen.getByRole('button', { name: 'My account' }));
        fireEvent.change(screen.getByLabelText('Display name'), {
            target: { value: 'Alice Cooper' },
        });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() => {
            expect(changeDisplayName).toHaveBeenCalledWith('Alice Cooper');
        });

        expect(changeUsername).not.toHaveBeenCalled();
        expect(updateUser).toHaveBeenCalledWith(updatedUser);
    });

    it('changes both username and display name', async () => {
        changeUsername.mockResolvedValueOnce({
            ...USER,
            username: 'bob',
        });

        const updatedUser = {
            ...USER,
            username: 'bob',
            displayName: 'Bob',
        };

        changeDisplayName.mockResolvedValueOnce(updatedUser);

        render(<AccountPopover />);

        fireEvent.click(screen.getByRole('button', { name: 'My account' }));

        fireEvent.change(screen.getByLabelText('Username'), {
            target: { value: 'bob' },
        });
        fireEvent.change(screen.getByLabelText('Display name'), {
            target: { value: 'Bob' },
        });

        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() => {
            expect(changeUsername).toHaveBeenCalledWith('bob');
            expect(changeDisplayName).toHaveBeenCalledWith('Bob');
        });

        expect(updateUser).toHaveBeenCalledOnce();
        expect(updateUser).toHaveBeenCalledWith(updatedUser);
    });

    it('does not update an invalid username or empty display name', async () => {
        render(<AccountPopover />);

        fireEvent.click(screen.getByRole('button', { name: 'My account' }));

        fireEvent.change(screen.getByLabelText('Username'), {
            target: { value: 'ab' },
        });
        fireEvent.change(screen.getByLabelText('Display name'), {
            target: { value: '' },
        });

        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() => {
            expect(changeUsername).not.toHaveBeenCalled();
            expect(changeDisplayName).not.toHaveBeenCalled();
        });

        expect(updateUser).not.toHaveBeenCalled();
    });

    it('renders an empty display name when it is null', () => {
        useSession.mockReturnValue({
            session: {
                user: {
                    ...USER,
                    displayName: null,
                },
            },
            updateUser,
        });

        render(<AccountPopover />);

        expect(screen.getByLabelText('Display name')).toHaveValue('');
    });
});
