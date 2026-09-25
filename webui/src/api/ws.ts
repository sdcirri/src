import { Client, type IMessage } from '@stomp/stompjs';
import type { MessageDto } from '@/api/types.ts';

const WS_URL = `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/api/ws`;

export function connectMessages(onMessage: (message: MessageDto) => void): () => void {
    const client = new Client({
        brokerURL: WS_URL,
        reconnectDelay: 5000,
        onConnect: () => {
            client.subscribe('/user/msgQueue/messages', (frame: IMessage) => {
                onMessage(JSON.parse(frame.body) as MessageDto);
            });
        },
    });
    client.activate();
    return () => { void client.deactivate(); };
}
