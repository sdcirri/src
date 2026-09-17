import { type SubmitEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router';

import { useSession } from '@/session/useSession.ts';
import { ApiError } from '@/api/types.ts';

import '@/css/forms.css';
import '@/css/main.css';

function LoginPage() {
    const { signIn } = useSession();
    const navigate = useNavigate();
    const [error, setError] = useState<string | null>(null);
    const [pending, setPending] = useState(false);

    async function onSubmit(e: SubmitEvent<HTMLFormElement>) {
        e.preventDefault();
        const data = new FormData(e.currentTarget);
        const username = String(data.get('username') ?? '');
        const password = String(data.get('password') ?? '');

        if (username.length < 3 || username.length > 255 || password.length < 8 || password.length > 255) {
            setError('Username or password is wrong');
            return;
        }

        setError(null);
        setPending(true);

        try {
            await signIn(username, password);
            navigate('/', { replace: true });
        } catch (err) {
            setError(err instanceof ApiError && err.status === 401
                ? 'Username or password is wrong'
                : 'Login failed');
        } finally {
            setPending(false);
        }
    }

    return (
        <div id='root-container' className='center'>
            <form id='login-form' className='form center' onSubmit={onSubmit}>
                <h4>Login</h4>
                {error && <p>{error}</p>}
                <label htmlFor='username'>Username</label>
                <input id='username' type='text' name='username' placeholder='Username' required />
                <label htmlFor='password'>Password</label>
                <input id='password' type='password' name='password' placeholder='Password' required />
                <button type='submit' disabled={pending}>Login</button>
                <Link to='/register'>New here? Register!</Link>
            </form>
        </div>
    )
}

export default LoginPage
