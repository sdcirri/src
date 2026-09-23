import { useEffect, useState, type ReactNode } from 'react';

import { login, logout, refreshSession, register } from '@/api/auth.ts';
import { getMyCryptoSpecs, getUserInfo } from '@/api/users.ts';
import { decryptKeys } from '@/crypto/kek.ts';
import { ApiError } from '@/api/types.ts';

import { SessionContext } from './context.ts';
import type { Session } from './types.ts';

export function SessionProvider({ children }: { children: ReactNode }) {
    const [session, setSession] = useState<Session>({
        status: 'loading',
        user: null,
        crypto: null,
        keys: null,
    });

    useEffect(() => {
        bootstrap().then(setSession);
    }, []);

    return (
        <SessionContext.Provider value={{ session, signIn, signUp, unlock, signOut }}>
            {children}
        </SessionContext.Provider>
    );

    async function signIn(username: string, password: string) {
        await login({ username, password });                // setta i cookie
        const crypto = await getMyCryptoSpecs();
        const user = await getUserInfo(crypto.id);
        const keys = await decryptKeys(crypto, password);   // solo in RAM
        setSession({ status: 'unlocked', user, crypto, keys });
    }

    async function unlock(password: string) {
        const keys = await decryptKeys(session.crypto!, password);
        setSession({ ...session, status: 'unlocked', keys });
    }

    async function signUp(username: string, displayName: string | null, password: string) {
        const crypto = await register({ username, displayName, password });
        const user = await getUserInfo(crypto.id);
        const keys = await decryptKeys(crypto, password);
        setSession({ status: 'unlocked', user, crypto, keys });
    }

    async function signOut() {
        await logout();
        setSession({ status: 'anonymous', user: null, crypto: null, keys: null });
    }
}

async function bootstrap(): Promise<Session> {
    try {
        const crypto = await getMyCryptoSpecs();
        const user = await getUserInfo(crypto.id);
        return { status: 'locked', user, crypto, keys: null };
    } catch (e) {
        if (e instanceof ApiError && [401, 403].includes(e.status)) {
            try {
                await refreshSession();
                const crypto = await getMyCryptoSpecs();
                const user = await getUserInfo(crypto.id);
                return { status: 'locked', user, crypto, keys: null };
            } catch {
                return { status: 'anonymous', user: null, crypto: null, keys: null };
            }
        }
        throw e;
    }
}
