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
import java.util.HashSet;
import java.util.Set;
import static WampusWorld.WampusWorldCore.WampusWorld;
import static WampusWorld.WampusWorldCore.Direction;
import static WampusWorld.WampusWorldCore.PerceptionType;
import static WampusWorld.WampusWorldCore.Action;
import static WampusWorld.WampusWorldCore.Room;
import WampusWorld.AgentState;


// агент-середовище, відповідає за моделювання світу Вампуса,
// управління станом спелеолога та обробку його дій.
public class EnvironmentAgent extends Agent {
    private WampusWorld wampusWorld; // екземпляр світу Вампуса
    private AgentState explorerState; // поточний стан спелеолога
    private AID explorerAID; // ідентифікатор агента-спелеолога
    private long initialTime; // для відстеження часу виконання задачі
    private int currentTurn; // лічильник ходів
    private boolean gameOver = false; // флаг завершення гри

    @Override
    protected void setup() {
        System.out.println("Агент-середовище " + getAID().getName() + " запущений.");

        // ініціалізація світу Вампуса (розмір 4х4)
        wampusWorld = new WampusWorld(4);
        // ініціалізація стану спелеолога (починає в (0,0), дивиться на схід)
        explorerState = new AgentState(0, 0, Direction.EAST);
        initialTime = System.currentTimeMillis();
        currentTurn = 0;

        // реєстрація послуги в DF (жовті сторінки)
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("wampus-environment");
        sd.setName("Прогулянка печерою скарбів"); // назва послуги згідно завдання
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
            System.out.println("Середовище: зареєстровано в DF з послугою '" + sd.getName() + "'");
        } catch (FIPAException fe) {
            fe.printStackTrace();
            System.err.println("Середовище: помилка реєстрації в DF: " + fe.getMessage());
        }

        // додавання поведінки для прийому повідомлень від спелеолога (тепер обробляє і REQUEST, і CFP)
        addBehaviour(new ReceiveExplorerActionBehaviour());

        System.out.println("Середовище: готове до прийому першого запиту від спелеолога.");
    }

    @Override
    protected void takeDown() {
        // скасування реєстрації в DF
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
            System.err.println("Середовище: помилка скасування реєстрації в DF: " + fe.getMessage());
        }
        System.out.println("Агент-середовище " + getAID().getName() + " завершив роботу.");
    }

    // внутрішня циклічна поведінка для прийому повідомлень від агента-спелеолога
    // та обробки його дій або запитів на стан.
    private class ReceiveExplorerActionBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            // шаблон повідомлення для прийому CFP (Call for Proposal) або REQUEST від спелеолога
            MessageTemplate mt = MessageTemplate.or(
                    MessageTemplate.MatchPerformative(ACLMessage.CFP),
                    MessageTemplate.MatchPerformative(ACLMessage.REQUEST)
            );
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null && !gameOver) {
                currentTurn++;
                System.out.println("\n--- Хід " + currentTurn + " ---");
                System.out.println("Середовище: отримано повідомлення від " + msg.getSender().getName() + ": '" + msg.getContent() + "' (Performative: " + ACLMessage.getPerformative(msg.getPerformative()) + ")");

                // встановлення AID спелеолога, якщо ще не встановлено
                if (explorerAID == null) {
                    explorerAID = msg.getSender();
                    explorerState.setEnvironmentAID(getAID());
                    explorerState.setNavigatorAID(new AID("NavigatorAgent", AID.ISLOCALNAME));
                }

                Set<PerceptionType> currentPerceptions = new HashSet<>();
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.INFORM); // Завжди відповідаємо INFORM зі сприйняттями

                if (msg.getPerformative() == ACLMessage.REQUEST) {
                    //  якщо це запит на стан, просто надсилаємо сприйняття без зміни стану
                    currentPerceptions = getPerceptionsForCurrentRoom(false, false);
                    System.out.println("Середовище: обробляю запит на стан. Відправляю поточні сприйняття.");
                } else if (msg.getPerformative() == ACLMessage.CFP) {
                    // якщо це пропозиція виконати дію
                    String actionStr = msg.getContent();
                    Action action = null;
                    try {
                        action = Action.valueOf(actionStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        System.err.println("Середовище: невідома дія від спелеолога: " + actionStr);
                        reply.setPerformative(ACLMessage.REFUSE); // Відхиляємо невідому дію
                        reply.setContent("Невідома дія: " + actionStr);
                        myAgent.send(reply);
                        return;
                    }

                    handleExplorerAction(action); // Обробляємо дію спелеолога

                    if (gameOver) {
                        System.out.println("Середовище: гра завершена. Не надсилаю подальші сприйняття.");
                        // Тут ми вже відправили GAME_OVER, тож додаткова відповідь не потрібна
                        return;
                    }
                    currentPerceptions = getPerceptionsForCurrentRoom(false, false);
                    System.out.println("Середовище: оброблено дію '" + actionStr + "'. Відправляю нові сприйняття.");
                }

                reply.setContent(generatePerceptionMessage(currentPerceptions, false, false));
                myAgent.send(reply);
                System.out.println("Середовище: відправлено відповідь спелеологу: '" + reply.getContent() + "'");

                // Вивід карти світу і стану спелеолога (переміщено в окремий метод)
                printEnvironmentState();
            } else {
                block();
            }
        }
    }

    // обробка дії спелеолога.
    // @param action дія, яку виконує спелеолог.
    private void handleExplorerAction(Action action) {
        int currentX = explorerState.getX();
        int currentY = explorerState.getY();
        Room currentRoom = wampusWorld.getRoom(currentX, currentY);
        if (currentRoom == null) {
            System.err.println("Середовище: Помилка, поточна кімната недійсна!");
            return;
        }

        boolean bumped = false;
        boolean screamed = false;

        System.out.println("Середовище: спелеолог виконує дію: " + action.toString()); // Додано логування дії

        switch (action) {
            case FORWARD:
                int prevX = explorerState.getX();
                int prevY = explorerState.getY();
                boolean moved = explorerState.moveForward(wampusWorld.getSize(), wampusWorld.getSize());
                if (!moved) {
                    bumped = true;
                    explorerState.setX(prevX);
                    explorerState.setY(prevY);
                    System.out.println("Середовище: спелеолог зіткнувся зі стіною! Залишається у (" + explorerState.getX() + ", " + explorerState.getY() + ")"); 
                } else {
                    currentRoom = wampusWorld.getRoom(explorerState.getX(), explorerState.getY());
                    System.out.println("Середовище: спелеолог перемістився до (" + explorerState.getX() + ", " + explorerState.getY() + ")"); 
                    if (currentRoom.hasPit()) {
                        System.out.println("Середовище: спелеолог впав у яму! Гра завершена.");
                        sendGameOutcome("FAIL: Впав у яму");
                        gameOver = true;
                    } else if (currentRoom.hasWampus() && wampusWorld.isWampusAlive()) {
                        System.out.println("Середовище: Вампус з'їв спелеолога! Гра завершена.");
                        sendGameOutcome("FAIL: З'їдений Вампусом");
                        gameOver = true;
                    } else {
                        currentRoom.setExplored(true);
                    }
                }
                break;
            case TURN_LEFT:
                explorerState.turnLeft();
                System.out.println("Середовище: спелеолог повернув ліворуч. Новий напрямок: " + explorerState.getDirection()); 
                break;
            case TURN_RIGHT:
                explorerState.turnRight();
                System.out.println("Середовище: спелеолог повернув праворуч. Новий напрямок: " + explorerState.getDirection()); 
                break;
            case SHOOT:
                if (explorerState.hasArrow()) {
                    explorerState.useArrow();
                    System.out.println("Середовище: спелеолог вистрілив! Залишилося стріл: 0"); 
                    // перевірка, чи Вампус був у напрямку стрільби
                    int targetX = explorerState.getX();
                    int targetY = explorerState.getY();
                    
                    // Вампус знаходиться в прямому напрямку, якщо він на тій же лінії Х/У і між поточною позицією спелеолога та кінцем світу в цьому напрямку.
                    switch (explorerState.getDirection()) {
                        case NORTH:
                            targetY = 0; // стріляє до північного краю карти
                            break;
                        case EAST:
                            targetX = wampusWorld.getSize() - 1; // стріляє до східного краю карти
                            break;
                        case SOUTH:
                            targetY = wampusWorld.getSize() - 1; // стріляє до південного краю карти
                            break;
                        case WEST:
                            targetX = 0; // стріляє до західного краю карти
                            break;
                    }

                    boolean wampusHit = false;
                    // чи Вампус на лінії вогню
                    if (explorerState.getDirection() == Direction.NORTH || explorerState.getDirection() == Direction.SOUTH) {
                        // вертикальний постріл по Y
                        int startY = Math.min(explorerState.getY(), targetY);
                        int endY = Math.max(explorerState.getY(), targetY);
                        for (int yCoord = startY; yCoord <= endY; yCoord++) {
                            // перевірка кімнати вздовж лінії стрільби
                            if (wampusWorld.getRoom(explorerState.getX(), yCoord) != null &&
                                    wampusWorld.getRoom(explorerState.getX(), yCoord).hasWampus() && wampusWorld.isWampusAlive()) {
                                wampusHit = true;
                                break;
                            }
                        }
                    } else { // EAST or WEST
                        // горизонтальний постріл по X
                        int startX = Math.min(explorerState.getX(), targetX);
                        int endX = Math.max(explorerState.getX(), targetX);
                        for (int xCoord = startX; xCoord <= endX; xCoord++) {
                            // перевірка кімнати вздовж лінії стрільби
                            if (wampusWorld.getRoom(xCoord, explorerState.getY()) != null &&
                                    wampusWorld.getRoom(xCoord, explorerState.getY()).hasWampus() && wampusWorld.isWampusAlive()) {
                                wampusHit = true;
                                break;
                            }
                        }
                    }


                    if (wampusHit) {
                        wampusWorld.setWampusAlive(false);
                        screamed = true;
                        System.out.println("Середовище: Вампуса вбито! Крик чутно."); 
                    } else {
                        System.out.println("Середовище: Вампус не вбитий. Стріла пролетіла повз."); 
                    }
                } else {
                    System.out.println("Середовище: немає стріл! Дія SHOOT неможлива."); 
                }
                break;
            case GRAB:
                if (currentRoom.hasGold()) {
                    explorerState.setHasGold(true);
                    currentRoom.setHasGold(false);
                    System.out.println("Середовище: спелеолог схопив золото! Тепер має золото: " + explorerState.hasGold()); 
                } else {
                    System.out.println("Середовище: золота немає в цій кімнаті. Дія GRAB неможлива."); 
                }
                break;
            case CLIMB:
                System.out.println("Середовище: спелеолог намагається піднятися..."); 
                if (explorerState.getX() == 0 && explorerState.getY() == 0) {
                    if (explorerState.hasGold()) {
                        System.out.println("Середовище: спелеолог піднявся із золотом! Успіх!");
                        sendGameOutcome("SUCCESS: Золото здобуто");
                    } else {
                        System.out.println("Середовище: спелеолог піднявся без золота. Невдача.");
                        sendGameOutcome("FAIL: Без золота");
                    }
                    gameOver = true;
                } else {
                    System.out.println("Середовище: можна піднятися лише в початковій кімнаті (0,0). Спелеолог знаходиться у (" + explorerState.getX() + ", " + explorerState.getY() + ")"); 
                }
                break;
        }

        wampusWorld.updatePerceptions();
    }

    // отримання поточних сприйнять для кімнати, де знаходиться спелеолог.
    // @param bumped чи відбулося зіткнення.
    // @param screamed чи був крик Вампуса.
    // @return набір сприйнять.
    private Set<PerceptionType> getPerceptionsForCurrentRoom(boolean bumped, boolean screamed) {
        Set<PerceptionType> perceptions = new HashSet<>();
        int currentX = explorerState.getX();
        int currentY = explorerState.getY();
        Room currentRoom = wampusWorld.getRoom(currentX, currentY);

        if (currentRoom == null) {
            System.err.println("Середовище: Помилка: поточна кімната недійсна при отриманні сприйнять!");
            return perceptions;
        }

        perceptions.addAll(currentRoom.getPerceptions());

        if (bumped) {
            perceptions.add(PerceptionType.BUMP);
        }
        if (screamed) {
            perceptions.add(PerceptionType.SCREAM);
        }

        if (perceptions.isEmpty()) {
            perceptions.add(PerceptionType.SAFE);
        }
        return perceptions;
    }

    // формування повідомлення зі сприйняттями для агента-спелеолога.
    // @param perceptions набір сприйнять.
    // @param bumped чи відбулося зіткнення.
    // @param screamed чи був крик Вампуса.
    // @return строкове представлення сприйнять.
    private String generatePerceptionMessage(Set<PerceptionType> perceptions, boolean bumped, boolean screamed) {
        StringBuilder sb = new StringBuilder();
        if (perceptions.contains(PerceptionType.STENCH)) sb.append("STENCH;");
        if (perceptions.contains(PerceptionType.BREEZE)) sb.append("BREEZE;");
        if (perceptions.contains(PerceptionType.GLITTER)) sb.append("GLITTER;");
        if (perceptions.contains(PerceptionType.BUMP) || bumped) sb.append("BUMP;");
        if (perceptions.contains(PerceptionType.SCREAM) || screamed) sb.append("SCREAM;");
        if (perceptions.contains(PerceptionType.SAFE) && sb.length() == 0) sb.append("SAFE;");
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    // надсилає повідомлення про результат гри спелеологу та завершує гру.
    // @param outcome результат гри (SUCCESS/FAIL).
    private void sendGameOutcome(String outcome) {
        if (explorerAID != null) {
            ACLMessage finalMsg = new ACLMessage(ACLMessage.INFORM);
            finalMsg.addReceiver(explorerAID);
            finalMsg.setContent("GAME_OVER:" + outcome);
            send(finalMsg);
            System.out.println("Середовище: відправлено фінальне повідомлення: " + finalMsg.getContent());
        }
        long endTime = System.currentTimeMillis();
        System.out.println("Середовище: гра завершена після " + currentTurn + " ходів за " + (endTime - initialTime) + " мс.");
        doDelete();
    }

    // допоміжний метод для друку стану середовища
    private void printEnvironmentState() {
        StringBuilder sb = new StringBuilder();
        sb.append("Поточний стан світу Вампуса:\n");
        for (int i = 0; i < wampusWorld.getSize(); i++) {
            for (int j = 0; j < wampusWorld.getSize(); j++) {
                sb.append("|");
                // отримання кімнати за координатами (враховуйте, що WampusWorld.getRoom приймає (x, y))
                Room currentRoom = wampusWorld.getRoom(j, i); // змінено на (j, i) для коректного відображення матриці
                if (explorerState.getX() == j && explorerState.getY() == i) {
                    sb.append("A"); // Агент
                } else {
                    if (currentRoom.hasGold()) sb.append("G");
                    if (currentRoom.hasPit()) sb.append("P");
                    if (currentRoom.hasWampus() && wampusWorld.isWampusAlive()) sb.append("W");
                    if (currentRoom.hasStench()) sb.append("S");
                    if (currentRoom.hasBreeze()) sb.append("B");
                    sb.append(" ");
                }
            }
            sb.append("|\n"); // кінець рядка мапи
        }
        sb.append(explorerState.toString()).append("\n"); // стан спелеолога
        System.out.print(sb.toString()); // весь зібраний текст одним блоком
    }
}
