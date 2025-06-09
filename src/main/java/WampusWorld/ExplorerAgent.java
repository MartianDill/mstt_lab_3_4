package WampusWorld;
import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import static WampusWorld.WampusWorldCore.PerceptionType;
import static WampusWorld.WampusWorldCore.Action;


// агент-спелеолог, який взаємодіє з середовищем та навігатором.
// він перетворює сприйняття у природну мову, надсилає їх навігатору
// та виконує дії, рекомендовані навігатором.
public class ExplorerAgent extends Agent {
    private AID environmentAID; // ідентифікатор агента-середовища
    private AID navigatorAID;   // ідентифікатор агента-навігатора
    private boolean gameStarted = false; // флаг, що вказує на початок гри
    private boolean gameFinished = false; // флаг, що вказує на завершення гри
    private Set<PerceptionType> currentPerceptions; // поточні сприйняття від середовища
    private boolean isReceiveActionBehaviourAdded = false;

    // словник синонімічних англійських речень для кожного типу сприйняття
    private static final Map<PerceptionType, List<String>> PERCEPTION_SYNONYMS = new HashMap<>();
    static {
        PERCEPTION_SYNONYMS.put(PerceptionType.STENCH, Arrays.asList(
                "I smell something terrible here.",
                "There's a terrible stench.",
                "It smells awful here."
        ));
        PERCEPTION_SYNONYMS.put(PerceptionType.BREEZE, Arrays.asList(
                "I feel a breeze here.",
                "There is a cool breeze.",
                "It's breezy in this room."
        ));
        PERCEPTION_SYNONYMS.put(PerceptionType.GLITTER, Arrays.asList(
                "I see a glitter.",
                "There's something shiny here.",
                "I perceive a brilliant sparkle."
        ));
        PERCEPTION_SYNONYMS.put(PerceptionType.BUMP, Arrays.asList(
                "I hit a wall.",
                "I bumped into something.",
                "My path is blocked by a wall."
        ));
        PERCEPTION_SYNONYMS.put(PerceptionType.SCREAM, Arrays.asList(
                "I hear a scream.",
                "A loud scream echoed.",
                "Something just screamed."
        ));
        PERCEPTION_SYNONYMS.put(PerceptionType.SAFE, Arrays.asList(
                "It feels safe here.",
                "This room is clear.",
                "I perceive no immediate danger."
        ));
    }

    // словник для перетворення природної мови на дію
    private static final Map<String, Action> ACTION_KEYWORDS = new HashMap<>();
    static {
        ACTION_KEYWORDS.put("повернути ліворуч", Action.TURN_LEFT);
        ACTION_KEYWORDS.put("повернути праворуч", Action.TURN_RIGHT);
        ACTION_KEYWORDS.put("рухатися вперед", Action.FORWARD);
        ACTION_KEYWORDS.put("стріляти", Action.SHOOT);
        ACTION_KEYWORDS.put("схопити золото", Action.GRAB);
        ACTION_KEYWORDS.put("піднятися", Action.CLIMB);
    }

    private Random random = new Random(); // для випадкового вибору синонімів

    @Override
    protected void setup() {
        System.out.println("Агент-спелеолог " + getAID().getName() + " запущений.");

        // реєстрація послуги в DF
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("wampus-explorer");
        sd.setName("Wampus-Explorer-Service");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
            System.out.println("Спелеолог: зареєстровано в DF.");
        } catch (FIPAException fe) {
            fe.printStackTrace();
            System.err.println("Спелеолог: помилка реєстрації в DF: " + fe.getMessage());
        }

        // поведінка для пошуку інших агентів та початку взаємодії
        addBehaviour(new TickerBehaviour(this, 1000) { // перевіряємо кожну секунду
            @Override
            protected void onTick() {
                if (!gameStarted && !gameFinished) {
                    System.out.println("Спелеолог: TickerBehaviour шукає агентів..."); // Додано логування
                    // пошук агента-середовища
                    DFAgentDescription templateEnv = new DFAgentDescription();
                    ServiceDescription serviceTemplateEnv = new ServiceDescription();
                    serviceTemplateEnv.setType("wampus-environment");
                    templateEnv.addServices(serviceTemplateEnv);
                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, templateEnv);
                        if (result.length > 0) {
                            environmentAID = result[0].getName();
                            System.out.println("Спелеолог: знайдено середовище: " + environmentAID.getName());
                        }
                    } catch (FIPAException fe) {
                        fe.printStackTrace();
                    }

                    // пошук агента-навігатора
                    DFAgentDescription templateNav = new DFAgentDescription();
                    ServiceDescription serviceTemplateNav = new ServiceDescription();
                    serviceTemplateNav.setType("wampus-navigation");
                    templateNav.addServices(serviceTemplateNav);
                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, templateNav);
                        if (result.length > 0) {
                            navigatorAID = result[0].getName();
                            System.out.println("Спелеолог: знайдено навігатора: " + navigatorAID.getName());
                        }
                    } catch (FIPAException fe) {
                        fe.printStackTrace();
                    }

                    if (environmentAID != null && navigatorAID != null) {
                        gameStarted = true;
                        System.out.println("Спелеолог: усі агенти знайдено, гра розпочинається. Додаю основні поведінки.");
                        // додаємо поведінку для прийому сприйнять від середовища
                        addBehaviour(new ReceivePerceptionFromEnvironmentBehaviour());
                        // додаємо поведінку для прийому дії від навігатора, тільки один раз
                        if (!isReceiveActionBehaviourAdded) {
                            addBehaviour(new ReceiveActionFromNavigatorBehaviour());
                            isReceiveActionBehaviourAdded = true;
                        }
                        // *************** ДОДАНО: надсилаємо початковий запит на сприйняття ***************
                        myAgent.addBehaviour(new SendInitialPerceptionRequestBehaviour());
                        // ***********************************************************************************
                        stop(); // зупинити TickerBehaviour
                    } else {
                        System.out.println("Спелеолог: очікую на знаходження всіх агентів..."); // Додано логування
                    }
                }
            }
        });
    }

    @Override
    protected void takeDown() {
        // скасування реєстрації в DF
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
            System.err.println("Спелеолог: помилка скасування реєстрації в DF: " + fe.getMessage());
        }
        System.out.println("Агент-спелеолог " + getAID().getName() + " завершив роботу.");
    }

    // Внутрішня одноразова поведінка для надсилання першого запиту на сприйняття до середовища
    private class SendInitialPerceptionRequestBehaviour extends OneShotBehaviour {
        @Override
        public void action() {
            if (environmentAID != null) {
                ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                msg.addReceiver(environmentAID);
                msg.setContent("request-current-perception"); // Може бути будь-який зміст, який Environment зрозуміє як запит
                myAgent.send(msg);
                System.out.println("Спелеолог: відправлено перший запит на сприйняття до середовища."); // Додано логування
            } else {
                System.err.println("Спелеолог: не вдалося відправити початковий запит, environmentAID = null."); // Додано логування
            }
        }
    }


    // внутрішня циклічна поведінка для прийому сприйнять від агента-середовища.
    private class ReceivePerceptionFromEnvironmentBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            // шаблон повідомлення для прийому INFORM від середовища
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null && !gameFinished) {
                String content = msg.getContent();
                System.out.println("Спелеолог: отримано сприйняття від середовища: '" + content + "'");

                // перевірка на завершення гри
                if (content.startsWith("GAME_OVER:")) {
                    handleGameOutcome(content);
                    gameFinished = true;
                    // видалити агента, щоб він не продовжував працювати
                    myAgent.doDelete();
                    return;
                }

                // парсинг сприйнять
                currentPerceptions = parsePerceptionString(content);

                // якщо отримано сприйняття, надіслати їх навігатору
                myAgent.addBehaviour(new SendPerceptionToNavigatorBehaviour(currentPerceptions));

            } else {
                block();
            }
        }
    }

    // внутрішня одноразова поведінка для надсилання сприйнять навігатору.
    private class SendPerceptionToNavigatorBehaviour extends OneShotBehaviour {
        private Set<PerceptionType> perceptionsToSend;

        public SendPerceptionToNavigatorBehaviour(Set<PerceptionType> perceptions) {
            this.perceptionsToSend = perceptions;
        }

        @Override
        public void action() {
            if (navigatorAID != null) {
                ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                msg.addReceiver(navigatorAID);
                String naturalLanguagePerception = generatePerceptionMessage(perceptionsToSend);
                msg.setContent(naturalLanguagePerception);
                myAgent.send(msg);
                System.out.println("Спелеолог: відправлено сприйняття навігатору: '" + naturalLanguagePerception + "'"); // Додано логування
            } else {
                System.err.println("Спелеолог: навігатора не знайдено, не можу надіслати сприйняття.");
            }
        }
    }

    // внутрішня циклічна поведінка для прийому рекомендованої дії від навігатора.
    private class ReceiveActionFromNavigatorBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            // шаблон повідомлення для прийому INFORM (рекомендована дія) від навігатора
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null && !gameFinished) {
                String actionContent = msg.getContent();
                System.out.println("Спелеолог: отримано рекомендацію від навігатора: '" + actionContent + "'"); // Додано логування

                // парсинг дії з природної мови
                Action recommendedAction = parseActionMessage(actionContent);

                if (recommendedAction != null) {
                    // надіслати дію середовищу
                    myAgent.addBehaviour(new SendActionToEnvironmentBehaviour(recommendedAction));
                } else {
                    System.err.println("Спелеолог: не вдалося розпізнати дію з повідомлення: " + actionContent);
                }
            } else {
                block();
            }
        }
    }

    // внутрішня одноразова поведінка для надсилання дії агенту-середовищу.
    private class SendActionToEnvironmentBehaviour extends OneShotBehaviour {
        private Action actionToSend;

        public SendActionToEnvironmentBehaviour(Action action) {
            this.actionToSend = action;
        }

        @Override
        public void action() {
            if (environmentAID != null) {
                ACLMessage msg = new ACLMessage(ACLMessage.CFP);
                msg.addReceiver(environmentAID);
                msg.setContent(actionToSend.toString());
                myAgent.send(msg);
                System.out.println("Спелеолог: відправлено дію середовищу: '" + actionToSend.toString() + "'"); // Додано логування
            } else {
                System.err.println("Спелеолог: середовища не знайдено, не можу надіслати дію.");
            }
        }
    }

    // парсинг строкового представлення сприйнять від середовища в Set<PerceptionType>.
    // @param perceptionString рядок сприйнять (наприклад, "STENCH;BREEZE").
    // @return набір сприйнять.
    private Set<PerceptionType> parsePerceptionString(String perceptionString) {
        Set<PerceptionType> perceptions = new HashSet<>();
        if (perceptionString == null || perceptionString.isEmpty()) {
            return perceptions;
        }
        String[] parts = perceptionString.split(";");
        for (String part : parts) {
            try {
                perceptions.add(PerceptionType.valueOf(part.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                System.err.println("Спелеолог: невідомий тип сприйняття: " + part);
            }
        }
        return perceptions;
    }

    // формування природно-мовного повідомлення зі сприйнять для навігатора.
    // @param perceptions набір сприйнять.
    // @return природно-мовне повідомлення.
    private String generatePerceptionMessage(Set<PerceptionType> perceptions) {
        StringBuilder sb = new StringBuilder();
        // перевірка, чи є спеціальні сприйняття, які мають пріоритет
        if (perceptions.contains(PerceptionType.BUMP)) {
            sb.append(getRandomSynonym(PerceptionType.BUMP));
        } else if (perceptions.contains(PerceptionType.SCREAM)) {
            sb.append(getRandomSynonym(PerceptionType.SCREAM));
        } else {
            // якщо немає BUMP або SCREAM, додаємо інші сприйняття
            List<String> perceptionPhrases = new ArrayList<>();
            if (perceptions.contains(PerceptionType.STENCH)) {
                perceptionPhrases.add(getRandomSynonym(PerceptionType.STENCH));
            }
            if (perceptions.contains(PerceptionType.BREEZE)) {
                perceptionPhrases.add(getRandomSynonym(PerceptionType.BREEZE));
            }
            if (perceptions.contains(PerceptionType.GLITTER)) {
                perceptionPhrases.add(getRandomSynonym(PerceptionType.GLITTER));
            }

            if (perceptionPhrases.isEmpty() && perceptions.contains(PerceptionType.SAFE)) {
                perceptionPhrases.add(getRandomSynonym(PerceptionType.SAFE));
            }

            for (int i = 0; i < perceptionPhrases.size(); i++) {
                sb.append(perceptionPhrases.get(i));
                if (i < perceptionPhrases.size() - 1) {
                    sb.append(". ");
                }
            }
        }

        // якщо жодних сприйнять, але є SAFE
        if (sb.length() == 0 && perceptions.contains(PerceptionType.SAFE)) {
            sb.append(getRandomSynonym(PerceptionType.SAFE));
        } else if (sb.length() == 0) {
            sb.append("I perceive nothing unusual.");
        }


        return sb.toString();
    }

    // отримання випадкового синоніму для даного типу сприйняття
    private String getRandomSynonym(PerceptionType type) {
        List<String> synonyms = PERCEPTION_SYNONYMS.get(type);
        if (synonyms != null && !synonyms.isEmpty()) {
            return synonyms.get(random.nextInt(synonyms.size()));
        }
        return "Unknown perception.";
    }

    // граматичний розбір природно-мовного повідомлення від навігатора
    // @param actionMessage природно-мовне повідомлення про дію.
    // @return об'єкт Action або null, якщо дію не розпізнано.
    private Action parseActionMessage(String actionMessage) {
        String lowerCaseMessage = actionMessage.toLowerCase().trim();
        for (Map.Entry<String, Action> entry : ACTION_KEYWORDS.entrySet()) {
            if (lowerCaseMessage.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    // обробка повідомлення про завершення гри від середовища.
    // @param outcomeMessage повідомлення про результат гри.
    private void handleGameOutcome(String outcomeMessage) {
        System.out.println("Спелеолог: гра завершена! Результат: " + outcomeMessage.replace("GAME_OVER:", ""));
        gameFinished = true;
    }
}
