package com.rutik.systemdesign.lld.creational.prototype;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PROTOTYPE PATTERN — Real World Example
 *
 * Scenario: Graphic Editor — Shape Cloning System
 *
 * Problem:
 *   A graphic editor lets users duplicate shapes (rectangles, circles, compound
 *   groups). Each shape carries a significant amount of state: position, size,
 *   color, stroke, applied effects, and child shapes in the case of groups.
 *   Creating a fresh shape and reconfiguring all its properties is tedious and
 *   couples client code to every concrete shape class. Copy-paste in the editor
 *   must produce independent copies — editing a pasted shape must not alter the
 *   original.
 *
 * Solution:
 *   Each shape implements a Cloneable Shape interface. Composite shapes (groups)
 *   perform deep clones so that nested children are fully independent. The editor
 *   calls shape.clone() without knowing whether it is dealing with a Rectangle,
 *   Circle, or Group. A ShapeRegistry caches template shapes (e.g., "company logo")
 *   that users can stamp out repeatedly.
 *
 * Run: javac RealWorldExample.java && java GraphicEditorDemo
 */

// ─────────────────────────────────────────────────────────────────────────────
// Prototype interface
// ─────────────────────────────────────────────────────────────────────────────
interface Shape {
    /** Returns a deep, independent copy of this shape. */
    Shape clone();

    void move(int dx, int dy);
    void draw();
    String getType();
    String getId();
}

// ─────────────────────────────────────────────────────────────────────────────
// Value objects
// ─────────────────────────────────────────────────────────────────────────────
class Color {

    final int r, g, b;

    Color(int r, int g, int b) {
        this.r = r; this.g = g; this.b = b;
    }

    // Copy constructor
    Color(Color other) {
        this.r = other.r; this.g = other.g; this.b = other.b;
    }

    @Override
    public String toString() {
        return String.format("rgb(%d,%d,%d)", r, g, b);
    }
}

class Point {

    int x, y;

    Point(int x, int y) {
        this.x = x; this.y = y;
    }

    // Copy constructor
    Point(Point other) {
        this.x = other.x; this.y = other.y;
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Abstract base shape — holds common state and the clone counter
// ─────────────────────────────────────────────────────────────────────────────
abstract class BaseShape implements Shape {

    private static int idSequence = 1;

    protected final String id;
    protected Point position;
    protected Color fillColor;
    protected Color strokeColor;
    protected int strokeWidth;

    protected BaseShape(int x, int y, Color fill, Color stroke, int strokeWidth) {
        this.id          = getType() + "#" + idSequence++;
        this.position    = new Point(x, y);
        this.fillColor   = new Color(fill);
        this.strokeColor = new Color(stroke);
        this.strokeWidth = strokeWidth;
    }

    /** Copy constructor — used by subclass clone() implementations. */
    protected BaseShape(BaseShape other) {
        this.id          = getType() + "#" + idSequence++; // new unique ID
        this.position    = new Point(other.position);
        this.fillColor   = new Color(other.fillColor);
        this.strokeColor = new Color(other.strokeColor);
        this.strokeWidth = other.strokeWidth;
    }

    @Override
    public void move(int dx, int dy) {
        position.x += dx;
        position.y += dy;
    }

    @Override
    public String getId() {
        return id;
    }

    protected String baseState() {
        return "id=" + id
                + ", pos=" + position
                + ", fill=" + fillColor
                + ", stroke=" + strokeColor + "(w=" + strokeWidth + ")";
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Concrete Prototype: Rectangle
// ─────────────────────────────────────────────────────────────────────────────
class Rectangle extends BaseShape {

    private int width;
    private int height;
    private int cornerRadius;

    public Rectangle(int x, int y, int width, int height,
                     Color fill, Color stroke, int strokeWidth, int cornerRadius) {
        super(x, y, fill, stroke, strokeWidth);
        this.width        = width;
        this.height       = height;
        this.cornerRadius = cornerRadius;
    }

    /** Deep copy constructor */
    private Rectangle(Rectangle other) {
        super(other); // copies position, colors
        this.width        = other.width;
        this.height       = other.height;
        this.cornerRadius = other.cornerRadius;
    }

    @Override
    public Shape clone() {
        return new Rectangle(this);
    }

    @Override
    public void draw() {
        System.out.println("  [Rectangle] " + baseState()
                + ", size=" + width + "x" + height
                + ", radius=" + cornerRadius);
    }

    @Override
    public String getType() {
        return "Rect";
    }

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Concrete Prototype: Circle
// ─────────────────────────────────────────────────────────────────────────────
class Circle extends BaseShape {

    private int radius;
    private boolean filled;

    public Circle(int x, int y, int radius, boolean filled,
                  Color fill, Color stroke, int strokeWidth) {
        super(x, y, fill, stroke, strokeWidth);
        this.radius = radius;
        this.filled = filled;
    }

    /** Deep copy constructor */
    private Circle(Circle other) {
        super(other);
        this.radius = other.radius;
        this.filled = other.filled;
    }

    @Override
    public Shape clone() {
        return new Circle(this);
    }

    @Override
    public void draw() {
        System.out.println("  [Circle]    " + baseState()
                + ", radius=" + radius
                + ", filled=" + filled);
    }

    @Override
    public String getType() {
        return "Circle";
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Concrete Prototype: ShapeGroup (Composite)
// Deep clone must recursively clone all children.
// ─────────────────────────────────────────────────────────────────────────────
class ShapeGroup extends BaseShape {

    private final String groupName;
    private final List<Shape> children;

    public ShapeGroup(int x, int y, String groupName) {
        super(x, y,
              new Color(0, 0, 0),   // transparent fill (not used visually)
              new Color(0, 0, 255), // blue selection border
              1);
        this.groupName = groupName;
        this.children  = new ArrayList<>();
    }

    /** Deep copy constructor — clones each child recursively. */
    private ShapeGroup(ShapeGroup other) {
        super(other);
        this.groupName = other.groupName + " (copy)";
        this.children  = new ArrayList<>();
        for (Shape child : other.children) {
            this.children.add(child.clone()); // recursive deep clone
        }
    }

    public void add(Shape shape) {
        children.add(shape);
    }

    @Override
    public Shape clone() {
        return new ShapeGroup(this);
    }

    @Override
    public void move(int dx, int dy) {
        super.move(dx, dy);
        // Move all children with the group
        for (Shape child : children) {
            child.move(dx, dy);
        }
    }

    @Override
    public void draw() {
        System.out.println("  [Group]     name='" + groupName + "', "
                + "pos=" + position + ", children=" + children.size());
        for (Shape child : children) {
            System.out.print("    ");
            child.draw();
        }
    }

    @Override
    public String getType() {
        return "Group";
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shape Registry — caches named template shapes; clients clone from it
// ─────────────────────────────────────────────────────────────────────────────
class ShapeRegistry {

    private final Map<String, Shape> templates = new HashMap<>();

    public void register(String name, Shape shape) {
        templates.put(name, shape);
    }

    /**
     * Returns a cloned copy of the named template.
     * The client never sees the concrete type — just a Shape.
     */
    public Shape stamp(String name) {
        Shape template = templates.get(name);
        if (template == null) {
            throw new IllegalArgumentException("No template registered for: " + name);
        }
        return template.clone();
    }

    public List<String> listTemplates() {
        return new ArrayList<>(templates.keySet());
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Graphic Editor — manages the canvas
// ─────────────────────────────────────────────────────────────────────────────
class GraphicEditor {

    private final List<Shape> canvas = new ArrayList<>();
    private final ShapeRegistry registry = new ShapeRegistry();

    public GraphicEditor() {
        // Pre-register reusable template shapes
        Color red    = new Color(220,  50,  50);
        Color blue   = new Color( 30, 120, 220);
        Color green  = new Color( 50, 180,  50);
        Color black  = new Color(  0,   0,   0);
        Color white  = new Color(255, 255, 255);

        registry.register("button",
                new Rectangle(0, 0, 120, 40, blue, black, 2, 8));

        registry.register("icon-circle",
                new Circle(0, 0, 20, true, red, black, 1));

        // Company logo: a group with a rectangle + circle
        ShapeGroup logo = new ShapeGroup(0, 0, "CompanyLogo");
        logo.add(new Rectangle(0, 0, 80, 30, white, blue, 3, 4));
        logo.add(new Circle(15, 15, 10, true, blue, white, 2));
        registry.register("company-logo", logo);
    }

    public void addShape(Shape shape) {
        canvas.add(shape);
    }

    public Shape stampFromTemplate(String name, int x, int y) {
        Shape clone = registry.stamp(name);
        clone.move(x, y);
        canvas.add(clone);
        return clone;
    }

    public void drawAll() {
        System.out.println("  Canvas (" + canvas.size() + " shapes):");
        for (Shape s : canvas) {
            s.draw();
        }
    }

    public List<Shape> getCanvas() {
        return canvas;
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Demo Entry Point
// ─────────────────────────────────────────────────────────────────────────────
public class RealWorldExample {

    public static void main(String[] args) {
        System.out.println("=== Prototype Pattern: Graphic Editor Shape Cloning Demo ===\n");

        GraphicEditor editor = new GraphicEditor();

        // ── 1. Clone individual shapes ─────────────────────────────────────────
        System.out.println("--- 1. Cloning individual shapes ---\n");

        Color navy  = new Color(0, 0, 128);
        Color white = new Color(255, 255, 255);
        Color black = new Color(0, 0, 0);

        Rectangle original = new Rectangle(10, 10, 200, 100, navy, black, 2, 5);
        System.out.println("Original rectangle:");
        original.draw();

        Rectangle cloned = (Rectangle) original.clone();
        cloned.move(50, 50);
        cloned.resize(150, 80);
        System.out.println("Cloned + moved + resized:");
        cloned.draw();

        System.out.println("Original unchanged:");
        original.draw();

        // ── 2. Deep clone of a ShapeGroup ─────────────────────────────────────
        System.out.println("\n--- 2. Deep cloning a ShapeGroup ---\n");

        ShapeGroup group = new ShapeGroup(0, 0, "MyGroup");
        group.add(new Circle(10, 10, 30, true, new Color(255, 100, 0), black, 1));
        group.add(new Rectangle(50, 50, 60, 40, new Color(0, 200, 100), black, 1, 3));

        System.out.println("Original group:");
        group.draw();

        ShapeGroup groupClone = (ShapeGroup) group.clone();
        groupClone.move(100, 100); // moves clone AND its children — original unchanged

        System.out.println("\nClone after moving 100,100:");
        groupClone.draw();

        System.out.println("\nOriginal group (unchanged):");
        group.draw();

        // ── 3. ShapeRegistry (stamp pattern) ──────────────────────────────────
        System.out.println("\n--- 3. Stamping shapes from registry templates ---\n");

        // Stamp multiple buttons at different positions
        Shape btn1 = editor.stampFromTemplate("button", 10, 50);
        Shape btn2 = editor.stampFromTemplate("button", 10, 100);
        Shape btn3 = editor.stampFromTemplate("button", 10, 150);

        // Stamp the logo several times
        Shape logo1 = editor.stampFromTemplate("company-logo", 300, 20);
        Shape logo2 = editor.stampFromTemplate("company-logo", 300, 120);

        System.out.println("Canvas after stamping 3 buttons + 2 logos:");
        editor.drawAll();

        // ── 4. Verify independence of stamps ──────────────────────────────────
        System.out.println("\n--- 4. Verifying stamps are independent instances ---");
        System.out.println("btn1 != btn2: " + (btn1 != btn2));
        System.out.println("btn1.id=" + btn1.getId() + ", btn2.id=" + btn2.getId());
        System.out.println("logo1 != logo2: " + (logo1 != logo2));
        System.out.println("logo1.id=" + logo1.getId() + ", logo2.id=" + logo2.getId());

        System.out.println("\n=== Demo complete ===");
    }
}
