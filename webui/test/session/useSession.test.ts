import { renderHook } from '@testing-library/react';
import { expect, it } from 'vitest';

import { useSession } from '@/session/useSession';

it('throws when used outside SessionProvider', () => {
    expect(() => {
        renderHook(() => useSession());
    }).toThrow('useSession must be used inside SessionProvider');
});
