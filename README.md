# 🚧🏗️ [WIP] 𝑆pring 𝑅eact 𝐶hat - an E2EE, cross-device chat that runs (almost) entirely in your browser

SRC is an end-to-end encrypted chat application designed to run as a web application across
a variety of devices.

The project aims to provide a secure cryptographic protocol supporting multiple concurrent
sessions across multiple devices, while keeping conversations and session state synchronized
between them.

This is meant as a personal project exploring secure communication protocols, not as a
production-ready product to be used in real, high-risk communication scenarios.

## Features

- **End-to-end encryption protocol**: communication is secured with a per-contact secret, each chat
  is secured with a secret key stored nowhere, but locally computed each time for each chat
- **Cryptographic algorithms run entirely inside your browser**: all cryptographic algorithms are
  implemented in TypeScript, thanks to the _Noble_ cryptography libraries, and run entirely inside your
  local browser, the back end acts essentially as a vault and message broker, securely storing cryptographic
  material and encrypted messages and forwarding them to the recipient, who is the only one, except for the
  sender, who is able to decrypt the message
- **Portable cryptographic identity**: private cryptographic material is encrypted using a key derived from
  the user's password before being stored by the backend. This allows a user to recover the same cryptographic
  identity from another browser session without storing plaintext private keys on the server
- **Multiple concurrent sessions**: the authentication model supports multiple active sessions for the same
  account, each with independently revocable access and refresh tokens
- **Client-side private-key protection**: private keys are encrypted before leaving the browser and are only
  decrypted locally after the user authenticates and provides the required key material

## Protocol specifications
**TBA**

## Known limitations
**TBA**

## Tech stack
### Backend
![Java](https://img.shields.io/badge/java-%23ED8B00.svg?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/spring%20boot-%236DB33F.svg?style=for-the-badge&logo=springboot&logoColor=white)
![Hibernate](https://img.shields.io/badge/Hibernate-%2359666C.svg?style=for-the-badge&logo=Hibernate&logoColor=white)

### Frontend
![TypeScript](https://img.shields.io/badge/typescript-%23007ACC.svg?style=for-the-badge&logo=typescript&logoColor=white)
![React](https://img.shields.io/badge/react-%2320232a.svg?style=for-the-badge&logo=react&logoColor=%2361DAFB)
![Vite](https://img.shields.io/badge/vite-%23646CFF.svg?style=for-the-badge&logo=vite&logoColor=white)

### Data and infrastructure
![PostgreSQL](https://img.shields.io/badge/postgresql-%23316192.svg?style=for-the-badge&logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/docker-%230db7ed.svg?style=for-the-badge&logo=docker&logoColor=white)
![Nginx](https://img.shields.io/badge/nginx-%23009639.svg?style=for-the-badge&logo=nginx&logoColor=white)

### Security and testing
![JUnit](https://img.shields.io/badge/JUnit%206-%23f5f5f5.svg?style=for-the-badge&logo=openjdk&logoColor=dc524a)
![Semgrep](https://img.shields.io/badge/semgrep-%2300C7B7.svg?style=for-the-badge&logoColor=white)

## Build and run
**TBA**
