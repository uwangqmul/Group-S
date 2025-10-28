//package players.mcMiniMaxSearch;
//
//import core.AbstractGameState;
//import core.AbstractPlayer;
//import core.actions.AbstractAction;
//import games.sushigo.SGGameState;
//
//import java.util.List;
//
//public abstract class SushigoAgent extends AbstractPlayer{
//
//    private static final int MAX_DEPTH = 3;
//    private static final int NUM_SAMPLES = 5;
//    private static final Random random = new Random();
//
//    public SushigoAgent() {
//        super(null, "SushiGoAgent");
//    }
//
//    @Override
//    public AbstractAction _getAction(AbstractGameState observation, List<AbstractAction> possibleActions) {
//        SGGameState state = (SGGameState) observation;
//        int playerId = state.getCurrentPlayer();
//        AbstractAction bestAction = possibleActions.get(0);
//        double bestValue = Double.NEGATIVE_INFINITY;
//
//        for (AbstractAction action : possibleActions) {
//            SGGameState nextState = (SGGameState) state.copy();
//            action.execute(nextState);
//            double value = MCStar2SS(nextState, MAX_DEPTH, -Double.MAX_VALUE, Double.MAX_VALUE, false, playerId);
//
//            if (value > bestValue) {
//                bestValue = value;
//                bestAction = action;
//            }
//        }
//        return bestAction != null ? bestAction : possibleActions.get(0);
//    }
//
//    /**
//     * Monte Carlo ★-Minimax (Star2SS)
//     * @param state  Current game state
//     * @param depth  Remaining depth
//     * @param alpha  Lower bound (alpha)
//     * @param beta   Upper bound (beta)
//     * @param isMaximizing True if current player is maximizing
//     * @param rootPlayer The root player (agent)
//     * @return Estimated heuristic value
//     */
//    private double MCStar2SS(SGGameState state, int depth, double alpha, double beta, boolean isMaximizing, int rootPlayer) {
//        // Terminal or depth limit reached
//        if (depth == 0 || state.isGameOver()) {
//            return SGHeuristic.evaluateState(state, rootPlayer); have to override this from the interface
//        }
//
//        // Handle CHANCE node/random events such as card draws
//        if (isChanceNode(state)) {
//            double totalValue = 0.0;
//
//            for (int i = 0; i < NUM_SAMPLES; i++) {
//                SGGameState sampledState = sampleChanceOutcome(state);
//                double sampleValue = MCStar2SS(sampledState, depth - 1, alpha, beta, isMaximizing, rootPlayer);
//                totalValue += sampleValue;
//            }
//
//            // Average of samples
//            return totalValue / NUM_SAMPLES;
//        }
//
//        //guys, with this line, im trying to access possible moves so that our
//        // minimax/Monte Carlo search can use it to build its tree and simulate. more when we meet
//        List<AbstractAction> legalActions = state.getCardChoices().get(state.getCurrentPlayer());
//
//        // MAX node (AI's turn)
//        if (isMaximizing) {
//            double value = Double.NEGATIVE_INFINITY;
//
//            for (AbstractAction action : legalActions) {
//                SGGameState nextState = (SGGameState) state.copy();
//                action.execute(nextState);
//
//                double childValue = MCStar2SS(nextState, depth - 1, alpha, beta, false, rootPlayer);
//                value = Math.max(value, childValue);
//                alpha = Math.max(alpha, value);
//
//                // Star2-like pruning condition
//                if (beta <= alpha) {
//                    break; // β cutoff
//                }
//            }
//            return value;
//        }
//        // MIN node (opponent’s turn)
//        else {
//            double value = Double.POSITIVE_INFINITY;
//
//            for (AbstractAction action : legalActions) {
//                SGGameState nextState = (SGGameState) state.copy();
//                action.execute(nextState);
//
//                double childValue = MCStar2SS(nextState, depth - 1, alpha, beta, true, rootPlayer);
//                value = Math.min(value, childValue);
//                beta = Math.min(beta, value);
//
//                // Star2-like pruning condition
//                if (beta <= alpha) {
//                    break; // α cutoff
//                }
//            }
//            return value;
//        }
//    }
//
//}
