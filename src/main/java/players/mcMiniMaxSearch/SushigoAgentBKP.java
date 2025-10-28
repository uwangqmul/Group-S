package players.mcMiniMaxSearch;

import core.AbstractPlayer;
import core.AbstractGameState;
import core.actions.AbstractAction;
import games.sushigo.SGGameState;
import games.sushigo.actions.ChooseCard;
import games.sushigo.cards.SGCard;
import games.sushigo.cards.SGCard.SGCardType;
import core.components.Card;
import players.basicMCTS.BasicMCTSParams;
import players.basicMCTS.BasicMCTSPlayer;

import java.util.List;

public class SushigoAgentBKP extends AbstractPlayer{

    public SushigoAgentBKP() {
        super(null, "SushigoAgentBKP");
    }

    @Override
    public AbstractAction _getAction(AbstractGameState observation, List<AbstractAction> possibleActions) {
        SGGameState state = (SGGameState) observation;
        int playerId = state.getCurrentPlayer();

        AbstractAction bestAction = null;
        double maxHeuristicValue = -Double.MAX_VALUE;

        for (AbstractAction action : possibleActions) {
            if (action instanceof ChooseCard) {
                ChooseCard playCardAction = (ChooseCard) action;
                Card card = playCardAction.getCard(state);
                SGCardType cardToPlay = ((SGCard) card).type;
//                double heuristicValue = scoreCardValue(cardToPlay, state, playerId);
                double heuristicValue = SGHeuristic.estimateCardValue((SGCard) card, state, playerId);

                if (heuristicValue > maxHeuristicValue) {
                    maxHeuristicValue = heuristicValue;
                    bestAction = action;
                }
            }
        }

        return bestAction != null ? bestAction : possibleActions.get(0);
    }

//    private double scoreCardValue(SGCardType cardType, SGGameState state, int playerId) {
//        // Retrieve the player's current tableau (cards played in front of them this round)
//        // In the TAG framework, you would use state.getComponent(playerTableauId)
//        // Since we don't have the exact class structure, we use a conceptual getter.
//        Deck playerTableau = state.getPlayerHands().get(playerId);
//        System.out.print(playerTableau);
//
//        switch (cardType) {
//            case SquidNigiri:
//                return 10.0;
//            case SalmonNigiri:
//                return 7.0;
//            case EggNigiri:
//                return 4.0;
//
//            case Wasabi:
//                return 9.5;
//
//            case Sashimi:
//                int sashimiCount = playerTableau.getOrDefault(SGCardType.Sashimi, 0);
//                if (sashimiCount == 2) return 15.0;
//                if (sashimiCount == 1 || sashimiCount == 0) return 8.0;
//                return 0.5;
//
//            case Tempura:
//                int tempuraCount = playerTableau.getOrDefault(SGCardType.Tempura, 0);
//                return tempuraCount == 1 ? 6.0 : 3.0;
//
//            case Dumpling:
//                return 5.0 + playerTableau.getOrDefault(SGCardType.Dumpling, 0);
//
//            case Maki:
//                // You likely need to extract the number of maki rolls this card gives
//                int[] makiRolls = state.getPlayedCardTypes()[i].get(Maki).getValue();  // Assuming this method exists
//                int maxMaki = Arrays.stream(makiRolls).max().orElse(0);
//                return switch (maxMaki) {
//                    case 3 -> 4.0;
//                    case 2 -> 3.0;
//                    case 1 -> 2.0;
//                    default -> 1.0;
//                };
//
//            case Chopsticks:
//                return 0.5;
//
//            case Pudding:
//                return 0.1;
//
//            default:
//                return 0.0;
//    }
//    }

    @Override
    public SushigoAgentBKP copy() {
        return new SushigoAgentBKP();
    }
}
