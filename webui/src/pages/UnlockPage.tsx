import { M3LoadingIndicator } from '@alerix/m3-loading-indicator/react';
import { type SubmitEvent, useState } from 'react';

import { useSession } from '@/session/useSession.ts';

import '@/css/forms.css';
import '@/css/main.css';

function UnlockPage() {
    const { unlock } = useSession();
    const [error, setError] = useState<string | null>(null);
    const [shake, setShake] = useState(false);
    const [pending, setPending] = useState(false);

    function showError(message: string) {
        setError(message);
        setShake(false);
        requestAnimationFrame(() => setShake(true));
    }

    async function onSubmit (e: SubmitEvent<HTMLFormElement>) {
        e.preventDefault();
        const data = new FormData(e.currentTarget);
        const password = String(data.get('password') ?? '');

        setError(null);
        setPending(true);
        try {
            await unlock(password);
        } catch {
            showError('Wrong password');
        } finally {
            setPending(false);
        }
    }

    return (
        <div id='root-container' className='center'>
            <form
                id='login-form'
                className={shake ? 'form center form-shake' : 'form center'}
                onSubmit={onSubmit}
                onAnimationEnd={(e) => { if (e.target === e.currentTarget) setShake(false); }}
            >
                <h4>Locked</h4>
                {error && <p className='form-error'>{error}</p>}
                <p>The app is locked, please provide your password to continue</p>
                <label htmlFor='password'>Password</label>
                <input id='password' type='password' name='password' placeholder='Password' />
                <button type='submit' disabled={pending}>Unlock</button>
                {pending && <M3LoadingIndicator size={128} className='spinner' />}
            </form>
        </div>
    )
}

export default UnlockPage
