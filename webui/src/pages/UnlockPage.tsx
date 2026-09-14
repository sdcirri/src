import '@/css/main.css';
import '@/css/forms.css';

function UnlockPage() {
    return (
        <div id='root-container' className='center'>
            <form id='login-form' className='form center'>
                <h4>Locked</h4>
                <p>The app is locked, please provide your password to continue</p>
                <label htmlFor='password'>Password</label>
                <input id='password' type='password' name='password' placeholder='Password' />
                <button type='submit'>Unlock</button>
            </form>
        </div>
    )
}

export default UnlockPage
