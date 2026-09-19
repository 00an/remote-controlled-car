// Hardware-accelerated AES-128 block wrapper using mbedTLS
#pragma once
#include <stdint.h>

// Encrypt a single 16-byte block with AES-128 (ECB).
// 'key' must be 16 bytes. 'in' must be 16 bytes. 'out' receives 16 bytes.
void aes128_encrypt_block(const uint8_t key[16], const uint8_t in[16], uint8_t out[16]);

// Decrypt a single 16-byte block with AES-128 (ECB).
void aes128_decrypt_block(const uint8_t key[16], const uint8_t in[16], uint8_t out[16]);
