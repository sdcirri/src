import type { ContactCryptoDto, MessageDto, MessageRequest } from '@/api/types.ts';
import { request } from '@/api/client.ts';

import type { DecryptedCryptoSpecs } from '@/crypto/kek.ts';
import { fromBase64, toBase64 } from '@/crypto/common.ts';
import { encryptMessage } from '@/crypto/messaging.ts';

export async function getChat(contactId: string, pageNumber: number = 0): Promise<MessageDto[]> {
    return request<MessageDto[]>(`/chats/${contactId}?pageNumber=${pageNumber}`, {
        method: 'POST',
    })
}

export async function sendMessage(
    contactId: string,
    text: string,
    ownCryptoSpecs: DecryptedCryptoSpecs,
    contactCryptoSpecs: ContactCryptoDto,
): Promise<MessageDto> {
    const contactKey = fromBase64(contactCryptoSpecs.publicX25519);
    const crypto = await encryptMessage(text, ownCryptoSpecs.privateX25519, contactKey);
    const req: MessageRequest = {
        messageData: toBase64(crypto.messageData),
        messageIV: toBase64(crypto.messageIV),
    }

    return request<MessageDto>(`/chats/${contactId}`, {
        method: 'POST',
        body: JSON.stringify(req),
    })
}
