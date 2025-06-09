package WampusWorld;
import jade.core.AID;
import static WampusWorld.WampusWorldCore.Direction;

// клас, що описує поточний стан спелеолога у світі Вампуса
// містить координати, напрямок, наявність стріли та золота та ідентифікатори інших агентів для комунікації
public class AgentState {
    private int x;
    private int y;
    private Direction direction;
    private boolean hasArrow;
    private boolean hasGold;
    private AID environmentAID; // ідентифікатор агента-середовища
    private AID navigatorAID;   // ідентифікатор агента-навігатора

    // конструктор для ініціалізації стану спелеолога.
    // @param startX початкова X-координата.
    // @param startY початкова Y-координата.
    // @param startDirection початковий напрямок.
    public AgentState(int startX, int startY, Direction startDirection) {
        this.x = startX;
        this.y = startY;
        this.direction = startDirection;
        this.hasArrow = true; // спелеолог починає з однією стрілою
        this.hasGold = false; // поки не має золота
    }

    // методи отримання (геттери)
    public int getX() { return x; }
    public int getY() { return y; }
    public Direction getDirection() { return direction; }
    public boolean hasArrow() { return hasArrow; }
    public boolean hasGold() { return hasGold; }
    public AID getEnvironmentAID() { return environmentAID; }
    public AID getNavigatorAID() { return navigatorAID; }

    // методи встановлення (сеттери)
    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
    public void setDirection(Direction direction) { this.direction = direction; }
    public void setHasArrow(boolean hasArrow) { this.hasArrow = hasArrow; }
    public void setHasGold(boolean hasGold) { this.hasGold = hasGold; }
    public void setEnvironmentAID(AID environmentAID) { this.environmentAID = environmentAID; }
    public void setNavigatorAID(AID navigatorAID) { this.navigatorAID = navigatorAID; }

    // переміщення спелеолога вперед відповідно до поточного напрямку.
    // @param maxX максимальна X-координата (розмір сітки).
    // @param maxY максимальна Y-координата (розмір сітки).
    // @return true, якщо переміщення успішне (не вийшов за межі), false - якщо зіткнувся зі стіною.
    public boolean moveForward(int maxX, int maxY) {
        int newX = x;
        int newY = y;
        switch (direction) {
            case NORTH: newY--; break;
            case EAST: newX++; break;
            case SOUTH: newY++; break;
            case WEST: newX--; break;
        }

        if (newX >= 0 && newX < maxX && newY >= 0 && newY < maxY) {
            this.x = newX;
            this.y = newY;
            return true;
        }
        return false; // зіткнувся зі стіною
    }

    // повертає спелеолога ліворуч.
    public void turnLeft() {
        switch (direction) {
            case NORTH: direction = Direction.WEST; break;
            case EAST: direction = Direction.NORTH; break;
            case SOUTH: direction = Direction.EAST; break;
            case WEST: direction = Direction.SOUTH; break;
        }
    }

    // повертає спелеолога праворуч.
    public void turnRight() {
        switch (direction) {
            case NORTH: direction = Direction.EAST; break;
            case EAST: direction = Direction.SOUTH; break;
            case SOUTH: direction = Direction.WEST; break;
            case WEST: direction = Direction.NORTH; break;
        }
    }

    // використання стріли.
    // @return true, якщо стріла була використана, false - якщо стріл немає.
    public boolean useArrow() {
        if (hasArrow) {
            hasArrow = false;
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "Стан спелеолога: [" +
                "x=" + x +
                ", y=" + y +
                ", напрямок=" + direction +
                ", має стрілу=" + hasArrow +
                ", має золото=" + hasGold +
                ']';
    }
}
