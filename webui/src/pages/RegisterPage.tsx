import { type SubmitEvent, useState } from 'react';
import { useNavigate } from 'react-router';

import { useSession } from '@/session/useSession.ts';
import { ApiError } from '@/api/types.ts';

import '@/css/forms.css';
import '@/css/main.css';

function RegisterPage() {
    const { signUp } = useSession();
    const navigate = useNavigate();
    const [error, setError] = useState<string | null>(null);
    const [pending, setPending] = useState(false);

    async function onSubmit(e: SubmitEvent<HTMLFormElement>) {
        e.preventDefault();
        const data = new FormData(e.currentTarget);
        const username = String(data.get('username') ?? '');
        const displayName = String(data.get('displayName')).length > 0 ? String(data.get('displayName')) : null;
        const password = String(data.get('password') ?? '');
        const passwordConfirm = String(data.get('passwordConfirm') ?? '');

        setError(null);
        setPending(true);

        if (displayName != null && (displayName.length < 1 || displayName.length > 255)) {
            setError('Bad display name');
            setPending(false);
            return;
        }
        if (!/^[A-Za-z0-9_.-]{3,255}$/.test(username)) {
            setError('Bad username');
            setPending(false);
            return;
        }
        if (password.length < 8 || password.length > 255) {
            setError('Password must be between 8 and 255 characters');
            setPending(false);
            return;
        }
        if (password !== passwordConfirm) {
            setError('Passwords don\'t match');
            setPending(false);
            return;
        }

        try {
            await signUp(username, displayName, password);
            navigate('/', { replace: true });
        } catch (err) {
            if (!(err instanceof ApiError)) {
                setError('Registration failed');
            } else {
                switch (err.status) {
                    case 409:
                        setError('Username already taken');
                        break;
                    case 400:
                        setError('Password too weak');
                        break;
                    default:
                        setError('Registration failed');
                }
            }
        } finally {
            setPending(false);
            setError(null);
        }
    }
    
    return (
        <div id='root-container' className='center'>
            <form id='login-form' className='form center' onSubmit={onSubmit}>
                <h4>Register</h4>
                {error && <p className='form-error'>{error}</p>}
                <label htmlFor='username'>Username</label>
                <input id='username' type='text' name='username' placeholder='Username' required />
                <label htmlFor='displayName'>Display name (optional)</label>
                <input id='displayName' type='text' name='displayName' placeholder='Display name' />
                <label htmlFor='password'>Password</label>
                <input id='password' type='password' name='password' placeholder='Password' required />
                <label htmlFor='passwordConfirm'>Confirm password</label>
                <input id='passwordConfirm' type='password' name='passwordConfirm' placeholder='Confirm password' required />
                <button type='submit' disabled={pending}>Register</button>
            </form>
        </div>
    )
}

export default RegisterPage
