#include "aes_hardware.h"
#include "mbedtls/aes.h"

void aes128_encrypt_block(const uint8_t key[16], const uint8_t in[16], uint8_t out[16]) {
    mbedtls_aes_context ctx;
    mbedtls_aes_init(&ctx);
    // Set encryption key (128 bits)
    mbedtls_aes_setkey_enc(&ctx, key, 128);
    // ECB encrypt one block
    mbedtls_aes_crypt_ecb(&ctx, MBEDTLS_AES_ENCRYPT, in, out);
    mbedtls_aes_free(&ctx);
}

void aes128_decrypt_block(const uint8_t key[16], const uint8_t in[16], uint8_t out[16]) {
    mbedtls_aes_context ctx;
    mbedtls_aes_init(&ctx);
    // Set decryption key (128 bits)
    mbedtls_aes_setkey_dec(&ctx, key, 128);
    // ECB decrypt one block
    mbedtls_aes_crypt_ecb(&ctx, MBEDTLS_AES_DECRYPT, in, out);
    mbedtls_aes_free(&ctx);
}
