import { useSession } from '@/session/useSession.ts';

import '@/css/forms.css';
import '@/css/main.css';

function ErrorPage() {
    const { retryBootstrap } = useSession();

    return (
        <div id='root-container' className='center'>
            <div className='form center'>
                <h4>Something went wrong</h4>
                <p>Unable to restore your session.</p>

                <button type='button' onClick={() => void retryBootstrap()}>
                    Retry
                </button>
            </div>
        </div>
    );
}

export default ErrorPage
