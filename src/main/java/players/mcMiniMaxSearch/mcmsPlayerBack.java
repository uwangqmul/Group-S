package players.mcMiniMaxSearch;//package players.mcMiniMaxSearch;
//
//import core.AbstractGameState;
//import core.AbstractPlayer;
//import core.actions.AbstractAction;
//
//import java.util.*;
//
//public class mcmsPlayerBack extends AbstractPlayer {
//    private int depth;
//    private int numSamples;
//    private Random rng;
//
//
//    public mcmsPlayerBack(int id, int depth, int numSamples){
//        super(id);
//        this.depth = depth;
//        this.numSamples = numSamples;
//        this.rng = new Random();
//    }
//
//    @override
//    public AbstractAction _getAction(AbstractGameState stateObs, List<AbstractAction> actions) {
////        List<Action> actions = state.getLegalActions(this.id);
//
//        double alpha = Double.NEGATIVE_INFINITY;
//        double beta = Double.POSITIVE_INFINITY;
//        double bestValue = Double.NEGATIVE_INFINITY;
//        AbstractAction bestAction = actions.get(0);
//
//        // For each legal action, evaluate it using sparse sampling and minimax
//        for (AbstractAction action : actions) {
//            double value = evaluateChanceNode(stateObs.copy(), action, depth, alpha, beta);
//
//            if (value > bestValue) {
//                bestValue = value;
//                bestAction = action;
//            }
//            alpha = Math.max(alpha, bestValue);  // α-beta pruning
//        }
//
//        return bestAction;
//    }
//
//    private double evaluateState (AbstractGameState stateObs, int depth, double alpha, double beta){
//        if (!stateObs.isNotTerminal() || depth ==0){
//            return heuristic (stateObs);
//        }
//
//        int currentPlayer = stateObs.getCurrentPlayer();
//
//        if(currentPlayer == this.id){
//            double maxEval = Double.NEGATIVE_INFINITY;
//            for(AbstractAction action: stateObs.getQueuedAction());
//        }
//    }
//}
