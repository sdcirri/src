import { type SubmitEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router';

import { M3LoadingIndicator } from '@alerix/m3-loading-indicator/react';
import { useSession } from '@/session/useSession.ts';
import { ApiError } from '@/api/types.ts';

import '@/css/forms.css';
import '@/css/main.css';

function LoginPage() {
    const navigate = useNavigate();
    const [error, setError] = useState<string | null>(null);
    const [shake, setShake] = useState(false);
    const [pending, setPending] = useState(false);
    const { signIn } = useSession();

    function showError(message: string) {
        setError(message);
        setShake(false);
        requestAnimationFrame(() => setShake(true));
    }

    async function onSubmit(e: SubmitEvent<HTMLFormElement>) {
        e.preventDefault();
        const data = new FormData(e.currentTarget);
        const username = String(data.get('username') ?? '');
        const password = String(data.get('password') ?? '');

        if (username.length < 3 || username.length > 255 || password.length < 8 || password.length > 255) {
            showError('Username or password is wrong');
            return;
        }

        setError(null);
        setPending(true);

        try {
            await signIn(username, password);
            navigate('/', { replace: true });
        } catch (err) {
            showError(err instanceof ApiError && err.status === 401
                ? 'Username or password is wrong'
                : 'Login failed');
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
                <h4>Login</h4>
                {error && <p className='form-error'>{error}</p>}
                <label htmlFor='username'>Username</label>
                <input id='username' type='text' name='username' placeholder='Username' required />
                <label htmlFor='password'>Password</label>
                <input id='password' type='password' name='password' placeholder='Password' required />
                <button type='submit' disabled={pending}>Login</button>
                {pending && <M3LoadingIndicator size={128} className='spinner' />}
                <Link to='/register'>New here? Register!</Link>
            </form>
        </div>
    )
}

export default LoginPage
