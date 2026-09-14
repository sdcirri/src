import '@/css/main.css';
import '@/css/forms.css';

function RegisterPage() {
    return (
        <div id='root-container' className='center'>
            <form id='login-form' className='form center'>
                <h4>Register</h4>
                <label htmlFor='username'>Username</label>
                <input id='username' type='text' name='username' placeholder='Username' />
                <label htmlFor='displayName'>Display name</label>
                <input id='displayName' type='text' name='displayName' placeholder='Display name' />
                <label htmlFor='password'>Password</label>
                <input id='password' type='password' name='password' placeholder='Password' />
                <label htmlFor='passwordConfirm'>Confirm password</label>
                <input id='passwordConfirm' type='password' name='passwordConfirm' placeholder='Confirm password' />
                <button type='submit'>Register</button>
            </form>
        </div>
    )
}

export default RegisterPage
