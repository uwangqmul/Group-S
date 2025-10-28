package players.mcMiniMaxSearch;

import games.sushigo.SGGameState;
import games.sushigo.cards.SGCard;
import games.sushigo.cards.SGCard.SGCardType;
import games.sushigo.SGParameters;

public class SGHeuristic {

    /**
     * Lets try to estimates the immediate value of playing a given card for the player.
     * Considering current tableau, combos, Wasabi, and points.
     */

    public static double estimateCardValue(SGCard card, SGGameState gs, int playerId) {
        SGCardType type = card.type;
        SGParameters params = (SGParameters) gs.getGameParameters();
        int value = 0;

        // Access played card counters
        int tempuraCount = gs.getPlayedCardTypes()[playerId].get(SGCard.SGCardType.Tempura).getValue();
        int sashimiCount = gs.getPlayedCardTypes()[playerId].get(SGCard.SGCardType.Sashimi).getValue();
        int dumplingCount = gs.getPlayedCardTypes()[playerId].get(SGCard.SGCardType.Dumpling).getValue();
        int wasabiCount = gs.getPlayedCardTypes()[playerId].get(SGCard.SGCardType.Wasabi).getValue();
        int makiCount = gs.getPlayedCardTypes()[playerId].get(SGCard.SGCardType.Maki).getValue();

        switch (type) {
            case Tempura:
                if ((tempuraCount + 1) % 2 == 0) {
                    value += params.valueTempuraPair;
                }
                break;

            case Sashimi:
                // Sashimi gives points for triplets
                if ((sashimiCount + 1) % 3 == 0) {
                    value += params.valueSashimiTriple;
                }
                break;

            case Dumpling:
                // Dumpling gives increasing points per number collected
                int nextCount = dumplingCount + 1;
                int idx = Math.min(nextCount, params.valueDumpling.length) - 1;
                value += params.valueDumpling[idx];
                break;

            case SquidNigiri:
                value += params.valueSquidNigiri;
                if (wasabiCount > 0) {
                    value *= params.multiplierWasabi;
                }
                break;

            case SalmonNigiri:
                value += params.valueSalmonNigiri;
                if (wasabiCount > 0) {
                    value *= params.multiplierWasabi;
                }
                break;

            case EggNigiri:
                value += params.valueEggNigiri;
                if (wasabiCount > 0) {
                    value *= params.multiplierWasabi;
                }
                break;

            case Wasabi:
                // Wasabi itself is worth nothing until used on a Nigiri
                value = 0;
                break;

            case Chopsticks:
                // Chopsticks allow a second card play, approximate value as average card value (simplified)
                value = 2; // placeholder, can improve by expected card value
                break;

            case Maki:
                // Maki value is relative; we can approximate as 1 per roll
                value = card.count; // 1,2,3 rolls
                break;

            case Pudding:
                // Pudding points are end-game dependent; assign a small heuristic
                value = 1; // placeholder
                break;

            default:
                value = 0;
        }

        return value;
    }
}