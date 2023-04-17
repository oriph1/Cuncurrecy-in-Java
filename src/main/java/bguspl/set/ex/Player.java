package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    public final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;
    private Dealer dealer;
    public static AtomicBoolean stop = new AtomicBoolean(true);

    public AtomicBoolean working = new AtomicBoolean(false);
    public AtomicBoolean AIworking = new AtomicBoolean(true);


    public static Object lock = new Object();

    public AtomicBoolean needToSleep = new AtomicBoolean(false);

    private BlockingDeque<Integer> actions = new LinkedBlockingDeque<Integer>();

    private ArrayList<Integer> tokens = new ArrayList<Integer>();
    public boolean gotCheck = false;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer = dealer;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        waitingOnStaticLock(false);
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            waitingOnActionsLock();
            waitingOnStaticLock(false);
            if (actions.size() > 0)
                makeAction(actions.removeFirst());
        }
        if (!human) try {
            aiThread.join();
        } catch (InterruptedException ignored) {
        }
        working.set(false);
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    public void waitingOnStaticLock(boolean ai) {
        synchronized (lock) {
            while (stop.get()) {
                try {
                    if (!ai)
                        working.set(false);
                    else
                        AIworking.set(false);
                    dealer.playerFinishAndWakeUpDealer();
                    lock.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        if (!ai)
            working.set(true);
        else
            AIworking.set(true);
    }

    public void waitingOnActionsLock() {
        synchronized (actions) {
            while (actions.isEmpty() & !terminate) {
                try {
                    working.set(false);
                    dealer.playerFinishAndWakeUpDealer();
                    actions.wait();
                } catch (InterruptedException e) {
                }
            }
            working.set(true);

        }
    }

    public boolean removingSlot(int slot) { //return true if tokens contain the slot
        return tokens.contains(slot);
    }

    private void removeAction(int slot) { //remove the slot from tokens and from table
        removeToken(slot);
        table.removeToken(id, slot);
    }

    public boolean placeAction(int slot) { //check if we alerday have 3 tokens, if not ad to the table and to tokens
        if (tokens.size() >= 3) //if trying to place more than 3 token return
            return false;
        if (table.getCardFromSlot(slot) == -1)
            return false;
        //place token
        addToken(slot);
        table.placeToken(id, slot);
        return true;
    }

    public void removeToken(int slot) { //Rremove action from tokens
        tokens.remove((Object) slot);

    }

    public void addToken(int slot) {
            tokens.add(slot);
    }

    private void makeAction(int slot) {
        if (removingSlot(slot)) {
            removeAction(slot);
        } else {
            if (!placeAction(slot))
                return;
            if (tokens.size() == 3) {
                int prevScore = score;
                gotCheck = false;
                ThirdToken();
                if (terminate)
                    return;
                if (!gotCheck)
                    return;
                boolean gotPoint = (score == prevScore + 1); //point or penalty
                sleepForTime(gotPoint);
                if (!human)
                    wakeUp();
            }
        }
    }

    private void ThirdToken() {
        dealer.checkSet(id); //ask from dealer to check and wait
        waitingOnPlayerLock(false);
    }

    public synchronized void waitingOnPlayerLock(boolean ai) {
        while (needToSleep.get() == true & !terminate) {
            try {
                if (!ai)
                    working.set(false);
                else
                    AIworking.set(false);
                dealer.playerFinishAndWakeUpDealer();
                wait();
            } catch (InterruptedException e) {
            }
        }
        if (!ai)
            working.set(true);
        else
            AIworking.set(true);
    }

    public void sleepForTime(boolean gotPoint) {
        int timeToSleep;
        needToSleep.set(true);
        working.set(false);
        //update time to sleep
        if (gotPoint)
            timeToSleep = ((int) env.config.pointFreezeMillis / 1000);
        else
            timeToSleep = ((int) env.config.penaltyFreezeMillis / 1000);
        //every second update the timer
        for (int i = timeToSleep; i >= 0; i--) {
            env.ui.setFreeze(id, i * 1000);
            try {
                playerThread.sleep(1000);
            } catch (InterruptedException e) {
            }
        }
        working.set(true);
        needToSleep.set(false);
    }


    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */

    private int getNumberforAI() {
        ArrayList<Integer> slots = table.getSlotsWithCards();
        if (slots.size() == 0)
            return 0;
        //random slot
        Random rand = new Random();
        int randomNumber = rand.nextInt(slots.size());
        randomNumber = slots.get(randomNumber);
        return randomNumber;
    }

    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                int randomNumber = getNumberforAI();
                keyPressed(randomNumber); //press
                waitingOnPlayerLock(true);
                waitingOnStaticLock(true);

            }
            AIworking.set(false);
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        synchronized (actions) {
            actions.notifyAll();
        }
        synchronized (this) {
            notifyAll();
        }
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if (stop.get() || needToSleep.get() || table.getCardFromSlot(slot) == -1)
            return;
        synchronized (actions) {
            if (actions.size() < 3) {
                actions.add(slot); // add the action
                actions.notifyAll(); //wake the player up from waiting to action
            }
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        score++;
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, score);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
    }

    public int score() {
        return score;
    }

    public synchronized void wakeUp() {
        notifyAll();
    }

    public int[] getSetAsSlots() {
        int[] ans = new int[3];
        for (int i = 0; i < tokens.size(); i++) {
            ans[i] = tokens.get(i);
        }
        return ans;
    }

    public int[] getSetAsCards() {
        int[] ans = new int[3];
        for (int i = 0; i < tokens.size(); i++)
            ans[i] = table.getCardFromSlot(tokens.get(i));
        return ans;
    }

    public void removeFromActionBlock(int slot) {
        if (actions.contains(slot))
            actions.remove((Object) slot);
    }
    public ArrayList<Integer> getTokens(){
        return tokens;
    }
    public boolean getTerminate(){
        return terminate;
    }
}
