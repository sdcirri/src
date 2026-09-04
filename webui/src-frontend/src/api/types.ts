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
