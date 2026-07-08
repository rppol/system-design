# Number Systems & Bit Manipulation

---

## 1. Concept Overview

Computers store all data as bits — ones and zeros. Understanding how numbers are represented in binary, how arithmetic works at the hardware level, and how to manipulate bits directly is a prerequisite for writing correct low-level code and solving a class of interview problems that no higher-level abstraction can simplify.

Number systems are the languages of hardware. Binary (base-2) is what gates compute natively. Hexadecimal (base-16) is how engineers write binary compactly. Octal (base-8) appears in Unix file permissions. Two's complement is how all modern CPUs represent signed integers. IEEE-754 is how they represent floating-point numbers. Endianness is how multi-byte values are laid out in memory.

Bit manipulation is a toolkit of constant-time operations (AND, OR, XOR, NOT, shifts) that can replace loops in many algorithms, detect properties (sign, parity, power-of-two), and pack/unpack data efficiently. It also underlies hash functions, compression codecs, cryptographic primitives, and network protocol parsing.

---

## 2. Intuition

> **One-line analogy**: Binary is the same place-value system as decimal, just with only two digits — each column is 2× the one to the right instead of 10×.

**Mental model**: In decimal, `345 = 3 × 10² + 4 × 10¹ + 5 × 10⁰`. In binary, `0b1011 = 1 × 2³ + 0 × 2² + 1 × 2¹ + 1 × 2⁰ = 8 + 0 + 2 + 1 = 11`. Every n-bit integer represents exactly one number in the range [0, 2ⁿ-1] for unsigned, or [-2^(n-1), 2^(n-1)-1] for two's complement signed.

**Why it matters**: Bit manipulation problems appear in interviews because they test whether you understand the machine model, not just high-level abstractions. Practical engineering contexts include: flag masks in systems code, packet header parsing, cryptographic hash functions, hash-map internals (bit-masking to stay in range), SIMD vectorisation, and space-efficient data structures.

**Key insight**: XOR is the Swiss Army knife of bit manipulation. `a ^ a = 0`, `a ^ 0 = a`, and XOR is commutative and associative — so XORing all elements in a list cancels duplicates and leaves the single unique element. Many seemingly difficult bit problems reduce to a well-chosen XOR.

---

## 3. Core Principles

- **Base conversion**: any number N in base B is expressed as a sum of powers of B weighted by its digits.
- **Two's complement**: the dominant signed-integer representation. For an n-bit integer: positive numbers use bits 0..n-2 with bit n-1 = 0; negative -k is represented as 2ⁿ - k (equivalently: flip all bits of |k|, then add 1). This makes hardware addition circuits work identically for signed and unsigned numbers.
- **Overflow**: fixed-width integers wrap around silently in most languages (C, Java int). Python integers have arbitrary precision and never overflow.
- **Bitwise operators**: AND (`&`), OR (`|`), XOR (`^`), NOT (`~`), left shift (`<<`), right shift (`>>`). These operate on each bit independently (except shifts).
- **Arithmetic vs logical right shift**: arithmetic `>>` fills with the sign bit (preserves sign); logical `>>>` fills with zero. Python `>>` is always arithmetic for signed ints. Java has both `>>` (arithmetic) and `>>>` (logical unsigned).
- **IEEE-754**: 32-bit float = 1 sign bit + 8 exponent bits + 23 mantissa bits. 64-bit double = 1 + 11 + 52. Not all decimals have an exact binary float representation — `0.1 + 0.2 != 0.3` in floating-point arithmetic.
- **Endianness**: big-endian stores the most significant byte at the lowest address; little-endian stores the least significant byte. x86/x86-64 is little-endian. Network protocols (TCP/IP) use big-endian ("network byte order").

---

## 4. Types / Strategies

### 4.1 Number System Conversion

```
Decimal → Binary: repeatedly divide by 2, collect remainders in reverse.
  45 ÷ 2 = 22 r 1
  22 ÷ 2 = 11 r 0
  11 ÷ 2 =  5 r 1
   5 ÷ 2 =  2 r 1
   2 ÷ 2 =  1 r 0
   1 ÷ 2 =  0 r 1
  Read remainders bottom-up: 101101 = 45 ✓

Decimal → Hex: divide by 16, use A-F for 10-15.
  45 ÷ 16 = 2 r 13 (D)  →  0x2D = 45 ✓

Binary → Hex: group bits in fours from the right.
  10110111 = 1011 0111 = B7 = 0xB7 = 183
```

### 4.2 Two's Complement Mechanics

```
8-bit examples:
  +5  = 0000 0101
  -5  = flip+1 = 1111 1010 + 1 = 1111 1011
  -1  = 1111 1111
  -128 = 1000 0000  (minimum for int8)
  +127 = 0111 1111  (maximum for int8)

Key property: adding two's complement values uses the same circuit as unsigned addition.
  (+5) + (-5) = 0000 0101 + 1111 1011 = 1 0000 0000
                                         ^-- overflow bit discarded; result = 0 ✓
```

The worked bytes above are one instance of the general rule from Section 3 — flip every bit of the magnitude, then add 1:

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    k(["positive magnitude k"]) --> flipOp["Flip every bit<br/>(bitwise NOT)"]
    flipOp --> addOp["Add 1"]
    addOp --> negk(["result = -k"])

    class k,negk io
    class flipOp,addOp mathOp
```

Two transform steps, zero new hardware: this is why a CPU needs no separate subtract circuit — negate via this pipeline, then reuse the existing unsigned adder.

### 4.3 Bitwise Operation Truth Table

```
a | b | a AND b | a OR b | a XOR b | NOT a
0 | 0 |    0    |   0    |    0    |   1
0 | 1 |    0    |   1    |    1    |   1
1 | 0 |    0    |   1    |    1    |   0
1 | 1 |    1    |   1    |    0    |   0
```

### 4.4 Common Bit Tricks Catalogue

| Goal | Expression | Notes |
|------|-----------|-------|
| Check if n is even | `n & 1 == 0` | Last bit 0 → even |
| Check if n is power of 2 | `n > 0 and (n & (n-1)) == 0` | Powers of 2 have exactly one set bit |
| Get kth bit (0-indexed) | `(n >> k) & 1` | Shift k right, check last bit |
| Set kth bit | `n \| (1 << k)` | OR with a mask with only bit k set |
| Clear kth bit | `n & ~(1 << k)` | AND with complement of mask |
| Toggle kth bit | `n ^ (1 << k)` | XOR flips the bit |
| Clear lowest set bit | `n & (n - 1)` | Turns off rightmost 1; useful in Kernighan's bit-count |
| Get lowest set bit | `n & (-n)` | Isolates the rightmost 1 (used in Fenwick tree) |
| Count set bits (naive) | loop with `n & 1` + `n >>= 1` | O(number of bits) |
| Count set bits (fast) | `bin(n).count('1')` in Python; `Integer.bitCount(n)` in Java | Maps to `POPCNT` CPU instruction |
| Swap a and b | `a ^= b; b ^= a; a ^= b` | XOR swap — no temp variable needed |
| Multiply by 2^k | `n << k` | Left shift |
| Integer divide by 2^k | `n >> k` | Arithmetic right shift (signed) |

### 4.5 IEEE-754 Float Representation

```
32-bit float (single precision):
  Bit 31:    sign (0=positive, 1=negative)
  Bits 30-23: exponent (8 bits, biased by 127)
  Bits 22-0:  mantissa/fraction (23 bits, implicit leading 1)

  Value = (-1)^sign × 1.mantissa × 2^(exponent - 127)

  0.5 = 0 01111110 00000000000000000000000
        sign=0, exp=126-127=-1, mantissa=1.0 → 1.0 × 2^(-1) = 0.5 ✓

  Why 0.1 cannot be represented exactly:
  0.1 in binary = 0.0001100110011... (repeating) — requires infinite bits.
  Stored as rounded approximation. 0.1 + 0.2 = 0.30000000000000004 in float64.
```

---

## 5. Architecture Diagrams

### Memory Layout: Endianness

```
Value: 0x12345678 (decimal 305419896) stored at address 0x100

Big-endian (network order, Motorola, SPARC):
  Address:  0x100  0x101  0x102  0x103
  Byte:      0x12   0x34   0x56   0x78
  Most significant byte at lowest address.

Little-endian (x86, x86-64, ARM in default mode):
  Address:  0x100  0x101  0x102  0x103
  Byte:      0x78   0x56   0x34   0x12
  Least significant byte at lowest address.
```

### Two's Complement Number Line (4-bit)

```
Unsigned:  0    1    2    3    4    5    6    7    8    9   10   11   12   13   14   15
Binary:  0000 0001 0010 0011 0100 0101 0110 0111 1000 1001 1010 1011 1100 1101 1110 1111
Signed:    0    1    2    3    4    5    6    7   -8   -7   -6   -5   -4   -3   -2   -1
```

### Bit Counting via Kernighan's Method

```
n = 0b10110  (5 bits set: 2)
Step 1: n & (n-1) = 0b10110 & 0b10101 = 0b10100  (cleared rightmost set bit)
Step 2: n & (n-1) = 0b10100 & 0b10011 = 0b10000
Step 3: n & (n-1) = 0b10000 & 0b01111 = 0b00000
Count = 3 iterations = 3 set bits   → O(number of set bits), not O(total bits)
```

The trace above is one run of this general loop — it terminates in exactly k iterations, where k is the number of set bits, not the bit width:

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    start(["n, count = 0"]) --> check{"n equals 0?"}
    check -->|"no"| clear["n = n AND (n-1)<br/>clears lowest set bit"]
    clear --> inc["count += 1"]
    inc --> check
    check -->|"yes"| done(["return count"])

    class start,done io
    class check,clear mathOp
    class inc train
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 Bit Counting Algorithms

```python
from __future__ import annotations

def count_bits_naive(n: int) -> int:
    """O(log n) — checks every bit position."""
    count = 0
    while n:
        count += n & 1
        n >>= 1
    return count

def count_bits_kernighan(n: int) -> int:
    """O(k) where k = number of set bits — faster for sparse bitsets."""
    count = 0
    while n:
        n &= n - 1   # clears the lowest set bit
        count += 1
    return count

def count_bits_builtin(n: int) -> int:
    """O(1) — maps to single POPCNT instruction on modern CPUs."""
    return bin(n).count('1')   # Python
    # Java: Integer.bitCount(n)
```

### 6.2 Find the Single Non-Duplicate Element (XOR)

```python
def single_number(nums: list[int]) -> int:
    """
    Every element except one appears exactly twice.
    XOR all elements: pairs cancel (a ^ a = 0), unique element remains.
    Time: O(n). Space: O(1).
    """
    result = 0
    for n in nums:
        result ^= n
    return result
# [4, 1, 2, 1, 2] -> 4 ^ 1 ^ 2 ^ 1 ^ 2 = 4 ^ (1^1) ^ (2^2) = 4 ^ 0 ^ 0 = 4
```

### 6.3 Check and Manipulate Flags

```python
from enum import IntFlag

class Permission(IntFlag):
    READ    = 0b001   # 1
    WRITE   = 0b010   # 2
    EXECUTE = 0b100   # 4

def has_permission(user_perms: int, perm: Permission) -> bool:
    return bool(user_perms & perm)

def grant(user_perms: int, perm: Permission) -> int:
    return user_perms | perm

def revoke(user_perms: int, perm: Permission) -> int:
    return user_perms & ~perm

user = Permission.READ | Permission.WRITE  # 0b011 = 3
print(has_permission(user, Permission.EXECUTE))  # False
user = grant(user, Permission.EXECUTE)          # 0b111 = 7
user = revoke(user, Permission.WRITE)            # 0b101 = 5
```

### 6.4 Reverse Bits of a 32-bit Integer

```python
def reverse_bits(n: int) -> int:
    """Reverse the bits of a 32-bit unsigned integer."""
    result = 0
    for _ in range(32):
        result = (result << 1) | (n & 1)  # shift result left, OR the last bit of n
        n >>= 1
    return result
# n = 0b00000010100101000001111010011100  (43261596)
# reversed = 0b00111001011110000010100101000000  (964176192)
```

### 6.5 Detecting Float Equality Pitfall

```python
# BROKEN: comparing floats with ==
def is_half(x: float) -> bool:
    return x == 0.5   # OK for 0.5 (representable exactly), BROKEN for 0.1+0.4

# BROKEN example:
val = 0.1 + 0.4
print(val == 0.5)   # False: 0.1+0.4 = 0.5000000000000001 in float64

# FIX: use an epsilon comparison for computed floats
import math
def is_approx_equal(a: float, b: float, rel_tol: float = 1e-9) -> bool:
    return math.isclose(a, b, rel_tol=rel_tol)

print(is_approx_equal(0.1 + 0.4, 0.5))  # True
```

---

## 7. Real-World Examples

**TCP/IP packet headers** — IPv4 header fields are packed into fixed bit widths (version: 4 bits, IHL: 4 bits, DSCP: 6 bits, ECN: 2 bits). Parsing a packet requires bitwise masking: `(header >> 28) & 0xF` extracts the version field. The kernel processes millions of packets per second using these O(1) bit ops.

**Unix file permissions** — `rwxr-xr--` is stored as 0o754 = 0b111 101 100. `chmod 644` is a bitwise operation: 0b110 100 100. The `os.stat()` mode field in Python uses bitwise AND with constants like `stat.S_IRUSR` (0o400) to check individual permissions.

**Bloom filters** — set membership data structure uses k hash functions, each hashing an element to a bit position. Membership check: test if all k bits are set. False positives possible, false negatives impossible. Every check is k bitwise OR operations on a bit array — O(k) time, O(m/8) bytes for m bits.

**Java HashMap bucket index** — `HashMap.put(key, value)` computes `(n-1) & hash(key)` to choose a bucket, where n is a power of 2. This bitwise AND replaces modulo (`%`) for power-of-two sizes, which is why HashMap capacity is always a power of 2: `1 << 4 = 16, 1 << 5 = 32`, etc. The AND is 5–10× faster than integer modulo on modern CPUs.

**Cryptographic hash functions** — SHA-256 consists exclusively of bitwise ops (AND, OR, XOR, NOT) and bit rotations applied over 64 rounds. The bitwise nature makes SHA-256 extremely fast (hardware can compute ~500 MB/s per core) and provides the avalanche effect (one bit change in input cascades to ~50% of output bits changing).

---

## 8. Tradeoffs

| Approach | Time | Space | Readability | Use case |
|----------|------|-------|-------------|----------|
| Bitwise flag masks (int) | O(1) per flag | O(1) | Low | Systems code, hot paths |
| Python `IntFlag` enum | O(1) per flag | O(1) | High | Application code, type safety |
| Set of strings | O(1) avg | O(n) | Very high | Non-performance-critical |
| Bitwise XOR for duplicate detection | O(n) | O(1) | Tricky | Competitive / interview |
| Hash set for duplicate detection | O(n) | O(n) | Clear | Production preference |

### Endianness

| Scenario | Recommended | Reason |
|----------|-------------|--------|
| File format (cross-platform) | Big-endian or explicit spec | Portability |
| Network protocol | Big-endian (RFC 791) | "network byte order" |
| In-process memory (x86) | Little-endian | Hardware native; faster |
| GPU / CUDA | Match CPU endianness | Use `htonl`/`ntohl` at boundary |

---

## 9. When to Use / When NOT to Use

**Use bit manipulation when:**
- Setting / testing / clearing boolean flags in a compact bitmask (system call flags, permissions, feature toggles).
- Implementing low-level data structures (Bloom filter, Fenwick/BIT tree uses `n & (-n)` to isolate last set bit, `n += n & (-n)` to advance).
- Writing performance-critical hot paths where the bitwise operation maps to a single CPU instruction.
- Competitive programming / interview problems where constant-factor performance matters or the problem is specifically about bit properties (power of 2, parity, XOR unique element).

**Do NOT use bit manipulation when:**
- Code readability is the priority — a boolean field or enum is always clearer.
- The "trick" saves a constant factor in an algorithm where the bottleneck is I/O or memory allocation, not CPU.
- Floating-point — bit manipulation of float bits (reinterpreting as int) is legal in C via union or `memcpy`, but has undefined behaviour traps and is almost never necessary in application code.

---

## 10. Common Pitfalls

### Pitfall 1: Off-by-one in bit indexing (0-indexed vs 1-indexed)

```python
# BROKEN: treating bit positions as 1-indexed
n = 0b1010
bit_pos = 2  # intending to check the 2nd bit (value 2)
# BROKEN:
print((n >> bit_pos) & 1)   # 0 -- wait, expected 1?
# The bit at position 2 (0-indexed from right) has value 2^2 = 4.
# 0b1010 = 8+2: set bits at positions 1 and 3. Position 2 is 0.
# FIX: be explicit about 0-indexed bit position = log2(value).
# bit at position 1 (value 2^1 = 2) is set: (0b1010 >> 1) & 1 == 1 ✓
```

### Pitfall 2: Integer overflow in bit shifting (Java)

```python
# BROKEN (Java): shifting by >= 32 on an int is undefined behaviour in some langs
// int x = 1 << 32;  // Java: undefined! Shift amount is taken mod 32.
// 1 << 32 in Java == 1 << 0 == 1  (NOT 0 as expected)

// FIX: use long (64-bit) when shifting by >= 32
long x = 1L << 32;  // 4294967296 ✓
// Python: no overflow (arbitrary precision), but be aware of semantics.
```

### Pitfall 3: NOT (~) on Python integers

```python
# GOTCHA: Python integers are signed arbitrary-precision.
# ~n == -(n+1), not what you might expect from a 32-bit mental model.
n = 5        # 0b101
print(~n)    # -6  (not 0b11111010 as you'd get in a 32-bit model)
# FIX: mask to the desired bit width
mask_32 = 0xFFFFFFFF
print((~n) & mask_32)  # 4294967290 = 0xFFFFFFFA ✓
```

### Pitfall 4: Comparing floats with `==`

```python
# BROKEN: exact float comparison
x = 0.1 + 0.2
if x == 0.3:  # False — float representation error
    print("equal")

# FIX: use math.isclose
import math
if math.isclose(x, 0.3, rel_tol=1e-9):
    print("equal")  # True
```

---

## 11. Technologies & Tools

| Tool / Concept | Purpose | Notes |
|---------------|---------|-------|
| `bin(n)`, `hex(n)`, `oct(n)` | Python base conversion | Built-in; `bin(n).count('1')` = popcount |
| `int('1010', 2)` | Binary string to int | `int(s, base)` for any base |
| `Integer.bitCount(n)` | Java popcount | Maps to POPCNT instruction |
| `Integer.toBinaryString(n)` | Java binary representation | Unsigned 32-bit |
| `struct` module (Python) | Pack/unpack bytes with endianness | `struct.pack('>I', val)` = big-endian uint32 |
| `ctypes` (Python) | Bit manipulation of memory buffers | Accessing raw C-style memory layout |
| `socket.htonl` / `ntohl` | Host-to-network byte order conversion | Used when writing network protocols |
| Bitset (Java) / `array` of ints | Compact bit arrays | Rolling your own Bloom filter or bitmask DP |

---

## 12. Interview Questions with Answers

**Q1: What is two's complement, and why do modern CPUs use it?**
Two's complement represents -k as 2ⁿ - k (equivalently: flip all bits of |k| and add 1). It is used because the same adder circuit works for both signed and unsigned addition — no special-casing. One's complement and sign-magnitude representations require different adder logic for positive and negative operands and have a confusing "-0" representation.

**Q2: How do you check if a number is a power of 2 in O(1)?**
`n > 0 and (n & (n-1)) == 0`. Powers of 2 have exactly one set bit (e.g., 8 = 0b1000). `n-1` clears that bit and sets all lower bits (7 = 0b0111). ANDing gives 0 if and only if n is a power of 2. The `n > 0` guard handles the n = 0 edge case (0 & -1 = 0 would be a false positive).

**Q3: Given an array where every element appears exactly twice except one, find the unique element in O(n) time and O(1) space.**
XOR all elements. Each pair cancels (`a ^ a = 0`), leaving only the unique element. XOR is commutative and associative so order doesn't matter. Code: `result = 0; for x in arr: result ^= x; return result`.

**Q4: How would you count the number of set bits in an integer?**
Three approaches: (a) loop and check last bit (`n & 1`), shift right — O(log n); (b) Kernighan's: `while n: n &= n-1; count += 1` — O(k) where k is the number of set bits; (c) built-in `bin(n).count('1')` in Python or `Integer.bitCount(n)` in Java — O(1) using a POPCNT CPU instruction. Kernighan's is faster than (a) when set bits are sparse.

**Q5: Explain endianness and when it matters.**
Endianness is the byte order for multi-byte values in memory. Little-endian (x86/x86-64) stores the least-significant byte at the lowest address. Big-endian stores the most-significant byte first. It matters when: serialising data to disk or network (use explicit byte-order convention, e.g., network byte order = big-endian, `htonl`/`ntohl`), writing binary file parsers (JPEG/PNG/WAV headers have specific endianness), or casting an integer pointer to a byte pointer.

**Q6: What is `x & (x-1)`? What is `x & (-x)`?**
`x & (x-1)` clears the lowest set bit of x. Used in Kernighan's bit-count and to check for powers of 2. `x & (-x)` (equivalently `x & (~x + 1)`) isolates (returns) the lowest set bit. Used in the Fenwick/Binary Indexed Tree for the "responsible range" computation: `i += i & (-i)` advances to the next update position.

**Q7: How does Python handle integer overflow compared to Java/C?**
Python integers are arbitrary-precision (backed by a C long array that grows as needed) — they never overflow. Java `int` is 32-bit two's complement and wraps silently on overflow (e.g., `Integer.MAX_VALUE + 1 == Integer.MIN_VALUE`). C integer overflow is *undefined behaviour* for signed types. This matters in interview problems: a Python solution `a + b` never overflows; the same Java solution might.

**Q8: What is 0.1 + 0.2 in floating-point arithmetic?**
0.30000000000000004, not 0.3. Neither 0.1 nor 0.2 has an exact binary float representation — they are rounded to the nearest representable value. Addition compounds the rounding error. Fix: use `math.isclose` for comparison, the `Decimal` module for exact decimal arithmetic, or integer arithmetic scaled by a power of 10 (store amounts in cents, not dollars).

**Q9: How do you set, clear, and toggle the kth bit of an integer?**
Set: `n | (1 << k)`. Clear: `n & ~(1 << k)`. Toggle: `n ^ (1 << k)`. Check: `(n >> k) & 1`. These are O(1) operations. In Python, `~(1 << k)` produces a negative number (arbitrary-precision NOT), so for a 32-bit context use `n & ~(1 << k) & 0xFFFFFFFF` or use `IntFlag`.

**Q10: What is a XOR swap and what are its limitations?**
`a ^= b; b ^= a; a ^= b` swaps a and b without a temporary variable. Works because XOR is its own inverse. Limitation: if `a` and `b` point to the same memory location, all three operations produce 0 (XORing a value with itself). Always use a temp variable in production code — it is clearer and the compiler optimises it identically. XOR swap is only useful on systems with no temporary registers (rare).

**Q11: How do you reverse the bits of a 32-bit integer?**
Shift result left and OR the last bit of n, then shift n right, repeating 32 times. O(32) = O(1). Can also be done with a lookup table (precompute 8-bit reversal for each byte) for higher throughput. Used in bit-reversal permutations in the FFT algorithm.

**Q12: What is the difference between arithmetic and logical right shift?**
Arithmetic right shift (`>>` in Java/Python for signed ints) fills the vacated high bit with the sign bit — preserving the sign for negative numbers. Logical right shift (`>>>` in Java) fills with zero regardless of sign. For positive numbers they are identical. In Python, `>>` is always arithmetic. Use `>>> 0` in JavaScript or `int32 >>> 0` to get unsigned semantics.

**Q13: How are permissions encoded in Linux file modes, and how do you test them?**
A Unix file mode is a 12-bit number: 3 bits for setuid/setgid/sticky, then 3×3 bits for owner/group/other (read/write/execute). `0o755` = owner can do anything, group and other can read+execute. Test: `mode & stat.S_IRUSR` (0o400) is non-zero if owner has read permission. This is why `chmod 644` (= 0o644 = 0b110 100 100) is the standard for web files: owner read+write, group and other read-only.

**Q14: You need to find two missing numbers from 1..n. How do you use XOR or math?**
With XOR alone you cannot distinguish two missing numbers (the XOR of the pair is ambiguous). Use two properties: (a) sum of 1..n = n(n+1)/2; subtract the array sum → sum of the two missing numbers. (b) product or XOR of the pair can partition them. The standard O(n) O(1)-space approach: compute XOR of all elements and all 1..n — call it `xor_all`. Find any set bit in `xor_all`. Use that bit to split elements into two groups; XOR each group with the corresponding half of 1..n → recovers each missing number.

**Q15: What are IEEE-754 special values and when do they appear?**
`+Inf` / `-Inf`: result of dividing by zero or overflow. `NaN` (Not a Number): result of `0.0/0.0`, `sqrt(-1)`, or `Inf - Inf`. `-0.0`: distinct from `0.0` in IEEE-754 but compares equal (`0.0 == -0.0` is True). `float('inf') > any_finite` is always True. `math.isnan(float('nan'))` required to check NaN — `x != x` is also True only for NaN but is obscure. These appear in scientific computing and ML (loss = NaN is a common gradient explosion symptom).

---

## 13. Best Practices

1. **Use named constants or IntFlag for bitmasks** — `PERMISSION_READ = 0x1` is far clearer than `0x1` scattered in code.
2. **Never compare floats with `==`** — use `math.isclose` with appropriate tolerance, or use integer arithmetic (scaled by 100 for currency).
3. **Be explicit about bit width** — Python integers are arbitrary-precision; Java/C integers have fixed width with silent overflow. State assumptions.
4. **Prefer readability over cleverness for XOR tricks** — the `result ^= x` pattern for finding a unique element is acceptable; but writing `x ^= y; y ^= x; x ^= y` instead of `x, y = y, x` will confuse every future reader.
5. **Handle endianness at I/O boundaries** — inside a process, use native endianness; convert to/from network byte order only when sending over the wire or writing to disk.
6. **Test powers-of-2 edge cases**: 0, 1, the max int value, and the sign bit.
7. **Use `bin()` / `hex()` to visualise when debugging** — seeing `0b10110100` is much more informative than `180` when chasing a bit-manipulation bug.

---

## 14. Case Study: Compact Bitset for Membership Testing

**Scenario**: You need to track which user IDs (0–999,999) have been seen in a stream. A hash set costs ~50 MB (50 bytes per entry × 1 million entries). A bitset costs 125 KB (1 bit per ID, 1,000,000 / 8 = 125,000 bytes). For a cache that must fit in L3 (typically 4–32 MB), this is a 400× reduction.

```python
from __future__ import annotations

class Bitset:
    """Fixed-size bitset backed by a list of Python ints (each 64 bits on CPython)."""

    def __init__(self, size: int) -> None:
        self._bits: list[int] = [0] * ((size + 63) // 64)  # ceil(size/64) words
        self._size = size

    def set(self, pos: int) -> None:
        word, bit = divmod(pos, 64)
        self._bits[word] |= (1 << bit)

    def get(self, pos: int) -> bool:
        word, bit = divmod(pos, 64)
        return bool((self._bits[word] >> bit) & 1)

    def clear(self, pos: int) -> None:
        word, bit = divmod(pos, 64)
        self._bits[word] &= ~(1 << bit)

    def count(self) -> int:
        return sum(bin(w).count('1') for w in self._bits)

# BROKEN: using a plain Python set — 50 MB for 1M integers
seen: set[int] = set()
for uid in stream:
    seen.add(uid)  # Each int object in CPython: 28 bytes + overhead

# FIX: bitset — 125 KB for 1M integers
seen = Bitset(1_000_000)
for uid in stream:
    seen.set(uid)
# Memory: 1_000_000 / 8 = 125_000 bytes = 122 KB ✓
```

**Comparison**:

| Approach | Space | Lookup | Insert | Notes |
|----------|-------|--------|--------|-------|
| Python `set` | ~50 MB for 1M items | O(1) avg | O(1) avg | Best for sparse IDs, non-int keys |
| Bitset (custom) | 125 KB for 1M IDs | O(1) | O(1) | IDs must be integers in [0, n) |
| `bitarray` library | 125 KB | O(1) | O(1) | C-backed, fastest pure bitset |
| Bloom filter | ~1 MB (8 bits/item typical) | O(k) | O(k) | False positives; no delete |

**Discussion Q&As**:

**Why does `n & (-n)` isolate the lowest set bit?**
`-n` is the two's complement of n, which flips all bits and adds 1. Flipping all bits of n makes every bit below the lowest set bit become 1, and the lowest set bit becomes 0. Adding 1 carries through all the 1s up to the position of the lowest set bit, which flips back to 1 — with all bits below it becoming 0. ANDing with the original n leaves only this bit. Example: n = 0b10110, -n = 0b01010 (two's complement) — wait, let me recalculate: n=0b10110=22, -n=-22=0b...101010 in arbitrary precision. Actually for a clean example: n=0b01100=12, -n=0b...10100=-12 (flip: 0b10011, +1: 0b10100). n & -n = 0b01100 & 0b10100 = 0b00100 = 4 = rightmost set bit. ✓

---

## See Also

- [arrays_strings_and_hashing](../arrays_strings_and_hashing/README.md) — hash table uses bit-masking for bucket index (power-of-2 capacity)
- [graphs_tries_and_advanced_structures](../graphs_tries_and_advanced_structures/README.md) — Fenwick tree uses `n & (-n)` and `n += n & (-n)`
- [`python/strings_bytes_encoding_and_regex`](../../python/strings_bytes_encoding_and_regex/README.md) — UTF-8, bytes/bytearray, codec internals
- [`java/strings_and_text`](../../java/strings_and_text/README.md) — compact strings (Java 9+), surrogate pairs, Unicode code points
- [DSA Pattern Playbooks](../dsa_patterns/README.md) — apply this technique: [Bit Manipulation](../dsa_patterns/bit_manipulation.md) (XOR tricks, bitmask enumeration, single-number family)
