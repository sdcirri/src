import type { UserCryptoDto, UserDto } from '@/api/types.ts';
import type { DecryptedCryptoSpecs } from '@/crypto/kek.ts';

export type SessionStatus = 'loading' | 'anonymous' | 'locked' | 'unlocked';

export type Session = {
    status: SessionStatus;
    user: UserDto | null;
    crypto: UserCryptoDto | null;
    keys: DecryptedCryptoSpecs | null;
};
