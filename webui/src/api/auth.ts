import type { LoginRequest, PasswordChangeRequest, UserCryptoDto, UserRegistrationRequest } from '@/api/types.ts';
import { request } from '@/api/client.ts';
import { bootstrapUserCrypto, reEncryptSpecs } from '@/crypto/kek.ts';

export function login(req: LoginRequest): Promise<void> {
    return request<void>('/auth/login', {
        method: 'POST',
        body: JSON.stringify(req),
    });
}

export function refreshSession(): Promise<void> {
    return request<void>('/auth/refresh', {
        method: 'POST',
    })
}

export function logout(): Promise<void> {
    return request<void>('/auth/logout', {
        method: 'POST',
    });
}

export async function register(req: UserRegistrationRequest): Promise<UserCryptoDto> {
    await request<void>('/auth/register', {
        method: 'POST',
        body: JSON.stringify(req),
    })

    const crypto = await bootstrapUserCrypto(req.password);
    return request<UserCryptoDto>('/auth/register/finalize', {
        method: 'POST',
        body: JSON.stringify(crypto),
    })
}

function toChangePasswordRequest(password: string, specs: UserCryptoDto): PasswordChangeRequest {
    return {
        password,
        newKekSalt: specs.kekSalt,
        newPrivateEd25519: specs.privateEd25519Crypto,
        newIvEd25519: specs.privateEd25519IV,
        newPrivateX25519: specs.privateX25519Crypto,
        newIvX25519: specs.privateX25519IV,
    }
}

export async function changePassword(oldPassword: string, newPassword: string, oldCrypto: UserCryptoDto): Promise<void> {
    const newSpecs = await reEncryptSpecs(oldPassword, newPassword, oldCrypto);
    return request<void>('/auth/me/password', {
        method: 'POST',
        body: JSON.stringify(toChangePasswordRequest(newPassword, newSpecs)),
    })
}
