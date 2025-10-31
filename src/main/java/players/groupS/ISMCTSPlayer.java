package players.groupS;

import core.AbstractGameState;
import core.AbstractPlayer;
import core.actions.AbstractAction;
import core.interfaces.IStateHeuristic;

import java.util.List;
import java.util.Random;

/**
 * Information Set Monte Carlo Tree Search Player for Sushi Go!
 * Uses ISMCTSTreeNode to handle the search in imperfect information games.
 */
public class ISMCTSPlayer extends AbstractPlayer { // Renamed class

    Random rnd; // Random generator instance for the player and potentially the tree

    /**
     * Default constructor. Creates player with default parameters and a seed based on system time.
     */
    public ISMCTSPlayer() {
        // Use the constructor that takes params, providing default ISMCTSParams
        this(new ISMCTSParams(System.currentTimeMillis()));
    }

    /**
     * Constructor taking a parameters object.
     * @param params ISMCTSParams object containing configuration for the agent.
     */
    public ISMCTSPlayer(ISMCTSParams params) {
        super(params, "ISMCTS Player"); // Pass params to superclass and set player name
        // Initialize the random number generator using the seed from parameters
        rnd = new Random(params.getRandomSeed());
    }

    /**
     * Constructor allowing direct setting of the random seed.
     * @param seed The random seed to use.
     */
    public ISMCTSPlayer(long seed) {
        // Create default params first, then override the seed
        this(new ISMCTSParams());
        getParameters().setRandomSeed(seed); // Set seed in the parameters object
        rnd = new Random(seed); // Initialize this player's rnd instance
    }

    /**
     * Main method called by the game loop to determine the player's action.
     * This is where the ISMCTS search is initiated.
     * @param gameState The current game state from this player's perspective (their information set).
     * @param availableActions List of currently available actions (Note: ISMCTS typically computes this internally).
     * @return The best action found by the ISMCTS search, or a fallback random action if search fails.
     */
    @Override
    public AbstractAction _getAction(AbstractGameState gameState, List<AbstractAction> availableActions) {
        // Retrieve the correct parameters instance associated with this player object
        ISMCTSParams currentParams = getParameters();

        // Create the root node of the ISMCTS tree.
        // The gameState passed is crucial - it must represent the player's current information set.
        ISMCTSTreeNode root = new ISMCTSTreeNode(this, null, gameState, rnd);

        // --- Initiate the ISMCTS search ---
        // The mctsSearch method within ISMCTSTreeNode contains the main algorithm loop.
        root.mctsSearch();

        // --- Retrieve the best action ---
        // The bestAction method in ISMCTSTreeNode typically selects based on visits or value.
        AbstractAction chosenAction = root.bestAction();

        // --- Fallback Mechanism ---
        // If the search somehow fails to return an action (e.g., zero budget, immediate terminal state with no actions),
        // fall back to a random legal action from the current information set state.
        if (chosenAction == null) {
            System.err.println("WARNING: ISMCTS search returned null action for player " + getPlayerID() + ". Falling back to random.");
            // Get currently legal actions from the info set perspective
            List<AbstractAction> currentLegalActions = getForwardModel().computeAvailableActions(gameState, currentParams.actionSpace);
            if (!currentLegalActions.isEmpty()) {
                // Pick a random one if available
                chosenAction = currentLegalActions.get(rnd.nextInt(currentLegalActions.size()));
            } else {
                // This should only happen if the game is over or in an error state
                System.err.println("FATAL: No available actions from game state in ISMCTSPlayer fallback for player " + getPlayerID() + ".");
                // Returning null might cause issues, depends on the game framework.
                // Consider if a specific 'pass' or 'do nothing' action exists for Sushi Go!
                return null;
            }
        }
        return chosenAction;
    }

    /**
     * Casts and returns the parameters object for this player.
     * @return The ISMCTSParams instance associated with this player.
     */
    @Override
    public ISMCTSParams getParameters() {
        // Cast the generic 'parameters' field from AbstractPlayer to our specific type
        return (ISMCTSParams) parameters;
    }

    /**
     * Optional: Allows setting the heuristic function externally after player creation.
     * @param heuristic The IStateHeuristic implementation to use for rollouts.
     */
    public void setStateHeuristic(IStateHeuristic heuristic) {
        // Access parameters and set the heuristic
        getParameters().heuristic = heuristic;
    }

    /**
     * Returns the name of the player type.
     * @return String "ISMCTSPlayer".
     */
    @Override
    public String toString() {
        return "ISMCTSPlayer"; // Updated player name
    }

    /**
     * Creates a copy of this player instance.
     * Essential for running experiments where multiple copies of the agent might be needed.
     * @return A new ISMCTSPlayer instance with copied parameters.
     */
    @Override
    public ISMCTSPlayer copy() {
        // Create a new player instance, passing a *copy* of the parameters
        return new ISMCTSPlayer((ISMCTSParams) parameters.copy());
    }

    /**
     * Provides access to the player's random number generator, potentially needed by the tree node.
     * @return The Random instance for this player.
     */
    public Random getRnd() {
        return rnd;
    }
}