package WampusWorld;
import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static WampusWorld.WampusWorldCore.Direction;
import static WampusWorld.WampusWorldCore.PerceptionType;
import static WampusWorld.WampusWorldCore.Action;
import static WampusWorld.WampusWorldCore.NavigatorCellInfo;


// агент-навігатор, відповідає за обробку сприйнять спелеолога,
// побудову внутрішньої карти світу та прийняття рішень щодо наступної дії.
public class NavigatorAgent extends Agent {
    private AID explorerAID; // ідентифікатор агента-спелеолога
    private NavigatorCellInfo[][] worldMap; // внутрішнє представлення світу
    private int gridSize = 4; // розмір світу 4x4
    private int currentExplorerX;
    private int currentExplorerY;
    private Direction currentExplorerDirection;
    private boolean hasArrow;
    private boolean hasGold;
    private boolean wampusAliveInMind; // чи вважає навігатор, що вампус живий
    private boolean wampusKilledReported; // чи повідомлено про вбивство вампуса

    // для збереження попередньої дії, щоб відстежувати BUMP
    private Action lastActionAttempted = null;

    // словник для перетворення природної мови на сприйняття
    private static final Map<String, PerceptionType> PERCEPTION_KEYWORDS = new HashMap<>();
    static {
        PERCEPTION_KEYWORDS.put("smells awful", PerceptionType.STENCH);
        PERCEPTION_KEYWORDS.put("feel breeze", PerceptionType.BREEZE);
        PERCEPTION_KEYWORDS.put("see glitter", PerceptionType.GLITTER);
        PERCEPTION_KEYWORDS.put("hit a wall", PerceptionType.BUMP);
        PERCEPTION_KEYWORDS.put("scream", PerceptionType.SCREAM);
        PERCEPTION_KEYWORDS.put("safe", PerceptionType.SAFE);
    }

    @Override
    protected void setup() {
        System.out.println("Агент-навігатор " + getAID().getName() + " запущений.");

        // ініціалізація внутрішньої карти світу
        worldMap = new NavigatorCellInfo[gridSize][gridSize];
        for (int i = 0; i < gridSize; i++) {
            for (int j = 0; j < gridSize; j++) {
                worldMap[i][j] = new NavigatorCellInfo();
            }
        }

        // початковий стан спелеолога
        currentExplorerX = 0;
        currentExplorerY = 0;
        currentExplorerDirection = Direction.EAST;
        hasArrow = true;
        hasGold = false;
        wampusAliveInMind = true;
        wampusKilledReported = false;

        // початкова клітинка (0,0) завжди безпечна і відвідана
        worldMap[0][0].setVisited(true);
        worldMap[0][0].setSafe();

        // реєстрація послуги в DF (жовті сторінки)
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("wampus-navigation");
        sd.setName("Wampus-Navigator");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        // додавання поведінки для прийому повідомлень від спелеолога
        addBehaviour(new ReceiveExplorerMessageBehaviour());
    }

    @Override
    protected void takeDown() {
        // скасування реєстрації в DF
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println("Агент-навігатор " + getAID().getName() + " завершив роботу.");
    }

    // встановлення AID агента-спелеолога.
    // @param aid AID агента-спелеолога.
    public void setExplorerAID(AID aid) {
        this.explorerAID = aid;
    }

    // внутрішня циклічна поведінка для прийому повідомлень від агента-спелеолога.
    private class ReceiveExplorerMessageBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            // шаблон повідомлення для прийому REQUEST від будь-якого агента
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                System.out.println("Навігатор: отримано повідомлення від " + msg.getSender().getName() + ": " + msg.getContent());

                if (explorerAID == null) {
                    explorerAID = msg.getSender();
                }

                String messageContent = msg.getContent().toLowerCase();
                Set<PerceptionType> currentPerceptions = parsePerceptionMessage(messageContent);

                updateKnowledgeBase(currentPerceptions, currentExplorerX, currentExplorerY);

                // якщо була дія BUMP, потрібно відкотити позицію спелеолога і спробувати інший шлях
                if (currentPerceptions.contains(PerceptionType.BUMP) && lastActionAttempted == Action.FORWARD) {
                    // відкотити позицію спелеолога
                    switch (currentExplorerDirection) {
                        case NORTH: currentExplorerY++; break;
                        case EAST: currentExplorerX--; break;
                        case SOUTH: currentExplorerY--; break;
                        case WEST: currentExplorerX++; break;
                    }
                    System.out.println("Навігатор: Спелеолог зіткнувся зі стіною, відкотив позицію до (" + currentExplorerX + ", " + currentExplorerY + ").");
                } else {
                    if (lastActionAttempted == Action.FORWARD) {
                        // позиція спелеолога вже оновилася в `EnvironmentAgent`, тут ми її просто підтверджуємо
                    }
                }

                Action nextAction = decideNextAction();

                String replyContent = generateActionMessage(nextAction);

                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.INFORM);
                reply.setContent(replyContent);
                myAgent.send(reply);
                System.out.println("Навігатор: відправлено відповідь спелеологу: '" + replyContent + "'");

                applyActionToNavigatorState(nextAction);

                printNavigatorMap();

            } else {
                block();
            }
        }
    }

    // парсинг природно-мовного повідомлення від спелеолога у набір сприйнять.
    // @param message природно-мовне повідомлення.
    // @return набір сприйнять.
    private Set<PerceptionType> parsePerceptionMessage(String message) {
        Set<PerceptionType> perceptions = new HashSet<>();
        for (Map.Entry<String, PerceptionType> entry : PERCEPTION_KEYWORDS.entrySet()) {
            if (message.contains(entry.getKey())) {
                perceptions.add(entry.getValue());
            }
        }
        if (perceptions.isEmpty() && !message.contains("hit a wall") && !message.contains("scream")) {
            perceptions.add(PerceptionType.SAFE);
        }
        return perceptions;
    }

    // оновлення внутрішньої карти світу навігатора на основі отриманих сприйнять.
    // @param perceptions поточні сприйняття.
    // @param x поточна X-координата спелеолога.
    // @param y поточна Y-координата спелеолога.
    private void updateKnowledgeBase(Set<PerceptionType> perceptions, int x, int y) {
        worldMap[x][y].setVisited(true);
        System.out.println("Навігатор: оновлення знань для (" + x + ", " + y + ") зі сприйняттями: " + perceptions);

        worldMap[x][y].setPerceivedGlitter(perceptions.contains(PerceptionType.GLITTER));
        worldMap[x][y].setPerceivedStench(perceptions.contains(PerceptionType.STENCH));
        worldMap[x][y].setPerceivedBreeze(perceptions.contains(PerceptionType.BREEZE));

        if (perceptions.contains(PerceptionType.GLITTER)) {
            hasGold = true;
            worldMap[x][y].setIsGoldConfirmed(true);
        }
        if (perceptions.contains(PerceptionType.SCREAM)) {
            wampusAliveInMind = false;
            wampusKilledReported = true;
            System.out.println("Навігатор: Вампуса вбито!");
            for (int i = 0; i < gridSize; i++) {
                for (int j = 0; j < gridSize; j++) {
                    worldMap[i][j].setWampusSuspect();
                    worldMap[i][j].setIsWampusConfirmed(false);
                }
            }
        }

        if (!perceptions.contains(PerceptionType.STENCH) && !perceptions.contains(PerceptionType.BREEZE)) {
            worldMap[x][y].setSafe();
            markAdjacentCellsAsSafe(x, y);
        } else {
            if (perceptions.contains(PerceptionType.STENCH) && wampusAliveInMind) {
                markAdjacentCellsAsWampusSuspect(x, y);
            }
            if (perceptions.contains(PerceptionType.BREEZE)) {
                markAdjacentCellsAsPitSuspect(x, y);
            }
        }

        inferTrueLocations();
    }

    // позначає сусідні клітинки як безпечні, якщо вони ще не відвідані.
    // @param x X-координата.
    // @param y Y-координата.
    private void markAdjacentCellsAsSafe(int x, int y) {
        int[] dx = {0, 0, 1, -1};
        int[] dy = {1, -1, 0, 0};
        for (int i = 0; i < 4; i++) {
            int nx = x + dx[i];
            int ny = y + dy[i];
            if (isValid(nx, ny) && !worldMap[nx][ny].isVisited() && !worldMap[nx][ny].isSafe()) {
                worldMap[nx][ny].setSafe();
                System.out.println("Навігатор: Клітинка (" + nx + ", " + ny + ") позначена як безпечна.");
            }
        }
    }

    // позначає сусідні клітинки як потенційно містять Вампуса.
    // @param x X-координата.
    // @param y Y-координата.
    private void markAdjacentCellsAsWampusSuspect(int x, int y) {
        int[] dx = {0, 0, 1, -1};
        int[] dy = {1, -1, 0, 0};
        for (int i = 0; i < 4; i++) {
            int nx = x + dx[i];
            int ny = y + dy[i];
            if (isValid(nx, ny) && !worldMap[nx][ny].isVisited() && !worldMap[nx][ny].isSafe()) {
                worldMap[nx][ny].setWampusSuspect();
            }
        }
    }

    // позначає сусідні клітинки як потенційно містять Яму.
    // @param x X-координата.
    // @param y Y-координата.
    private void markAdjacentCellsAsPitSuspect(int x, int y) {
        int[] dx = {0, 0, 1, -1};
        int[] dy = {1, -1, 0, 0};
        for (int i = 0; i < 4; i++) {
            int nx = x + dx[i];
            int ny = y + dy[i];
            if (isValid(nx, ny) && !worldMap[nx][ny].isVisited() && !worldMap[nx][ny].isSafe()) {
                worldMap[nx][ny].setPitSuspect();
            }
        }
    }

    // спроба логічно вивести справжнє місцезнаходження Вампуса та Ям.
    private void inferTrueLocations() {
        if (wampusAliveInMind) {
            int wampusCandidates = 0;
            int potentialWampusX = -1, potentialWampusY = -1;
            for (int i = 0; i < gridSize; i++) {
                for (int j = 0; j < gridSize; j++) {
                    if (worldMap[i][j].isWampusSuspect() && !worldMap[i][j].isSafe()) {
                        wampusCandidates++;
                        potentialWampusX = i;
                        potentialWampusY = j;
                    }
                }
            }
            if (wampusCandidates == 1) {
                worldMap[potentialWampusX][potentialWampusY].setIsWampusConfirmed(true);
                System.out.println("Навігатор: Вампуса підтверджено у (" + potentialWampusX + ", " + potentialWampusY + ")");
            }
        }
    }

    // приймає рішення про наступну дію спелеолога на основі внутрішньої карти.
    // @return рекомендована дія.
    private Action decideNextAction() {
        if (hasGold && currentExplorerX == 0 && currentExplorerY == 0) {
            System.out.println("Навігатор: Золото є і в початковій клітинці, рекомендую CLIMB.");
            lastActionAttempted = Action.CLIMB;
            return Action.CLIMB;
        }

        if (worldMap[currentExplorerX][currentExplorerY].isPerceivedGlitter()) {
            System.out.println("Навігатор: Виявлено блиск, рекомендую GRAB.");
            lastActionAttempted = Action.GRAB;
            return Action.GRAB;
        }

        if (hasArrow && wampusAliveInMind) {
            for (int i = 0; i < gridSize; i++) {
                for (int j = 0; j < gridSize; j++) {
                    if (worldMap[i][j].isWampusConfirmed()) {
                        // перевірити, чи Вампус знаходиться в напрямку стрільби
                        if ((currentExplorerDirection == Direction.NORTH && j == currentExplorerY && i < currentExplorerX) ||
                                (currentExplorerDirection == Direction.SOUTH && j == currentExplorerY && i > currentExplorerX) ||
                                (currentExplorerDirection == Direction.EAST && i == currentExplorerX && j > currentExplorerY) ||
                                (currentExplorerDirection == Direction.WEST && i == currentExplorerX && j < currentExplorerY)) {
                            System.out.println("Навігатор: Вампуса підтверджено у напрямку, рекомендую SHOOT.");
                            lastActionAttempted = Action.SHOOT;
                            return Action.SHOOT;
                        }
                    }
                }
            }
        }

        int[] dx = {0, 0, 1, -1};
        int[] dy = {1, -1, 0, 0};

        for (int i = 0; i < 4; i++) {
            int nextX = currentExplorerX + dx[i];
            int nextY = currentExplorerY + dy[i];

            if (isValid(nextX, nextY) && worldMap[nextX][nextY].isSafe() && !worldMap[nextX][nextY].isVisited()) {
                Direction targetDirection = getDirectionTo(currentExplorerX, currentExplorerY, nextX, nextY);
                if (targetDirection != currentExplorerDirection) {
                    Action turnAction = getTurnAction(currentExplorerDirection, targetDirection);
                    if (turnAction != null) {
                        System.out.println("Навігатор: Знайдено безпечну, невідвідану клітинку (" + nextX + ", " + nextY + "), рекомендую " + turnAction);
                        lastActionAttempted = turnAction;
                        return turnAction;
                    }
                } else {
                    System.out.println("Навігатор: Знайдено безпечну, невідвідану клітинку (" + nextX + ", " + nextY + "), рекомендую FORWARD");
                    lastActionAttempted = Action.FORWARD;
                    return Action.FORWARD;
                }
            }
        }

        System.out.println("Навігатор: Немає очевидних безпечних шляхів, пробуємо повернути.");
        lastActionAttempted = Action.TURN_RIGHT;
        return Action.TURN_RIGHT;
    }

    // отримання напрямку від поточної клітинки до цільової.
    // @param fromX X-координата поточної клітинки.
    // @param fromY Y-координата поточної клітинки.
    // @param toX X-координата цільової клітинки.
    // @param toY Y-координата цільової клітинки.
    // @return напрямок до цільової клітинки.
    private Direction getDirectionTo(int fromX, int fromY, int toX, int toY) {
        if (toX > fromX) return Direction.EAST;
        if (toX < fromX) return Direction.WEST;
        if (toY > fromY) return Direction.SOUTH;
        if (toY < fromY) return Direction.NORTH;
        return currentExplorerDirection;
    }

    // отримання дії повороту для досягнення цільового напрямку.
    // @param current поточний напрямок.
    // @param target цільовий напрямок.
    // @return дія повороту (TURN_LEFT або TURN_RIGHT).
    private Action getTurnAction(Direction current, Direction target) {
        if (current == target) return null;

        switch (current) {
            case NORTH:
                if (target == Direction.EAST) return Action.TURN_RIGHT;
                if (target == Direction.WEST) return Action.TURN_LEFT;
                break;
            case EAST:
                if (target == Direction.SOUTH) return Action.TURN_RIGHT;
                if (target == Direction.NORTH) return Action.TURN_LEFT;
                break;
            case SOUTH:
                if (target == Direction.WEST) return Action.TURN_RIGHT;
                if (target == Direction.EAST) return Action.TURN_LEFT;
                break;
            case WEST:
                if (target == Direction.NORTH) return Action.TURN_RIGHT;
                if (target == Direction.SOUTH) return Action.TURN_LEFT;
                break;
        }
        return Action.TURN_LEFT;
    }

    // формування природно-мовного повідомлення для спелеолога на основі обраної дії.
    // @param action дія.
    // @return природно-мовне повідомлення.
    private String generateActionMessage(Action action) {
        switch (action) {
            case TURN_LEFT: return "Повернути ліворуч";
            case TURN_RIGHT: return "Повернути праворуч";
            case FORWARD: return "Рухатися вперед";
            case SHOOT: return "Стріляти";
            case GRAB: return "Схопити золото";
            case CLIMB: return "Піднятися";
            default: return "Невідома дія";
        }
    }

    // оновлення внутрішнього стану навігатора після виконання дії спелеолога.
    // @param action дія, яка була виконана.
    private void applyActionToNavigatorState(Action action) {
        lastActionAttempted = action;
        switch (action) {
            case FORWARD:
                int newX = currentExplorerX;
                int newY = currentExplorerY;
                switch (currentExplorerDirection) {
                    case NORTH: newY--; break;
                    case EAST: newX++; break;
                    case SOUTH: newY++; break;
                    case WEST: newX--; break;
                }
                if (isValid(newX, newY)) {
                    currentExplorerX = newX;
                    currentExplorerY = newY;
                }
                break;
            case TURN_LEFT:
                switch (currentExplorerDirection) {
                    case NORTH: currentExplorerDirection = Direction.WEST; break;
                    case EAST: currentExplorerDirection = Direction.NORTH; break;
                    case SOUTH: currentExplorerDirection = Direction.EAST; break;
                    case WEST: currentExplorerDirection = Direction.SOUTH; break;
                }
                break;
            case TURN_RIGHT:
                switch (currentExplorerDirection) {
                    case NORTH: currentExplorerDirection = Direction.EAST; break;
                    case EAST: currentExplorerDirection = Direction.SOUTH; break;
                    case SOUTH: currentExplorerDirection = Direction.WEST; break;
                    case WEST: currentExplorerDirection = Direction.NORTH; break;
                }
                break;
            case SHOOT:
                hasArrow = false;
                break;
            case GRAB:
                hasGold = true;
                break;
            case CLIMB:
                break;
        }
    }

    // перевіряє, чи є координати дійсними в межах сітки.
    // @param x X-координата.
    // @param y Y-координата.
    // @return true, якщо координати дійсні, інакше false.
    private boolean isValid(int x, int y) {
        return x >= 0 && x < gridSize && y >= 0 && y < gridSize;
    }

    // друк внутрішньої карти навігатора для налагодження.
    private void printNavigatorMap() {
        System.out.println("--- Карта навігатора ---");
        for (int j = 0; j < gridSize; j++) {
            for (int i = 0; i < gridSize; i++) {
                System.out.print("|");
                if (i == currentExplorerX && j == currentExplorerY) {
                    System.out.print("A");
                } else if (worldMap[i][j].isGoldConfirmed()) {
                    System.out.print("G");
                } else if (worldMap[i][j].isWampusConfirmed()) {
                    System.out.print("W");
                } else if (worldMap[i][j].isPitConfirmed()) {
                    System.out.print("P");
                } else if (worldMap[i][j].isSafe()) {
                    System.out.print("S");
                } else if (worldMap[i][j].isWampusSuspect()) {
                    System.out.print("w");
                } else if (worldMap[i][j].isPitSuspect()) {
                    System.out.print("p");
                } else if (worldMap[i][j].isVisited()) {
                    System.out.print("V");
                } else {
                    System.out.print("?");
                }
                System.out.print(" ");
            }
            System.out.println("|");
        }
        System.out.println("Позиція спелеолога: (" + currentExplorerX + ", " + currentExplorerY + "), Напрямок: " + currentExplorerDirection);
        System.out.println("Наявність стріли: " + hasArrow + ", Наявність золота: " + hasGold + ", Вампус живий (навігатор): " + wampusAliveInMind);
        System.out.println("------------------------");
    }
}
