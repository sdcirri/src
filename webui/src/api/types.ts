
export class ApiError extends Error {
    readonly status: number;

    constructor(status: number, message: string) {
        super(message);
        this.name = 'ApiError';
        this.status = status;
    }
}

export type MessageDto = {
    timestamp: number;
    data: string;
    iv: string;
    direction: 'INCOMING' | 'OUTGOING';
};

export type ChatDto = {
    chatId: string;
    contactId: string;
    lastMessage: MessageDto;
};

export type ContactCryptoDto = {
    publicEd25519: string;
    publicX25519: string;
};

export type UserCryptoDto = {
    id: string;
    kekSalt: string;
    privateEd25519Crypto: string;
    privateEd25519IV: string;
    publicEd25519: string;
    privateX25519Crypto: string;
    privateX25519IV: string;
    publicX25519: string;
};

export type UserDto = {
    id: string;
    username: string;
    displayName: string;
    proPic: string;
};

export type LoginRequest = {
    username: string;
    password: string;
};

export type MessageRequest = {
    messageData: string;
    messageIV: string;
};

export type UserRegistrationRequest = {
    username: string;
    displayName: string | null;
    password: string;
};

export type UserRegistrationFinalizationRequest = {
    kekSalt: string;
    privateEd25519Crypto: string;
    privateEd25519IV: string;
    publicEd25519: string;
    privateX25519Crypto: string;
    privateX25519IV: string;
    publicX25519: string;
};

export type PasswordChangeRequest = {
    password: string;
    newKekSalt: string;
    newPrivateEd25519: string;
    newIvEd25519: string;
    newPrivateX25519: string;
    newIvX25519: string;
};
