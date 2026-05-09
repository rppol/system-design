package com.rutik.systemdesign.lld.structural.bridge;

/**
 * BRIDGE PATTERN — Real-World Example: Remote Control + Device
 *
 * Scenario:
 *   A universal remote control system. Remote controls (the abstraction) and
 *   devices (the implementation) must vary independently:
 *
 *   Devices:  TV, Radio, Projector, ...
 *   Remotes:  BasicRemote, AdvancedRemote, VoiceRemote, ...
 *
 *   Without Bridge: 2 remotes × 3 devices = 6 subclasses, growing as M×N.
 *   With Bridge:    2 remotes + 3 devices = 5 classes, any combo works.
 *
 * Roles:
 *   - Device          : Implementor  — low-level device primitives
 *   - TV / Radio      : ConcreteImplementors
 *   - RemoteControl   : Abstraction  — high-level remote interface
 *   - AdvancedRemote  : RefinedAbstraction — adds extra features
 */

// ─────────────────────────────────────────────
// IMPLEMENTOR: Device
// Defines the primitives all devices must support.
// Remote controls talk to devices only through this interface.
// ─────────────────────────────────────────────

interface Device {
    boolean isEnabled();
    void enable();
    void disable();
    int getVolume();
    void setVolume(int percent);     // 0–100
    int getChannel();
    void setChannel(int channel);
    String getDeviceName();
}

// ─────────────────────────────────────────────
// CONCRETE IMPLEMENTOR: TV
// ─────────────────────────────────────────────

class TV implements Device {

    private boolean on = false;
    private int volume = 30;
    private int channel = 1;

    @Override public boolean isEnabled()  { return on; }

    @Override
    public void enable() {
        on = true;
        System.out.println("[TV] Powered ON");
    }

    @Override
    public void disable() {
        on = false;
        System.out.println("[TV] Powered OFF");
    }

    @Override public int getVolume() { return volume; }

    @Override
    public void setVolume(int percent) {
        volume = Math.max(0, Math.min(100, percent));
        System.out.println("[TV] Volume set to " + volume + "%");
    }

    @Override public int getChannel() { return channel; }

    @Override
    public void setChannel(int channel) {
        this.channel = channel;
        System.out.println("[TV] Channel changed to " + channel);
    }

    @Override public String getDeviceName() { return "Samsung TV"; }
}

// ─────────────────────────────────────────────
// CONCRETE IMPLEMENTOR: Radio
// Same interface, completely different internals
// (radio doesn't have channels — it has frequencies,
// but we model them as channel numbers for simplicity).
// ─────────────────────────────────────────────

class Radio implements Device {

    private boolean on = false;
    private int volume = 50;
    private int frequency = 101;  // FM frequency as integer (e.g., 101 = 101.0 MHz)

    @Override public boolean isEnabled()  { return on; }

    @Override
    public void enable() {
        on = true;
        System.out.println("[Radio] Powered ON — tuned to " + frequency + ".0 FM");
    }

    @Override
    public void disable() {
        on = false;
        System.out.println("[Radio] Powered OFF");
    }

    @Override public int getVolume() { return volume; }

    @Override
    public void setVolume(int percent) {
        volume = Math.max(0, Math.min(100, percent));
        System.out.println("[Radio] Volume set to " + volume + "%");
    }

    @Override public int getChannel() { return frequency; }

    @Override
    public void setChannel(int channel) {
        this.frequency = channel;
        System.out.println("[Radio] Tuned to " + frequency + ".0 FM");
    }

    @Override public String getDeviceName() { return "Sony Radio"; }
}

// ─────────────────────────────────────────────
// ABSTRACTION: RemoteControl
// High-level remote operations. Holds a Device reference
// (the bridge). Orchestrates device primitives into
// meaningful user-facing actions.
// ─────────────────────────────────────────────

class RemoteControl {

    // The bridge — a reference to any Device implementation
    protected final Device device;

    public RemoteControl(Device device) {
        this.device = device;
        System.out.println("Remote paired with: " + device.getDeviceName());
    }

    /** Toggle power: turns off if on, turns on if off. */
    public void togglePower() {
        if (device.isEnabled()) {
            device.disable();
        } else {
            device.enable();
        }
    }

    /** Decrease volume by 10%, clamped at 0. */
    public void volumeDown() {
        device.setVolume(device.getVolume() - 10);
    }

    /** Increase volume by 10%, clamped at 100. */
    public void volumeUp() {
        device.setVolume(device.getVolume() + 10);
    }

    /** Move to the previous channel/frequency. */
    public void channelDown() {
        device.setChannel(device.getChannel() - 1);
    }

    /** Move to the next channel/frequency. */
    public void channelUp() {
        device.setChannel(device.getChannel() + 1);
    }

    /** Print current device status. */
    public void printStatus() {
        System.out.println("[Remote] " + device.getDeviceName()
                + " | Power: " + (device.isEnabled() ? "ON" : "OFF")
                + " | Volume: " + device.getVolume() + "%"
                + " | Channel: " + device.getChannel());
    }
}

// ─────────────────────────────────────────────
// REFINED ABSTRACTION: AdvancedRemote
// Extends RemoteControl with additional features
// (mute, jump to channel). Still uses Device primitives.
// The RefinedAbstraction adds capabilities that are
// independent of which device is connected.
// ─────────────────────────────────────────────

class AdvancedRemote extends RemoteControl {

    // Stores the volume before muting so we can restore it
    private int volumeBeforeMute = -1;

    public AdvancedRemote(Device device) {
        super(device);
    }

    /**
     * Mute: sets volume to 0 and remembers previous level.
     * Un-mute: restores the previous volume level.
     */
    public void mute() {
        if (device.getVolume() > 0) {
            volumeBeforeMute = device.getVolume();
            device.setVolume(0);
            System.out.println("[AdvancedRemote] Muted (was " + volumeBeforeMute + "%)");
        } else if (volumeBeforeMute >= 0) {
            device.setVolume(volumeBeforeMute);
            System.out.println("[AdvancedRemote] Un-muted (restored to " + volumeBeforeMute + "%)");
            volumeBeforeMute = -1;
        }
    }

    /**
     * Jump directly to a specific channel number.
     */
    public void jumpToChannel(int channel) {
        System.out.println("[AdvancedRemote] Jumping to channel " + channel);
        device.setChannel(channel);
    }

    /**
     * Set exact volume level (bypasses step increments).
     */
    public void setExactVolume(int percent) {
        System.out.println("[AdvancedRemote] Setting exact volume to " + percent + "%");
        device.setVolume(percent);
    }
}

// ─────────────────────────────────────────────
// MAIN
// ─────────────────────────────────────────────

public class RealWorldExample {

    public static void main(String[] args) {

        System.out.println("==============================================");
        System.out.println(" BasicRemote + TV");
        System.out.println("==============================================");
        Device tv = new TV();
        RemoteControl basicRemote = new RemoteControl(tv);

        basicRemote.togglePower();      // TV: ON
        basicRemote.volumeUp();         // 40%
        basicRemote.volumeUp();         // 50%
        basicRemote.channelUp();        // channel 2
        basicRemote.channelUp();        // channel 3
        basicRemote.printStatus();
        basicRemote.togglePower();      // TV: OFF

        System.out.println();

        System.out.println("==============================================");
        System.out.println(" BasicRemote + Radio (same remote class, different device)");
        System.out.println("==============================================");
        Device radio = new Radio();
        RemoteControl radioRemote = new RemoteControl(radio);

        radioRemote.togglePower();      // Radio: ON
        radioRemote.channelUp();        // 102.0 FM
        radioRemote.channelUp();        // 103.0 FM
        radioRemote.volumeDown();       // 40%
        radioRemote.printStatus();

        System.out.println();

        System.out.println("==============================================");
        System.out.println(" AdvancedRemote + TV (refined abstraction)");
        System.out.println("==============================================");
        Device tv2 = new TV();
        AdvancedRemote advancedRemote = new AdvancedRemote(tv2);

        advancedRemote.togglePower();           // TV: ON
        advancedRemote.setExactVolume(65);      // volume 65%
        advancedRemote.mute();                  // mute  → 0%
        advancedRemote.mute();                  // unmute → 65%
        advancedRemote.jumpToChannel(42);       // channel 42
        advancedRemote.printStatus();

        System.out.println();

        System.out.println("==============================================");
        System.out.println(" AdvancedRemote + Radio (refined + different device)");
        System.out.println("==============================================");
        AdvancedRemote advancedRadioRemote = new AdvancedRemote(new Radio());
        advancedRadioRemote.togglePower();
        advancedRadioRemote.jumpToChannel(98);  // 98.0 FM
        advancedRadioRemote.setExactVolume(70);
        advancedRadioRemote.mute();
        advancedRadioRemote.mute();
        advancedRadioRemote.printStatus();
    }
}

/*
 * WHAT THIS EXAMPLE DEMONSTRATES:
 *
 * 1. Independent variation:
 *    RemoteControl ←bridge→ Device
 *    Add Projector device  → all remotes work with it immediately.
 *    Add VoiceRemote       → works with all devices immediately.
 *    No combinatorial subclass explosion.
 *
 * 2. RefinedAbstraction adds capability without duplicating device knowledge:
 *    AdvancedRemote.mute() is expressed entirely via device.setVolume() —
 *    it doesn't need to know if the device is a TV or Radio.
 *
 * 3. The "bridge" (device reference) is injected at construction time.
 *    It could also be swapped at runtime: add setDevice(Device d) to the remote.
 *
 * 4. Device (Implementor) interface is stable. TV and Radio internals differ
 *    wildly (volume levels, tuning model) but expose the same interface.
 *    The remote never cares about those internal differences.
 */
