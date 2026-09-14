import type { ContactCryptoDto, UserCryptoDto, UserDto } from '@/api/types.ts';
import { request } from '@/api/client.ts';

export async function searchUsers(query: string, page: number | null): Promise<UserDto[]> {
    page = page ?? 0;
    if (query.length >= 3 && page >= 0)
        return request<UserDto[]>(`/users/search?q=${query}&n=${page}`)
    return [];
}

export async function getUserInfo(userId: string): Promise<UserDto> {
    return request<UserDto>(`/users/${userId}`);
}

export async function getMyCryptoSpecs(): Promise<UserCryptoDto> {
    return request<UserCryptoDto>('/users/me/crypto');
}

export async function getContactCryptoSpecs(contactId: string): Promise<ContactCryptoDto> {
    return request<ContactCryptoDto>(`/users/${contactId}/crypto`);
}

export async function changeDisplayName(newDisplayName: string): Promise<UserDto> {
    return request<UserDto>('/users/me/display_name', {
        method: 'PUT',
        body: JSON.stringify({ display_name: newDisplayName }),
    });
}

export async function changeUsername(newUsername: string): Promise<UserDto> {
    return request<UserDto>('/users/me/username', {
        method: 'PUT',
        body: JSON.stringify({ username: newUsername }),
    });
}

export async function changeProPic(image: File | Blob): Promise<UserDto> {
    const formData = new FormData();
    formData.append('image', image);

    return request<UserDto>('/users/me/propic', {
        method: 'PUT',
        body: formData,
    });
}
