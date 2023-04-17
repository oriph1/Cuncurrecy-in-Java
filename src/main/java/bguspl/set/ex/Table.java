package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    private int[][] slotToToken;

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        slotToToken = new int[slotToCard.length][env.config.players];
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     *
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {
        }

        //update the table
        cardToSlot[card] = slot;
        setCardFromSlot(slot, card);

        //update the interface
        env.ui.placeCard(card, slot);
    }

    /**
     * Removes a card from a grid slot on the table.
     *
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int card, int slot, Player[] players) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {
        }
        //update the table
        cardToSlot[card] = -1;
        setCardFromSlot(slot, -1);
        removeTokenFromPlayers(slot, players); //remove the tokens from the players
        removeSlotFromPlayerActions(slot, players);
        for (int i = 0; i < players.length; i++) //update the tokens array
            slotToToken[slot][i] = 0;

        //update the interface
        env.ui.removeTokens(slot);
        env.ui.removeCard(slot);

    }

    private void removeSlotFromPlayerActions(int slot, Player[] players) {
        for (int i = 0; i < players.length; i++)
            players[i].removeFromActionBlock(slot);
    }

    public void removeTokenFromPlayers(int slot, Player[] players) {
        for (int i = 0; i < players.length; i++)
            if (slotToToken[slot][i] == 1)
                players[i].removeToken(slot); //each player that placed token remove it
    }

    /**
     * Places a player token on a grid slot.
     *
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
        slotToToken[slot][player] = 1; //update the token array
        env.ui.placeToken(player, slot);//update the interface

    }

    /**
     * Removes a token of a player from a grid slot.
     *
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return - true iff a token was successfully removed.
     */
    public void removeToken(int player, int slot) {
        slotToToken[slot][player] = 0; //update the token array
        env.ui.removeToken(player, slot);//update the interface
    }

    public void removeAll(Player[] players) { //remove all the cards

        for (int i = 0; i < slotToCard.length; i++) {
            if (slotToCard[i] != null && slotToCard[i] != -1)
                removeCard(slotToCard[i], i, players); //remove each card
        }

    }

    public ArrayList<Integer> getPlayersFromSlots(int[] slots) {
        ArrayList<Integer> ans = new ArrayList<Integer>();

        for (int i = 0; i < slots.length; i++) {
            for (int j = 0; j < slotToToken[slots[i]].length; j++) {
                if (slotToToken[slots[i]][j] == 1 & !ans.contains(j)) //check in each slot for every player if placed token
                    ans.add(j);// add it to players to remove
            }
        }
        return ans;
    }

    public ArrayList<Integer> getSlotsWithCards() {
        ArrayList<Integer> ans = new ArrayList<>();

        for (int i = 0; i < slotToCard.length; i++) {
            if (slotToCard[i] != null)
                if (slotToCard[i] != -1)
                    ans.add(i);
        }

        return ans;
    }

    public int getCardFromSlot(int slot) {
        return slotToCard[slot];
    }

    public void setCardFromSlot(int slot, int card) {
        slotToCard[slot] = card;
    }
    public int[][] getSlotToToken(){
        return slotToToken;
    }

}