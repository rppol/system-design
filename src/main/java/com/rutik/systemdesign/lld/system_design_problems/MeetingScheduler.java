package com.rutik.systemdesign.lld.system_design_problems;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

// =============================================================================
//  MEETING ROOM / CALENDAR SCHEDULER - Low-Level Design
//  Patterns used:
//    - Strategy : MsRoomSelector picks a room among the free ones
//    - Observer : MsMeetingListener hears about bookings and cancellations
//  Each room and each attendee keeps a TreeMap<start, meeting>. Intervals are
//  half-open [start, end), so 10:00-10:30 and 10:30-11:00 do not overlap.
//  Because entries in one calendar never overlap, a conflict check is one
//  lowerEntry(end) lookup: O(log n). book() and cancel() are synchronized so a
//  booking updates the room and every attendee calendar atomically.
// =============================================================================

record MsTimeSlot(LocalDateTime start, LocalDateTime end) {
    MsTimeSlot {
        if (!start.isBefore(end)) throw new IllegalArgumentException("start must be before end");
    }

    Duration length() {
        return Duration.between(start, end);
    }

    @Override
    public String toString() {
        DateTimeFormatter hm = DateTimeFormatter.ofPattern("HH:mm");
        return start.format(hm) + "-" + end.format(hm);
    }
}

record MsMeeting(int id, String title, Set<String> attendees, MsTimeSlot slot, MsRoom room) {}

final class MsSchedulingException extends RuntimeException {
    MsSchedulingException(String message) {
        super(message);
    }
}

/** A calendar of non-overlapping meetings keyed by start time. */
final class MsCalendar {
    private final TreeMap<LocalDateTime, MsMeeting> byStart = new TreeMap<>();

    /** The last meeting starting before slot.end is the only one that can overlap it. */
    Optional<MsMeeting> conflictWith(MsTimeSlot slot) {
        Map.Entry<LocalDateTime, MsMeeting> e = byStart.lowerEntry(slot.end());
        if (e != null && e.getValue().slot().end().isAfter(slot.start())) return Optional.of(e.getValue());
        return Optional.empty();
    }

    void add(MsMeeting m) {
        byStart.put(m.slot().start(), m);
    }

    void remove(MsMeeting m) {
        byStart.remove(m.slot().start());
    }

    List<MsTimeSlot> busyWithin(MsTimeSlot window) {
        List<MsTimeSlot> out = new ArrayList<>();
        LocalDateTime from = Optional.ofNullable(byStart.lowerKey(window.start())).orElse(window.start());
        for (MsMeeting m : byStart.subMap(from, true, window.end(), false).values()) {
            if (m.slot().end().isAfter(window.start())) out.add(m.slot());
        }
        return out;
    }
}

final class MsRoom {
    final String name;
    final int capacity;
    final MsCalendar calendar = new MsCalendar();

    MsRoom(String name, int capacity) {
        this.name = name;
        this.capacity = capacity;
    }
}

interface MsRoomSelector {
    Optional<MsRoom> choose(List<MsRoom> freeRooms, int headcount);
}

/** Smallest room that fits, so large rooms stay free for large meetings. */
final class MsSmallestFitSelector implements MsRoomSelector {
    @Override
    public Optional<MsRoom> choose(List<MsRoom> freeRooms, int headcount) {
        return freeRooms.stream()
                .filter(r -> r.capacity >= headcount)
                .min(Comparator.comparingInt((MsRoom r) -> r.capacity).thenComparing(r -> r.name));
    }
}

interface MsMeetingListener {
    void onBooked(MsMeeting m);

    void onCancelled(MsMeeting m);
}

final class MsConsoleNotifier implements MsMeetingListener {
    @Override
    public void onBooked(MsMeeting m) {
        System.out.println("  invite  #" + m.id() + " '" + m.title() + "' " + m.slot() + " in " + m.room().name
                + " -> " + new TreeSet<>(m.attendees()));
    }

    @Override
    public void onCancelled(MsMeeting m) {
        System.out.println("  cancel  #" + m.id() + " '" + m.title() + "' -> " + new TreeSet<>(m.attendees()));
    }
}

public final class MeetingScheduler {
    private final List<MsRoom> rooms;
    private final MsRoomSelector selector;
    private final List<MsMeetingListener> listeners = new ArrayList<>();
    private final Map<String, MsCalendar> people = new HashMap<>();
    private final Map<Integer, MsMeeting> meetings = new LinkedHashMap<>();
    private final AtomicInteger ids = new AtomicInteger();

    public MeetingScheduler(List<MsRoom> rooms, MsRoomSelector selector) {
        this.rooms = List.copyOf(rooms);
        this.selector = selector;
    }

    public void addListener(MsMeetingListener l) {
        listeners.add(l);
    }

    public synchronized MsMeeting book(String title, Set<String> attendees, MsTimeSlot slot) {
        for (String person : new TreeSet<>(attendees)) {
            Optional<MsMeeting> clash = calendarOf(person).conflictWith(slot);
            if (clash.isPresent()) {
                throw new MsSchedulingException(person + " is busy in '" + clash.get().title() + "' " + clash.get().slot());
            }
        }
        List<MsRoom> free = rooms.stream().filter(r -> r.calendar.conflictWith(slot).isEmpty()).toList();
        MsRoom room = selector.choose(free, attendees.size())
                .orElseThrow(() -> new MsSchedulingException("no free room for " + attendees.size() + " at " + slot));
        MsMeeting m = new MsMeeting(ids.incrementAndGet(), title, Set.copyOf(attendees), slot, room);
        room.calendar.add(m);
        for (String person : attendees) calendarOf(person).add(m);
        meetings.put(m.id(), m);
        listeners.forEach(l -> l.onBooked(m));
        return m;
    }

    public synchronized void cancel(int meetingId) {
        MsMeeting m = meetings.remove(meetingId);
        if (m == null) throw new MsSchedulingException("no meeting #" + meetingId);
        m.room().calendar.remove(m);
        for (String person : m.attendees()) calendarOf(person).remove(m);
        listeners.forEach(l -> l.onCancelled(m));
    }

    /** Gaps of at least minLength inside window when every attendee is free. */
    public synchronized List<MsTimeSlot> freeSlots(Set<String> attendees, MsTimeSlot window, Duration minLength) {
        List<MsTimeSlot> busy = new ArrayList<>();
        for (String person : attendees) busy.addAll(calendarOf(person).busyWithin(window));
        busy.sort(Comparator.comparing(MsTimeSlot::start));
        List<MsTimeSlot> free = new ArrayList<>();
        LocalDateTime cursor = window.start();
        for (MsTimeSlot b : busy) {
            if (b.start().isAfter(cursor)) addIfLongEnough(free, cursor, min(b.start(), window.end()), minLength);
            if (b.end().isAfter(cursor)) cursor = b.end();
        }
        addIfLongEnough(free, cursor, window.end(), minLength);
        return free;
    }

    private static void addIfLongEnough(List<MsTimeSlot> out, LocalDateTime from, LocalDateTime to, Duration min) {
        if (from.isBefore(to) && Duration.between(from, to).compareTo(min) >= 0) out.add(new MsTimeSlot(from, to));
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private MsCalendar calendarOf(String person) {
        return people.computeIfAbsent(person, p -> new MsCalendar());
    }

    private static MsTimeSlot at(String from, String to) {
        LocalDateTime day = LocalDateTime.of(2026, 11, 2, 0, 0);
        return new MsTimeSlot(day.plusMinutes(minutes(from)), day.plusMinutes(minutes(to)));
    }

    private static int minutes(String hhmm) {
        String[] p = hhmm.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }

    private static void tryBook(MeetingScheduler s, String title, Set<String> who, MsTimeSlot slot) {
        try {
            s.book(title, who, slot);
        } catch (MsSchedulingException e) {
            System.out.println("  reject  '" + title + "' " + slot + ": " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        MeetingScheduler s = new MeetingScheduler(
                List.of(new MsRoom("Board-10", 10), new MsRoom("Focus-4", 4), new MsRoom("Huddle-2", 2)),
                new MsSmallestFitSelector());
        s.addListener(new MsConsoleNotifier());

        System.out.println("== booking ==");
        tryBook(s, "1:1", Set.of("alice", "bob"), at("10:00", "10:30"));
        tryBook(s, "Design review", Set.of("carol", "dave", "erin"), at("10:00", "11:00"));
        tryBook(s, "Vendor sync", Set.of("frank", "grace"), at("10:15", "10:45"));
        tryBook(s, "Planning", Set.of("alice", "carol"), at("10:30", "11:00"));
        tryBook(s, "Follow-up", Set.of("alice", "bob"), at("10:30", "11:00"));
        tryBook(s, "All hands", Set.of("a1", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9", "a10", "a11"),
                at("14:00", "15:00"));

        System.out.println("== free slots for alice + carol, 09:00-12:00, >= 30 min ==");
        MsTimeSlot morning = at("09:00", "12:00");
        System.out.println("  " + s.freeSlots(Set.of("alice", "carol"), morning, Duration.ofMinutes(30)));

        System.out.println("== cancel the design review ==");
        s.cancel(2);
        System.out.println("  " + s.freeSlots(Set.of("alice", "carol"), morning, Duration.ofMinutes(30)));
        tryBook(s, "Planning", Set.of("alice", "carol"), at("11:00", "11:30"));
    }
}
