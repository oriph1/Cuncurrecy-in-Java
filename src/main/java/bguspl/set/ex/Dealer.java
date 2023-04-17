package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;
    private BlockingDeque<Integer> playerToCheck;
    private Thread[] threads; //threads for players
    private int indexDeck; //helper for shuffle
    private int sleepingTime; //dealer sleeping time while time loop

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        playerToCheck = new LinkedBlockingDeque<Integer>();
        threads = new Thread[players.length];
        indexDeck = 0;
        sleepingTime = 1000;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");

        //creating threads players
        for (int i = 0; i < players.length; i++) {
            threads[i] = new Thread(players[i]);
            threads[i].start();
        }

        while (!shouldFinish()) {
            Collections.shuffle(deck);
            indexDeck = 0;
            if (env.util.findSets(deck, 1).size() == 0)
                break;
            placeAllCardsOnTable();
            updateTimerDisplay(true);
            wakePlayersUp();
            timerLoop();
            waitingPlayersToFinish();
            removeAllCardsFromTable();
        }

        if (!terminate)
            terminate();

        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    private void wakePlayersUp() {
        Player.stop.set(false);
        synchronized (Player.lock) {
            Player.lock.notifyAll();
        }
    }

    private List<Integer> shuffleSlots() {
        List<Integer> slots = new ArrayList<Integer>();
        for (int i = 0; i < env.config.tableSize; i++) {
            slots.add(i);
        }
        Collections.shuffle(slots);
        return slots;
    }

    private void placeAllCardsOnTable() {
        List<Integer> slots = shuffleSlots(); //getting the slots with random order
        ArrayList<Integer> set = new ArrayList(); /** FOR OUT USING - TAMIR */

        int preIndex = indexDeck;
        do {
            indexDeck = preIndex;
            Collections.shuffle(deck);
            for (int i = 0; i < Math.min(slots.size(), deck.size()); i++) {

                set.add(deck.get(indexDeck));/** FOR OUT USING - TAMIR */
                indexDeck++; //update index deck
            }
        } while (env.util.findSets(set, 1).size() == 0);
        for (int i = 0; i < Math.min(slots.size(), deck.size()); i++) {
            table.placeCard(set.get(i), slots.get(i)); //place each card from deck in spot i
        }
    }

    private synchronized void waitingPlayersToFinish() {
        Player.stop.set(true);
        while (!everyOneIsFinished()) {
            try {
                wait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public boolean everyOneIsFinished() {
        for (Player p : players) {
            if (p.working.get())
                return false;
            if(!p.human &&p.AIworking.get())
                return false;
        }
        return true;
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        sleepingTime = 1000; //sleeping time for beginning
        while (!shouldFinish() && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            if (playerToCheck.isEmpty())
                continue;
            int id = playerToCheck.getFirst();
            int[] setAsSlot = players[id].getSetAsSlots(); //get the set by slots
            int[] setAsCards = players[id].getSetAsCards();//get the set by cards
            if (!checkSet(setAsCards, id)) {
                players[id].penalty();
                ArrayList<Integer> toRemove = new ArrayList<>();
                toRemove.add(id);
                removeFromLine(toRemove);
                continue;
            }
            waitingPlayersToFinish();
            players[id].point();
            removeFromLine(playerToRemove(setAsSlot));
            removeCardsFromTable(setAsSlot);
            placeCardsOnTable(setAsSlot);
            updateTimerDisplay(true);
            wakePlayersUp();
        }
    }

    private ArrayList<Integer> playerToRemove(int[] slots) {
        return table.getPlayersFromSlots(slots); //list of players to remove
    }

    public synchronized void checkSet(int playerId) {
        players[playerId].needToSleep.set(true);
        playerToCheck.add(playerId);
        synchronized (playerToCheck) {
            playerToCheck.notifyAll();
        }
    }

    private boolean checkSet(int[] cards, int id) {
        players[id].gotCheck = true;
        if (env.util.testSet(cards)) //check if set
            return true;
        else
            return false;
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        waitingPlayersToFinish();
        for (int i = 0; i < players.length; i++)
            players[i].terminate();
        wakePlayersUp();
        terminate = true;
        for (int i = 0; i < players.length; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable(int[] slots) {
        for (int i = 0; i < slots.length; i++) {
            int card = table.getCardFromSlot(slots[i]);
            deck.remove((Object) card); //remove the cards from the deck
            table.removeCard(card, slots[i], players); //remove the cards from the table
            indexDeck--; //update index deck
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable(int[] slots) {
        for (int i = 0; i < Math.min(slots.length, deck.size()); i++) {
            if (indexDeck == deck.size())
                break;
            table.placeCard(deck.get(indexDeck), slots[i]); //place each card in the set slots
            indexDeck++; //update index deck
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        synchronized (playerToCheck) {
            try {
                if (playerToCheck.isEmpty())
                    playerToCheck.wait(sleepingTime);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (reset) {//check if reset
            env.ui.setCountdown(60999, false);
            reshuffleTime = System.currentTimeMillis() + 60999;
        } else if (reshuffleTime - System.currentTimeMillis() > 10000) //check if the last 10 seconds
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), false);
        else {
            env.ui.setCountdown(Math.max(reshuffleTime - System.currentTimeMillis(), 0), true);
            sleepingTime = 5;
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        //stop players from playing
        ArrayList<Integer> allPlayers = new ArrayList<>();
        for (Player p : players) {
            allPlayers.add(p.id);
        }
        removeFromLine(allPlayers);
        table.removeAll(players); //remove all
    }

    private void removeFromLine(ArrayList<Integer> playersToRemove) {
        playerToCheck.removeAll(playersToRemove);
        for (Integer id : playersToRemove) {
            players[id].needToSleep.set(false);
            players[id].wakeUp();
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int max = 0;
        ArrayList<Integer> maxId = new ArrayList<Integer>(); //array for winners

        for (int i = 0; i < players.length; i++) {
            if (players[i].score() > max) { //update the winners
                max = players[i].score();
                maxId.clear();
                maxId.add(players[i].id);

            } else if (players[i].score() == max) //if tie add another winner
                maxId.add(players[i].id);
        }

        //switching to array
        int[] maxIdArray = new int[maxId.size()];
        for (int i = 0; i < maxIdArray.length; i++)
            maxIdArray[i] = maxId.get(i);

        env.ui.announceWinner(maxIdArray); //announce winners
    }

    public synchronized void playerFinishAndWakeUpDealer() {
        notifyAll();
    }

    public BlockingDeque<Integer> getPlayerToCheck(){
        return playerToCheck;
    }
}
