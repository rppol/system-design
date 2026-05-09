package com.rutik.systemdesign.lld.system_design_problems; /**
 * LIBRARY MANAGEMENT SYSTEM
 *
 * Patterns used:
 *   Builder  — constructing Book objects with required/optional fields
 *   Iterator — filtering the book catalog without exposing internals
 *   Observer — notifying members of due dates and available reservations
 *
 * Features:
 *   - Search books by title, author, genre (via Iterator)
 *   - Borrow and return books (with availability tracking)
 *   - Reserve books when unavailable
 *   - Overdue and reservation notifications (via Observer)
 */

import java.time.LocalDate;
import java.util.*;

// ─────────────────────────────────────────────────────────────
// Domain Enums
// ─────────────────────────────────────────────────────────────

enum BookStatus { AVAILABLE, BORROWED, RESERVED, LOST }
enum MemberStatus { ACTIVE, SUSPENDED, EXPIRED }
enum NotificationType { OVERDUE, RESERVATION_AVAILABLE, BORROW_CONFIRMED, RETURN_CONFIRMED }

// ─────────────────────────────────────────────────────────────
// Builder Pattern — Book
// ─────────────────────────────────────────────────────────────

class Book {
    // Required fields
    private final String isbn;
    private final String title;

    // Optional fields
    private final String author;
    private final int    publicationYear;
    private final String genre;
    private final int    totalCopies;
    private final String publisher;
    private final String language;

    private Book(Builder builder) {
        this.isbn            = builder.isbn;
        this.title           = builder.title;
        this.author          = builder.author;
        this.publicationYear = builder.publicationYear;
        this.genre           = builder.genre;
        this.totalCopies     = builder.totalCopies;
        this.publisher       = builder.publisher;
        this.language        = builder.language;
    }

    public String getIsbn()            { return isbn; }
    public String getTitle()           { return title; }
    public String getAuthor()          { return author; }
    public int    getPublicationYear() { return publicationYear; }
    public String getGenre()           { return genre; }
    public int    getTotalCopies()     { return totalCopies; }
    public String getPublisher()       { return publisher; }
    public String getLanguage()        { return language; }

    @Override
    public String toString() {
        return String.format("Book{isbn='%s', title='%s', author='%s', genre='%s', copies=%d}",
                isbn, title, author, genre, totalCopies);
    }

    // ── Builder ──
    public static class Builder {
        // Required
        private final String isbn;
        private final String title;

        // Optional with defaults
        private String author          = "Unknown";
        private int    publicationYear = 0;
        private String genre           = "General";
        private int    totalCopies     = 1;
        private String publisher       = "";
        private String language        = "English";

        public Builder(String isbn, String title) {
            if (isbn == null || isbn.isBlank())   throw new IllegalArgumentException("ISBN required");
            if (title == null || title.isBlank()) throw new IllegalArgumentException("Title required");
            this.isbn  = isbn;
            this.title = title;
        }

        public Builder author(String author)              { this.author = author; return this; }
        public Builder publicationYear(int year)          { this.publicationYear = year; return this; }
        public Builder genre(String genre)                { this.genre = genre; return this; }
        public Builder copies(int copies)                 { this.totalCopies = copies; return this; }
        public Builder publisher(String publisher)        { this.publisher = publisher; return this; }
        public Builder language(String language)          { this.language = language; return this; }

        public Book build() { return new Book(this); }
    }
}

// ─────────────────────────────────────────────────────────────
// BookCopy — individual physical copy
// ─────────────────────────────────────────────────────────────

class BookCopy {
    private final String copyId;
    private final String isbn;
    private BookStatus status;

    public BookCopy(String copyId, String isbn) {
        this.copyId = copyId;
        this.isbn   = isbn;
        this.status = BookStatus.AVAILABLE;
    }

    public String     getCopyId() { return copyId; }
    public String     getIsbn()   { return isbn; }
    public BookStatus getStatus() { return status; }
    public void       setStatus(BookStatus status) { this.status = status; }

    public boolean isAvailable() { return status == BookStatus.AVAILABLE; }
}

// ─────────────────────────────────────────────────────────────
// Loan record
// ─────────────────────────────────────────────────────────────

class Loan {
    private final String memberId;
    private final String copyId;
    private final String isbn;
    private final LocalDate borrowDate;
    private final LocalDate dueDate;
    private LocalDate returnDate;
    private boolean overdue;

    public Loan(String memberId, String copyId, String isbn) {
        this.memberId   = memberId;
        this.copyId     = copyId;
        this.isbn       = isbn;
        this.borrowDate = LocalDate.now();
        this.dueDate    = LocalDate.now().plusDays(14); // 2-week loan period
        this.overdue    = false;
    }

    public String    getMemberId()  { return memberId; }
    public String    getCopyId()    { return copyId; }
    public String    getIsbn()      { return isbn; }
    public LocalDate getBorrowDate(){ return borrowDate; }
    public LocalDate getDueDate()   { return dueDate; }
    public boolean   isActive()     { return returnDate == null; }
    public void      returnBook()   { returnDate = LocalDate.now(); }
    public boolean   isOverdue()    { return isActive() && LocalDate.now().isAfter(dueDate); }

    @Override
    public String toString() {
        return String.format("Loan{member=%s, isbn=%s, due=%s, returned=%s}",
                memberId, isbn, dueDate, returnDate != null ? returnDate : "active");
    }
}

// ─────────────────────────────────────────────────────────────
// Member
// ─────────────────────────────────────────────────────────────

class Member {
    private final String memberId;
    private final String name;
    private final String email;
    private MemberStatus status;
    private final List<Loan> loanHistory = new ArrayList<>();
    private static final int MAX_BOOKS = 5;

    public Member(String memberId, String name, String email) {
        this.memberId = memberId;
        this.name     = name;
        this.email    = email;
        this.status   = MemberStatus.ACTIVE;
    }

    public String       getMemberId()  { return memberId; }
    public String       getName()      { return name; }
    public String       getEmail()     { return email; }
    public MemberStatus getStatus()    { return status; }

    public long getActiveLoansCount() {
        return loanHistory.stream().filter(Loan::isActive).count();
    }

    public boolean canBorrow() {
        return status == MemberStatus.ACTIVE && getActiveLoansCount() < MAX_BOOKS;
    }

    public void addLoan(Loan loan) { loanHistory.add(loan); }

    public List<Loan> getActiveLoans() {
        List<Loan> active = new ArrayList<>();
        loanHistory.stream().filter(Loan::isActive).forEach(active::add);
        return active;
    }

    @Override
    public String toString() {
        return String.format("Member{id=%s, name='%s', activeLoans=%d}", memberId, name, getActiveLoansCount());
    }
}

// ─────────────────────────────────────────────────────────────
// Observer Pattern — Library notifications
// ─────────────────────────────────────────────────────────────

interface LibraryObserver {
    void onNotification(NotificationType type, Member member, Book book, String message);
}

class EmailNotificationObserver implements LibraryObserver {
    @Override
    public void onNotification(NotificationType type, Member member, Book book, String message) {
        System.out.printf("  [EMAIL] To: %s | Type: %-25s | %s%n",
                member.getEmail(), type, message);
    }
}

class AuditLogObserver implements LibraryObserver {
    private final List<String> log = new ArrayList<>();

    @Override
    public void onNotification(NotificationType type, Member member, Book book, String message) {
        String entry = String.format("[%s] member=%s book=%s: %s",
                LocalDate.now(), member.getMemberId(),
                book != null ? book.getIsbn() : "N/A", message);
        log.add(entry);
        System.out.println("  [AUDIT] " + entry);
    }

    public void printLog() {
        System.out.println("\n--- Audit Log ---");
        log.forEach(e -> System.out.println("  " + e));
    }
}

// ─────────────────────────────────────────────────────────────
// Iterator Pattern — Book Catalog
// ─────────────────────────────────────────────────────────────

class BookCatalog implements Iterable<Book> {
    private final List<Book> books = new ArrayList<>();

    public void addBook(Book book) { books.add(book); }

    @Override
    public Iterator<Book> iterator() { return books.iterator(); }

    /** Returns a filtering iterator for genre */
    public Iterator<Book> byGenre(String genre) {
        return books.stream()
                .filter(b -> b.getGenre().equalsIgnoreCase(genre))
                .iterator();
    }

    /** Returns a filtering iterator for author */
    public Iterator<Book> byAuthor(String author) {
        return books.stream()
                .filter(b -> b.getAuthor().equalsIgnoreCase(author))
                .iterator();
    }

    /** Search by title (case-insensitive contains) */
    public Iterator<Book> byTitleContaining(String keyword) {
        return books.stream()
                .filter(b -> b.getTitle().toLowerCase().contains(keyword.toLowerCase()))
                .iterator();
    }
}

// ─────────────────────────────────────────────────────────────
// Library — Orchestrator
// ─────────────────────────────────────────────────────────────

class Library {
    private final String name;
    private final BookCatalog catalog = new BookCatalog();
    private final Map<String, BookCopy> copies  = new HashMap<>();  // copyId → BookCopy
    private final Map<String, Member>   members = new HashMap<>();  // memberId → Member
    private final Map<String, Queue<String>> reservations = new HashMap<>(); // isbn → queue of memberIds
    private final Map<String, Loan>     activeLoans = new HashMap<>();  // copyId → Loan
    private final List<LibraryObserver> observers = new ArrayList<>();

    private int copyCounter = 1;

    public Library(String name) { this.name = name; }

    // ── Observer management ──
    public void addObserver(LibraryObserver observer) { observers.add(observer); }

    private void notify(NotificationType type, Member member, Book book, String message) {
        observers.forEach(o -> o.onNotification(type, member, book, message));
    }

    // ── Registration ──
    public void registerMember(Member member) {
        members.put(member.getMemberId(), member);
        System.out.println("[Library] Registered member: " + member.getName());
    }

    public void addBookWithCopies(Book book, int numCopies) {
        catalog.addBook(book);
        for (int i = 0; i < numCopies; i++) {
            String copyId = book.getIsbn() + "-C" + (copyCounter++);
            copies.put(copyId, new BookCopy(copyId, book.getIsbn()));
        }
        System.out.println("[Library] Added book: " + book.getTitle() + " (" + numCopies + " copies)");
    }

    // ── Search (uses Iterator) ──
    public List<Book> searchByTitle(String keyword) {
        List<Book> results = new ArrayList<>();
        catalog.byTitleContaining(keyword).forEachRemaining(results::add);
        return results;
    }

    public List<Book> searchByGenre(String genre) {
        List<Book> results = new ArrayList<>();
        catalog.byGenre(genre).forEachRemaining(results::add);
        return results;
    }

    public List<Book> searchByAuthor(String author) {
        List<Book> results = new ArrayList<>();
        catalog.byAuthor(author).forEachRemaining(results::add);
        return results;
    }

    // ── Find available copy ──
    private Optional<BookCopy> findAvailableCopy(String isbn) {
        return copies.values().stream()
                .filter(c -> c.getIsbn().equals(isbn) && c.isAvailable())
                .findFirst();
    }

    // ── Borrow ──
    public boolean borrowBook(String memberId, String isbn) {
        Member member = members.get(memberId);
        if (member == null) { System.out.println("[ERROR] Member not found: " + memberId); return false; }
        if (!member.canBorrow()) {
            System.out.println("[BORROW] " + member.getName() + " cannot borrow (limit or suspended)");
            return false;
        }

        Optional<BookCopy> copy = findAvailableCopy(isbn);
        if (copy.isEmpty()) {
            System.out.println("[BORROW] No available copy for ISBN " + isbn + ". Consider reserving.");
            return false;
        }

        BookCopy bc = copy.get();
        bc.setStatus(BookStatus.BORROWED);

        Loan loan = new Loan(memberId, bc.getCopyId(), isbn);
        member.addLoan(loan);
        activeLoans.put(bc.getCopyId(), loan);

        // Find book for notification
        Book book = getBookByIsbn(isbn);
        System.out.printf("[BORROW] %s borrowed '%s' — due %s%n",
                member.getName(), book != null ? book.getTitle() : isbn, loan.getDueDate());
        if (book != null) notify(NotificationType.BORROW_CONFIRMED, member, book,
                "Borrowed '" + book.getTitle() + "', due " + loan.getDueDate());
        return true;
    }

    // ── Return ──
    public boolean returnBook(String memberId, String isbn) {
        Member member = members.get(memberId);
        if (member == null) return false;

        // Find active loan for this member and ISBN
        Optional<Loan> loanOpt = member.getActiveLoans().stream()
                .filter(l -> l.getIsbn().equals(isbn))
                .findFirst();

        if (loanOpt.isEmpty()) {
            System.out.println("[RETURN] No active loan found for " + memberId + " / " + isbn);
            return false;
        }

        Loan loan = loanOpt.get();
        loan.returnBook();

        BookCopy bc = copies.get(loan.getCopyId());
        activeLoans.remove(bc.getCopyId());
        Book book = getBookByIsbn(isbn);

        // Check if anyone has reserved this book
        Queue<String> waitlist = reservations.getOrDefault(isbn, new LinkedList<>());
        if (!waitlist.isEmpty()) {
            String nextMemberId = waitlist.poll();
            bc.setStatus(BookStatus.RESERVED);
            Member waitingMember = members.get(nextMemberId);
            if (waitingMember != null && book != null) {
                System.out.printf("[RETURN] '%s' returned by %s — notifying %s (reserved)%n",
                        book.getTitle(), member.getName(), waitingMember.getName());
                notify(NotificationType.RESERVATION_AVAILABLE, waitingMember, book,
                        "'" + book.getTitle() + "' is now available for pickup!");
            }
        } else {
            bc.setStatus(BookStatus.AVAILABLE);
            System.out.printf("[RETURN] '%s' returned by %s — now available%n",
                    book != null ? book.getTitle() : isbn, member.getName());
        }

        if (book != null) notify(NotificationType.RETURN_CONFIRMED, member, book,
                "Returned '" + book.getTitle() + "'");
        return true;
    }

    // ── Reserve ──
    public void reserveBook(String memberId, String isbn) {
        Member member = members.get(memberId);
        if (member == null) return;

        reservations.computeIfAbsent(isbn, k -> new LinkedList<>()).add(memberId);
        Book book = getBookByIsbn(isbn);
        System.out.printf("[RESERVE] %s reserved '%s' (queue position: %d)%n",
                member.getName(), book != null ? book.getTitle() : isbn,
                reservations.get(isbn).size());
    }

    // ── Helpers ──
    private Book getBookByIsbn(String isbn) {
        for (Iterator<Book> it = catalog.iterator(); it.hasNext(); ) {
            Book b = it.next();
            if (b.getIsbn().equals(isbn)) return b;
        }
        return null;
    }

    public void displayInventory() {
        System.out.println("\n[Library: " + name + "] Inventory:");
        for (Book book : catalog) {
            long available = copies.values().stream()
                    .filter(c -> c.getIsbn().equals(book.getIsbn()) && c.isAvailable())
                    .count();
            long total = copies.values().stream()
                    .filter(c -> c.getIsbn().equals(book.getIsbn()))
                    .count();
            System.out.printf("  %-40s | %s | %d/%d available%n",
                    book.getTitle(), book.getAuthor(), available, total);
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Demo
// ─────────────────────────────────────────────────────────────

class LibraryManagementDemo {

    public static void main(String[] args) {
        Library library = new Library("City Public Library");

        // Observers
        AuditLogObserver auditLog = new AuditLogObserver();
        library.addObserver(new EmailNotificationObserver());
        library.addObserver(auditLog);

        // Add books using Builder
        Book effectiveJava = new Book.Builder("978-0134685991", "Effective Java")
                .author("Joshua Bloch")
                .publicationYear(2018)
                .genre("Programming")
                .copies(3)
                .publisher("Addison-Wesley")
                .build();

        Book cleanCode = new Book.Builder("978-0132350884", "Clean Code")
                .author("Robert C. Martin")
                .publicationYear(2008)
                .genre("Programming")
                .copies(2)
                .build();

        Book designPatterns = new Book.Builder("978-0201633610", "Design Patterns")
                .author("Gang of Four")
                .publicationYear(1994)
                .genre("Programming")
                .copies(1)
                .build();

        Book dune = new Book.Builder("978-0441013593", "Dune")
                .author("Frank Herbert")
                .publicationYear(1965)
                .genre("Science Fiction")
                .copies(2)
                .build();

        library.addBookWithCopies(effectiveJava, 3);
        library.addBookWithCopies(cleanCode, 2);
        library.addBookWithCopies(designPatterns, 1);
        library.addBookWithCopies(dune, 2);

        // Register members
        Member alice = new Member("M001", "Alice", "alice@example.com");
        Member bob   = new Member("M002", "Bob",   "bob@example.com");
        Member carol = new Member("M003", "Carol", "carol@example.com");
        library.registerMember(alice);
        library.registerMember(bob);
        library.registerMember(carol);

        library.displayInventory();

        System.out.println("\n=== Search Demo (Iterator Pattern) ===");
        System.out.println("Search by genre 'Programming':");
        library.searchByGenre("Programming").forEach(b -> System.out.println("  " + b.getTitle()));

        System.out.println("Search by author 'Robert C. Martin':");
        library.searchByAuthor("Robert C. Martin").forEach(b -> System.out.println("  " + b.getTitle()));

        System.out.println("Search by title containing 'Java':");
        library.searchByTitle("Java").forEach(b -> System.out.println("  " + b.getTitle()));

        System.out.println("\n=== Borrow/Return Demo ===");
        library.borrowBook("M001", "978-0201633610"); // Alice borrows Design Patterns
        library.borrowBook("M002", "978-0201633610"); // Bob tries — no copies left
        library.reserveBook("M002", "978-0201633610"); // Bob reserves

        library.borrowBook("M001", "978-0134685991"); // Alice borrows Effective Java
        library.borrowBook("M003", "978-0134685991"); // Carol borrows Effective Java

        System.out.println("\n=== Return triggers reservation notification ===");
        library.returnBook("M001", "978-0201633610"); // Alice returns — Bob gets notified

        library.displayInventory();
        auditLog.printLog();
    }
}
