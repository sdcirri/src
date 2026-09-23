import { type SubmitEvent, useState } from 'react';

import AccountCircle from '@material-symbols/svg-400/rounded/account_circle.svg?react';

import { changeDisplayName, changeUsername } from '@/api/users.ts';
import { useSession } from '@/session/useSession.ts';

import '@/css/popover.css';
import '@/css/forms.css';

function AccountPopover() {
    const { session, updateUser } = useSession();
    const [accountOpen, setAccountOpen] = useState(false);
    const [draft, setDraft] = useState<{ username: string; displayName: string } | null>(null);

    const username = draft?.username ?? session.user?.username ?? '';
    const displayName = draft?.displayName ?? session.user?.displayName ?? '';

    async function onSubmit(e: SubmitEvent<HTMLFormElement>) {
        e.preventDefault();
        if (!session.user) return;

        const data = new FormData(e.currentTarget);
        const username = String(data.get('username') ?? '');
        const displayName = String(data.get('displayName') ?? '');

        let user = null;
        if (session.user.username !== username && username.length >= 3)
            user = await changeUsername(username);
        if (session.user.displayName !== displayName && displayName.length > 0)
            user = await changeDisplayName(displayName);
        if (user) updateUser(user);

        setAccountOpen(false);
    }

    return (
        <div className='account-menu'>
            <button
                type='button'
                className='topbar-button'
                aria-label='My account'
                aria-expanded={accountOpen}
                aria-controls='account-popover'
                onClick={() => setAccountOpen(open => !open)}
            >
                <AccountCircle />
            </button>
            {session.user && (
                <div
                    id='account-popover'
                    className={accountOpen ? 'account-popover is-open' : 'account-popover'}
                    role='dialog'
                    aria-label='My account'
                    aria-hidden={!accountOpen}
                    inert={!accountOpen}
                >
                    <form className='form' onSubmit={onSubmit}>
                        <h4>My account</h4>
                        <label htmlFor='displayName'>Display name</label>
                        <input
                            id='displayName'
                            name='displayName'
                            defaultValue={session.user.displayName ?? ''}
                            onSubmit={e => setDraft({ username, displayName: e.target.value })}
                        />
                        <label htmlFor='username'>Username</label>
                        <input
                            id='username'
                            name='username'
                            defaultValue={session.user.username}
                            onSubmit={e => setDraft({ username: e.target.value, displayName })}
                        />
                        <button type='submit'>Save</button>
                        <button type='button' onClick={() => setAccountOpen(false)}>Cancel</button>
                    </form>
                </div>
            )}
        </div>
    );
}

export default AccountPopover
