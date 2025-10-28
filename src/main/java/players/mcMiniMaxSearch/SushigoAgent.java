package players.mcMiniMaxSearch;

import core.AbstractGameState;
import core.AbstractPlayer;
import core.actions.AbstractAction;
import core.components.Card;
import games.sushigo.SGGameState;
import games.sushigo.actions.ChooseCard;
import games.sushigo.cards.SGCard;
import games.sushigo.cards.SGCard.SGCardType;

import java.util.List;

public abstract class SushigoAgentBack2 extends AbstractPlayer{

    private static final int MAX_DEPTH = 3;
    private static final int NUM_SAMPLES = 5;
    private static final Random random = new Random();

    public SushigoAgentBack2() {
        super(null, "SushiGoAgent");
    }

    @Override
    public AbstractAction _getAction(AbstractGameState observation, List<AbstractAction> possibleActions) {
        SGGameState state = (SGGameState) observation;
        int playerId = state.getCurrentPlayer();
        AbstractAction bestAction = possibleActions.get(0);
        double bestValue = Double.NEGATIVE_INFINITY;

        for (AbstractAction action : possibleActions) {
            SGGameState nextState = (SGGameState) state.copy();
            action.execute(nextState);
            double value = MCStar2SS(nextState, MAX_DEPTH, -Double.MAX_VALUE, Double.MAX_VALUE, false, playerId);

            if (value > bestValue) {
                bestValue = value;
                bestAction = action;
            }
        }
        return bestAction != null ? bestAction : possibleActions.get(0);
    }
}
