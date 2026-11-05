import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { MessageDto } from '@/api/types.ts';

type MessageBubbleProps = {
    message: MessageDto;
    myPrivateX25519: Uint8Array;
    theirPublicX25519: Uint8Array;
};

const { messageBubble, observe, disconnect } = vi.hoisted(() => ({
    messageBubble: vi.fn(),
    observe: vi.fn(),
    disconnect: vi.fn(),
}));

vi.mock('@/components/MessageBubble.tsx', () => ({
    default: (props: MessageBubbleProps) => {
        messageBubble(props);
        return <span>{props.message.id}</span>;
    },
}));

import MessageHistory from '@/components/MessageHistory.tsx';

const MY_PRIVATE_KEY = new Uint8Array([1, 2, 3]);
const THEIR_PUBLIC_KEY = new Uint8Array([4, 5, 6]);

function message(id: string, timestamp: number): MessageDto {
    return {
        id,
        senderId: 'sender-id',
        chatId: 'chat-id',
        timestamp,
        data: 'AQID',
        iv: 'BAUG',
        direction: 'INCOMING',
    };
}

const MESSAGE_1 = message('message-1', 1);
const MESSAGE_2 = message('message-2', 2);

function renderHistory(
    messages: MessageDto[] = [],
    hasMore = false,
    loadingMore = false,
    onLoadMore = vi.fn(),
) {
    return render(
        <MessageHistory
            messages={messages}
            hasMore={hasMore}
            loadingMore={loadingMore}
            onLoadMore={onLoadMore}
            myPrivateX25519={MY_PRIVATE_KEY}
            theirPublicX25519={THEIR_PUBLIC_KEY}
        />
    );
}

function setScrollMetrics(
    body: HTMLElement,
    scrollTop: number,
    scrollHeight: number,
    clientHeight: number,
) {
    Object.defineProperty(body, 'scrollHeight', { value: scrollHeight, configurable: true });
    Object.defineProperty(body, 'clientHeight', { value: clientHeight, configurable: true });
    body.scrollTop = scrollTop;
}

describe('MessageHistory', () => {
    beforeEach(() => {
        messageBubble.mockClear();
        observe.mockClear();
        disconnect.mockClear();

        vi.stubGlobal('ResizeObserver', class {
            observe = observe;
            disconnect = disconnect;
        });
    });

    it('renders the empty state when there are no messages', () => {
        renderHistory();

        expect(screen.getByText('No messages yet.')).toBeInTheDocument();
        expect(messageBubble).not.toHaveBeenCalled();
    });

    it('renders every message with the crypto keys', () => {
        renderHistory([MESSAGE_1, MESSAGE_2]);

        expect(screen.queryByText('No messages yet.')).not.toBeInTheDocument();
        expect(messageBubble).toHaveBeenCalledTimes(2);

        expect(messageBubble.mock.calls[0][0]).toEqual({
            message: MESSAGE_1,
            myPrivateX25519: MY_PRIVATE_KEY,
            theirPublicX25519: THEIR_PUBLIC_KEY,
        });

        expect(messageBubble.mock.calls[1][0]).toEqual({
            message: MESSAGE_2,
            myPrivateX25519: MY_PRIVATE_KEY,
            theirPublicX25519: THEIR_PUBLIC_KEY,
        });
    });

    it('loads older messages from the button', () => {
        const onLoadMore = vi.fn();

        renderHistory([MESSAGE_1], true, false, onLoadMore);

        fireEvent.click(screen.getByRole('button', { name: 'Load older messages' }));

        expect(onLoadMore).toHaveBeenCalledOnce();
    });

    it('disables load more while loading', () => {
        const onLoadMore = vi.fn();

        renderHistory([MESSAGE_1], true, true, onLoadMore);

        const button = screen.getByRole('button', { name: 'Loading older messages…' });

        expect(button).toBeDisabled();
        expect(screen.getByLabelText('Message history')).toHaveAttribute('aria-busy', 'true');

        fireEvent.click(button);

        expect(onLoadMore).not.toHaveBeenCalled();
    });

    it('does not render load more when there are no older messages', () => {
        renderHistory([MESSAGE_1]);

        expect(screen.queryByRole('button')).not.toBeInTheDocument();
    });

    it('loads more when scrolling near the top', () => {
        const onLoadMore = vi.fn();

        renderHistory([MESSAGE_1], true, false, onLoadMore);

        const body = screen.getByLabelText('Message history');
        setScrollMetrics(body, 20, 500, 100);

        fireEvent.scroll(body);

        expect(onLoadMore).toHaveBeenCalledOnce();
    });

    it.each([
        ['not near the top', true, false, 25, 500, 100],
        ['not scrollable', true, false, 20, 100, 100],
        ['no more messages', false, false, 20, 500, 100],
        ['already loading', true, true, 20, 500, 100],
    ])(
        'does not load more when %s',
        (_, hasMore, loadingMore, scrollTop, scrollHeight, clientHeight) => {
            const onLoadMore = vi.fn();

            renderHistory([MESSAGE_1], hasMore, loadingMore, onLoadMore);

            const body = screen.getByLabelText('Message history');
            setScrollMetrics(body, scrollTop, scrollHeight, clientHeight);

            fireEvent.scroll(body);

            expect(onLoadMore).not.toHaveBeenCalled();
        }
    );

    it('stays pinned to the bottom when messages change', () => {
        const { rerender } = renderHistory([MESSAGE_1]);

        const body = screen.getByLabelText('Message history');
        Object.defineProperty(body, 'scrollHeight', { value: 500, configurable: true });

        rerender(
            <MessageHistory
                messages={[MESSAGE_1, MESSAGE_2]}
                hasMore={false}
                loadingMore={false}
                onLoadMore={vi.fn()}
                myPrivateX25519={MY_PRIVATE_KEY}
                theirPublicX25519={THEIR_PUBLIC_KEY}
            />
        );

        expect(body.scrollTop).toBe(500);
    });

    it('preserves the visible message position when older messages are prepended', () => {
        const { rerender } = renderHistory([MESSAGE_1, MESSAGE_2]);

        const body = screen.getByLabelText('Message history');
        const first = document.querySelector('[data-message-id="message-1"]')!;
        const second = document.querySelector('[data-message-id="message-2"]')!;

        setScrollMetrics(body, 300, 1000, 100);

        body.getBoundingClientRect = () => ({ top: 100 } as DOMRect);
        first.getBoundingClientRect = () => ({ bottom: 90 } as DOMRect);

        let secondTop = 120;
        second.getBoundingClientRect = () => ({
            top: secondTop,
            bottom: secondTop + 20,
        } as DOMRect);

        fireEvent.scroll(body);

        secondTop = 170;

        rerender(
            <MessageHistory
                messages={[message('older', 0), MESSAGE_1, MESSAGE_2]}
                hasMore={false}
                loadingMore={false}
                onLoadMore={vi.fn()}
                myPrivateX25519={MY_PRIVATE_KEY}
                theirPublicX25519={THEIR_PUBLIC_KEY}
            />
        );

        expect(body.scrollTop).toBe(350);
    });

    it('does not change scroll position when the saved anchor no longer exists', () => {
        const { rerender } = renderHistory([MESSAGE_1]);

        const body = screen.getByLabelText('Message history');
        const first = document.querySelector('[data-message-id="message-1"]')!;

        setScrollMetrics(body, 300, 1000, 100);

        body.getBoundingClientRect = () => ({ top: 100 } as DOMRect);
        first.getBoundingClientRect = () => ({ top: 120, bottom: 140 } as DOMRect);

        fireEvent.scroll(body);

        rerender(
            <MessageHistory
                messages={[MESSAGE_2]}
                hasMore={false}
                loadingMore={false}
                onLoadMore={vi.fn()}
                myPrivateX25519={MY_PRIVATE_KEY}
                theirPublicX25519={THEIR_PUBLIC_KEY}
            />
        );

        expect(body.scrollTop).toBe(300);
    });

    it('observes both containers and disconnects on unmount', () => {
        const { unmount } = renderHistory();

        expect(observe).toHaveBeenCalledTimes(2);

        unmount();

        expect(disconnect).toHaveBeenCalledOnce();
    });

    it('keeps the current position when not pinned and no message is visible', () => {
        const { rerender } = renderHistory([MESSAGE_1]);

        const body = screen.getByLabelText('Message history');
        const first = document.querySelector('[data-message-id="message-1"]')!;

        setScrollMetrics(body, 300, 1000, 100);

        body.getBoundingClientRect = () => ({ top: 100 } as DOMRect);
        first.getBoundingClientRect = () => ({ top: 50, bottom: 90 } as DOMRect);

        fireEvent.scroll(body);

        rerender(
            <MessageHistory
                messages={[MESSAGE_1, MESSAGE_2]}
                hasMore={false}
                loadingMore={false}
                onLoadMore={vi.fn()}
                myPrivateX25519={MY_PRIVATE_KEY}
                theirPublicX25519={THEIR_PUBLIC_KEY}
            />
        );

        expect(body.scrollTop).toBe(300);
    });
});
