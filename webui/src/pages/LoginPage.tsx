import { Link } from 'react-router';

import '@/css/forms.css';
import '@/css/main.css';

function LoginPage() {
    return (
        <div id='root-container' className='center'>
            <form id='login-form' className='form center'>
                <h4>Login</h4>
                <label htmlFor='username'>Username</label>
                <input id='username' type='text' name='username' placeholder='Username' />
                <label htmlFor='password'>Password</label>
                <input id='password' type='password' name='password' placeholder='Password' />
                <button type='submit'>Login</button>
                <Link to='/register'>New here? Register!</Link>
            </form>
        </div>
    )
}

export default LoginPage
