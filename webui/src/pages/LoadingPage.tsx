import { M3LoadingIndicator } from '@alerix/m3-loading-indicator/react';

import '@/css/main.css';

function LoadingPage() {
    return (
        <div id='root-container' className='center'>
            <M3LoadingIndicator size={128} />
        </div>
    );
}

export default LoadingPage
