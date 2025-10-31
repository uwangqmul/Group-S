package players.groupS;

import core.AbstractGameState;
import core.interfaces.IStateHeuristic;
import players.PlayerParameters;

import java.util.Arrays;

/**
 * Parameters for Information Set MCTS Player.
 * Extends basic MCTS parameters. Add new ISMCTS specific params here if needed.
 */
public class ISMCTSParams extends PlayerParameters { // Renamed class

    //Standard MCTS Parameters
    public double K = Math.sqrt(2); // UCB exploration constant
    public int rolloutLength = 10; // Max depth for rollouts
    public int maxTreeDepth = 100; // Max depth of the tree search itself
    public double epsilon = 1e-6; // Small value for tie-breaking and avoiding division by zero
    public IStateHeuristic heuristic = AbstractGameState::getHeuristicScore; // Heuristic used at the end of rollouts

    //Add ISMCTS specific parameters below

    /**
     * Constructor for ISMCTS Parameters. Sets up default values and tunable ranges.
     */
    public ISMCTSParams() {
        this(System.currentTimeMillis()); // Use current time as default seed
    }

    /**
     * Constructor with a specific random seed.
     * @param seed The random seed to use.
     */
    public ISMCTSParams(long seed) {
        super();// Pass seed to the superclass (PlayerParameters)
        setRandomSeed(seed);
        // Define tunable parameters (same as BasicMCTSParams initially)
        addTunableParameter("K", Math.sqrt(2), Arrays.asList(0.0, 0.1, 1.0, Math.sqrt(2), 3.0, 10.0));
        addTunableParameter("rolloutLength", 10, Arrays.asList(0, 3, 10, 30, 100));
        addTunableParameter("maxTreeDepth", 100, Arrays.asList(1, 3, 10, 30, 100));
        addTunableParameter("epsilon", 1e-6);
        addTunableParameter("heuristic", (IStateHeuristic) AbstractGameState::getHeuristicScore);
        // Add new tunable parameters for ISMCTS specific features here
    }

    /**
     * Resets the parameters based on the values stored in the tunable parameters map.
     * Called when parameters might have been adjusted externally (e.g., during tuning).
     */
    @Override
    public void _reset() {
        super._reset(); // Handles framework parameters like budget, seed etc.
        // Re-assign standard MCTS parameters from the map
        K = (double) getParameterValue("K");
        rolloutLength = (int) getParameterValue("rolloutLength");
        maxTreeDepth = (int) getParameterValue("maxTreeDepth");
        epsilon = (double) getParameterValue("epsilon");
        heuristic = (IStateHeuristic) getParameterValue("heuristic");
        // Reset any new ISMCTS specific parameters from the map here
    }

    /**
     * Creates a copy of the parameters object.
     * Important for ensuring players in experiments don't share parameter instances.
     * Relies on TunableParameters.copy() mechanism in the superclass.
     * @return A new ISMCTSParams object.
     */
    @Override
    protected ISMCTSParams _copy() { // Return type matches the class name
        // Create a new instance using the current random seed
        ISMCTSParams copy = new ISMCTSParams(getRandomSeed());
        // The superclass's copy mechanism should handle copying the tunable parameter values.
        // If you add local non-tunable fields, you need to copy them manually here.
        return copy;
    }

    /**
     * Getter for the state evaluation heuristic. Required by the interface.
     * @return The IStateHeuristic instance.
     */
    @Override
    public IStateHeuristic getStateHeuristic() {
        return heuristic;
    }

    /**
     * Instantiates the player class associated with these parameters.
     * This is crucial for the framework to create your player.
     * @return A new ISMCTSPlayer instance configured with a copy of these parameters.
     */
    @Override
    public ISMCTSPlayer instantiate() { // Return type matches your player class name
        // Ensure ISMCTSPlayer class is accessible (same package or imported)
        return new ISMCTSPlayer((ISMCTSParams) this.copy()); // Pass a copy of params
    }
}