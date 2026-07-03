# Cryptography Fundamentals

**Module 20 — Phase 5: Systems & Security**

---

## 1. Concept Overview

Cryptography is the science of securing information by transforming it into an unreadable form, ensuring only authorized parties can read or verify it. At the systems engineering level, cryptography provides four fundamental security properties:

- **Confidentiality** — only intended recipients can read the data (encryption)
- **Integrity** — data has not been altered in transit or at rest (hashing, HMAC)
- **Authenticity** — data genuinely comes from the claimed sender (digital signatures, HMAC)
- **Non-repudiation** — the sender cannot later deny having sent the data (digital signatures)

Modern software systems depend on cryptographic primitives at every layer: TLS securing HTTP traffic, bcrypt protecting stored passwords, HMAC authenticating API requests, and RSA/ECDSA signing JWTs and software releases. Understanding which primitive to use — and critically, which to avoid — separates engineers who build secure systems from those who build systems with subtle, catastrophic vulnerabilities.

**Scope of this module**: hash functions, symmetric encryption (AES-GCM), asymmetric encryption (RSA concepts), HMAC, Diffie-Hellman key exchange, salting and password hashing, digital signatures. The goal is interview-level depth plus the ability to implement correct primitives using Python's standard library.

---

## 2. Intuition

> A hash function is a fingerprint machine: feed in a document and it prints a unique 32-byte thumbprint. The same document always gives the same thumbprint. No two different documents should ever give the same thumbprint. And you can never reconstruct the document from the thumbprint alone.

**Mental model — the lockbox analogy for asymmetric encryption:**
Public key = a padlock you hand out freely. Anyone can snap it shut (encrypt). Only you hold the key (private key) that opens it (decrypt). For digital signatures, it is reversed: you seal the envelope with your private key, and anyone with your public key can verify the seal is yours — but no one else can forge it.

**Why it matters:**
- 2022 Uber breach involved stolen credentials because passwords were stored with weak hashing — a cryptographic failure that exposed 57 million records
- The 2011 LinkedIn breach stored passwords with unsalted SHA-1; attackers cracked 90% within days
- OWASP ranks "Cryptographic Failures" as A02:2021 — the second most critical web application vulnerability class
- Every TLS connection, every password verification, every signed JWT, every encrypted S3 bucket relies on the primitives covered here

**Key insight:** Cryptography does not eliminate risk; it transforms risk. A system using AES-256-GCM correctly is only as secure as its key management. The most common real-world cryptographic failures are not mathematical breaks — they are implementation mistakes: missing authentication, reused IVs, weak key derivation, and timing-vulnerable comparisons.

---

## 3. Core Principles

### Kerckhoffs's Principle
Security must rest entirely in the key, not in the secrecy of the algorithm. Public algorithms (AES, RSA, SHA-256) are better than obscure ones because they have been analyzed by thousands of cryptographers. "Security through obscurity" is not security.

### The One-Way Property (Hash Functions)
A cryptographic hash H(m) must be:
- **Preimage resistant**: given H(m), it is computationally infeasible to find m
- **Second-preimage resistant**: given m and H(m), it is computationally infeasible to find m' ≠ m where H(m') = H(m)
- **Collision resistant**: it is computationally infeasible to find any pair m, m' where H(m) = H(m')

### Avalanche Effect
Changing a single bit in the input produces approximately half the output bits flipping. This means similar inputs produce wildly different hashes — a property that prevents partial information leakage. SHA-256("hello") and SHA-256("Hello") share no obvious relationship.

### The Key Exchange Problem
Symmetric encryption is fast and secure but requires both parties to share a secret key. How do you share a secret over an insecure channel? This is the key exchange problem, solved by Diffie-Hellman and its elliptic curve variant (ECDH).

### Authenticated Encryption
Encryption alone provides confidentiality but not integrity. An attacker may not be able to read your ciphertext, but they might be able to flip bits in a predictable way (bit-flipping attacks on CBC mode). Authenticated Encryption with Associated Data (AEAD) — exemplified by AES-GCM — combines encryption with an authentication tag so any tampering is detected.

### The Cost Factor Principle for Password Hashing
General-purpose hash functions (SHA-256) execute in microseconds, allowing billions of guesses per second on GPU hardware. Password hash functions (bcrypt, scrypt, Argon2) are intentionally slow — bcrypt at cost factor 12 takes approximately 200–300 ms per hash — making brute-force attacks economically prohibitive.

---

## 4. Types / Architectures / Strategies

### 4.1 Hash Functions

| Function | Output Size | Status | Use Case |
|----------|------------|--------|----------|
| MD5 | 128 bits (16 bytes) | BROKEN — collision attacks exist | Legacy checksums only |
| SHA-1 | 160 bits (20 bytes) | BROKEN — collision demonstrated 2017 | Avoid for security |
| SHA-256 | 256 bits (32 bytes) | Secure | Integrity, signatures, HMAC |
| SHA-384 | 384 bits (48 bytes) | Secure | Higher security margin |
| SHA-512 | 512 bits (64 bytes) | Secure | When 256 bits is insufficient |
| SHA3-256 | 256 bits (32 bytes) | Secure | SHA-256 alternative (Keccak sponge) |
| BLAKE3 | 256 bits default | Secure, very fast | Performance-critical hashing |

SHA-256 properties: deterministic, one-way, 2^128 resistance to birthday attacks (birthday bound), 256-bit output = 32 bytes = 64 hex characters.

### 4.2 Symmetric Encryption

**AES (Advanced Encryption Standard):**
- Block cipher: encrypts 128-bit (16-byte) blocks
- Key sizes: 128-bit (16 bytes), 192-bit (24 bytes), 256-bit (32 bytes)
- AES-256 = 14 rounds of substitution-permutation operations
- Requires a mode of operation for data longer than one block

**Modes of Operation:**

| Mode | Authentication | Parallelizable | Notes |
|------|---------------|---------------|-------|
| ECB | No | Yes | BROKEN — identical blocks produce identical ciphertext |
| CBC | No | Decrypt only | Bit-flipping attacks; padding oracle vulnerabilities |
| CTR | No | Yes | Turns AES into a stream cipher |
| GCM | Yes (AEAD) | Yes | Recommended default; 128-bit authentication tag |
| CCM | Yes (AEAD) | No | Lower-memory environments |

**Use AES-256-GCM as the default symmetric cipher.** It provides both confidentiality and integrity. Requires a unique 96-bit (12-byte) nonce/IV per encryption operation with the same key. Reusing a nonce with the same key catastrophically breaks GCM security.

### 4.3 Asymmetric Encryption (Public-Key Cryptography)

**RSA:**
- Key generation: choose two large primes p, q; n = p*q; e = 65537 (standard); d = modular inverse of e
- Encrypt: c = m^e mod n; Decrypt: m = c^d mod n
- Security: based on hardness of factoring large integers
- RSA-2048 key = 2048 bits; RSA-4096 for long-term security
- Slow: RSA-2048 decryption ~10 ms; used to encrypt small payloads (symmetric keys)
- Hybrid encryption: use RSA to exchange an AES key, then AES for bulk data

**Elliptic Curve Cryptography (ECC):**
- Security based on elliptic curve discrete logarithm problem
- 256-bit ECDSA key ≈ 3072-bit RSA key in security level
- ECDH key exchange ~0.1 ms vs RSA ~10 ms — 100x faster
- Curve25519 / X25519 is the modern recommendation for key exchange
- NIST P-256 (secp256r1) widely used, NSA Suite B compliant

### 4.4 HMAC (Hash-based Message Authentication Code)

HMAC provides both integrity and authenticity using a shared secret key:

```
HMAC(K, m) = H((K XOR opad) || H((K XOR ipad) || m))
```

Where ipad = 0x36 repeated, opad = 0x5C repeated, || = concatenation.

Why not just H(K || m)? Because SHA-2 is vulnerable to **length extension attacks**: given H(K || m), an attacker can compute H(K || m || extra_data) without knowing K. The nested construction of HMAC prevents this.

**Common uses**: API request authentication (AWS Signature v4), JWT HS256 tokens, webhook signature verification.

### 4.5 Diffie-Hellman Key Exchange

Two parties (Alice and Bob) agree on a shared secret without ever transmitting it:

1. Public parameters: large prime p and generator g (both public)
2. Alice picks secret a, sends g^a mod p to Bob
3. Bob picks secret b, sends g^b mod p to Alice
4. Alice computes (g^b)^a mod p = g^ab mod p
5. Bob computes (g^a)^b mod p = g^ab mod p
6. Both arrive at the same shared secret g^ab mod p

An eavesdropper sees g^a and g^b but cannot compute g^ab without solving the discrete logarithm problem.

**ECDH**: same concept over elliptic curves; 256-bit keys instead of 2048-bit; ~100x faster.

### 4.6 Password Hashing and Salting

A **salt** is a random value (typically 16–32 bytes) generated per user and stored alongside the hash. Before hashing, the salt is concatenated with the password:

```
stored = bcrypt_hash(salt + password)  # bcrypt embeds salt in output
```

**Why salting is essential:**
- Without salt: an attacker precomputes H("password123") once and checks it against all rows in a stolen database
- Rainbow table attack: precomputed chains of hash→input mappings cover billions of common passwords
- With salt: the attacker must compute H(salt + "password123") separately for each row; precomputation is impossible
- bcrypt, scrypt, Argon2 all include automatic salting and are intentionally slow

**PBKDF2** (Password-Based Key Derivation Function 2): applies a hash function (HMAC-SHA256) repeatedly for a configurable number of iterations. NIST SP 800-132 (2023) recommends minimum 600,000 iterations for PBKDF2-HMAC-SHA256. Available in Python's `hashlib.pbkdf2_hmac`.

### 4.7 Digital Signatures

A digital signature provides authenticity and non-repudiation:

- **Sign**: `signature = encrypt(private_key, hash(message))`
- **Verify**: `hash(message) == decrypt(public_key, signature)`

Properties:
- Only the private key holder can create a valid signature
- Anyone with the public key can verify
- Signature covers the hash of the message, not the message itself (practical for large messages)
- Non-repudiation: the signer cannot deny signing (unlike HMAC, where any party with the shared key could have generated it)

Common algorithms: RSA-PSS (RSA with probabilistic padding), ECDSA (elliptic curve), Ed25519 (modern, fast, deterministic).

---

## 5. Architecture Diagrams

### Hash Function Flow

```
Input message (any size)
        |
        v
+------------------+
|   Hash Function   |   SHA-256: 64 rounds of mixing
|   (SHA-256)       |
+------------------+
        |
        v
Fixed-size output: 32 bytes (256 bits)
e9d71f5e...  <-- completely different from SHA-256("Hell")
                    even though input differs by 1 bit (avalanche)
```

### Symmetric Encryption (AES-256-GCM)

```
Plaintext ──────────────────────────────────────────┐
                                                     v
32-byte key ──> [ AES-256-GCM ] <── 12-byte nonce (unique per message)
                      |
                      v
          Ciphertext (same length as plaintext)
          +
          16-byte authentication tag
          +
          nonce (sent alongside ciphertext)

Decryption:
Ciphertext + tag + nonce + key ──> [ AES-256-GCM Decrypt ]
        |
        v (tag verified before any plaintext released)
Plaintext OR "Authentication Failed" exception
```

### Asymmetric Key Exchange + Hybrid Encryption

```
Alice                              Bob
  |                                  |
  |  Alice's RSA key pair:           |  Bob's RSA key pair:
  |  pub_A, priv_A                   |  pub_B, priv_B
  |                                  |
  |--- Bob's public key (pub_B) --->|  (key directory or cert)
  |                                  |
  |  Generate random AES-256 key K  |
  |  encrypted_K = RSA_encrypt(pub_B, K)
  |                                  |
  |--- encrypted_K + AES-GCM(K, data) --->|
  |                                  |
  |                    K = RSA_decrypt(priv_B, encrypted_K)
  |                    data = AES-GCM_decrypt(K, ciphertext)
```

### HMAC Construction

```
Key K (padded to block size)
    |
    |--- XOR ipad (0x36...) -------> [  K XOR ipad  ]
    |                                        |
    |                                  concat with message
    |                                        |
    |                                   Hash function
    |                                        |
    |                                   inner hash
    |
    |--- XOR opad (0x5C...) -------> [  K XOR opad  ]
                                             |
                                       concat with inner hash
                                             |
                                        Hash function
                                             |
                                         HMAC output
```

### Diffie-Hellman Key Exchange

```
Public knowledge: prime p, generator g

Alice                                Bob
secret a (random)                    secret b (random)
A = g^a mod p  ──── send A ────>    A received
                <─── send B ────    B = g^b mod p

shared = B^a mod p                  shared = A^b mod p
       = g^(ab) mod p               = g^(ab) mod p
       
Both arrive at the SAME shared secret.
Eavesdropper sees g^a mod p and g^b mod p but cannot
compute g^(ab) mod p without discrete logarithm.
```

### Password Storage Evolution

```
BROKEN: Plain text        "password123"  ──────> DB stores "password123"
                          attacker reads DB ──> instant compromise

BROKEN: Plain SHA-256     SHA256("password123") ──> DB stores hash
                          attacker uses rainbow table ──> cracks in seconds

BROKEN: SHA-256 + salt    SHA256(salt + "password123") ──> slightly better
  but still fast hash:    GPU does 10 billion SHA256/sec ──> brute-forceable

CORRECT: PBKDF2-HMAC-SHA256 (600k iterations) + random salt
  or bcrypt (cost 12) + embedded salt
  GPU does ~1000 bcrypt/sec at cost 12 ──> economically infeasible to crack
```

### TLS 1.3 Handshake (where crypto primitives converge)

```
Client                                          Server
  |                                               |
  |── ClientHello (supported ciphers, key share) ──>|
  |                                               |
  |<── ServerHello (chosen cipher, key share) ────|
  |    (ECDH X25519: both compute shared secret)  |
  |                                               |
  |<── {Certificate, CertVerify, Finished} ────── |
  |    (digital signature with server private key) |
  |                                               |
  |── {Finished} ─────────────────────────────>  |
  |    (HMAC of handshake transcript)             |
  |                                               |
  |<══ Encrypted application data (AES-256-GCM) ═>|
  
Primitives used: ECDH (key exchange), ECDSA/RSA (certificates),
HMAC-SHA256 (session keys derivation via HKDF), AES-256-GCM (bulk data)
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 Hash Functions with hashlib

```python
import hashlib
import secrets
import time

# SHA-256: 256-bit (32-byte) output
message = b"Hello, World!"
digest = hashlib.sha256(message).digest()       # bytes, 32 bytes
hex_digest = hashlib.sha256(message).hexdigest() # string, 64 hex chars

print(f"SHA-256 output length: {len(digest)} bytes = {len(digest)*8} bits")
print(f"Hex representation: {hex_digest}")

# Avalanche effect demonstration
msg1 = b"Hello, World!"
msg2 = b"Hello, World."   # only last char changed

h1 = hashlib.sha256(msg1).hexdigest()
h2 = hashlib.sha256(msg2).hexdigest()

# Count differing bits
bits1 = bin(int(h1, 16))[2:].zfill(256)
bits2 = bin(int(h2, 16))[2:].zfill(256)
differing = sum(b1 != b2 for b1, b2 in zip(bits1, bits2))
print(f"Changed 1 byte. Differing output bits: {differing}/256 (~{differing/256*100:.0f}%)")
# Output: approximately 128 bits differ (50% of output flips)

# Multiple SHA variants
data = b"system design"
algos = {
    "md5":     hashlib.md5(data).hexdigest(),     # 32 hex = 16 bytes (DO NOT USE for security)
    "sha1":    hashlib.sha1(data).hexdigest(),     # 40 hex = 20 bytes (DO NOT USE for security)
    "sha256":  hashlib.sha256(data).hexdigest(),   # 64 hex = 32 bytes
    "sha384":  hashlib.sha384(data).hexdigest(),   # 96 hex = 48 bytes
    "sha512":  hashlib.sha512(data).hexdigest(),   # 128 hex = 64 bytes
    "sha3_256": hashlib.sha3_256(data).hexdigest(),# 64 hex = 32 bytes (Keccak)
    "blake2b": hashlib.blake2b(data).hexdigest(),  # 128 hex = 64 bytes
}
for algo, digest in algos.items():
    print(f"{algo:10s}: {len(digest)//2:3d} bytes  {digest[:16]}...")

# Streaming hash for large files (memory-efficient)
def sha256_file(filepath: str) -> str:
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()
```

### 6.2 HMAC for Message Authentication

```python
import hmac
import hashlib
import secrets
import base64

# Generate a secure 32-byte shared secret key
secret_key = secrets.token_bytes(32)  # 256-bit key

# Sign a message
message = b"user_id=42&amount=100&currency=USD"
mac = hmac.new(secret_key, message, hashlib.sha256).digest()
mac_hex = mac.hex()
print(f"HMAC-SHA256 (32 bytes): {mac_hex}")

# Verify a message — CRITICAL: use hmac.compare_digest, NOT ==
def verify_hmac(key: bytes, message: bytes, received_mac: bytes) -> bool:
    expected = hmac.new(key, message, hashlib.sha256).digest()
    # hmac.compare_digest performs constant-time comparison
    # prevents timing attacks where attacker learns partial information
    # from how long the comparison takes
    return hmac.compare_digest(expected, received_mac)

# Correct verification
is_valid = verify_hmac(secret_key, message, mac)
print(f"Valid signature: {is_valid}")  # True

# Tampered message
tampered = b"user_id=42&amount=999&currency=USD"
is_tampered_valid = verify_hmac(secret_key, tampered, mac)
print(f"Tampered message valid: {is_tampered_valid}")  # False

# Webhook signature pattern (similar to GitHub, Stripe)
def create_webhook_signature(payload: bytes, secret: bytes) -> str:
    mac = hmac.new(secret, payload, hashlib.sha256).hexdigest()
    return f"sha256={mac}"

def verify_webhook_signature(payload: bytes, secret: bytes, header: str) -> bool:
    expected = create_webhook_signature(payload, secret)
    return hmac.compare_digest(expected, header)

webhook_secret = secrets.token_bytes(32)
payload = b'{"event": "payment.succeeded", "amount": 100}'
sig_header = create_webhook_signature(payload, webhook_secret)
print(f"Webhook signature: {sig_header[:30]}...")
print(f"Verified: {verify_webhook_signature(payload, webhook_secret, sig_header)}")
```

### 6.3 Password Hashing with PBKDF2-HMAC

```python
import hashlib
import secrets
import base64
import time

# PBKDF2-HMAC: the standard library equivalent of bcrypt/Argon2
# NIST SP 800-132 (2023): minimum 600,000 iterations for PBKDF2-HMAC-SHA256

ITERATIONS = 600_000   # NIST 2023 minimum
SALT_LENGTH = 32       # 256-bit random salt
DK_LENGTH   = 32       # 256-bit derived key output

def hash_password(password: str) -> dict:
    """Hash a password securely. Returns dict with all fields needed for verification."""
    salt = secrets.token_bytes(SALT_LENGTH)
    
    start = time.perf_counter()
    dk = hashlib.pbkdf2_hmac(
        hash_name="sha256",
        password=password.encode("utf-8"),
        salt=salt,
        iterations=ITERATIONS,
        dklen=DK_LENGTH,
    )
    elapsed_ms = (time.perf_counter() - start) * 1000
    
    return {
        "algorithm": "pbkdf2_hmac_sha256",
        "iterations": ITERATIONS,
        "salt": base64.b64encode(salt).decode(),
        "hash": base64.b64encode(dk).decode(),
        "time_ms": elapsed_ms,
    }

def verify_password(password: str, stored: dict) -> bool:
    """Verify a password against a stored hash record."""
    salt = base64.b64decode(stored["salt"])
    expected_hash = base64.b64decode(stored["hash"])
    
    dk = hashlib.pbkdf2_hmac(
        hash_name="sha256",
        password=password.encode("utf-8"),
        salt=salt,
        iterations=stored["iterations"],
        dklen=len(expected_hash),
    )
    # Constant-time comparison — same timing whether 1 byte or all bytes match
    return hmac.compare_digest(dk, expected_hash)

# Demo
import hmac

stored = hash_password("mysecretpassword")
print(f"Hash record: algorithm={stored['algorithm']}, iterations={stored['iterations']}")
print(f"Salt (b64): {stored['salt'][:20]}...")
print(f"Hash (b64): {stored['hash'][:20]}...")
print(f"Time to hash: {stored['time_ms']:.1f} ms")

print(f"\nCorrect password: {verify_password('mysecretpassword', stored)}")  # True
print(f"Wrong password:   {verify_password('wrongpassword', stored)}")      # False

# Two different users with the SAME password get DIFFERENT stored hashes
# because each has a unique random salt
stored2 = hash_password("mysecretpassword")
print(f"\nSame password, different hashes:")
print(f"  User 1: {stored['hash'][:20]}...")
print(f"  User 2: {stored2['hash'][:20]}...")
print(f"  Identical: {stored['hash'] == stored2['hash']}")  # False
```

### 6.4 Deriving Multiple Keys from a Single Secret (HKDF)

```python
import hashlib
import hmac

# HKDF: HMAC-based Key Derivation Function (RFC 5869)
# Used in TLS 1.3 to derive session keys from ECDH shared secret
# stdlib implementation

def hkdf_extract(salt: bytes | None, input_key_material: bytes) -> bytes:
    """HKDF Extract step: converts input key material to a pseudorandom key."""
    if salt is None:
        salt = bytes(hashlib.sha256().digest_size)  # 32 zero bytes
    return hmac.new(salt, input_key_material, hashlib.sha256).digest()

def hkdf_expand(prk: bytes, info: bytes, length: int) -> bytes:
    """HKDF Expand step: expands a pseudorandom key to the desired length."""
    hash_len = len(prk)
    n = (length + hash_len - 1) // hash_len
    okm = b""
    t = b""
    for i in range(1, n + 1):
        t = hmac.new(prk, t + info + bytes([i]), hashlib.sha256).digest()
        okm += t
    return okm[:length]

def hkdf(input_key_material: bytes, length: int,
         salt: bytes | None = None, info: bytes = b"") -> bytes:
    """Full HKDF: extract + expand."""
    prk = hkdf_extract(salt, input_key_material)
    return hkdf_expand(prk, info, length)

# Example: derive separate encryption and MAC keys from a shared secret
shared_secret = secrets.token_bytes(32)  # e.g., output of ECDH
enc_key = hkdf(shared_secret, 32, info=b"encryption key")
mac_key = hkdf(shared_secret, 32, info=b"mac key")

print(f"Encryption key: {enc_key.hex()[:32]}...")
print(f"MAC key:        {mac_key.hex()[:32]}...")
print(f"Keys differ:    {enc_key != mac_key}")  # True — different contexts
```

### 6.5 Timing-Safe Comparison

```python
import hmac
import time

# Demonstrate why constant-time comparison matters
def insecure_compare(a: bytes, b: bytes) -> bool:
    """INSECURE: short-circuits on first mismatch — leaks timing info."""
    if len(a) != len(b):
        return False
    for x, y in zip(a, b):
        if x != y:
            return False   # returns early! timing varies with match length
    return True

def secure_compare(a: bytes, b: bytes) -> bool:
    """SECURE: constant time regardless of where strings differ."""
    return hmac.compare_digest(a, b)   # always compares all bytes

# Timing oracle demo (illustrative — real attacks need millions of samples)
target = b"secret_token_123456"
early_mismatch = b"Xecret_token_123456"   # mismatch at index 0
late_mismatch  = b"secret_token_12345X"   # mismatch at index 18

# In theory (and practice at scale):
# insecure_compare(target, early_mismatch) returns faster than
# insecure_compare(target, late_mismatch)
# Attacker uses this timing difference to guess bytes one at a time
# Against a remote API, requires millions of requests but is feasible

# hmac.compare_digest is the correct default for ALL token/secret comparisons
assert secure_compare(target, target) == True
assert secure_compare(target, early_mismatch) == False
```

---

## 7. Real-World Examples

### TLS 1.3 Session Establishment
When your browser connects to https://example.com, in order:
1. Client and server perform X25519 ECDH to establish a shared secret (takes ~0.1 ms)
2. HKDF (HMAC-SHA256) derives separate encryption and MAC keys from the shared secret
3. Server sends its certificate (RSA or ECDSA digital signature by a CA)
4. Client verifies the certificate chain, confirms the server's identity
5. Subsequent data encrypted with AES-256-GCM (or ChaCha20-Poly1305 on mobile)

### JWT (JSON Web Tokens)
A JWT = header.payload.signature
- HS256 variant: `signature = HMAC-SHA256(secret, base64(header) + "." + base64(payload))`
  — shared secret, symmetric; both issuer and verifier need the same key
- RS256 variant: `signature = RSA-SHA256-sign(private_key, header + "." + payload)`
  — asymmetric; issuer has private key, all verifiers have public key

### AWS Signature Version 4
Every AWS API request is authenticated with HMAC-SHA256. The signature incorporates the request method, URL, headers, body hash, date, and region — chaining four HMAC operations to derive a date-scoped, region-scoped signing key. This prevents replay attacks and ensures requests cannot be tampered with in transit.

### Password Storage: The LinkedIn 2012 Breach
LinkedIn stored 6.5 million passwords as unsalted SHA-1 hashes. Within three days of the database being leaked, 90% of hashes had been cracked using precomputed rainbow tables. Unsalted SHA-1 is fast enough that a single GPU runs 20 billion SHA-1 operations per second. Had LinkedIn used bcrypt at cost 10, the same GPU could attempt only ~100,000 guesses per second, and each user would have a unique salt defeating precomputation.

### Signal Protocol (Double Ratchet)
Signal's end-to-end encryption combines X3DH (Extended Triple Diffie-Hellman) for initial key agreement with a Double Ratchet algorithm — alternating Diffie-Hellman ratchet and symmetric-key ratchet — providing forward secrecy (past messages safe if current key is compromised) and break-in recovery (future messages safe after a breach is detected). All built on Curve25519 ECDH and HKDF.

### Software Release Signatures
Linux package managers (apt, yum) verify GPG signatures on every package before installation. A package maintainer signs the package hash with their RSA or Ed25519 private key; the package manager verifies with the maintainer's public key. This ensures packages have not been tampered with since signing, even if the distribution mirror is compromised.

---

## 8. Tradeoffs

### Symmetric vs Asymmetric Encryption

| Property | Symmetric (AES-256-GCM) | Asymmetric (RSA-2048) |
|----------|------------------------|----------------------|
| Speed | Very fast (~1 GB/s on modern CPU with AES-NI) | Slow (~100 KB/s for RSA encrypt/decrypt) |
| Key size | 32 bytes (256-bit key) | 256 bytes (2048-bit key) |
| Key distribution | Hard — need secure channel to share key | Easy — publish public key freely |
| Use case | Bulk data encryption, data at rest | Key exchange, signatures, encrypting small data |
| Scalability | 1 key per pair of parties (N^2 keys for N users) | 1 key pair per user (N keys for N users) |

### Password Hashing Algorithms

| Algorithm | Speed | Salt | Built-in | GPU Resistance | Recommendation |
|-----------|-------|------|----------|---------------|---------------|
| MD5 / SHA-1 | ~10 billion/sec (GPU) | No | No | None | Never use for passwords |
| SHA-256 | ~1 billion/sec (GPU) | No | No | None | Never use for passwords alone |
| PBKDF2-HMAC-SHA256 | Configurable | Yes (separate) | Python stdlib | Low–Medium | Acceptable; use 600k+ iterations |
| bcrypt (cost 12) | ~1,000/sec (GPU) | Yes (embedded) | No (third-party) | Good | Recommended; well-studied |
| scrypt | Configurable | Yes | No (third-party) | Good (memory-hard) | Good for high security |
| Argon2id | Configurable | Yes | No (third-party) | Best (memory + CPU) | Current best practice (OWASP 2024) |

### Hash Functions: Speed vs Security

| Algorithm | Throughput | Security | Notes |
|-----------|-----------|----------|-------|
| MD5 | ~10 GB/s | BROKEN | Collisions demonstrated; do not use for security |
| SHA-1 | ~5 GB/s | BROKEN | SHA-1 collision found (SHAttered, 2017) |
| SHA-256 | ~1–3 GB/s (with SHA-NI) | Secure | Standard for integrity; 2^128 collision resistance |
| SHA3-256 | ~0.5–1 GB/s | Secure | Different construction (Keccak sponge); immune to length extension |
| BLAKE3 | ~5 GB/s | Secure | Very fast; parallel; good for checksums |

### HMAC vs Digital Signatures

| Property | HMAC | Digital Signature (RSA/ECDSA) |
|----------|------|-------------------------------|
| Keys | Shared secret | Public/private key pair |
| Non-repudiation | No — either party could have generated it | Yes — only private key holder can sign |
| Speed | Very fast | Slow (RSA), moderate (ECDSA) |
| Key distribution | Both parties need the secret | Public key distributable freely |
| Use case | API auth, session tokens, webhook verification | Software signing, certificates, JWTs (RS256) |

---

## 9. When to Use / When NOT to Use

### Hash Functions (SHA-256)

**Use when:**
- Verifying file or message integrity (checksums, content-addressed storage)
- As a building block for HMAC or digital signatures
- Generating deterministic identifiers from content (Git object IDs, content-addressable cache keys)
- Deduplication — same content produces same hash
- Within key derivation functions (PBKDF2, HKDF)

**Do NOT use when:**
- Storing passwords — use bcrypt, scrypt, or Argon2 instead; SHA-256 is too fast
- As a MAC — SHA-256(key || message) is vulnerable to length extension; use HMAC-SHA256
- For randomness — a hash is deterministic, not a random number generator

### Symmetric Encryption (AES-256-GCM)

**Use when:**
- Encrypting data at rest (database columns, files, disk volumes)
- Encrypting bulk data in transit (after key exchange)
- Both parties share a secret key or have established one via ECDH

**Do NOT use when:**
- You need non-repudiation — use asymmetric signatures
- The key has not been established securely — solve key distribution first
- Using ECB mode or CBC without authentication — always use GCM or another AEAD mode

### HMAC

**Use when:**
- Authenticating API requests with a shared secret
- Verifying webhook payloads
- Generating and verifying session tokens
- Any scenario where both parties share a key and need integrity + authenticity

**Do NOT use when:**
- You need non-repudiation — HMAC cannot prove who signed (either party with the key could have)
- The shared key is not truly secret
- Using it directly as a password hash — it is fast and therefore brute-forceable

### Asymmetric (RSA / ECDSA)

**Use when:**
- Key distribution is a problem (parties cannot pre-share a secret)
- Non-repudiation is required (software signing, legal documents)
- Building a PKI (certificate authorities, mTLS)

**Do NOT use when:**
- Encrypting large amounts of data — use hybrid encryption (RSA to wrap an AES key)
- RSA-1024 — it is broken; use RSA-2048 minimum, RSA-4096 for long-term
- Raw RSA without proper padding (OAEP for encryption, PSS for signing)

### PBKDF2 / bcrypt / Argon2

**Always use one of these when:**
- Storing user passwords
- Deriving encryption keys from user-chosen passwords

**Never use:**
- Plain SHA-256/SHA-512 for password storage, regardless of salting
- MD5 or SHA-1 in any new security context

---

## 10. Common Pitfalls

### Pitfall 1: Plain SHA-256 for Password Storage

```python
# BROKEN: Fast hash makes brute-force trivial
import hashlib

def store_password_broken(password: str) -> str:
    # SHA-256 alone: GPU runs 1 billion hashes/sec
    # No salt: rainbow table attack works across all users at once
    return hashlib.sha256(password.encode()).hexdigest()

# Attacker with a 4x RTX 4090 GPU:
# 10 billion SHA-256/sec
# "password123" cracked in milliseconds from a dictionary
# All users with the same password get the same hash — crack once, break many
```

```python
# FIX: Use PBKDF2-HMAC-SHA256 with 600,000 iterations + unique random salt
import hashlib
import secrets
import hmac
import base64

ITERATIONS = 600_000
SALT_BYTES  = 32

def store_password_correct(password: str) -> str:
    """Returns a single string containing algorithm, iterations, salt, and hash."""
    salt = secrets.token_bytes(SALT_BYTES)   # unique per user
    dk = hashlib.pbkdf2_hmac(
        hash_name="sha256",
        password=password.encode("utf-8"),
        salt=salt,
        iterations=ITERATIONS,
    )
    # Encode all components into one storable string
    salt_b64 = base64.b64encode(salt).decode()
    hash_b64  = base64.b64encode(dk).decode()
    return f"pbkdf2_sha256${ITERATIONS}${salt_b64}${hash_b64}"

def verify_password_correct(password: str, stored: str) -> bool:
    parts = stored.split("$")
    algorithm, iterations, salt_b64, hash_b64 = parts
    salt     = base64.b64decode(salt_b64)
    expected = base64.b64decode(hash_b64)
    iterations = int(iterations)
    dk = hashlib.pbkdf2_hmac(
        hash_name="sha256",
        password=password.encode("utf-8"),
        salt=salt,
        iterations=iterations,
    )
    return hmac.compare_digest(dk, expected)
    # GPU runs ~1,000 PBKDF2-SHA256-600k hashes/sec — 1 million times slower
```

### Pitfall 2: Non-Constant-Time Comparison for Secrets

```python
# BROKEN: Python's == operator short-circuits — returns False on first mismatch
# An attacker querying your API millions of times can statistically measure
# how long comparisons take and deduce the correct token byte by byte.

import os

VALID_API_KEY = "supersecrettoken123"

def check_api_key_broken(provided_key: str) -> bool:
    return provided_key == VALID_API_KEY   # TIMING VULNERABLE
    # When provided_key = "X...", comparison exits in ~50 ns
    # When provided_key = "su...", comparison exits in ~100 ns (more bytes match)
    # Statistical difference is measurable from network even with noise
```

```python
# FIX: Always use hmac.compare_digest for comparing secrets and tokens
import hmac

VALID_API_KEY = b"supersecrettoken123"

def check_api_key_correct(provided_key: str) -> bool:
    provided_bytes = provided_key.encode("utf-8")
    # compare_digest examines ALL bytes regardless of where mismatch occurs
    # Takes the same time whether 0 bytes match or all but 1 match
    return hmac.compare_digest(provided_bytes, VALID_API_KEY)
```

### Pitfall 3: Reusing AES-GCM Nonce

```python
# BROKEN: Reusing the same nonce with the same AES-GCM key is catastrophic.
# Given two ciphertexts encrypted with the same key and nonce:
# C1 = P1 XOR keystream, C2 = P2 XOR keystream
# C1 XOR C2 = P1 XOR P2  => attacker can recover plaintext relationships
# In 2012, TLS BEAST attack exploited IV reuse in CBC; GCM nonce reuse is worse.

import os

key = os.urandom(32)
FIXED_NONCE = bytes(12)   # DO NOT DO THIS in real code

# Both messages encrypted with the same keystream — trivially breakable
```

```python
# FIX: Generate a fresh random nonce for every encryption operation.
# Store nonce alongside ciphertext (it is not secret).
import os
import struct

def encrypt_message(key: bytes, plaintext: bytes) -> bytes:
    """Returns nonce + ciphertext (12 bytes nonce prefix)."""
    # Generate a unique nonce for this operation
    nonce = os.urandom(12)   # 96-bit random nonce
    # With secrets.token_bytes(12) or os.urandom(12), nonce collision
    # probability after 2^32 messages is ~1 in 2^32 — negligible.
    # For high-volume systems (>1 billion messages/key), rotate the key.
    
    # NOTE: AES-GCM requires the `cryptography` library for actual encryption;
    # this shows the nonce management pattern using stdlib's os.urandom.
    # The nonce is prepended to ciphertext so the recipient can extract it.
    
    # Pseudocode for actual AES-GCM (requires `cryptography` package):
    # from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    # ciphertext_and_tag = AESGCM(key).encrypt(nonce, plaintext, None)
    # return nonce + ciphertext_and_tag
    
    return nonce  # placeholder for illustration

def decrypt_message(key: bytes, nonce_and_ciphertext: bytes) -> bytes:
    nonce      = nonce_and_ciphertext[:12]
    ciphertext = nonce_and_ciphertext[12:]
    # AESGCM(key).decrypt(nonce, ciphertext, None) — raises exception if tag invalid
    pass
```

### Pitfall 4: Length Extension Attacks on H(key || message)

```python
# BROKEN: Naive MAC using SHA-256(key + message) is vulnerable to length extension.
# SHA-256 internal state after processing message m is exactly H(key || m).
# An attacker who knows H(key || m) can compute H(key || m || padding || extra)
# without knowing the key, forging valid MACs for extended messages.

import hashlib

SECRET_KEY = b"mysecretkey"

def naive_mac_broken(message: bytes) -> str:
    return hashlib.sha256(SECRET_KEY + message).hexdigest()
    # An attacker seeing this MAC can extend the message and forge a valid MAC.
```

```python
# FIX: Use HMAC-SHA256, which wraps the hash in a two-pass construction
# that prevents length extension by design.

import hmac
import hashlib
import secrets

SECRET_KEY = secrets.token_bytes(32)

def correct_mac(message: bytes) -> bytes:
    return hmac.new(SECRET_KEY, message, hashlib.sha256).digest()
    # The double-hash structure of HMAC makes length extension impossible.
```

---

## 11. Technologies & Tools

| Tool / Library | Language | Purpose | Notes |
|---------------|----------|---------|-------|
| Python `hashlib` | Python | SHA-*, BLAKE2, PBKDF2-HMAC | Standard library; no install needed |
| Python `hmac` | Python | HMAC-SHA256, constant-time compare | Standard library |
| Python `secrets` | Python | Cryptographically secure random bytes/tokens | Standard library; use instead of `random` |
| `cryptography` (PyPI) | Python | AES-GCM, RSA, ECDSA, X25519 | De-facto Python crypto library; wraps OpenSSL |
| `bcrypt` (PyPI) | Python | bcrypt password hashing | Industry standard; uses C extension |
| `argon2-cffi` (PyPI) | Python | Argon2id password hashing | Current OWASP recommendation |
| OpenSSL | C / CLI | TLS, AES, RSA, ECC, X.509 | Foundation for most TLS implementations |
| Bouncy Castle | Java | Full crypto suite | Common in Java/Android |
| libsodium / NaCl | C | Modern crypto primitives (Curve25519, XSalsa20, Poly1305) | High-level, hard-to-misuse API |
| AWS KMS | Cloud | Key management, envelope encryption | Managed HSM; never see raw keys |
| HashiCorp Vault | Infrastructure | Secrets management, key rotation, PKI | See `../../devops/secrets_management/` |
| Let's Encrypt | Infrastructure | Free TLS certificates (ACME protocol) | Automated certificate lifecycle |
| GnuPG (GPG) | CLI | File encryption, signing, key management | Common for package signing |
| Java `javax.crypto` | Java | AES, RSA, HMAC, KeyDerivation | Standard Java crypto API |

---

## 12. Interview Questions with Answers

**Q: Why should you never use SHA-256 directly to store passwords?**
SHA-256 is designed to be fast — modern GPUs compute over 1 billion SHA-256 hashes per second. An attacker who steals your password database can attempt billions of guesses per second. Password hashing requires intentional slowness (bcrypt at cost 12 takes ~200 ms, limiting an attacker to ~1,000 guesses/second on a GPU) plus a unique salt per user (defeating rainbow table precomputation). Use bcrypt, scrypt, or Argon2 — never plain hash functions — for passwords.

**Q: What is a timing attack and why does it affect MAC verification?**
A timing attack exploits the fact that Python's `==` operator short-circuits — it returns False as soon as it finds the first mismatched byte. An attacker who can make millions of API calls can statistically measure response times to determine how many bytes of their guessed token match the real token, byte by byte. `hmac.compare_digest` is specifically designed to compare all bytes in constant time regardless of where (or whether) a mismatch occurs, eliminating this information leak.

**Q: Why is HMAC preferred over H(key || message) for MACs?**
SHA-2 functions are susceptible to length extension attacks: given H(m), an attacker can compute H(m || padding || extra) without knowing m. If the MAC is H(key || message), the attacker can compute a valid MAC for (message || padding || extra) without knowing the key. HMAC's nested construction — H(K XOR opad || H(K XOR ipad || message)) — prevents length extension because the outer hash wraps the inner hash output rather than the message. SHA-3 (Keccak sponge) is inherently immune to length extension, making H(key || message) with SHA-3 technically safe, but HMAC with SHA-2 is the universally deployed standard.

**Q: What is the difference between encryption and hashing?**
Hashing is one-way and deterministic: given H(m), you cannot recover m (only verify against it). Encryption is two-way: given ciphertext and key, you can recover the original plaintext. Hashing is used for integrity verification and password storage; encryption is used for confidentiality (protecting data you need to read later). A common mistake is "encrypting" passwords — if you can decrypt them, so can an attacker who compromises your key.

**Q: What is a nonce and why must AES-GCM nonces be unique?**
A nonce (number used once) is the initialization vector for AES-GCM. AES-GCM generates a keystream: a sequence of pseudorandom bytes derived from key + nonce, then XORs this keystream with the plaintext. If two different plaintexts are encrypted with the same key and same nonce, both ciphertexts are XORed with the same keystream. XORing the two ciphertexts cancels the keystream (C1 XOR C2 = P1 XOR P2), giving the attacker direct XOR of the plaintexts — sufficient to reconstruct both. Additionally, the authentication tag mechanism is completely broken. Always generate a random 12-byte nonce per message with `os.urandom(12)`.

**Q: What is the Diffie-Hellman key exchange and what problem does it solve?**
DH solves the key distribution problem: how to establish a shared symmetric secret between two parties communicating over an untrusted channel, without ever transmitting that secret. Both parties exchange public values (g^a mod p and g^b mod p), each apply their private value to the other's public value, and arrive at the same shared secret g^ab mod p. An eavesdropper who sees both public values cannot compute g^ab without solving the discrete logarithm problem. ECDH does the same over elliptic curves with smaller keys (256-bit vs 2048-bit) and faster computation (~0.1 ms vs ~10 ms).

**Q: What is the difference between HMAC and digital signatures?**
HMAC uses a shared secret key — both parties can generate and verify the MAC, so you cannot prove to a third party which party generated it (no non-repudiation). Digital signatures use a public/private key pair — only the private key holder can generate the signature, but anyone with the public key can verify it. If Alice signs a message with her private key, she cannot later deny signing it (non-repudiation). HMAC is faster and simpler; digital signatures are needed when you need to prove authorship to parties who don't share your secret.

**Q: What is a salt, and how does it prevent rainbow table attacks?**
A salt is a random value (typically 16–32 bytes) generated uniquely per user and prepended (or combined) with the password before hashing. Without salting, an attacker can precompute a rainbow table: a large precomputed database mapping hashes back to passwords (billions of entries, gigabytes in size). Any user whose password is in the table is cracked instantly. With salting, the attacker must compute H(unique_salt + password) separately for each user — precomputation becomes impossible because each salt makes the hash space unique. Modern algorithms (bcrypt, Argon2) embed the salt automatically in the output string.

**Q: What does AES-GCM's authentication tag protect against?**
The 16-byte authentication tag is an AEAD (Authenticated Encryption with Associated Data) component — it is essentially an HMAC over the ciphertext and any additional associated data (e.g., headers, metadata). If the ciphertext is tampered with in transit (bit flipping, truncation, reordering), the tag verification fails and decryption raises an exception before any plaintext is returned. Without authentication (e.g., AES-CBC without a separate MAC), bit-flipping attacks on CBC allow an attacker to make predictable modifications to the decrypted plaintext. Always use an AEAD mode; never use AES-ECB or AES-CBC without a MAC.

**Q: Why is RSA used to exchange keys but not to encrypt bulk data?**
RSA encryption / decryption is computationally expensive — roughly 10 ms per operation for RSA-2048 decryption, compared to ~1 GB/s for AES-256-GCM. RSA also has a maximum message size (limited to key size minus padding overhead — about 190 bytes for RSA-2048 with OAEP). The standard pattern is hybrid encryption: use RSA to encrypt a randomly generated 32-byte AES key, then use AES-GCM to encrypt the actual data. This gives the key distribution advantages of asymmetric crypto with the speed of symmetric crypto.

**Q: What is forward secrecy and how does ECDH provide it?**
Forward secrecy (perfect forward secrecy) means that compromise of a long-term private key does not compromise past session recordings. TLS 1.3 achieves this by using ephemeral ECDH: each TLS session generates a new temporary key pair, derives a session key, then discards the private key. Even if an attacker records all traffic and later steals the server's certificate private key, they cannot decrypt past sessions because each session used a unique ephemeral ECDH private key that no longer exists. TLS 1.2 with RSA key exchange (sending the AES key encrypted with the server's long-term RSA key) does not have forward secrecy.

**Q: What is the birthday paradox and how does it apply to hash collision resistance?**
The birthday paradox states that in a room of 23 people, there is >50% probability two share a birthday. Analogously, for a hash function with n-bit output, you need approximately 2^(n/2) random inputs to find a collision with 50% probability — not 2^n as intuition suggests. SHA-256 has 256-bit output, so collision resistance requires finding 2^128 hashes — computationally infeasible. MD5 (128-bit output) requires only 2^64 hashes to find a collision — feasible with modern hardware, and actual collision-generating tools exist.

**Q: What is the difference between entropy and randomness in cryptography?**
Cryptographic security requires high-entropy randomness — unpredictability backed by physical randomness sources. Python's `random` module is a pseudo-random number generator (Mersenne Twister) — its state can be predicted from 624 outputs, making it completely inappropriate for security. `secrets.token_bytes(n)` uses the OS CSPRNG (on Linux: `getrandom()` or `/dev/urandom`; on macOS: `arc4random`) which gathers entropy from hardware events (interrupt timing, disk activity, thermal noise). Always use `secrets` for generating keys, salts, tokens, and any security-critical random values.

**Q: Explain how bcrypt's cost factor provides tunable slowness.**
bcrypt is the Blowfish cipher initialization function (expensive_key_setup) applied to a password. The cost factor (typically 10–12) determines the number of iterations: the function performs 2^cost rounds of the expensive key setup. At cost 10: ~100 ms on a modern server; cost 12: ~300–400 ms. As hardware gets faster, you increase the cost factor to maintain the desired verification time. GPUs are relatively ineffective against bcrypt because bcrypt requires large amounts of sequential memory access that GPUs cannot parallelize efficiently, unlike SHA-256.

**Q: What is a key derivation function (KDF) and when would you use PBKDF2 vs HKDF?**
A KDF derives one or more cryptographic keys from a source of keying material. PBKDF2 is a password-based KDF: it adds computational hardness (via iteration count) to derive a key from a low-entropy, human-chosen password — its purpose is to make brute-force expensive. HKDF (HMAC-based KDF) is for high-entropy inputs: it derives multiple cryptographically independent keys from an already-secret, high-entropy input like an ECDH shared secret. HKDF adds no computational overhead (it is fast), relying instead on the input's existing entropy. Use PBKDF2 (or bcrypt/Argon2) for passwords; use HKDF for deriving session keys from a key exchange output.

**Q: What is authenticated encryption with associated data (AEAD) and what goes in the "associated data"?**
AEAD encrypts plaintext (for confidentiality) and also authenticates associated data that is not encrypted (for integrity). Associated data is typically metadata that must be sent in plaintext but must not be tampered with — for example, a packet header, a recipient identifier, or a version number. An attacker cannot modify the associated data without breaking the authentication tag, even though the associated data is not encrypted. In AES-GCM, passing associated data as the `aad` parameter ensures that both the ciphertext and the metadata are covered by the authentication tag.

---

## 13. Best Practices

### Key Management
- Never hardcode keys in source code — use environment variables, AWS KMS, HashiCorp Vault, or GCP Secret Manager (see `../../devops/secrets_management/`)
- Rotate encryption keys regularly; maintain key versioning so old data can be decrypted and re-encrypted
- Use envelope encryption for data at rest: a data encryption key (DEK) encrypts the data; a key encryption key (KEK) encrypts the DEK; KEK lives in a hardware security module (HSM) or KMS
- Generate keys with `secrets.token_bytes(32)` — never with Python's `random` module

### Hashing
- Use SHA-256 or SHA-3-256 for integrity verification, HMAC, digital signatures
- Never use MD5 or SHA-1 in new security code
- For file checksums where collision resistance is not security-critical (content-addressable storage), SHA-256 or BLAKE3 are appropriate
- Always hash passwords with bcrypt (cost 12), scrypt, or Argon2id — never with plain SHA-256

### Encryption
- Default to AES-256-GCM for symmetric encryption; it is authenticated by default
- Generate a fresh random 96-bit (12-byte) nonce per encryption operation; store alongside ciphertext
- Use hybrid encryption for asymmetric scenarios: RSA or ECDH to wrap an AES key, AES-GCM for data
- Prefer X25519/Ed25519 over RSA for new systems; they are faster, have smaller keys, and are harder to mis-implement

### HMAC and Token Verification
- Always use `hmac.compare_digest` for comparing MACs, tokens, and any secret values
- Use HMAC-SHA256 instead of plain SHA-256 for MACs — prevents length extension attacks
- HMAC key should be at least 32 bytes (256-bit), generated with `secrets.token_bytes(32)`
- Include context in HMAC inputs to prevent cross-protocol attacks: `HMAC(key, "reset_token:" + user_id + ":" + timestamp)`

### Password Storage
- Hash with bcrypt (cost 12), scrypt, or Argon2id — each includes automatic salting
- If restricted to stdlib: use PBKDF2-HMAC-SHA256 with 600,000+ iterations (NIST 2023) and a 32-byte random salt
- Set bcrypt cost factor so hashing takes 100–300 ms on your hardware; re-evaluate every 18 months
- Never store passwords in retrievable form — not encrypted, not plain SHA-256

### Randomness
- `secrets.token_bytes(n)` — cryptographically secure random bytes
- `secrets.token_hex(n)` — hex string of n random bytes
- `secrets.token_urlsafe(n)` — URL-safe base64 of n random bytes
- `os.urandom(n)` — lower-level equivalent; same security as `secrets`
- Never use `random.random()`, `random.randint()`, or `uuid.uuid4()` (which uses `os.urandom` internally but is not guaranteed) for security purposes

### General
- Keep cryptographic code isolated in a single module; make it easy to audit and swap implementations
- Prefer well-audited libraries (`cryptography`, `bcrypt`, `argon2-cffi`) over custom implementations
- Fail closed: if tag verification fails, raise an exception — never attempt to use unauthenticated data
- Log authentication failures with sufficient context to detect brute-force attempts; never log plaintext passwords or keys

---

## 14. Case Study

### Password Storage System: The Wrong Way, the Better Way, and the Right Way

**Scenario**: You are a backend engineer at a startup. The system stores 500,000 user accounts. You are auditing the password storage after a security review identifies it as a critical risk. Here are three generations of the implementation:

---

#### Generation 1: BROKEN — Plain Text Storage

```python
# GENERATION 1: BROKEN — Never store plaintext passwords
import sqlite3

class UserStorageGen1:
    def __init__(self, db_path=":memory:"):
        self.conn = sqlite3.connect(db_path)
        self.conn.execute(
            "CREATE TABLE users (id INTEGER PRIMARY KEY, username TEXT, password TEXT)"
        )

    def create_user(self, username: str, password: str) -> int:
        # BROKEN: Stores password in plain text
        # When DB is dumped (SQL injection, backup exposure, insider threat),
        # attacker immediately has all 500,000 passwords.
        # Password reuse means attacker now owns accounts at other services too.
        cur = self.conn.execute(
            "INSERT INTO users (username, password) VALUES (?, ?)",
            (username, password)
        )
        return cur.lastrowid

    def authenticate_gen1(self, username: str, password: str) -> bool:
        row = self.conn.execute(
            "SELECT password FROM users WHERE username = ?", (username,)
        ).fetchone()
        if not row:
            return False
        return row[0] == password   # Direct comparison: fast but catastrophic on breach
```

#### Generation 2: BROKEN — Unsalted SHA-256

```python
import hashlib

class UserStorageGen2:
    def create_user(self, username: str, password: str) -> str:
        # BROKEN 1: No salt — all users with "password123" store the same hash.
        #   Attacker cracks once, breaks all.
        # BROKEN 2: SHA-256 is ~1 billion hashes/sec on GPU.
        #   Attacker brute-forces top-10000 passwords in ~10 microseconds each.
        # BROKEN 3: Rainbow tables precomputed for top-billion passwords exist online.
        return hashlib.sha256(password.encode()).hexdigest()

    def authenticate_gen2(self, stored_hash: str, password: str) -> bool:
        candidate = hashlib.sha256(password.encode()).hexdigest()
        return stored_hash == candidate  # Also timing-vulnerable
```

#### Generation 3: FIX — PBKDF2-HMAC-SHA256 with Random Salt

```python
import hashlib
import hmac
import secrets
import base64
import time
import sqlite3
from typing import Optional

# NIST SP 800-132 (2023): minimum 600,000 iterations for PBKDF2-HMAC-SHA256
PBKDF2_ITERATIONS = 600_000
SALT_LENGTH       = 32      # 256-bit salt
DK_LENGTH         = 32      # 256-bit derived key

class PasswordHasher:
    """
    Handles password hashing using PBKDF2-HMAC-SHA256.
    This is the stdlib equivalent of bcrypt when bcrypt is unavailable.
    In production, prefer bcrypt (cost 12) or Argon2id over PBKDF2.
    """

    @staticmethod
    def hash(password: str) -> str:
        """Hash a password. Returns a single storable string."""
        if not password:
            raise ValueError("Password must not be empty")
        
        salt = secrets.token_bytes(SALT_LENGTH)
        dk = hashlib.pbkdf2_hmac(
            hash_name="sha256",
            password=password.encode("utf-8"),
            salt=salt,
            iterations=PBKDF2_ITERATIONS,
            dklen=DK_LENGTH,
        )
        # Format: algorithm$iterations$salt_b64$hash_b64
        return "$".join([
            "pbkdf2_sha256",
            str(PBKDF2_ITERATIONS),
            base64.b64encode(salt).decode("ascii"),
            base64.b64encode(dk).decode("ascii"),
        ])

    @staticmethod
    def verify(password: str, stored: str) -> bool:
        """Verify a password against a stored hash. Always constant-time."""
        try:
            algorithm, iterations_str, salt_b64, hash_b64 = stored.split("$")
        except ValueError:
            return False   # Malformed record — fail closed

        if algorithm != "pbkdf2_sha256":
            return False

        salt     = base64.b64decode(salt_b64)
        expected = base64.b64decode(hash_b64)
        iterations = int(iterations_str)

        dk = hashlib.pbkdf2_hmac(
            hash_name="sha256",
            password=password.encode("utf-8"),
            salt=salt,
            iterations=iterations,
            dklen=len(expected),
        )
        # CRITICAL: constant-time comparison prevents timing oracle attacks
        return hmac.compare_digest(dk, expected)

    @staticmethod
    def needs_rehash(stored: str, current_iterations: int = PBKDF2_ITERATIONS) -> bool:
        """Check if a stored hash should be upgraded (e.g., iteration count increased)."""
        try:
            _, iterations_str, _, _ = stored.split("$")
            return int(iterations_str) < current_iterations
        except ValueError:
            return True


class SecureUserStore:
    def __init__(self, db_path: str = ":memory:"):
        self.conn = sqlite3.connect(db_path)
        self.conn.execute("""
            CREATE TABLE users (
                id           INTEGER PRIMARY KEY AUTOINCREMENT,
                username     TEXT UNIQUE NOT NULL,
                password_hash TEXT NOT NULL,
                created_at   REAL NOT NULL
            )
        """)
        self.hasher = PasswordHasher()

    def create_user(self, username: str, password: str) -> int:
        """Create a new user with a securely hashed password."""
        password_hash = self.hasher.hash(password)
        try:
            cur = self.conn.execute(
                "INSERT INTO users (username, password_hash, created_at) VALUES (?, ?, ?)",
                (username, password_hash, time.time())
            )
            return cur.lastrowid
        except sqlite3.IntegrityError:
            raise ValueError(f"Username '{username}' already exists")

    def authenticate(self, username: str, password: str) -> Optional[int]:
        """
        Authenticate a user. Returns user ID on success, None on failure.
        Timing: always takes ~600ms regardless of whether user exists,
        to prevent username enumeration via timing.
        """
        row = self.conn.execute(
            "SELECT id, password_hash FROM users WHERE username = ?", (username,)
        ).fetchone()

        if row is None:
            # User doesn't exist — still run a hash to normalize timing
            # (prevent username enumeration by timing difference)
            dummy_hash = self.hasher.hash("dummy_password_for_timing_normalization")
            self.hasher.verify("dummy", dummy_hash)
            return None

        user_id, stored_hash = row
        if not self.hasher.verify(password, stored_hash):
            return None

        # Upgrade hash if iteration count has increased since this user last logged in
        if self.hasher.needs_rehash(stored_hash):
            new_hash = self.hasher.hash(password)
            self.conn.execute(
                "UPDATE users SET password_hash = ? WHERE id = ?",
                (new_hash, user_id)
            )
            self.conn.commit()

        return user_id


def benchmark_password_hashing():
    """Demonstrate that intentional slowness is the key defense."""
    hasher = PasswordHasher()
    password = "mysecretpassword"

    # Measure PBKDF2 hash time
    start = time.perf_counter()
    stored = hasher.hash(password)
    hash_time_ms = (time.perf_counter() - start) * 1000

    # Measure verification time
    start = time.perf_counter()
    valid = hasher.verify(password, stored)
    verify_time_ms = (time.perf_counter() - start) * 1000

    # Measure plain SHA-256 for comparison
    start = time.perf_counter()
    for _ in range(1000):
        hashlib.sha256(password.encode()).hexdigest()
    sha256_time_us = (time.perf_counter() - start) * 1000 / 1000  # us per hash

    print("Password hashing benchmarks")
    print("=" * 50)
    print(f"PBKDF2-HMAC-SHA256 ({PBKDF2_ITERATIONS:,} iterations):")
    print(f"  Hash time:    {hash_time_ms:.1f} ms")
    print(f"  Verify time:  {verify_time_ms:.1f} ms")
    print(f"  Valid:        {valid}")
    print()
    print(f"Plain SHA-256:")
    print(f"  Hash time:    {sha256_time_us:.3f} ms (per hash)")
    print()
    if hash_time_ms > 0:
        speedup = hash_time_ms / sha256_time_us
        print(f"PBKDF2 is {speedup:.0f}x slower than SHA-256 per hash")
        sha256_per_sec = 1000 / sha256_time_us
        pbkdf2_per_sec = 1000 / hash_time_ms
        print(f"SHA-256 throughput:   {sha256_per_sec:,.0f} hashes/sec")
        print(f"PBKDF2 throughput:    {pbkdf2_per_sec:,.1f} hashes/sec")
        print()
        print("Attacker with 1 GPU cracking a leaked database:")
        gpu_sha256_per_sec = 1_000_000_000    # ~1 billion SHA-256/sec (RTX 4090)
        gpu_pbkdf2_per_sec = 1_000            # ~1,000 PBKDF2-600k/sec (RTX 4090)
        wordlist_size = 10_000_000_000        # 10 billion common passwords
        sha256_crack_sec = wordlist_size / gpu_sha256_per_sec
        pbkdf2_crack_sec = wordlist_size / gpu_pbkdf2_per_sec
        print(f"  To try 10 billion guesses against one account:")
        print(f"    SHA-256:  {sha256_crack_sec:.1f} seconds")
        print(f"    PBKDF2:   {pbkdf2_crack_sec/3600/24/365:.1f} years")


def run_demo():
    """Full demo: create users, authenticate, verify security properties."""
    store = SecureUserStore()

    # Create users
    store.create_user("alice", "SecurePass123!")
    store.create_user("bob",   "SecurePass123!")  # Same password, different hash

    # Retrieve and compare hashes (show salt uniqueness)
    alice_hash = store.conn.execute(
        "SELECT password_hash FROM users WHERE username = 'alice'"
    ).fetchone()[0]
    bob_hash = store.conn.execute(
        "SELECT password_hash FROM users WHERE username = 'bob'"
    ).fetchone()[0]

    print("Security property: same password → different stored hash")
    print(f"  Alice: {alice_hash[:40]}...")
    print(f"  Bob:   {bob_hash[:40]}...")
    print(f"  Identical: {alice_hash == bob_hash}")  # Must be False

    print()

    # Authentication tests
    uid = store.authenticate("alice", "SecurePass123!")
    print(f"Correct password → user ID: {uid}")      # Some integer

    uid = store.authenticate("alice", "wrongpassword")
    print(f"Wrong password → user ID: {uid}")         # None

    uid = store.authenticate("nonexistent", "anything")
    print(f"Unknown user → user ID: {uid}")           # None

    print()
    benchmark_password_hashing()


if __name__ == "__main__":
    run_demo()
```

#### Performance and Security Metrics

| Metric | Plain Text | SHA-256 | PBKDF2 (600k iter) | bcrypt cost 12 |
|--------|-----------|---------|-------------------|----------------|
| Server hash time | ~0 ms | ~0.001 ms | ~150–300 ms | ~200–400 ms |
| GPU guesses/sec (RTX 4090) | N/A | ~10 billion | ~1,000 | ~1,000 |
| Rainbow table attack | Trivially defeated by salt? | No — attackers have tables | Yes — salt prevents precomputation | Yes — embedded salt |
| Database breach impact | All passwords immediate | Cracked in hours | Years to crack per account | Years to crack per account |
| Automatic salt | No | No | No (caller responsible) | Yes |
| Recommended iterations/cost | — | — | 600,000+ (NIST 2023) | 12 (OWASP 2024) |

#### Discussion Questions

1. PBKDF2's iteration count is a tunable parameter. How would you decide when to increase it, and how would you handle the migration of existing hashed passwords in the database without requiring all users to reset their passwords?

2. The `authenticate` method runs a dummy hash when the username does not exist to normalize timing. Is this sufficient to prevent username enumeration? What other side channels could leak whether a username exists, and how would you address each?

3. If your password database were leaked today, how long would you have before a well-resourced attacker (nation-state level) could crack a significant portion of accounts hashed with PBKDF2-HMAC-SHA256 at 600,000 iterations, and what immediate actions should you take? Consider both online (live service) and offline (exfiltrated database copy) attack scenarios.

---

## See Also

- [networking_fundamentals](../networking_fundamentals/) — TLS 1.3 handshake builds directly on these crypto primitives: X25519 (ECDH), HKDF, AES-256-GCM, Ed25519/RSA certificates
- [../../backend/backend_security_owasp](../../backend/backend_security_owasp/) — OWASP A02 Cryptographic Failures: BCrypt cost calibration, applied crypto in web applications, common vulnerability patterns
- [../../backend/auth_and_authorization_systems](../../backend/auth_and_authorization_systems/) — JWT internals (HMAC-SHA256 for HS256, RSA-SHA256 for RS256), mTLS certificate verification, OAuth 2.0 token security
- [../../devops/secrets_management](../../devops/secrets_management/) — HashiCorp Vault, AWS KMS, key rotation strategies, HSMs, envelope encryption in production
