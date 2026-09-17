import { type SubmitEvent, useState } from 'react';

import { useSession } from '@/session/useSession.ts';

import '@/css/forms.css';
import '@/css/main.css';

function UnlockPage() {
    const { unlock } = useSession();
    const [error, setError] = useState<string | null>(null);
    const [pending, setPending] = useState(false);

    async function onSubmit (e: SubmitEvent<HTMLFormElement>) {
        e.preventDefault();
        const data = new FormData(e.currentTarget);
        const password = String(data.get('password') ?? '');

        setError(null);
        setPending(true);
        try {
            await unlock(password);
        } catch {
            setError('Wrong password');
        } finally {
            setPending(false);
        }
    }

    return (
        <div id='root-container' className='center'>
            <form id='login-form' className='form center' onSubmit={onSubmit}>
                <h4>Locked</h4>
                {error && <p>{error}</p>}
                <p>The app is locked, please provide your password to continue</p>
                <label htmlFor='password'>Password</label>
                <input id='password' type='password' name='password' placeholder='Password' />
                <button type='submit' disabled={pending}>Unlock</button>
            </form>
        </div>
    )
}

export default UnlockPage
