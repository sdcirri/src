import { useSession } from '@/session/useSession.ts';
import { type SubmitEvent, useState } from 'react';

import { ApiError } from '@/api/types.ts';
import { register } from '@/api/auth.ts';

import '@/css/forms.css';
import '@/css/main.css';

function RegisterPage() {
    const { signIn } = useSession();
    const [error, setError] = useState<string | null>(null);
    const [pending, setPending] = useState(false);

    async function onSubmit(e: SubmitEvent<HTMLFormElement>) {
        e.preventDefault();
        const data = new FormData(e.currentTarget);
        const username = String(data.get('username') ?? '');
        const displayName = String(data.get('displayName') ?? '');
        const password = String(data.get('password') ?? '');
        const passwordConfirm = String(data.get('passwordConfirm') ?? '');

        if (password !== passwordConfirm) {
            setError("Passwords don't match");
            setPending(false);
            return;
        }

        setError(null);
        setPending(true);
        try {
            await register({ username, displayName, password });
            await signIn(username, password);
        } catch (err) {
            setError(err instanceof ApiError && err.status === 401
                ? 'Username or password is wrong'
                : 'Registration failed');
        } finally {
            setPending(false);
        }
    }
    
    return (
        <div id='root-container' className='center'>
            <form id='login-form' className='form center' onSubmit={onSubmit}>
                <h4>Register</h4>
                {error && <p>{error}</p>}
                <label htmlFor='username'>Username</label>
                <input id='username' type='text' name='username' placeholder='Username' />
                <label htmlFor='displayName'>Display name</label>
                <input id='displayName' type='text' name='displayName' placeholder='Display name' />
                <label htmlFor='password'>Password</label>
                <input id='password' type='password' name='password' placeholder='Password' />
                <label htmlFor='passwordConfirm'>Confirm password</label>
                <input id='passwordConfirm' type='password' name='passwordConfirm' placeholder='Confirm password' />
                <button type='submit' disabled={pending}>Register</button>
            </form>
        </div>
    )
}

export default RegisterPage
