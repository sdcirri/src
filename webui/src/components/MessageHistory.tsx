import { useLayoutEffect, useRef } from 'react';

import type { MessageDto } from '@/api/types.ts';

import MessageBubble from '@/components/MessageBubble.tsx';

import '@/css/chat.css';

type MessageHistoryProps = {
    messages: MessageDto[];
    hasMore: boolean;
    loadingMore: boolean;
    onLoadMore: () => void;
    myPrivateX25519: Uint8Array;
    theirPublicX25519: Uint8Array;
};

type ScrollAnchor = { id: string; offset: number };
const BOTTOM_THRESHOLD = 48;

function MessageHistory({ messages, hasMore, loadingMore, onLoadMore, myPrivateX25519, theirPublicX25519 }: MessageHistoryProps) {
    const bodyRef = useRef<HTMLDivElement>(null);
    const listRef = useRef<HTMLDivElement>(null);
    const pinnedToBottom = useRef(true);
    const anchor = useRef<ScrollAnchor | null>(null);

    function rememberPosition() {
        const body = bodyRef.current;
        const list = listRef.current;
        if (!body || !list) return;

        pinnedToBottom.current = body.scrollHeight - body.scrollTop - body.clientHeight <= BOTTOM_THRESHOLD;
        const top = body.getBoundingClientRect().top;
        const firstVisible = Array.from(list.children).find(node => node.getBoundingClientRect().bottom > top);
        anchor.current = firstVisible ? {
            id: firstVisible.getAttribute('data-message-id')!,
            offset: firstVisible.getBoundingClientRect().top - top,
        } : null;
    }

    function restorePosition() {
        const body = bodyRef.current;
        const list = listRef.current;
        if (!body || !list) return;

        if (pinnedToBottom.current) {
            body.scrollTop = body.scrollHeight;
        } else if (anchor.current) {
            const saved = anchor.current;
            const node = Array.from(list.children).find(child => child.getAttribute('data-message-id') === saved.id);
            if (node) body.scrollTop += node.getBoundingClientRect().top - body.getBoundingClientRect().top - saved.offset;
        }
    }

    useLayoutEffect(() => {
        restorePosition();
    }, [messages, hasMore, loadingMore]);

    useLayoutEffect(() => {
        const body = bodyRef.current;
        const list = listRef.current;
        if (!body || !list) return;

        const observer = new ResizeObserver(restorePosition);
        observer.observe(body);
        observer.observe(list);
        return () => observer.disconnect();
    }, []);

    function handleScroll() {
        rememberPosition();
        const body = bodyRef.current;
        if (body && body.scrollTop <= 24 && body.scrollHeight > body.clientHeight && hasMore && !loadingMore) {
            onLoadMore();
        }
    }

    return (
        <div id='chat-body' ref={bodyRef} onScroll={handleScroll} aria-label='Message history' aria-busy={loadingMore}>
            {hasMore && (
                <button type='button' className='chat-load-more' disabled={loadingMore} onClick={onLoadMore}>
                    {loadingMore ? 'Loading older messages…' : 'Load older messages'}
                </button>
            )}
            <div className='chat-messages' ref={listRef}>
                {messages.map(message => (
                    <div className='chat-message' data-message-id={message.iv} key={message.iv}>
                        <MessageBubble message={message} myPrivateX25519={myPrivateX25519} theirPublicX25519={theirPublicX25519} />
                    </div>
                ))}
            </div>
            {messages.length === 0 && <p className='chat-status'>No messages yet.</p>}
        </div>
    );
}

export default MessageHistory;
