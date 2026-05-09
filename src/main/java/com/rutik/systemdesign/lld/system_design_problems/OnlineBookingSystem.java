package com.rutik.systemdesign.lld.system_design_problems; /**
 * ONLINE BOOKING SYSTEM (Movie Ticket Booking)
 *
 * Patterns used:
 *   Builder  — constructing Movie and Show objects with many fields
 *   Strategy — flexible, runtime-selectable pricing strategies
 *   Observer — notifying users and systems after booking events
 *
 * Features:
 *   - Search shows by city, movie, date
 *   - Select seats and check availability
 *   - Confirm booking with chosen pricing strategy
 *   - Cancel booking with refund calculation
 *   - Multi-channel notifications (email, SMS, loyalty points)
 */

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

// ─────────────────────────────────────────────────────────────
// Enums
// ─────────────────────────────────────────────────────────────

enum SeatType  { STANDARD, PREMIUM, VIP }
enum BookingStatus { PENDING, CONFIRMED, CANCELLED, EXPIRED }
enum Genre     { ACTION, COMEDY, DRAMA, THRILLER, ANIMATION, SCIFI }

// ─────────────────────────────────────────────────────────────
// Builder Pattern — Movie
// ─────────────────────────────────────────────────────────────

class Movie {
    private final String   movieId;
    private final String   title;
    private final int      durationMinutes;

    // Optional fields
    private final Genre    genre;
    private final String   rating;     // PG, PG-13, R
    private final String   language;
    private final boolean  hasSubtitles;
    private final String   director;
    private final double   imdbScore;

    private Movie(Builder b) {
        this.movieId         = b.movieId;
        this.title           = b.title;
        this.durationMinutes = b.durationMinutes;
        this.genre           = b.genre;
        this.rating          = b.rating;
        this.language        = b.language;
        this.hasSubtitles    = b.hasSubtitles;
        this.director        = b.director;
        this.imdbScore       = b.imdbScore;
    }

    public String getMovieId()          { return movieId; }
    public String getTitle()            { return title; }
    public int    getDurationMinutes()  { return durationMinutes; }
    public Genre  getGenre()            { return genre; }
    public String getRating()           { return rating; }
    public String getLanguage()         { return language; }
    public double getImdbScore()        { return imdbScore; }

    @Override
    public String toString() {
        return String.format("Movie{id='%s', title='%s', genre=%s, rating='%s', imdb=%.1f}",
                movieId, title, genre, rating, imdbScore);
    }

    public static class Builder {
        private final String movieId;
        private final String title;
        private final int    durationMinutes;

        private Genre   genre        = Genre.DRAMA;
        private String  rating       = "PG";
        private String  language     = "English";
        private boolean hasSubtitles = false;
        private String  director     = "";
        private double  imdbScore    = 0.0;

        public Builder(String movieId, String title, int durationMinutes) {
            if (movieId == null || title == null) throw new IllegalArgumentException("movieId and title required");
            this.movieId         = movieId;
            this.title           = title;
            this.durationMinutes = durationMinutes;
        }

        public Builder genre(Genre g)            { this.genre = g;        return this; }
        public Builder rating(String r)          { this.rating = r;       return this; }
        public Builder language(String l)        { this.language = l;     return this; }
        public Builder subtitles(boolean s)      { this.hasSubtitles = s; return this; }
        public Builder director(String d)        { this.director = d;     return this; }
        public Builder imdbScore(double s)       { this.imdbScore = s;    return this; }

        public Movie build() { return new Movie(this); }
    }
}

// ─────────────────────────────────────────────────────────────
// Seat
// ─────────────────────────────────────────────────────────────

class Seat {
    private final String   seatId;
    private final String   row;
    private final int      number;
    private final SeatType type;
    private final double   basePrice;
    private boolean        reserved;

    public Seat(String row, int number, SeatType type, double basePrice) {
        this.seatId    = row + number;
        this.row       = row;
        this.number    = number;
        this.type      = type;
        this.basePrice = basePrice;
        this.reserved  = false;
    }

    public String   getSeatId()    { return seatId; }
    public String   getRow()       { return row; }
    public int      getNumber()    { return number; }
    public SeatType getType()      { return type; }
    public double   getBasePrice() { return basePrice; }
    public boolean  isReserved()   { return reserved; }
    public void     reserve()      { reserved = true; }
    public void     release()      { reserved = false; }

    @Override
    public String toString() {
        return String.format("[%s %s $%.2f %s]", seatId, type,
                basePrice, reserved ? "TAKEN" : "FREE");
    }
}

// ─────────────────────────────────────────────────────────────
// Builder Pattern — Show
// ─────────────────────────────────────────────────────────────

class Show {
    private final String         showId;
    private final Movie          movie;
    private final String         theater;
    private final String         screen;
    private final LocalDateTime  startTime;
    private final List<Seat>     seats;
    private final boolean        isPeakHour;

    private Show(Builder b) {
        this.showId    = b.showId;
        this.movie     = b.movie;
        this.theater   = b.theater;
        this.screen    = b.screen;
        this.startTime = b.startTime;
        this.seats     = b.seats;
        this.isPeakHour = isPeakHour(b.startTime);
    }

    private static boolean isPeakHour(LocalDateTime dt) {
        int hour = dt.getHour();
        return (hour >= 18 && hour <= 22) || dt.getDayOfWeek().getValue() >= 6;
    }

    public String         getShowId()    { return showId; }
    public Movie          getMovie()     { return movie; }
    public String         getTheater()   { return theater; }
    public LocalDateTime  getStartTime() { return startTime; }
    public List<Seat>     getSeats()     { return Collections.unmodifiableList(seats); }
    public boolean        isPeakHour()   { return isPeakHour; }

    public List<Seat> getAvailableSeats() {
        List<Seat> available = new ArrayList<>();
        for (Seat s : seats) if (!s.isReserved()) available.add(s);
        return available;
    }

    public Optional<Seat> getSeatById(String seatId) {
        return seats.stream().filter(s -> s.getSeatId().equals(seatId)).findFirst();
    }

    public static class Builder {
        private final String        showId;
        private final Movie         movie;
        private final String        theater;
        private final String        screen;
        private final LocalDateTime startTime;
        private List<Seat>          seats = new ArrayList<>();

        public Builder(String showId, Movie movie, String theater, String screen, LocalDateTime startTime) {
            this.showId    = showId;
            this.movie     = movie;
            this.theater   = theater;
            this.screen    = screen;
            this.startTime = startTime;
        }

        public Builder withSeats(List<Seat> seats) { this.seats = new ArrayList<>(seats); return this; }

        public Builder withDefaultSeating(int rows, int seatsPerRow) {
            seats.clear();
            for (int r = 0; r < rows; r++) {
                String row = String.valueOf((char)('A' + r));
                for (int n = 1; n <= seatsPerRow; n++) {
                    SeatType type;
                    double price;
                    if (r < 2)       { type = SeatType.STANDARD; price = 10.0; }
                    else if (r < 4)  { type = SeatType.PREMIUM;  price = 15.0; }
                    else             { type = SeatType.VIP;       price = 25.0; }
                    seats.add(new Seat(row, n, type, price));
                }
            }
            return this;
        }

        public Show build() { return new Show(this); }
    }
}

// ─────────────────────────────────────────────────────────────
// Strategy Pattern — Pricing
// ─────────────────────────────────────────────────────────────

interface PricingStrategy {
    double calculatePrice(Seat seat, Show show);
    String getStrategyName();
}

class RegularPricing implements PricingStrategy {
    @Override
    public double calculatePrice(Seat seat, Show show) {
        return seat.getBasePrice();
    }
    @Override public String getStrategyName() { return "Regular"; }
}

class PeakHourPricing implements PricingStrategy {
    private static final double PEAK_MULTIPLIER = 1.5;
    @Override
    public double calculatePrice(Seat seat, Show show) {
        double base = seat.getBasePrice();
        return show.isPeakHour() ? base * PEAK_MULTIPLIER : base;
    }
    @Override public String getStrategyName() { return "Peak Hour (1.5x on evenings/weekends)"; }
}

class MemberDiscountPricing implements PricingStrategy {
    private final double discountPercent;
    public MemberDiscountPricing(double discountPercent) { this.discountPercent = discountPercent; }
    @Override
    public double calculatePrice(Seat seat, Show show) {
        return seat.getBasePrice() * (1.0 - discountPercent / 100.0);
    }
    @Override public String getStrategyName() { return "Member Discount (" + discountPercent + "% off)"; }
}

class ChildPricing implements PricingStrategy {
    private static final double CHILD_PRICE = 7.0;
    @Override
    public double calculatePrice(Seat seat, Show show) {
        // Child price is flat regardless of seat type
        return Math.min(seat.getBasePrice(), CHILD_PRICE);
    }
    @Override public String getStrategyName() { return "Child Pricing (max $7)"; }
}

class GroupPricing implements PricingStrategy {
    private static final int    GROUP_THRESHOLD = 10;
    private static final double GROUP_DISCOUNT  = 0.80; // 20% off
    private final int groupSize;
    public GroupPricing(int groupSize) { this.groupSize = groupSize; }
    @Override
    public double calculatePrice(Seat seat, Show show) {
        return groupSize >= GROUP_THRESHOLD
                ? seat.getBasePrice() * GROUP_DISCOUNT
                : seat.getBasePrice();
    }
    @Override public String getStrategyName() { return "Group Pricing (" + groupSize + " persons)"; }
}

// ─────────────────────────────────────────────────────────────
// Booking
// ─────────────────────────────────────────────────────────────

class Booking {
    private final String         bookingId;
    private final String         userId;
    private final Show           show;
    private final List<Seat>     seats;
    private final double         totalAmount;
    private final PricingStrategy pricingStrategy;
    private BookingStatus         status;
    private final LocalDateTime   bookingTime;

    public Booking(String bookingId, String userId, Show show,
                   List<Seat> seats, PricingStrategy strategy) {
        this.bookingId       = bookingId;
        this.userId          = userId;
        this.show            = show;
        this.seats           = new ArrayList<>(seats);
        this.pricingStrategy = strategy;
        this.totalAmount     = seats.stream()
                .mapToDouble(s -> strategy.calculatePrice(s, show))
                .sum();
        this.status          = BookingStatus.PENDING;
        this.bookingTime     = LocalDateTime.now();
    }

    public String        getBookingId()  { return bookingId; }
    public String        getUserId()     { return userId; }
    public Show          getShow()       { return show; }
    public List<Seat>    getSeats()      { return Collections.unmodifiableList(seats); }
    public double        getTotalAmount(){ return totalAmount; }
    public BookingStatus getStatus()     { return status; }
    public void          confirm()       { status = BookingStatus.CONFIRMED; }
    public void          cancel()        { status = BookingStatus.CANCELLED; }

    @Override
    public String toString() {
        return String.format("Booking{id='%s', user='%s', movie='%s', seats=%d, total=$%.2f, pricing='%s', status=%s}",
                bookingId, userId, show.getMovie().getTitle(),
                seats.size(), totalAmount, pricingStrategy.getStrategyName(), status);
    }
}

// ─────────────────────────────────────────────────────────────
// Observer Pattern — Booking events
// ─────────────────────────────────────────────────────────────

interface BookingObserver {
    void onBookingConfirmed(Booking booking);
    void onBookingCancelled(Booking booking);
}

class EmailNotificationService implements BookingObserver {
    @Override
    public void onBookingConfirmed(Booking booking) {
        System.out.printf("  [EMAIL] Booking confirmed! ID=%s | Movie='%s' | Seats=%s | Total=$%.2f%n",
                booking.getBookingId(), booking.getShow().getMovie().getTitle(),
                booking.getSeats().stream().map(Seat::getSeatId).toList(),
                booking.getTotalAmount());
    }
    @Override
    public void onBookingCancelled(Booking booking) {
        System.out.printf("  [EMAIL] Booking cancelled. ID=%s | Refund=$%.2f%n",
                booking.getBookingId(), booking.getTotalAmount());
    }
}

class SMSNotificationService implements BookingObserver {
    @Override
    public void onBookingConfirmed(Booking booking) {
        System.out.printf("  [SMS]   Your booking %s for '%s' is confirmed. Seats: %s%n",
                booking.getBookingId(), booking.getShow().getMovie().getTitle(),
                booking.getSeats().stream().map(Seat::getSeatId).toList());
    }
    @Override
    public void onBookingCancelled(Booking booking) {
        System.out.printf("  [SMS]   Booking %s cancelled. Refund initiated.%n", booking.getBookingId());
    }
}

class LoyaltyPointsService implements BookingObserver {
    private final Map<String, Integer> pointsBalance = new HashMap<>();
    private static final double POINTS_PER_DOLLAR = 10.0;

    @Override
    public void onBookingConfirmed(Booking booking) {
        int points = (int)(booking.getTotalAmount() * POINTS_PER_DOLLAR);
        pointsBalance.merge(booking.getUserId(), points, Integer::sum);
        System.out.printf("  [LOYALTY] User '%s' earned %d points. Total: %d%n",
                booking.getUserId(), points, pointsBalance.get(booking.getUserId()));
    }

    @Override
    public void onBookingCancelled(Booking booking) {
        int points = (int)(booking.getTotalAmount() * POINTS_PER_DOLLAR);
        pointsBalance.merge(booking.getUserId(), -points, Integer::sum);
        System.out.printf("  [LOYALTY] User '%s' lost %d points. Total: %d%n",
                booking.getUserId(), points, pointsBalance.getOrDefault(booking.getUserId(), 0));
    }
}

// ─────────────────────────────────────────────────────────────
// Booking Service — Orchestrator
// ─────────────────────────────────────────────────────────────

class BookingService {
    private final Map<String, Show>    shows     = new HashMap<>();
    private final Map<String, Booking> bookings  = new HashMap<>();
    private final List<BookingObserver> observers = new ArrayList<>();
    private int bookingCounter = 1000;

    public void addObserver(BookingObserver o) { observers.add(o); }
    public void addShow(Show show)             { shows.put(show.getShowId(), show); }

    public List<Show> searchShows(String movieTitle, LocalDate date) {
        List<Show> results = new ArrayList<>();
        for (Show show : shows.values()) {
            boolean titleMatch = show.getMovie().getTitle()
                    .toLowerCase().contains(movieTitle.toLowerCase());
            boolean dateMatch  = show.getStartTime().toLocalDate().equals(date);
            if (titleMatch && dateMatch) results.add(show);
        }
        return results;
    }

    /**
     * Selects seats — in production this would use optimistic locking or
     * a distributed lock to prevent double-booking.
     */
    public Optional<Booking> selectAndBook(String userId, String showId,
                                            List<String> seatIds,
                                            PricingStrategy pricingStrategy) {
        Show show = shows.get(showId);
        if (show == null) { System.out.println("[BOOKING] Show not found: " + showId); return Optional.empty(); }

        List<Seat> selectedSeats = new ArrayList<>();
        for (String seatId : seatIds) {
            Optional<Seat> seatOpt = show.getSeatById(seatId);
            if (seatOpt.isEmpty()) {
                System.out.println("[BOOKING] Seat not found: " + seatId);
                return Optional.empty();
            }
            Seat seat = seatOpt.get();
            if (seat.isReserved()) {
                System.out.println("[BOOKING] Seat already taken: " + seatId);
                return Optional.empty();
            }
            selectedSeats.add(seat);
        }

        // Mark seats as reserved
        selectedSeats.forEach(Seat::reserve);

        String bookingId = "BK" + (bookingCounter++);
        Booking booking = new Booking(bookingId, userId, show, selectedSeats, pricingStrategy);
        booking.confirm();
        bookings.put(bookingId, booking);

        // Notify observers
        observers.forEach(o -> o.onBookingConfirmed(booking));

        return Optional.of(booking);
    }

    public boolean cancelBooking(String bookingId) {
        Booking booking = bookings.get(bookingId);
        if (booking == null || booking.getStatus() == BookingStatus.CANCELLED) return false;

        // Release seats
        booking.getSeats().forEach(Seat::release);
        booking.cancel();

        observers.forEach(o -> o.onBookingCancelled(booking));
        return true;
    }
}

// ─────────────────────────────────────────────────────────────
// Demo
// ─────────────────────────────────────────────────────────────

class OnlineBookingDemo {

    public static void main(String[] args) {
        // Create movies using Builder
        Movie avengers = new Movie.Builder("M001", "Avengers: Endgame", 182)
                .genre(Genre.ACTION)
                .rating("PG-13")
                .director("The Russo Brothers")
                .imdbScore(8.4)
                .build();

        Movie inception = new Movie.Builder("M002", "Inception", 148)
                .genre(Genre.SCIFI)
                .rating("PG-13")
                .director("Christopher Nolan")
                .imdbScore(8.8)
                .build();

        // Create shows using Builder
        Show show1 = new Show.Builder("S001", avengers, "CityPlex", "Screen A",
                LocalDateTime.of(LocalDate.now(), LocalTime.of(21, 0))) // 9 PM — peak
                .withDefaultSeating(6, 8)
                .build();

        Show show2 = new Show.Builder("S002", avengers, "CityPlex", "Screen B",
                LocalDateTime.of(LocalDate.now(), LocalTime.of(14, 0))) // 2 PM — off-peak
                .withDefaultSeating(6, 8)
                .build();

        Show show3 = new Show.Builder("S003", inception, "CityPlex", "Screen C",
                LocalDateTime.of(LocalDate.now(), LocalTime.of(19, 0)))
                .withDefaultSeating(5, 10)
                .build();

        // Setup booking service
        BookingService service = new BookingService();
        service.addShow(show1);
        service.addShow(show2);
        service.addShow(show3);

        service.addObserver(new EmailNotificationService());
        service.addObserver(new SMSNotificationService());
        service.addObserver(new LoyaltyPointsService());

        System.out.println("=== Search Shows ===");
        List<Show> found = service.searchShows("Avengers", LocalDate.now());
        found.forEach(s -> System.out.printf("  Show %s | %s | %s | Peak=%b | Available=%d seats%n",
                s.getShowId(), s.getMovie().getTitle(), s.getStartTime(), s.isPeakHour(),
                s.getAvailableSeats().size()));

        System.out.println("\n=== Regular Booking (peak show, regular pricing) ===");
        Optional<Booking> b1 = service.selectAndBook(
                "user_alice", "S001", List.of("A1", "A2"),
                new RegularPricing());
        b1.ifPresent(b -> System.out.println("  " + b));

        System.out.println("\n=== Peak Hour Pricing (same show, different price) ===");
        Optional<Booking> b2 = service.selectAndBook(
                "user_bob", "S001", List.of("C1", "C2"),
                new PeakHourPricing());
        b2.ifPresent(b -> System.out.println("  " + b));

        System.out.println("\n=== Member Discount (20% off) ===");
        Optional<Booking> b3 = service.selectAndBook(
                "user_carol", "S002", List.of("E1", "E2", "E3"),
                new MemberDiscountPricing(20));
        b3.ifPresent(b -> System.out.println("  " + b));

        System.out.println("\n=== Double-booking prevention ===");
        Optional<Booking> b4 = service.selectAndBook(
                "user_dave", "S001", List.of("A1"), // A1 already taken
                new RegularPricing());
        if (b4.isEmpty()) System.out.println("  Correctly rejected double-booking attempt");

        System.out.println("\n=== Cancel Booking ===");
        b1.ifPresent(b -> {
            boolean cancelled = service.cancelBooking(b.getBookingId());
            System.out.println("  Cancelled: " + cancelled);
        });

        System.out.println("\n=== Seat now available again ===");
        Optional<Booking> b5 = service.selectAndBook(
                "user_eve", "S001", List.of("A1"),
                new ChildPricing());
        b5.ifPresent(b -> System.out.println("  " + b));
    }
}
