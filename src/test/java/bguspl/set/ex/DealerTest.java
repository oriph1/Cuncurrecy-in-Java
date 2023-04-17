package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import static org.junit.jupiter.api.Assertions.*;

class DealerTest {
    private Env env;

    /**
     * Game entities.
     */
    Dealer dealer;
    private Table table;
    private Player[] players;



    @BeforeEach
    void setUp() {
        Properties properties = new Properties();
        properties.put("Rows", "2");
        properties.put("Columns", "2");
        properties.put("FeatureSize", "3");
        properties.put("FeatureCount", "4");
        properties.put("TableDelaySeconds", "0");
        properties.put("PlayerKeys1", "81,87,69,82");
        properties.put("PlayerKeys2", "85,73,79,80");
        TableTest.MockLogger logger = new TableTest.MockLogger();
        Config config = new Config(logger, properties);
        Integer[] slotToCard = new Integer[config.tableSize];
        Integer[] cardToSlot = new Integer[config.deckSize];
        env = new Env(logger, config, new TableTest.MockUserInterface(), new TableTest.MockUtil());
        table = new Table(env, slotToCard, cardToSlot);
        Player a = new Player(env, dealer, table, 0, true);
        players = new Player[]{a};
        dealer = new Dealer(env, table, players);

    }


    @Test
    void CheckAllPlayerFinished(){
        Player b = players[0];
        b.working.set(true);
        assertFalse(dealer.everyOneIsFinished());
        b.working.set(false);
        assertTrue(dealer.everyOneIsFinished());
    }
    @Test
    void AddingPlayerToCheckSet(){
        assertFalse(dealer.getPlayerToCheck().contains(players[0].id));
        dealer.checkSet(players[0].id);
        assertTrue(dealer.getPlayerToCheck().contains(players[0].id));
    }


}