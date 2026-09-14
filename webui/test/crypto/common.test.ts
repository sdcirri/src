import { describe, expect, it } from 'vitest';

import { aesDecrypt, aesEncrypt, fromBase64, toBase64 } from '@/crypto/common';

import { expectBytes } from './helpers';

describe('toBase64 / fromBase64', () => {
    it('roundtrip', () => {
        const bytes = new Uint8Array([0, 1, 127, 128, 255]);
        expectBytes(fromBase64(toBase64(bytes)), bytes);
    });

    it('empty input', () => {
        expect(toBase64(new Uint8Array(0))).toBe('');
        expectBytes(fromBase64(''), new Uint8Array(0));
    });
});

describe('aesEncrypt / aesDecrypt', () => {
    it('roundtrip', async () => {
        const key = crypto.getRandomValues(new Uint8Array(32));
        const plaintext = new TextEncoder().encode('hello world');
        const encrypted = await aesEncrypt(key, plaintext);
        expectBytes(await aesDecrypt(key, encrypted), plaintext);
    });

    it('empty plaintext', async () => {
        const key = crypto.getRandomValues(new Uint8Array(32));
        const plaintext = new Uint8Array(0);
        const encrypted = await aesEncrypt(key, plaintext);
        expectBytes(await aesDecrypt(key, encrypted), plaintext);
    });

    it('iv is 12 bytes', async () => {
        const key = crypto.getRandomValues(new Uint8Array(32));
        const encrypted = await aesEncrypt(key, new TextEncoder().encode('test'));
        expect(encrypted.iv.byteLength).toBe(12);
    });

    it('ciphertext differs from plaintext', async () => {
        const key = crypto.getRandomValues(new Uint8Array(32));
        const plaintext = new TextEncoder().encode('hello world');
        const encrypted = await aesEncrypt(key, plaintext);
        expect(Uint8Array.from(encrypted.cipherText)).not.toEqual(Uint8Array.from(plaintext));
    });

    it('different keys produce different ciphertext', async () => {
        const key1 = crypto.getRandomValues(new Uint8Array(32));
        const key2 = crypto.getRandomValues(new Uint8Array(32));
        const plaintext = new TextEncoder().encode('hello world');
        const enc1 = await aesEncrypt(key1, plaintext);
        const enc2 = await aesEncrypt(key2, plaintext);
        expect(Uint8Array.from(enc1.cipherText)).not.toEqual(Uint8Array.from(enc2.cipherText));
    });

    it('wrong key fails to decrypt', async () => {
        const key1 = crypto.getRandomValues(new Uint8Array(32));
        const key2 = crypto.getRandomValues(new Uint8Array(32));
        const encrypted = await aesEncrypt(key1, new TextEncoder().encode('hello'));
        await expect(aesDecrypt(key2, encrypted)).rejects.toThrow();
    });
});
