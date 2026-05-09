package com.rutik.systemdesign.lld.structural.facade; /**
 * FACADE PATTERN — Real-World Example: Home Theater System
 *
 * Scenario:
 *   You have a state-of-the-art home theater system with six independent
 *   subsystems: Amplifier, DvdPlayer, Projector, Screen, TheaterLights,
 *   and PopcornPopper. Each subsystem has its own complex API.
 *
 * Problem (WITHOUT Facade):
 *   To watch a movie, a client must remember and invoke a precise sequence
 *   of 15+ calls across 6 different objects — in the right order:
 *
 *     popper.on();
 *     popper.pop();
 *     lights.dim(10);
 *     screen.down();
 *     projector.on();
 *     projector.wideScreenMode();
 *     amp.on();
 *     amp.setDvd(dvd);
 *     amp.setSurroundSound();
 *     amp.setVolume(5);
 *     dvd.on();
 *     dvd.play(movie);
 *
 *   The client is tightly coupled to all 6 subsystem classes. Changing any
 *   subsystem (e.g., swapping projector brand) forces client code changes.
 *   Every new "entry point" (game mode, music mode) requires the client to
 *   re-learn the full interaction sequence.
 *
 * Solution (WITH Facade):
 *   A HomeTheaterFacade hides all subsystem interactions behind two simple
 *   methods: watchMovie(String) and endMovie(). The client only talks to
 *   the Facade — it knows nothing about the 6 subsystems behind it.
 *
 * Key Points:
 *   - The Facade does NOT add new functionality — it simplifies access.
 *   - Subsystems are NOT modified — they remain fully functional on their own.
 *   - The Facade does NOT prevent advanced users from accessing subsystems directly.
 *   - The Facade encapsulates the correct sequence of calls (domain knowledge).
 *
 * Run: javac RealWorldExample.java && java HomeTheaterDemo
 */

// ─────────────────────────────────────────────────────────────────────────────
// SUBSYSTEM CLASSES
// These are the complex, independent components of the home theater.
// Each has its own rich API. They have NO knowledge of the Facade.
// In a real system, each of these would be in its own file.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Amplifier — controls audio output, sound modes, and volume.
 */
class Amplifier {

    private String description;
    private DvdPlayer dvdPlayer;
    private int volume;
    private boolean surroundSoundOn;
    private boolean on;

    public Amplifier(String description) {
        this.description = description;
    }

    public void on() {
        on = true;
        System.out.println("  [Amplifier] " + description + " on");
    }

    public void off() {
        on = false;
        System.out.println("  [Amplifier] " + description + " off");
    }

    public void setStereoSound() {
        surroundSoundOn = false;
        System.out.println("  [Amplifier] Stereo sound on");
    }

    public void setSurroundSound() {
        surroundSoundOn = true;
        System.out.println("  [Amplifier] Surround sound on (5 speakers, 1 subwoofer)");
    }

    public void setVolume(int level) {
        this.volume = level;
        System.out.println("  [Amplifier] Setting volume to " + level);
    }

    public void setDvd(DvdPlayer dvd) {
        this.dvdPlayer = dvd;
        System.out.println("  [Amplifier] Setting DVD player to " + dvd.getDescription());
    }

    public String getDescription() { return description; }
}

/**
 * DvdPlayer — loads and plays DVDs, handles chapters and pause.
 */
class DvdPlayer {

    private String description;
    private Amplifier amplifier;
    private String currentDvd;
    private boolean playing;
    private boolean on;

    public DvdPlayer(String description, Amplifier amplifier) {
        this.description = description;
        this.amplifier   = amplifier;
    }

    public void on() {
        on = true;
        System.out.println("  [DvdPlayer] " + description + " on");
    }

    public void off() {
        on       = false;
        playing  = false;
        System.out.println("  [DvdPlayer] " + description + " off");
    }

    public void play(String movie) {
        this.currentDvd = movie;
        this.playing    = true;
        System.out.println("  [DvdPlayer] Playing \"" + movie + "\"");
    }

    public void stop() {
        playing = false;
        System.out.println("  [DvdPlayer] Stopped \"" + currentDvd + "\"");
    }

    public void pause() {
        System.out.println("  [DvdPlayer] Paused \"" + currentDvd + "\"");
    }

    public void eject() {
        currentDvd = null;
        playing    = false;
        System.out.println("  [DvdPlayer] DVD ejected");
    }

    public void setTwoChannelAudio() {
        System.out.println("  [DvdPlayer] Setting 2-channel audio");
        amplifier.setStereoSound();
    }

    public void setSurroundAudio() {
        System.out.println("  [DvdPlayer] Setting surround audio");
        amplifier.setSurroundSound();
    }

    public String getDescription() { return description; }
    public boolean isPlaying()     { return playing; }
}

/**
 * Projector — controls the display device and its aspect modes.
 */
class Projector {

    private String description;
    private DvdPlayer dvdPlayer;
    private boolean on;

    public Projector(String description, DvdPlayer dvdPlayer) {
        this.description = description;
        this.dvdPlayer   = dvdPlayer;
    }

    public void on() {
        on = true;
        System.out.println("  [Projector] " + description + " on");
    }

    public void off() {
        on = false;
        System.out.println("  [Projector] " + description + " off");
    }

    public void wideScreenMode() {
        System.out.println("  [Projector] Wide screen mode (16x9 aspect ratio)");
    }

    public void tvMode() {
        System.out.println("  [Projector] TV mode (4x3 aspect ratio)");
    }

    public String getDescription() { return description; }
}

/**
 * Screen — a motorized projection screen that rolls up and down.
 */
class Screen {

    private String description;

    public Screen(String description) {
        this.description = description;
    }

    /** Lower the screen from the ceiling. */
    public void down() {
        System.out.println("  [Screen] " + description + " going down");
    }

    /** Retract the screen back to the ceiling. */
    public void up() {
        System.out.println("  [Screen] " + description + " going up");
    }
}

/**
 * TheaterLights — smart dimmable lighting for the theater room.
 */
class TheaterLights {

    private String description;
    private int brightness; // 0 (off) to 100 (full)

    public TheaterLights(String description) {
        this.description = description;
        this.brightness  = 100;
    }

    public void on() {
        brightness = 100;
        System.out.println("  [Lights] " + description + " on (full brightness)");
    }

    public void off() {
        brightness = 0;
        System.out.println("  [Lights] " + description + " off");
    }

    /** Dim to a specific brightness level (0–100). */
    public void dim(int level) {
        this.brightness = Math.max(0, Math.min(100, level));
        System.out.println("  [Lights] " + description + " dimming to " + brightness + "%");
    }

    public int getBrightness() { return brightness; }
}

/**
 * PopcornPopper — an air popper for movie snacks.
 */
class PopcornPopper {

    private String description;
    private boolean on;
    private boolean popping;

    public PopcornPopper(String description) {
        this.description = description;
    }

    public void on() {
        on = true;
        System.out.println("  [Popper] " + description + " on");
    }

    public void off() {
        on      = false;
        popping = false;
        System.out.println("  [Popper] " + description + " off");
    }

    public void pop() {
        popping = true;
        System.out.println("  [Popper] " + description + " popping popcorn!");
    }

    public void stopPopping() {
        popping = false;
        System.out.println("  [Popper] " + description + " stopped popping");
    }

    public boolean isPopping() { return popping; }
}

// ─────────────────────────────────────────────────────────────────────────────
// FACADE INTERFACE
// Defines the simplified API that clients use.
// Having an interface allows mocking the Facade in tests and swapping
// implementations (e.g., SmartHomeFacade for a different hardware brand).
// ─────────────────────────────────────────────────────────────────────────────

interface HomeTheater {
    /**
     * Orchestrates the full startup sequence to watch a movie:
     * popper → lights → screen → projector → amp → dvd → play.
     */
    void watchMovie(String movie);

    /**
     * Orchestrates the full shutdown sequence after the movie ends.
     */
    void endMovie();

    /**
     * Simplified mode for listening to music through the theater speakers.
     */
    void listenToMusic();

    /**
     * Shuts down music mode.
     */
    void endMusic();
}

// ─────────────────────────────────────────────────────────────────────────────
// CONCRETE FACADE
// The facade knows ALL the subsystem internals and coordinates them.
// It encodes the correct sequence of calls so clients don't have to.
// The Facade does NOT implement subsystem logic — it ORCHESTRATES.
// ─────────────────────────────────────────────────────────────────────────────

class HomeTheaterFacade implements HomeTheater {

    // All subsystem references — injected via constructor for testability
    private final Amplifier        amp;
    private final DvdPlayer        dvd;
    private final Projector        projector;
    private final Screen           screen;
    private final TheaterLights    lights;
    private final PopcornPopper    popper;

    /**
     * Constructor injection: all subsystem components are provided externally.
     * This enables the facade itself to be tested by passing mock subsystems.
     */
    public HomeTheaterFacade(Amplifier amp,
                              DvdPlayer dvd,
                              Projector projector,
                              Screen screen,
                              TheaterLights lights,
                              PopcornPopper popper) {
        this.amp      = amp;
        this.dvd      = dvd;
        this.projector = projector;
        this.screen   = screen;
        this.lights   = lights;
        this.popper   = popper;
    }

    /**
     * Encapsulates the 12-step startup ritual for watching a movie.
     *
     * Before Facade: client had to remember and call all 12 steps in order.
     * After Facade: client calls one method. The Facade owns the sequence.
     */
    @Override
    public void watchMovie(String movie) {
        System.out.println("  [Facade] Preparing to watch \"" + movie + "\"...");
        popper.on();                    // Step 1: Start popcorn popper
        popper.pop();                   // Step 2: Begin popping
        lights.dim(10);                 // Step 3: Dim lights to 10% for theater feel
        screen.down();                  // Step 4: Lower projection screen
        projector.on();                 // Step 5: Turn on projector
        projector.wideScreenMode();     // Step 6: Set to widescreen aspect
        amp.on();                       // Step 7: Power on amplifier
        amp.setDvd(dvd);               // Step 8: Route audio through DVD player
        amp.setSurroundSound();         // Step 9: Enable surround sound
        amp.setVolume(5);               // Step 10: Set comfortable volume
        dvd.on();                       // Step 11: Power on DVD player
        dvd.play(movie);               // Step 12: Start playing the movie
        System.out.println("  [Facade] Movie \"" + movie + "\" is now playing. Enjoy!");
    }

    /**
     * Encapsulates the 7-step teardown ritual after the movie ends.
     *
     * The Facade knows the correct order to shut everything down safely.
     */
    @Override
    public void endMovie() {
        System.out.println("  [Facade] Shutting down the home theater...");
        popper.stopPopping();           // Step 1: Stop popcorn popper
        popper.off();                   // Step 2: Turn off popper
        lights.on();                    // Step 3: Bring lights back to full
        screen.up();                    // Step 4: Retract projection screen
        projector.off();               // Step 5: Turn off projector
        amp.off();                      // Step 6: Turn off amplifier
        dvd.stop();                     // Step 7: Stop DVD
        dvd.eject();                    // Step 8: Eject disc
        dvd.off();                      // Step 9: Power off DVD player
        System.out.println("  [Facade] Home theater shut down. Goodnight!");
    }

    /**
     * A different high-level use case: music mode.
     * Uses only the amplifier — no projector, no screen, no DVD.
     * Demonstrates that the Facade can expose multiple simplified scenarios.
     */
    @Override
    public void listenToMusic() {
        System.out.println("  [Facade] Activating music mode...");
        lights.dim(40);                 // Softer light for music ambiance
        amp.on();
        amp.setStereoSound();           // Stereo for music (not surround)
        amp.setVolume(7);
        System.out.println("  [Facade] Music mode ready. Press play on your source.");
    }

    /**
     * Shuts down music mode — only amp and lights need attention.
     */
    @Override
    public void endMusic() {
        System.out.println("  [Facade] Shutting down music mode...");
        amp.off();
        lights.on();
        System.out.println("  [Facade] Music mode ended.");
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DEMO: BEFORE AND AFTER FACADE
// ─────────────────────────────────────────────────────────────────────────────

public class HomeTheaterDemo {

    public static void main(String[] args) {

        // ── Setup: create all subsystem components ────────────────────────────
        // In a real application, these would be injected via DI or created by
        // a factory. The Facade then wraps them.
        Amplifier     amp       = new Amplifier("Onkyo TX-RZ50 Amplifier");
        DvdPlayer     dvd       = new DvdPlayer("Sony BDP-S6700 DVD Player", amp);
        Projector     projector = new Projector("Epson Home Cinema 5050UB", dvd);
        Screen        screen    = new Screen("Elite Screens Motorized 120\"");
        TheaterLights lights    = new TheaterLights("Theater Smart Lighting");
        PopcornPopper popper    = new PopcornPopper("Cuisinart Air Popper");

        // ─────────────────────────────────────────────────────────────────────
        // WITHOUT FACADE — client must know and call every step manually
        // This is the problem the Facade pattern solves.
        // ─────────────────────────────────────────────────────────────────────
        System.out.println("=================================================");
        System.out.println("  BEFORE FACADE: Client calls subsystems directly ");
        System.out.println("=================================================");
        System.out.println("  Client code must know ALL of these steps:");
        System.out.println("  (12 ordered calls across 6 objects — error-prone)");
        System.out.println();

        // The client is tightly coupled to all 6 subsystem classes.
        // It must know the correct order. Getting this wrong breaks the experience.
        popper.on();
        popper.pop();
        lights.dim(10);
        screen.down();
        projector.on();
        projector.wideScreenMode();
        amp.on();
        amp.setDvd(dvd);
        amp.setSurroundSound();
        amp.setVolume(5);
        dvd.on();
        dvd.play("The Matrix");

        System.out.println();
        System.out.println("  ... watching movie ...");
        System.out.println();

        // Shutdown — another 9 ordered calls
        popper.stopPopping();
        popper.off();
        lights.on();
        screen.up();
        projector.off();
        amp.off();
        dvd.stop();
        dvd.eject();
        dvd.off();

        // ─────────────────────────────────────────────────────────────────────
        // WITH FACADE — client uses the simplified HomeTheater interface
        // ─────────────────────────────────────────────────────────────────────
        System.out.println();
        System.out.println("=================================================");
        System.out.println("  AFTER FACADE: Client uses HomeTheaterFacade     ");
        System.out.println("=================================================");

        // Client only knows HomeTheater (the facade interface).
        // It has ZERO knowledge of Amplifier, DvdPlayer, Projector, etc.
        HomeTheater homeTheater = new HomeTheaterFacade(amp, dvd, projector, screen, lights, popper);

        System.out.println();
        System.out.println("  Client call: homeTheater.watchMovie(\"Inception\")");
        System.out.println();
        homeTheater.watchMovie("Inception");

        System.out.println();
        System.out.println("  ... watching movie ...");
        System.out.println();

        System.out.println("  Client call: homeTheater.endMovie()");
        System.out.println();
        homeTheater.endMovie();

        // ── Music mode — a different use case, same facade ────────────────────
        System.out.println();
        System.out.println("-------------------------------------------------");
        System.out.println("  Music Mode via same Facade");
        System.out.println("-------------------------------------------------");
        System.out.println();
        System.out.println("  Client call: homeTheater.listenToMusic()");
        System.out.println();
        homeTheater.listenToMusic();

        System.out.println();
        System.out.println("  Client call: homeTheater.endMusic()");
        System.out.println();
        homeTheater.endMusic();

        // ── Advanced user can still access subsystems directly ─────────────────
        // The Facade does NOT lock clients out of subsystem details.
        // An advanced user who wants fine-grained control can still do:
        System.out.println();
        System.out.println("-------------------------------------------------");
        System.out.println("  Advanced usage: bypass Facade for fine control ");
        System.out.println("-------------------------------------------------");
        System.out.println("  (Facade doesn't prevent direct subsystem access)");
        amp.on();
        amp.setStereoSound();          // override Facade's default surround
        amp.setVolume(3);              // custom quiet volume
        dvd.on();
        dvd.play("Documentary: Planet Earth");
        System.out.println("  Playing at lower volume in stereo mode");
        dvd.stop();
        dvd.off();
        amp.off();

        System.out.println();
        System.out.println("=================================================");
        System.out.println("  Demo complete");
        System.out.println("=================================================");
    }
}

/*
 * PATTERN TAKEAWAYS:
 *
 * 1. FACADE REDUCES COUPLING
 *    Without Facade: client imports and depends on 6 subsystem classes.
 *    With Facade:    client imports and depends on 1 interface (HomeTheater).
 *    Swapping any subsystem component requires changing only the Facade,
 *    not every client.
 *
 * 2. FACADE ENCODES DOMAIN KNOWLEDGE
 *    The Facade "knows" that the screen must go down before the projector
 *    turns on, and the amplifier must be linked to the DVD player before
 *    starting playback. This is domain knowledge that was previously
 *    scattered across every client — the Facade centralizes it.
 *
 * 3. FACADE DOES NOT PROHIBIT DIRECT ACCESS
 *    Advanced clients can still use subsystems directly. The Facade is a
 *    convenience, not an access barrier. This differentiates Facade from
 *    Proxy, which is specifically about access control.
 *
 * 4. INTERFACE ON THE FACADE ENABLES MOCKING IN TESTS
 *    HomeTheater is an interface. Tests can mock it:
 *      HomeTheater mockTheater = mock(HomeTheater.class);
 *    This allows testing client code without instantiating any subsystem.
 *
 * 5. MULTIPLE USE CASES FROM ONE FACADE
 *    watchMovie(), listenToMusic(), gameMode() are different simplified
 *    scenarios over the same underlying subsystems. The Facade provides
 *    a menu of high-level operations.
 *
 * 6. COMPARE TO ADAPTER
 *    Adapter makes an incompatible interface compatible (changes the shape).
 *    Facade simplifies a compatible-but-complex interface (reduces surface area).
 */
