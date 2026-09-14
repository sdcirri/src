import { createContext } from 'react';
import type { Session } from './types.ts';

export const SessionContext = createContext<{
    session: Session;
    signIn: (username: string, password: string) => Promise<void>;
    unlock: (password: string) => Promise<void>;
    signOut: () => Promise<void>;
} | null>(null);
