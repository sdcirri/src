import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { IFrame, IMessage, StompConfig } from '@stomp/stompjs';

import type { MessageDto } from '@/api/types.ts';

type MockClient = {
    activate: ReturnType<typeof vi.fn>;
    deactivate: ReturnType<typeof vi.fn>;
    subscribe: ReturnType<typeof vi.fn>;
};

type ConnectedStompConfig = StompConfig & {
    onConnect: NonNullable<StompConfig['onConnect']>;
};

const stompMock = vi.hoisted(() => ({
    configs: [] as StompConfig[],
    instances: [] as MockClient[],
}));

vi.mock('@stomp/stompjs', () => ({
    Client: class {
        activate = vi.fn();
        deactivate = vi.fn().mockResolvedValue(undefined);
        subscribe = vi.fn();

        constructor(config: StompConfig) {
            stompMock.configs.push(config);
            stompMock.instances.push(this);
        }
    },
}));

function currentConfig(): ConnectedStompConfig {
    const config = stompMock.configs.at(-1);

    if (config === undefined)
        throw new Error('No STOMP config available');
    if (config.onConnect === undefined)
        throw new Error('STOMP onConnect is not configured');

    return config as ConnectedStompConfig;
}

function currentClient(): MockClient {
    const client = stompMock.instances.at(-1);
    if (client === undefined)
        throw new Error('No STOMP client available');
    return client;
}

async function loadConnectMessages(
    protocol: 'http:' | 'https:' = 'http:',
    host = 'example.test',
) {
    vi.stubGlobal('location', {
        protocol,
        host,
    });

    vi.resetModules();

    const module = await import('@/api/ws.ts');
    return module.connectMessages;
}

beforeEach(() => {
    stompMock.configs.length = 0;
    stompMock.instances.length = 0;

    vi.clearAllMocks();
    vi.unstubAllGlobals();
});

describe('connectMessages', () => {

    it('uses ws:// when the page is HTTP', async () => {
        const connectMessages = await loadConnectMessages('http:', 'chat.example.test');
        connectMessages(vi.fn());
        expect(currentConfig().brokerURL).toBe('ws://chat.example.test/api/ws');
    });

    it('uses wss:// when the page is HTTPS', async () => {
        const connectMessages = await loadConnectMessages('https:', 'chat.example.test');
        connectMessages(vi.fn());
        expect(currentConfig().brokerURL).toBe('wss://chat.example.test/api/ws');
    });

    it('sets reconnectDelay to 5000 ms', async () => {
        const connectMessages = await loadConnectMessages();
        connectMessages(vi.fn());
        expect(currentConfig().reconnectDelay).toBe(5000);
    });

    it('activates the STOMP client immediately', async () => {
        const connectMessages = await loadConnectMessages();
        connectMessages(vi.fn());
        expect(currentClient().activate).toHaveBeenCalledTimes(1);
    });

    it('does not subscribe before the STOMP connection', async () => {
        const connectMessages = await loadConnectMessages();
        connectMessages(vi.fn());
        expect(currentClient().subscribe).not.toHaveBeenCalled();
    });

    it('subscribes to the correct destination after onConnect', async () => {
        const connectMessages = await loadConnectMessages();
        connectMessages(vi.fn());
        currentConfig().onConnect({} as IFrame);

        expect(currentClient().subscribe).toHaveBeenCalledTimes(1);
        expect(currentClient().subscribe).toHaveBeenCalledWith(
            '/user/msgQueue/messages',
            expect.any(Function),
        );
    });

    it('parses the JSON body and passes the MessageDto to onMessage', async () => {
        const connectMessages = await loadConnectMessages();
        const onMessage = vi.fn();
        connectMessages(onMessage);
        currentConfig().onConnect({} as IFrame);

        const subscriptionCallback = currentClient().subscribe.mock.calls[0][1];

        const dto = {
            id: 'message-123',
            content: 'ciao',
        } as unknown as MessageDto;

        const frame = { body: JSON.stringify(dto) } as IMessage;
        subscriptionCallback(frame);

        expect(onMessage).toHaveBeenCalledTimes(1);
        expect(onMessage).toHaveBeenCalledWith(dto);
    });

    it('delivers multiple messages in the order they arrive', async () => {
        const connectMessages = await loadConnectMessages();
        const received: MessageDto[] = [];

        connectMessages(message => { received.push(message); });

        currentConfig().onConnect({} as IFrame);

        const subscriptionCallback = currentClient().subscribe.mock.calls[0][1];

        const first = {
            id: '1',
            content: 'uno',
        } as unknown as MessageDto;

        const second = {
            id: '2',
            content: 'due',
        } as unknown as MessageDto;

        subscriptionCallback({ body: JSON.stringify(first) } as IMessage);

        subscriptionCallback({ body: JSON.stringify(second) } as IMessage);

        expect(received).toEqual([first, second]);
    });

    it('resubscribes to the queue when STOMP reconnects', async () => {
        const connectMessages = await loadConnectMessages();
        connectMessages(vi.fn());

        currentConfig().onConnect({} as IFrame);
        currentConfig().onConnect({} as IFrame);

        expect(currentClient().subscribe).toHaveBeenCalledTimes(2);

        expect(currentClient().subscribe).toHaveBeenNthCalledWith(
            1,
            '/user/msgQueue/messages',
            expect.any(Function),
        );

        expect(currentClient().subscribe).toHaveBeenNthCalledWith(
            2,
            '/user/msgQueue/messages',
            expect.any(Function),
        );
    });

    it('calls deactivate when cleanup runs', async () => {
        const connectMessages = await loadConnectMessages();

        const disconnect = connectMessages(vi.fn());
        disconnect();

        expect(currentClient().deactivate).toHaveBeenCalledTimes(1);
    });

    it('can be deactivated before onConnect is called', async () => {
        const connectMessages = await loadConnectMessages();

        const disconnect = connectMessages(vi.fn());
        disconnect();

        expect(currentClient().subscribe).not.toHaveBeenCalled();
        expect(currentClient().deactivate).toHaveBeenCalledTimes(1);
    });

    it('does not call onMessage when the JSON is malformed', async () => {
        const connectMessages = await loadConnectMessages();
        const onMessage = vi.fn();
        connectMessages(onMessage);
        currentConfig().onConnect({} as IFrame);

        const subscriptionCallback = currentClient().subscribe.mock.calls[0][1];

        const frame = { body: '{ invalid JSON' } as IMessage;

        expect(() => subscriptionCallback(frame)).toThrow(SyntaxError);
        expect(onMessage).not.toHaveBeenCalled();
    });

    it('propagates errors thrown by onMessage', async () => {
        const connectMessages = await loadConnectMessages();
        const expectedError = new Error('consumer failure');

        const onMessage = vi.fn(() => { throw expectedError; });
        connectMessages(onMessage);
        currentConfig().onConnect({} as IFrame);

        const subscriptionCallback = currentClient().subscribe.mock.calls[0][1];

        const dto = {
            id: 'message-123',
        } as unknown as MessageDto;

        expect(() => subscriptionCallback({
            body: JSON.stringify(dto),
        } as IMessage)).toThrow(expectedError);
    });
});
