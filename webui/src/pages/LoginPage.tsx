
function LoginPage() {
    return (
        <div className='center'>
            <form id='login-form' className='form center'>
                <h4>Login</h4>
                <label htmlFor='username'>Username</label>
                <input type='text' name='username' placeholder='Username' />
                <label htmlFor='password'>Password</label>
                <input type='password' name='password' placeholder='Password' />
                <button type='submit'>Login</button>
            </form>
        </div>
    )
}

export default LoginPage
