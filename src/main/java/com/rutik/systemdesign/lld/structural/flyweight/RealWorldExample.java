package com.rutik.systemdesign.lld.structural.flyweight;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FLYWEIGHT PATTERN — Real-World Example: Text Editor Character Rendering
 *
 * Scenario:
 *   A text editor is rendering a 100,000-character document. Each character
 *   must be rendered at a specific (x, y) position with a specific color.
 *   Characters share formatting: the word "Hello" in Times New Roman 12pt
 *   uses the same font and size for all 5 characters — only their positions differ.
 *
 * Problem WITHOUT Flyweight:
 *   If we store a full object per rendered character — including font name,
 *   size, and style bitmaps — we allocate one large object per character.
 *   For a 100,000-character document with typical font data (~500 bytes per glyph):
 *     100,000 objects × ~500 bytes ≈ ~50 MB for just the glyph data.
 *   Scrolling and re-rendering require processing 100,000 full objects.
 *
 * Solution WITH Flyweight:
 *   Separate state into:
 *     INTRINSIC  — the character, font name, font size, bold/italic flags.
 *                  This is shared, identical for all 'A' glyphs in the same font.
 *                  Stored in the Flyweight (CharacterGlyph).
 *     EXTRINSIC  — x position, y position, foreground color.
 *                  This varies per occurrence. Stored in the context (CharacterContext),
 *                  NOT in the Flyweight.
 *
 *   Result: instead of 100,000 full objects, we have:
 *     - ~96 CharacterGlyph flyweights (for ~96 printable ASCII chars per font/size combo)
 *     - 100,000 lightweight CharacterContext objects (just x, y, color + glyph reference)
 *
 * Memory savings calculation (shown in main() at the end).
 *
 * Run: javac RealWorldExample.java && java TextEditorDemo
 */

// ─────────────────────────────────────────────────────────────────────────────
// FLYWEIGHT: CharacterGlyph
//
// Stores INTRINSIC (shared, immutable) state:
//   - character: the char value ('A', 'b', '1', etc.)
//   - fontName:  the typeface ("Times New Roman", "Arial", ...)
//   - fontSize:  the point size (10, 12, 14, ...)
//   - bold:      bold rendering flag
//   - italic:    italic rendering flag
//
// This object is shared across ALL occurrences of the same (char, font, size)
// combination in the document. It must be IMMUTABLE — no setters, all fields final.
//
// The render() method accepts extrinsic state (x, y, color) as parameters.
// Extrinsic state is NEVER stored as a field here.
// ─────────────────────────────────────────────────────────────────────────────

final class CharacterGlyph {

    // Intrinsic state — shared, immutable, defines the flyweight's identity
    private final char   character;
    private final String fontName;
    private final int    fontSize;
    private final boolean bold;
    private final boolean italic;

    /**
     * Package-private constructor — all instantiation must go through GlyphFactory.
     * This prevents accidental creation of un-shared glyph objects.
     */
    CharacterGlyph(char character, String fontName, int fontSize,
                   boolean bold, boolean italic) {
        this.character = character;
        this.fontName  = fontName;
        this.fontSize  = fontSize;
        this.bold      = bold;
        this.italic    = italic;
        // In a real renderer, this constructor would load the glyph bitmap / vector
        // from the font file — an expensive operation done ONCE per unique glyph.
        System.out.printf("  [GlyphFactory] Created glyph: '%c' %s %dpt%s%s%n",
                character, fontName, fontSize,
                bold   ? " Bold"   : "",
                italic ? " Italic" : "");
    }

    /**
     * Renders this glyph at the given position with the given color.
     *
     * @param x     Extrinsic: the column position on screen (varies per character)
     * @param y     Extrinsic: the row position on screen (varies per character)
     * @param color Extrinsic: the foreground rendering color (varies per character)
     *
     * The intrinsic state (character, font, size) comes from THIS object's fields.
     * The extrinsic state (x, y, color) is passed in — never stored in the flyweight.
     */
    public void render(int x, int y, String color) {
        // In a real system, this would invoke the GPU to blit the glyph bitmap
        // at pixel coordinates (x*charWidth + offsetX, y*lineHeight + offsetY)
        // using the pre-loaded font metrics stored in this object.
        System.out.printf("    Render '%c' [%s %dpt%s%s] at (%3d,%3d) color=%s%n",
                character, fontName, fontSize,
                bold   ? " Bold"   : "",
                italic ? " Italic" : "",
                x, y, color);
    }

    // Getters for the intrinsic state — used by factory for cache key construction
    public char    getCharacter() { return character; }
    public String  getFontName()  { return fontName; }
    public int     getFontSize()  { return fontSize; }
    public boolean isBold()       { return bold; }
    public boolean isItalic()     { return italic; }

    /**
     * Estimated memory footprint of ONE glyph object (intrinsic state only).
     * In a real renderer, this would include the glyph bitmap/vector data.
     * We simulate 400 bytes for font metrics + glyph path data.
     */
    public static int intrinsicSizeBytes() {
        return 400;  // simulated: char(2) + String refs + fontSize(4) + booleans(2) + font bitmap data
    }

    @Override
    public String toString() {
        return String.format("CharacterGlyph('%c', %s, %dpt%s%s)",
                character, fontName, fontSize,
                bold   ? ", Bold"   : "",
                italic ? ", Italic" : "");
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FLYWEIGHT FACTORY: GlyphFactory
//
// The central cache for CharacterGlyph objects.
// Guarantees that only one CharacterGlyph instance exists per unique
// (character, fontName, fontSize, bold, italic) combination.
//
// Thread-safe: uses ConcurrentHashMap with computeIfAbsent.
// ─────────────────────────────────────────────────────────────────────────────

class GlyphFactory {

    // Cache: composite key → shared CharacterGlyph instance
    // Key format: "char|fontName|fontSize|bold|italic"
    // e.g., "A|Arial|12|false|false"
    private final Map<String, CharacterGlyph> cache = new ConcurrentHashMap<>();

    /**
     * Returns the shared CharacterGlyph for the given (character, font, size, style).
     * Creates and caches a new one only on the first request for this combination.
     *
     * Thread-safe: computeIfAbsent is atomic in ConcurrentHashMap.
     */
    public CharacterGlyph getGlyph(char character, String fontName,
                                    int fontSize, boolean bold, boolean italic) {
        // Build the composite key that uniquely identifies this glyph
        String key = buildKey(character, fontName, fontSize, bold, italic);

        // computeIfAbsent: atomically creates if absent, returns existing otherwise
        return cache.computeIfAbsent(key,
                k -> new CharacterGlyph(character, fontName, fontSize, bold, italic));
    }

    /** Convenience overload for non-bold, non-italic glyphs. */
    public CharacterGlyph getGlyph(char character, String fontName, int fontSize) {
        return getGlyph(character, fontName, fontSize, false, false);
    }

    /** Returns the number of unique glyph objects currently cached. */
    public int cacheSize() {
        return cache.size();
    }

    /** Prints all cached glyphs for introspection. */
    public void printCache() {
        System.out.println("  GlyphFactory cache (" + cache.size() + " unique glyphs):");
        cache.forEach((key, glyph) ->
                System.out.println("    [" + key + "] → " + glyph));
    }

    private String buildKey(char character, String fontName,
                             int fontSize, boolean bold, boolean italic) {
        return character + "|" + fontName + "|" + fontSize + "|" + bold + "|" + italic;
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CONTEXT: CharacterContext
//
// Stores EXTRINSIC (per-occurrence, context-specific) state:
//   - x, y: position on the canvas (every character is in a different position)
//   - color: foreground color (may vary per character for syntax highlighting)
//   - glyph: a reference to the shared CharacterGlyph flyweight
//
// The document is made up of CharacterContext objects — one per rendered character.
// There can be 100,000 of these, but they are small (just 3 ints + 1 reference).
// The heavy glyph data is shared through the flyweight reference.
// ─────────────────────────────────────────────────────────────────────────────

class CharacterContext {

    // Extrinsic state — unique per rendered character occurrence
    private final int    x;       // column position
    private final int    y;       // row / line number
    private final String color;   // foreground color (hex or named)

    // Reference to the SHARED flyweight — this does NOT own the glyph data
    private final CharacterGlyph glyph;

    public CharacterContext(int x, int y, String color, CharacterGlyph glyph) {
        this.x     = x;
        this.y     = y;
        this.color = color;
        this.glyph = glyph;
    }

    /**
     * Renders this character by passing the extrinsic state to the flyweight's
     * render method. The flyweight combines its intrinsic state with these
     * extrinsic values to produce the final output.
     */
    public void render() {
        glyph.render(x, y, color);
    }

    /**
     * Estimated memory footprint of ONE CharacterContext object.
     * x(4) + y(4) + color reference(8) + glyph reference(8) = ~24 bytes
     * (plus String object overhead for color ~40 bytes → ~64 bytes total)
     */
    public static int extrinsicSizeBytes() {
        return 64; // x(int) + y(int) + color(String ref) + glyph(ref) + String data
    }

    public char getChar()  { return glyph.getCharacter(); }
    public int  getX()     { return x; }
    public int  getY()     { return y; }
    public String getColor() { return color; }
}

// ─────────────────────────────────────────────────────────────────────────────
// TEXT DOCUMENT
//
// The document holds a list of CharacterContext objects (one per character).
// It uses the GlyphFactory to ensure glyph sharing.
// ─────────────────────────────────────────────────────────────────────────────

class TextDocument {

    private final List<CharacterContext> characters = new ArrayList<>();
    private final GlyphFactory glyphFactory;

    public TextDocument(GlyphFactory factory) {
        this.glyphFactory = factory;
    }

    /**
     * Appends a character at the given position, with the given style and color.
     * The glyph for (char, font, size, bold, italic) is retrieved from the factory
     * (shared if it already exists, created once if it doesn't).
     */
    public void addCharacter(char c, int x, int y, String fontName, int fontSize,
                              boolean bold, boolean italic, String color) {
        // Get the shared flyweight — O(1) cache lookup in most cases
        CharacterGlyph glyph = glyphFactory.getGlyph(c, fontName, fontSize, bold, italic);
        // Store the extrinsic state in a lightweight context object
        characters.add(new CharacterContext(x, y, color, glyph));
    }

    /** Renders the entire document by calling render() on each character context. */
    public void render() {
        for (CharacterContext ctx : characters) {
            ctx.render();
        }
    }

    /** Returns the total number of characters in the document. */
    public int characterCount() {
        return characters.size();
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MEMORY STATS HELPER
// ─────────────────────────────────────────────────────────────────────────────

class MemoryStats {

    public static void printComparison(int characterCount, int uniqueGlyphs) {
        System.out.println();
        System.out.println("  ┌────────────────────────────────────────────────────────┐");
        System.out.println("  │              Memory Usage Comparison                   │");
        System.out.println("  ├────────────────────────────────────────────────────────┤");
        System.out.printf ("  │  Characters in document : %,8d                       │%n", characterCount);
        System.out.printf ("  │  Unique glyph objects   : %,8d                       │%n", uniqueGlyphs);
        System.out.println("  ├────────────────────────────────────────────────────────┤");

        // WITHOUT Flyweight: every character has its own full glyph object
        long withoutFlyweight = (long) characterCount
                * (CharacterGlyph.intrinsicSizeBytes() + CharacterContext.extrinsicSizeBytes());
        System.out.printf ("  │  Without Flyweight: %,8d chars × %d bytes = %,8d B  │%n",
                characterCount,
                CharacterGlyph.intrinsicSizeBytes() + CharacterContext.extrinsicSizeBytes(),
                withoutFlyweight);
        System.out.printf ("  │                                          ≈ %6.1f KB  │%n",
                withoutFlyweight / 1024.0);

        // WITH Flyweight: uniqueGlyphs full glyph objects + characterCount lightweight contexts
        long glyphMemory   = (long) uniqueGlyphs    * CharacterGlyph.intrinsicSizeBytes();
        long contextMemory = (long) characterCount  * CharacterContext.extrinsicSizeBytes();
        long withFlyweight = glyphMemory + contextMemory;
        System.out.printf ("  │  With    Flyweight: %,8d glyphs × %d B + %,8d contexts × %d B%n",
                uniqueGlyphs,    CharacterGlyph.intrinsicSizeBytes(),
                characterCount, CharacterContext.extrinsicSizeBytes());
        System.out.printf ("  │                                          = %,8d B  │%n", withFlyweight);
        System.out.printf ("  │                                          ≈ %6.1f KB  │%n",
                withFlyweight / 1024.0);

        System.out.println("  ├────────────────────────────────────────────────────────┤");
        double savingsPercent = 100.0 * (withoutFlyweight - withFlyweight) / withoutFlyweight;
        long   savedBytes     = withoutFlyweight - withFlyweight;
        System.out.printf ("  │  MEMORY SAVED: %,8d bytes (%.1f%% reduction)         │%n",
                savedBytes, savingsPercent);
        System.out.println("  └────────────────────────────────────────────────────────┘");
        System.out.println();
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MAIN DEMO
// ─────────────────────────────────────────────────────────────────────────────

public class RealWorldExample {

    public static void main(String[] args) {
        System.out.println("=======================================================");
        System.out.println("  Flyweight Pattern: Text Editor Character Rendering   ");
        System.out.println("=======================================================\n");

        GlyphFactory factory = new GlyphFactory();
        TextDocument  doc    = new TextDocument(factory);

        // ── Demo 1: Small document — observe glyph creation and reuse ─────────
        System.out.println("--- Demo 1: Rendering a short document ---");
        System.out.println("  Adding characters to document...\n");

        // Line 1: "Hello" in Arial 12pt black
        // Notice: 'l' appears TWICE — the second request hits the cache (no new glyph)
        doc.addCharacter('H', 0,  0, "Arial", 12, false, false, "#000000");
        doc.addCharacter('e', 1,  0, "Arial", 12, false, false, "#000000");
        doc.addCharacter('l', 2,  0, "Arial", 12, false, false, "#000000");
        doc.addCharacter('l', 3,  0, "Arial", 12, false, false, "#000000"); // cache hit for 'l'
        doc.addCharacter('o', 4,  0, "Arial", 12, false, false, "#000000");

        // Line 2: "World" in Arial 12pt black
        // 'o' and 'l' reuse glyphs already in cache
        doc.addCharacter('W', 0,  1, "Arial", 12, false, false, "#000000");
        doc.addCharacter('o', 1,  1, "Arial", 12, false, false, "#000000"); // cache hit for 'o'
        doc.addCharacter('r', 2,  1, "Arial", 12, false, false, "#000000");
        doc.addCharacter('l', 3,  1, "Arial", 12, false, false, "#000000"); // cache hit for 'l'
        doc.addCharacter('d', 4,  1, "Arial", 12, false, false, "#000000");

        // Line 3: "Hello" again but in BOLD — different flyweight (different intrinsic state)
        System.out.println("\n  [Different font style → new glyph objects for Bold variant:]");
        doc.addCharacter('H', 0,  2, "Arial", 12, true,  false, "#000000"); // Bold H — new glyph
        doc.addCharacter('e', 1,  2, "Arial", 12, true,  false, "#000000"); // Bold e — new glyph
        doc.addCharacter('l', 2,  2, "Arial", 12, true,  false, "#000000"); // Bold l — new glyph
        doc.addCharacter('l', 3,  2, "Arial", 12, true,  false, "#000000"); // Bold l — cache hit
        doc.addCharacter('o', 4,  2, "Arial", 12, true,  false, "#000000"); // Bold o — new glyph

        // Line 4: Syntax highlighting — same characters but different COLOR
        // Colors are EXTRINSIC — they don't create new glyphs; only the context differs
        System.out.println("\n  [Syntax highlighting: different colors — NO new glyphs created:]");
        doc.addCharacter('i', 0,  3, "Arial", 12, false, false, "#0000FF"); // blue keyword
        doc.addCharacter('n', 1,  3, "Arial", 12, false, false, "#0000FF"); // blue keyword
        doc.addCharacter('t', 2,  3, "Arial", 12, false, false, "#0000FF"); // blue keyword
        doc.addCharacter(' ', 3,  3, "Arial", 12, false, false, "#000000"); // space
        doc.addCharacter('x', 4,  3, "Arial", 12, false, false, "#CC0000"); // red variable
        // 'i', 'n', 't', 'x' are new glyph objects — but the same 'l' etc from above are reused
        // The COLOR CHANGE is handled purely by the extrinsic state in CharacterContext

        System.out.println("\n--- Rendering the document: ---");
        doc.render();

        System.out.println("\n--- GlyphFactory cache state after Demo 1: ---");
        factory.printCache();
        System.out.printf("  Total characters in doc: %d%n", doc.characterCount());
        System.out.printf("  Unique glyph objects:    %d%n", factory.cacheSize());

        // ── Demo 2: Large document — memory savings demonstration ─────────────
        System.out.println("\n\n--- Demo 2: Large document — memory savings ---");
        System.out.println("  Simulating a 10,000-character source code file...");
        System.out.println("  (rendering output suppressed for this demo)\n");

        GlyphFactory largeFactory = new GlyphFactory();
        int totalChars = 10_000;

        // Simulate a source code file: mix of letters, symbols, digits
        // Typical mix: ~70 unique characters (a-z, A-Z, 0-9, brackets, operators)
        // in 2 fonts (code body + comments) at 1 size → ~140 unique glyphs
        char[] codeChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789{}();,. ".toCharArray();
        String[] fonts   = {"Fira Code", "Fira Code"};
        String[] colors  = {"#D4D4D4", "#6A9955", "#569CD6", "#CE9178", "#4EC9B0"};

        int col = 0, row = 0;
        for (int i = 0; i < totalChars; i++) {
            char   c      = codeChars[i % codeChars.length];
            String font   = fonts[i % fonts.length];
            String color  = colors[i % colors.length];
            boolean bold  = (i % 50 == 0); // occasional bold keyword

            CharacterGlyph glyph = largeFactory.getGlyph(c, font, 14, bold, false);
            // In real code we'd store CharacterContext objects; here we just exercise the factory
            if (col++ > 80) { col = 0; row++; } // simple line wrapping simulation
        }

        System.out.println("  [GlyphFactory] Unique glyphs in cache: " + largeFactory.cacheSize()
                + " (out of " + totalChars + " characters rendered)");

        MemoryStats.printComparison(totalChars, largeFactory.cacheSize());

        // ── Demo 3: 100,000-character document ───────────────────────────────
        System.out.println("--- Demo 3: 100,000-character novel ---");
        System.out.println("  (typical novel: ~500,000 chars; using 100K for this demo)\n");

        GlyphFactory novelFactory = new GlyphFactory();
        int novelChars = 100_000;
        char[] englishChars = "etaoinshrdlcumwfgypbvkjxqzETAOINSHRDLCUMWFGYPBVKJXQZ ,.;:!?\"'".toCharArray();

        for (int i = 0; i < novelChars; i++) {
            char c = englishChars[i % englishChars.length];
            novelFactory.getGlyph(c, "Times New Roman", 12, false, false);
        }

        System.out.println("  Unique glyphs cached: " + novelFactory.cacheSize()
                + " (for " + novelChars + " characters)");
        MemoryStats.printComparison(novelChars, novelFactory.cacheSize());

        // ── Key insight summary ────────────────────────────────────────────────
        System.out.println("=======================================================");
        System.out.println("  Key Insights");
        System.out.println("=======================================================");
        System.out.println("  1. The factory creates each glyph ONCE — subsequent requests");
        System.out.println("     for the same (char, font, size) return the SAME object.");
        System.out.println();
        System.out.println("  2. Extrinsic state (x, y, color) is stored in CharacterContext,");
        System.out.println("     NOT in CharacterGlyph. 100,000 contexts share ~60 glyphs.");
        System.out.println();
        System.out.println("  3. Changing a character's color requires only updating its");
        System.out.println("     CharacterContext — no new glyph object is created.");
        System.out.println();
        System.out.println("  4. Thread safety: ConcurrentHashMap + computeIfAbsent ensures");
        System.out.println("     the factory is safe for concurrent document rendering threads.");
        System.out.println();
        System.out.println("  5. CharacterGlyph is immutable (all fields final, no setters).");
        System.out.println("     Immutability is REQUIRED for safe sharing across contexts.");
        System.out.println("=======================================================");
    }
}

/*
 * PATTERN STRUCTURE RECAP:
 *
 *   Flyweight (CharacterGlyph):
 *     - Stores intrinsic state: char, fontName, fontSize, bold, italic
 *     - Immutable: no setters, all fields final
 *     - render(x, y, color): uses intrinsic + passed extrinsic to produce output
 *
 *   FlyweightFactory (GlyphFactory):
 *     - Maintains cache: key → CharacterGlyph
 *     - getGlyph() returns existing or creates new (computeIfAbsent)
 *     - Thread-safe via ConcurrentHashMap
 *
 *   Context (CharacterContext):
 *     - Stores extrinsic state: x, y, color
 *     - Holds a reference to a shared CharacterGlyph
 *     - render() delegates to glyph.render(x, y, color)
 *     - One instance per character OCCURRENCE in the document
 *
 * KEY RULE: "If the state is the same across many objects, make it intrinsic.
 *            If the state varies per occurrence, make it extrinsic."
 */
