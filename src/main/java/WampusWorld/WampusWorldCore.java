package WampusWorld;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

// контейнер для всіх допоміжних класів та переліків світу Вампуса
public class WampusWorldCore {

    // перелік можливих напрямків руху або орієнтації спелеолога.
    public enum Direction {
        NORTH, // північ
        EAST,  // схід
        SOUTH, // південь
        WEST   // захід
    }

    // перелік можливих сприйнять, які агент-спелеолог може отримати від середовища.
    public enum PerceptionType {
        STENCH,  // запах Вампуса
        BREEZE,  // вітер від ями
        GLITTER, // блиск золота (якщо спелеолог у кімнаті з золотом)
        BUMP,    // зіткнення зі стіною
        SCREAM,  // крик Вампуса після пострілу
        SAFE     // безпечна кімната (немає запаху, вітру, Вампуса, ями)
    }

    // перелік можливих дій, які спелеолог може виконати.
    public enum Action {
        TURN_LEFT,  // повернути ліворуч
        TURN_RIGHT, // повернути праворуч
        FORWARD,    // рухатися вперед
        SHOOT,      // стріляти (використовує стрілу)
        GRAB,       // схопити (золото)
        CLIMB       // піднятися (завершити гру)
    }

    // клас, що представляє золотий злиток у світі Вампуса.
    public static class Gold {
        @Override
        public String toString() {
            return "Золото";
        }
    }

    // клас, що представляє яму у світі Вампуса.
    public static class Pit {
        @Override
        public String toString() {
            return "Яма";
        }
    }

    // клас, що представляє Вампуса у світі Вампуса.
    public static class Wampus {
        @Override
        public String toString() {
            return "Вампус";
        }
    }

    // клас, що представляє одну кімнату (клітинку) у лабіринті.
    public static class Room {
        private boolean hasPit;
        private boolean hasGold;
        private boolean hasWampus;
        private boolean hasStench;
        private boolean hasBreeze;
        private boolean isExplored;

        public Room() {
            this.hasPit = false;
            this.hasGold = false;
            this.hasWampus = false;
            this.hasStench = false;
            this.hasBreeze = false;
            this.isExplored = false;
        }

        public boolean hasPit() { return hasPit; }
        public boolean hasGold() { return hasGold; }
        public boolean hasWampus() { return hasWampus; }
        public boolean hasStench() { return hasStench; }
        public boolean hasBreeze() { return hasBreeze; }
        public boolean isExplored() { return isExplored; }

        public void setHasPit(boolean hasPit) { this.hasPit = hasPit; }
        public void setHasGold(boolean hasGold) { this.hasGold = hasGold; }
        public void setHasWampus(boolean hasWampus) { this.hasWampus = hasWampus; }
        public void setHasStench(boolean hasStench) { this.hasStench = hasStench; }
        public void setHasBreeze(boolean hasBreeze) { this.hasBreeze = hasBreeze; }
        public void setExplored(boolean explored) { isExplored = explored; }

        public Set<PerceptionType> getPerceptions() {
            Set<PerceptionType> perceptions = new HashSet<>();
            if (hasStench) perceptions.add(PerceptionType.STENCH);
            if (hasBreeze) perceptions.add(PerceptionType.BREEZE);
            if (hasGold) perceptions.add(PerceptionType.GLITTER);
            return perceptions;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("Кімната [");
            if (hasPit) sb.append("Яма, ");
            if (hasGold) sb.append("Золото, ");
            if (hasWampus) sb.append("Вампус, ");
            if (hasStench) sb.append("Запах, ");
            if (hasBreeze) sb.append("Вітер, ");
            if (isExplored) sb.append("Досліджено, ");

            if (sb.length() > "Кімната [".length()) {
                sb.setLength(sb.length() - 2);
            }
            sb.append("]");
            return sb.toString();
        }
    }

    // клас, що представляє лабіринт світу Вампуса.
    public static class WampusWorld {
        private Room[][] grid;
        private int size;
        private boolean wampusAlive;

        public WampusWorld(int size) {
            this.size = size;
            this.grid = new Room[size][size];
            this.wampusAlive = true;
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    grid[i][j] = new Room();
                }
            }
            placeObjects();
        }

        public Room getRoom(int x, int y) {
            if (x >= 0 && x < size && y >= 0 && y < size) {
                return grid[x][y];
            }
            return null;
        }

        public int getSize() {
            return size;
        }

        private void placeObjects() {
            Random rand = new Random();
            List<Integer> availableCells = new ArrayList<>();
            for (int i = 0; i < size * size; i++) {
                if (i != 0) { // кімната (0,0) завжди вільна
                    availableCells.add(i);
                }
            }
            Collections.shuffle(availableCells);

            // розміщення Вампуса (1 екземпляр)
            int wampusPos = availableCells.remove(0);
            int wampusX = wampusPos % size;
            int wampusY = wampusPos / size;
            grid[wampusX][wampusY].setHasWampus(true);
            System.out.println("Вампус розміщено у (" + wampusX + ", " + wampusY + ")");

            // розміщення золота (1 екземпляр)
            int goldPos = availableCells.remove(0);
            int goldX = goldPos % size;
            int goldY = goldPos / size;
            grid[goldX][goldY].setHasGold(true);
            System.out.println("Золото розміщено у (" + goldX + ", " + goldY + ")");

            // розміщення ям (2-3 ями для 4x4)
            int numPits = 2 + rand.nextInt(2);
            for (int i = 0; i < numPits; i++) {
                if (availableCells.isEmpty()) break;
                int pitPos = availableCells.remove(0);
                int pitX = pitPos % size;
                int pitY = pitPos / size;
                grid[pitX][pitY].setHasPit(true);
                System.out.println("Яма розміщена у (" + pitX + ", " + pitY + ")");
            }

            updatePerceptions();
        }

        public void updatePerceptions() {
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    grid[i][j].setHasStench(false);
                    grid[i][j].setHasBreeze(false);
                }
            }

            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    if (grid[i][j].hasWampus() && wampusAlive) {
                        setStenchAround(i, j);
                    }
                    if (grid[i][j].hasPit()) {
                        setBreezeAround(i, j);
                    }
                }
            }
        }

        private void setStenchAround(int x, int y) {
            int[] dx = {0, 0, 1, -1};
            int[] dy = {1, -1, 0, 0};
            for (int i = 0; i < 4; i++) {
                int nx = x + dx[i];
                int ny = y + dy[i];
                if (nx >= 0 && nx < size && ny >= 0 && ny < size) {
                    grid[nx][ny].setHasStench(true);
                }
            }
        }

        private void setBreezeAround(int x, int y) {
            int[] dx = {0, 0, 1, -1};
            int[] dy = {1, -1, 0, 0};
            for (int i = 0; i < 4; i++) {
                int nx = x + dx[i];
                int ny = y + dy[i];
                if (nx >= 0 && nx < size && ny >= 0 && ny < size) {
                    grid[nx][ny].setHasBreeze(true);
                }
            }
        }

        public boolean isWampusAlive() {
            return wampusAlive;
        }

        public void setWampusAlive(boolean wampusAlive) {
            this.wampusAlive = wampusAlive;
            updatePerceptions();
        }

        // Тепер приймаємо окремі параметри замість об'єкта AgentState,
        // оскільки AgentState тепер є окремим класом.
        public void printGrid(int agentX, int agentY, Direction agentDirection, boolean agentHasArrow, boolean agentHasGold) {
            System.out.println("Поточний стан світу Вампуса:");
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    System.out.print("|");
                    if (agentX == j && agentY == i) {
                        System.out.print("A");
                    } else {
                        if (grid[j][i].hasGold()) System.out.print("G");
                        if (grid[j][i].hasPit()) System.out.print("P");
                        if (grid[j][i].hasWampus() && wampusAlive) System.out.print("W");
                        if (grid[j][i].hasStench()) System.out.print("S");
                        if (grid[j][i].hasBreeze()) System.out.print("B");
                        System.out.print(" ");
                    }
                }
                System.out.println("|");
            }
            System.out.println("Стан спелеолога: [x=" + agentX + ", y=" + agentY + ", напрямок=" + agentDirection +
                    ", має стрілу=" + agentHasArrow + ", має золото=" + agentHasGold + ']');
        }
    }

    // клас, що представляє внутрішнє знання навігатора про конкретну клітинку світу.
    public static class NavigatorCellInfo {
        public boolean visited;
        public boolean safe;
        public boolean wampusSuspect;
        public boolean pitSuspect;
        public boolean isWampusConfirmed;
        public boolean isPitConfirmed;
        public boolean isGoldConfirmed;

        public boolean perceivedGlitter;
        public boolean perceivedStench;
        public boolean perceivedBreeze;

        public NavigatorCellInfo() {
            visited = false;
            safe = false;
            wampusSuspect = false;
            pitSuspect = false;
            isWampusConfirmed = false;
            isPitConfirmed = false;
            isGoldConfirmed = false;
            perceivedGlitter = false;
            perceivedStench = false;
            perceivedBreeze = false;
        }

        public void setSafe() {
            safe = true;
            wampusSuspect = false;
            pitSuspect = false;
        }

        public void setWampusSuspect() {
            if (!safe) {
                wampusSuspect = true;
            }
        }

        public void setPitSuspect() {
            if (!safe) {
                pitSuspect = true;
            }
        }

        public boolean isVisited() { return visited; }
        public boolean isSafe() { return safe; }
        public boolean isWampusSuspect() { return wampusSuspect; }
        public boolean isPitSuspect() { return pitSuspect; }
        public boolean isWampusConfirmed() { return isWampusConfirmed; }
        public boolean isPitConfirmed() { return isPitConfirmed; }
        public boolean isGoldConfirmed() { return isGoldConfirmed; }
        public boolean isPerceivedGlitter() { return perceivedGlitter; }
        public boolean isPerceivedStench() { return perceivedStench; }
        public boolean isPerceivedBreeze() { return perceivedBreeze; }

        public void setVisited(boolean visited) { this.visited = visited; }
        public void setIsWampusConfirmed(boolean isWampusConfirmed) { this.isWampusConfirmed = isWampusConfirmed; }
        public void setIsPitConfirmed(boolean isPitConfirmed) { this.isPitConfirmed = isPitConfirmed; }
        public void setIsGoldConfirmed(boolean isGoldConfirmed) { this.isGoldConfirmed = isGoldConfirmed; }
        public void setPerceivedGlitter(boolean perceivedGlitter) { this.perceivedGlitter = perceivedGlitter; }
        public void setPerceivedStench(boolean perceivedStench) { this.perceivedStench = perceivedStench; }
        public void setPerceivedBreeze(boolean perceivedBreeze) { this.perceivedBreeze = perceivedBreeze; }
    }
}
