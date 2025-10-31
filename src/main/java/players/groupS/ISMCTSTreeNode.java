package players.groupS;

import core.AbstractGameState;
import core.actions.AbstractAction;
import core.components.Deck;
import games.sushigo.SGGameState;
import games.sushigo.actions.ChooseCard;
import games.sushigo.cards.SGCard;
import players.PlayerConstants;
import players.simple.RandomPlayer;
// import groupA.HeuristicSushiGoPlayer;

import java.util.*;

import static players.PlayerConstants.*;
import static utilities.Utils.noise;

/**
 * Represents a node in the Information Set MCTS tree for Sushi Go!
 */
class ISMCTSTreeNode {
    //Tree Structure
    ISMCTSTreeNode root;
    ISMCTSTreeNode parent;
    // Map from action taken to reach child, to the child node itself
    Map<AbstractAction, ISMCTSTreeNode> children = new HashMap<>();
    final int depth;

    //MCTS Statistics
    private double totValue; // Sum of simulation results backed up through this node
    private int nVisits;     // Number of times this node was visited during backpropagation

    //Global Counter (only used by root)
    private int fmCallsCount;

    //References
    private ISMCTSPlayer player; // The player instance this node belongs to
    private ISMCTSParams params; // The parameters governing the search
    private Random rnd;        // Random number generator

    //Rollout
    // Start with a Random player, replace with your heuristic player later
    private RandomPlayer rolloutPlayer = new RandomPlayer(); // TODO: Replace with HeuristicSushiGoPlayer

    //State
    // The state *as known by the player* at this node (Information Set)
    private SGGameState informationSetState;

    /**
     * Constructor for creating a new node.
     */
    protected ISMCTSTreeNode(ISMCTSPlayer player, ISMCTSTreeNode parent, AbstractGameState currentInfoSetState, Random rnd) {
        this.player = player;
        this.params = player.getParameters(); // Get parameters from player
        this.parent = parent;
        this.root = parent == null ? this : parent.root; // Set root reference
        this.rnd = rnd;

        // Ensure state is the correct type and store it
        if (!(currentInfoSetState instanceof SGGameState)) {
            throw new IllegalArgumentException("ISMCTSTreeNode requires an SGGameState, but received: " + currentInfoSetState.getClass().getSimpleName());
        }
        this.informationSetState = (SGGameState) currentInfoSetState;

        // Initialize statistics
        this.totValue = 0.0;
        this.nVisits = 0;

        // Set depth and initialize root counter
        if (parent != null) {
            this.depth = parent.depth + 1;
        } else {
            this.depth = 0;
            this.fmCallsCount = 0; // Only root tracks total FM calls
        }

        // Initialize children map (will be populated dynamically)
        this.children = new HashMap<>();

        // Set up the rollout player
        rolloutPlayer.setForwardModel(player.getForwardModel());
        // rolloutPlayer.setParameters(...); // If your rollout player needs specific params
    }

    /**
     * Main ISMCTS search loop executed from the root node.
     */
    void mctsSearch() {
        //Budgeting setup
        long startTime = System.currentTimeMillis();
        long timeBudget = (params.budgetType == BUDGET_TIME) ? params.budget : Long.MAX_VALUE;
        int iterationBudget = (params.budgetType == BUDGET_ITERATIONS) ? params.budget : Integer.MAX_VALUE;
        long fmCallBudget = (params.budgetType == BUDGET_FM_CALLS) ? params.budget : Long.MAX_VALUE;
        int numIters = 0;

        // Main loop continues until a budget is exceeded or game ends
        while (true) {
            //Check stopping conditions
            if (params.budgetType == BUDGET_TIME) {
                long timeTaken = System.currentTimeMillis() - startTime;
                if (timeTaken >= timeBudget) break;
            } else if (params.budgetType == BUDGET_ITERATIONS) {
                if (numIters >= iterationBudget) break;
            } else if (params.budgetType == BUDGET_FM_CALLS) {
                if (root.fmCallsCount >= fmCallBudget) break; // Check global counter at root
            }
            if (!this.informationSetState.isNotTerminal()) { // Check if root state is terminal
                break;
            }

            //ISMCTS Single Iteration
            // 1. Create Determinization (a full 'guess' state for this iteration)
            AbstractGameState determinizedState = createDeterminization();

            // 2. Selection & Expansion (traverse tree guided by determinization)
            AbstractGameState determinizedStateAfterTreePhase = determinizedState; // Passed by reference effectively
            ISMCTSTreeNode selectedNode = treePolicy(determinizedStateAfterTreePhase);

            // 3. Simulation (Rollout using a determinization from the selected node)
            int playerID = selectedNode.informationSetState.getCurrentPlayer();
            AbstractGameState determinizedForRollout = selectedNode.informationSetState.copy(playerID);
            double delta = selectedNode.rollOut(determinizedForRollout);

            // 4. Backpropagation (update stats in the info set tree)
            selectedNode.backUp(delta);
            //End ISMCTS Iteration

            numIters++;
        }
    }

    /**
     * Creates a determinization using the game state's built-in copy method.
     * This method shuffles unknown cards (opponent hands, draw pile).
     * @return A perfect-information copy of the game state based on the current node's info set.
     */
    private AbstractGameState createDeterminization() {
        // Return immediately if the game represented by this node is already over
        if (!this.informationSetState.isNotTerminal()) {
            return this.informationSetState;
        }
        // Use the copy method from SGGameState designed for redeterminization
        // This relies on SGGameState._copy(playerId) correctly handling hidden info shuffling
        return this.informationSetState.copy(player.getPlayerID());
    }

    /**
     * Traverses the information set tree according to UCB, guided by the determinization.
     * Expands a node if an unexpanded action (legal in the determinization) is found.
     * Modifies the passed-in determinizedState based on the path taken.
     * @param determinizedStateToAdvance The perfect-information state (will be modified).
     * @return The node selected for rollout.
     */
    private ISMCTSTreeNode treePolicy(AbstractGameState determinizedStateToAdvance) {
        ISMCTSTreeNode currentInfoSetNode = this;

        // Descend until a terminal state, max depth, or expandable node is found
        while (currentInfoSetNode.informationSetState.isNotTerminal() && currentInfoSetNode.depth < params.maxTreeDepth) {

            // Ensure the children map includes placeholders for all actions legal in the current determinization
            currentInfoSetNode.ensureChildrenExist(determinizedStateToAdvance);

            // Find actions that are legal in the determinization AND haven't been expanded yet
            List<AbstractAction> unexpandedActionsInDet = currentInfoSetNode.unexpandedActions(determinizedStateToAdvance);

            if (!unexpandedActionsInDet.isEmpty()) {
                //Expansion Phase
                // Expand the first legal unexpanded action found
                return currentInfoSetNode.expand(unexpandedActionsInDet, determinizedStateToAdvance);
            } else {
                //Selection Phase
                // Node is fully expanded for actions known/legal in this determinization.
                // Select the best child based on UCB, considering only actions LEGAL in the determinization.
                AbstractAction actionChosen = currentInfoSetNode.ucb(determinizedStateToAdvance);

                if (actionChosen == null) {
                    // No legal or promising child found (e.g., all have terrible values, or state became terminal unexpectedly)
                    break; // End traversal here, rollout from current node
                }

                // Move to the chosen child node
                ISMCTSTreeNode nextNode = currentInfoSetNode.children.get(actionChosen);
                if (nextNode == null) {
                    // This is unexpected if ensureChildrenExist and unexpandedActions work correctly
                    System.err.println("ERROR: Selected action " + actionChosen + " in UCB has null child node! Parent: " + currentInfoSetNode.informationSetState.getGameID());
                    // Break here, rollout will happen from currentInfoSetNode
                    break;
                }
                currentInfoSetNode = nextNode;

                // Advance the determinized state according to the chosen action (modifies the object)
                advance(determinizedStateToAdvance, actionChosen.copy());
            }
        }
        // Return the node where the traversal stopped
        return currentInfoSetNode;
    }

    /**
     * Ensures entries (with potentially null values) exist in the children map
     * for all actions that are legal in the given determinized state.
     * This is important for tracking possible opponent moves.
     * @param determinizedState The current determinized state.
     */
    private void ensureChildrenExist(AbstractGameState determinizedState) {
        if (!informationSetState.isNotTerminal()) return; // Don't add children to terminal nodes

        // Get actions legal in the perfect-information determinized state
        List<AbstractAction> legalActionsInDeterminization = player.getForwardModel().computeAvailableActions(determinizedState, params.actionSpace);
        // For each legal action, make sure there's an entry in this node's children map
        for (AbstractAction action : legalActionsInDeterminization) {
            children.putIfAbsent(action, null); // Add action with null node if not already present
        }
    }

    /**
     * Finds actions that are legal in the determinized state but haven't yet been expanded (value is null in map).
     * @param determinizedState The current determinized state.
     * @return A list of legal, unexpanded actions.
     */
    private List<AbstractAction> unexpandedActions(AbstractGameState determinizedState) {
        List<AbstractAction> legalActionsInDeterminization = player.getForwardModel().computeAvailableActions(determinizedState, params.actionSpace);
        List<AbstractAction> unexpanded = new ArrayList<>();
        // Iterate through actions legal in the *determinized* state
        for (AbstractAction action : legalActionsInDeterminization) {
            // Check if this action is known (should be due to ensureChildrenExist) AND if its node is null (unexpanded)
            if (children.containsKey(action) && children.get(action) == null) {
                unexpanded.add(action);
            }
        }
        return unexpanded;
    }

    /**
     * Expands the current node by creating a new child node for a chosen unexpanded action.
     * Advances the determinized state and calculates the information set for the new node.
     * @param unexpandedActionsInDet List of legal, unexpanded actions.
     * @param determinizedStateToAdvance The determinized state (will be advanced).
     * @return The newly created child node.
     */
    private ISMCTSTreeNode expand(List<AbstractAction> unexpandedActionsInDet, AbstractGameState determinizedStateToAdvance) {
        // Choose one of the unexpanded actions randomly
        AbstractAction chosenAction = unexpandedActionsInDet.get(rnd.nextInt(unexpandedActionsInDet.size()));

        // Advance the determinized state according to this action (modifies the object)
        advance(determinizedStateToAdvance, chosenAction.copy());

        // Derive what the player would know (information set) in the state *after* the action
        AbstractGameState nextInfoSetState = getInformationSetState(determinizedStateToAdvance, player.getPlayerID());

        // Create the new node using this information set state
        ISMCTSTreeNode newNode = new ISMCTSTreeNode(player, this, nextInfoSetState, rnd);
        // Link the chosen action to this new node in the parent's children map
        children.put(chosenAction, newNode);

        return newNode;
    }

    /**
     * Derives the information set state (player's view) from a complete state for Sushi Go!
     * @param fullState The complete, perfect-information state (e.g., after an action in a determinization).
     * @param playerId The ID of the player whose perspective is needed.
     * @return A new AbstractGameState representing only what the player knows.
     */
    private AbstractGameState getInformationSetState(AbstractGameState fullState, int playerId) {
        // Return terminal states directly, information is complete
        if (!fullState.isNotTerminal()) return fullState;

        // Ensure we are working with the correct game state type
        if (!(fullState instanceof SGGameState)) {
            System.err.println("ERROR: getInformationSetState expected SGGameState but received " + fullState.getClass().getSimpleName());
            return fullState.copy(); // Return a basic copy on error
        }
        SGGameState sgFullState = (SGGameState) fullState;

        // Create a full copy first (use -1 to prevent internal redeterminization)
        SGGameState infoSetView = (SGGameState) fullState.copy(-1);

        //Apply Sushi Go! Specific Information Hiding
        for (int p = 0; p < infoSetView.getNPlayers(); p++) {
            // Check visibility using the original full state's rules
            if (p != playerId && !sgFullState.isHandKnown(playerId, p)) {
                // Get the hand deck for the opponent in the copied state
                Deck<SGCard> opponentHand = infoSetView.getPlayerHands().get(p);
                if (opponentHand != null) {
                    // Clear the contents - player shouldn't see the cards
                    // Assumes the Deck component tracks its capacity/size separately
                    opponentHand.clear();
                }
            }
        }

        //Hide Draw Pile
        // Access draw pile via getter (assuming one exists or state is accessible)
        // If no getter and not accessible, this step might be impossible without modifying SGGameState.
        // Let's assume we cannot modify it, so we skip hiding draw pile details beyond what copy(-1) does.
        /*
        Deck<SGCard> drawPile = infoSetView.getDrawPile(); // Needs public getter in SGGameState
        if (drawPile != null) {
            drawPile.clear(); // Hide contents
        }
        */

        //Hide Opponent Choices
        // SGGameState._copy(-1) might already handle this, but explicit clearing is safer.
        List<List<ChooseCard>> cardChoices = infoSetView.getCardChoices(); // Assuming getter and AbstractAction type
        if (cardChoices != null) {
            for (int p = 0; p < infoSetView.getNPlayers(); p++) {
                if (p != playerId && cardChoices.size() > p && cardChoices.get(p) != null) {
                    cardChoices.get(p).clear();
                }
            }
        }

        return infoSetView;
    }


    /**
     * Selects the best child action using the UCB formula, filtered by legality in the determinization.
     * @param determinizedState The current determinized state.
     * @return The best AbstractAction according to UCB, or null.
     */
    private AbstractAction ucb(AbstractGameState determinizedState) {
        AbstractAction bestAction = null;
        double bestValue = -Double.MAX_VALUE;

        // Determine which actions are legal in the current *determinized* state
        List<AbstractAction> legalActionsInDeterminization = player.getForwardModel().computeAvailableActions(determinizedState, params.actionSpace);
        Set<AbstractAction> legalActionSet = new HashSet<>(legalActionsInDeterminization); // For quick lookups

        int parentVisitCount = this.nVisits;
        boolean foundLegalChild = false; // Track if we found any valid child to select

        // Iterate through all known children of this information set node
        for (Map.Entry<AbstractAction, ISMCTSTreeNode> entry : children.entrySet()) {
            AbstractAction action = entry.getKey();
            ISMCTSTreeNode childNode = entry.getValue();

            //ISMCTS Check
            // Consider only children that:
            // 1. Have been expanded (node exists: childNode != null)
            // 2. Correspond to an action currently legal in the *determinized* state
            if (childNode != null && legalActionSet.contains(action)) {
                foundLegalChild = true; // We have a valid candidate

                //Calculate UCB Components
                // Exploitation: Average value from simulations through the child
                double exploitationTerm = childNode.totValue / (childNode.nVisits + params.epsilon);

                // TODO: Add Coulom's heuristic bonus to exploitationTerm if implementing

                // Exploration: Bonus for less-visited children
                // TODO: Implement subset-armed bandit refinement if desired (using availability counts)
                double explorationTerm = params.K * Math.sqrt(Math.log(Math.max(1, parentVisitCount)) / (childNode.nVisits + params.epsilon));

                //Combine based on player to move
                // Check whose turn it is in the *information set* state of the current node
                boolean iAmMoving = informationSetState.getCurrentPlayer() == player.getPlayerID();
                double uctValue = iAmMoving ? exploitationTerm : -exploitationTerm; // Maximize own score, minimize opponent's
                uctValue += explorationTerm; // Add exploration bonus

                // Add noise for random tie-breaking
                uctValue = noise(uctValue, params.epsilon, rnd.nextDouble());

                //Track Best Action
                if (bestAction == null || uctValue > bestValue) {
                    bestValue = uctValue;
                    bestAction = action;
                }
            }
        } // End loop through children

        //Handle Selection Failures
        if (bestAction != null) {
            root.fmCallsCount++; // Count FM call if we successfully select a child
        } else if (foundLegalChild) {
            // This case indicates an issue: we had legal, expanded children, but didn't select one.
            System.err.println("WARNING: UCB found legal children but failed to select one. Check for NaN/Infinity. Falling back.");
            bestAction = getRandomLegalActionFallback(determinizedState); // Fallback using determinized state
        } else if (informationSetState.isNotTerminal() && !legalActionsInDeterminization.isEmpty()) {
            // No *expanded* children were legal in this determinization, but the state is not terminal
            // and there *are* legal actions in the determinization (they just haven't been expanded from this info set node yet).
            // Return a random legal action from the determinization. Tree policy will stop here.
            bestAction = legalActionsInDeterminization.get(rnd.nextInt(legalActionsInDeterminization.size()));
        }
        // If state is terminal or no actions are legal at all in the determinization, bestAction remains null.

        return bestAction;
    }


    /**
     * Performs a Monte Carlo rollout (simulation) from a given determinized state.
     * @param determinizedState The perfect-information state from which to start the rollout.
     * @return The evaluated score (using the heuristic) of the final state reached.
     */
    /**
     * Performs a Monte Carlo rollout (simulation) from a given determinized state.
     * Includes safety checks to avoid framework crashes (e.g., empty hands).
     */
    /**
     * Safe wrapper to compute actions — catches framework crashes.
     */
    private List<AbstractAction> safeComputeAvailableActions(AbstractGameState state) {
        try {
            List<AbstractAction> actions = player.getForwardModel().computeAvailableActions(state, params.actionSpace);
            return (actions != null) ? actions : new ArrayList<>();
        } catch (Exception e) {
            // Example: empty hand in Sushi Go -> IndexOutOfBounds
            // System.err.println("[SAFE ACTIONS] Skipping invalid state: " + e);
            return new ArrayList<>();
        }
    }

    private double rollOut(AbstractGameState determinizedState) {
        int rolloutDepth = 0;
        AbstractGameState rolloutState = determinizedState.copy();

        // Only rollout if the state is not terminal and rollouts are enabled
        if (params.rolloutLength > 0 && rolloutState.isNotTerminal()) {
            while (!finishRollout(rolloutState, rolloutDepth)) {

                //Safely get available actions
                List<AbstractAction> allAvailableActions = safeComputeAvailableActions(rolloutState);

                //Stop early if no actions are available
                if (allAvailableActions == null || allAvailableActions.isEmpty()) {
                    // Possibly an invalid determinization (empty hand etc.)
                    // Stop rollout gracefully instead of crashing
                    break;
                }

                //Filter out "useChopsticks" actions
                List<AbstractAction> rolloutActions = new ArrayList<>();
                for (AbstractAction action : allAvailableActions) {
                    if (action instanceof games.sushigo.actions.ChooseCard) {
                        games.sushigo.actions.ChooseCard chooseAction = (games.sushigo.actions.ChooseCard) action;
                        if (!chooseAction.useChopsticks) {
                            rolloutActions.add(action);
                        }
                    } else {
                        rolloutActions.add(action);
                    }
                }

                //If still no valid actions after filtering, end rollout
                if (rolloutActions.isEmpty()) {
                    break;
                }

                //Choose random (or heuristic) rollout action
                AbstractAction next = rolloutPlayer.getAction(rolloutState, rolloutActions);

                //Advance the state safely
                try {
                    advance(rolloutState, next);
                } catch (Exception e) {
                    // If FM or action fails, stop the rollout cleanly
                    // System.err.println("Rollout advance failed: " + e);
                    break;
                }

                rolloutDepth++;
            }
        }

        //Evaluate final state using heuristic
        double value;
        try {
            value = params.heuristic.evaluateState(rolloutState, player.getPlayerID());
        } catch (Exception e) {
            // If heuristic crashes for any reason, neutral score
            // System.err.println("Heuristic failed in rollout: " + e);
            value = 0.0;
        }

        // Safety check for invalid values
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            // System.err.printf("Invalid heuristic value %.2f, replacing with 0.0%n", value);
            value = 0.0;
        }

        return value;
    }

//    private double rollOut(AbstractGameState determinizedState) { // Takes state to rollout FROM
//        int rolloutDepth = 0;
//        // Work on a copy of the determinized state so the original isn't changed
//        AbstractGameState rolloutState = determinizedState.copy();
//
//        // Only rollout if the state is not terminal and rollouts are enabled (length > 0)
//        if (params.rolloutLength > 0 && rolloutState.isNotTerminal()) {
//            // Loop until game ends or max rollout depth is reached
//            while (!finishRollout(rolloutState, rolloutDepth)) {
//
//                // --- Get All Available Actions ---
//                // Ask the game rules what moves are possible in the current rollout state
//                List<AbstractAction> allAvailableActions = player.getForwardModel().computeAvailableActions(rolloutState, rolloutPlayer.getParameters().actionSpace);
//
//                // --- Filter out Chopsticks Actions ---
//                List<AbstractAction> rolloutActions = new ArrayList<>();
//                if (allAvailableActions != null) {
//                    for (AbstractAction action : allAvailableActions) {
//                        // Check if the action is the specific 'ChooseCard' action AND if its 'useChopsticks' flag is true
//                        if (action instanceof games.sushigo.actions.ChooseCard) {
//                            games.sushigo.actions.ChooseCard chooseAction = (games.sushigo.actions.ChooseCard) action;
//                            if (!chooseAction.useChopsticks) {
//                                // If it's ChooseCard but NOT using chopsticks, add it
//                                rolloutActions.add(action);
//                            }
//                            // Implicitly skip (do not add) if useChopsticks is true
//                        } else {
//                            // If it's any other type of action, add it
//                            rolloutActions.add(action);
//                        }
//                    }
//                }
//
//                // --- Handle No Valid Actions ---
//                // If the filtered list is empty...
//                if (rolloutActions.isEmpty()) {
//                    // Check if the original list had actions (meaning only Chopsticks was available)
//                    if (allAvailableActions != null && !allAvailableActions.isEmpty()){
//                        // Log a warning if desired, but acceptable to end rollout here
//                        // System.err.println("Warning: Only Chopsticks action available during rollout, ending rollout early at depth " + rolloutDepth);
//                    } else {
//                        // If the original list was also empty, the game truly ended or is stuck
//                    }
//                    break; // End the rollout loop
//                }
//
//                // --- Choose and Execute Action ---
//                // Ask the rollout policy (currently RandomPlayer) to pick an action *from the filtered list*
//                AbstractAction next = rolloutPlayer.getAction(rolloutState, rolloutActions);
//
//                // Advance the rollout state using the chosen action
//                advance(rolloutState, next); // 'advance' also counts FM calls via root node
//                rolloutDepth++; // Increment rollout depth counter
//            } // End of while loop for rollout steps
//        } // End of if check for rollout possibility
//
//        // --- Evaluate the Final State ---
//        // Ask the heuristic function for the score of the final board state reached in the rollout
//        double value = params.heuristic.evaluateState(rolloutState, player.getPlayerID());
//        // Safety check for invalid heuristic values (NaN or Infinity)
//        if (Double.isNaN(value) || Double.isInfinite(value)) {
//            System.err.printf("ERROR: Heuristic returned invalid value (%.2f) during rollout! Player: %d\n", value, player.getPlayerID());
//            value = 0.0; // Return a neutral score (0.0) on error
//        }
//        return value; // Return the estimated score
//    } // End of rollOut method
//    private double rollOut(AbstractGameState determinizedState) {
//        int rolloutDepth = 0;
//        AbstractGameState rolloutState = determinizedState.copy(); // Work on a copy
//
//        // Only rollout if the state is not terminal and we have a rollout length > 0
//        if (params.rolloutLength > 0 && rolloutState.isNotTerminal()) {
//            // Loop until game ends or max rollout depth is reached
//            while (!finishRollout(rolloutState, rolloutDepth)) {
//
//                // --- TODO: Replace RandomPlayer with your HeuristicSushiGoPlayer ---
//                // Get available actions in the current rollout state
//                List<AbstractAction> availableActions = rolloutPlayer.getForwardModel().computeAvailableActions(rolloutState, rolloutPlayer.getParameters().actionSpace);
//                if (availableActions.isEmpty()) {
//                    // Should not happen if finishRollout is correct, but safety break
//                    break;
//                }
//
//                // Ask the rollout policy (currently random) for an action
//                AbstractAction next = rolloutPlayer.getAction(rolloutState, availableActions);
//
//                // Advance the rollout state
//                advance(rolloutState, next); // Use advance to correctly count FM calls
//                rolloutDepth++;
//            }
//        }
//
//        // --- Evaluate the final state of the rollout ---
//        double value = params.heuristic.evaluateState(rolloutState, player.getPlayerID());
//        // Safety check for invalid heuristic values
//        if (Double.isNaN(value) || Double.isInfinite(value)) {
//            System.err.printf("ERROR: Heuristic returned invalid value (%.2f) during rollout! Player: %d, Depth: %d\n", value, player.getPlayerID(), rolloutDepth);
//            value = 0.0; // Return a neutral value on error
//        }
//        return value;
//    }

    /**
     * Propagates the simulation result back up the tree from this node to the root.
     * Updates visit counts and total values for nodes on the path.
     * @param result The score obtained from the rollout.
     */
    private void backUp(double result) {
        ISMCTSTreeNode node = this;
        while (node != null) {
            node.nVisits++;
            // --- TODO: Implement Coulom "Mix" Backup Operator Here (Optional) ---
            // Replace simple addition with a mixed value calculation based on node.nVisits, child stats, etc.
            node.totValue += result; // Standard MCTS: Add result to total value
            node = node.parent; // Move up to the parent node
        }
    }

    // --- Helper methods ---

    /** Advances the game state and increments the global FM call counter. */
    private void advance(AbstractGameState gs, AbstractAction act) {
        player.getForwardModel().next(gs, act);
        root.fmCallsCount++; // Use root's counter
    }

    /** Checks if the rollout simulation should terminate. */
    private boolean finishRollout(AbstractGameState rollerState, int depth) {
        if (depth >= params.rolloutLength) return true; // Depth limit reached
        if (!rollerState.isNotTerminal()) return true; // Game has ended
        return false; // Continue rollout
    }

    /**
     * Selects the best action from the root node after the search is complete.
     * Typically based on the highest visit count (most robust). Includes fallbacks.
     * @return The best AbstractAction found.
     */
    AbstractAction bestAction() {
        double bestValue = -Double.MAX_VALUE;
        AbstractAction bestAction = null;

        // Get actions currently legal from the root's *information set* state
        List<AbstractAction> finalLegalActions = player.getForwardModel().computeAvailableActions(this.informationSetState, params.actionSpace);
        Set<AbstractAction> finalLegalSet = new HashSet<>(finalLegalActions);

        // Handle case where no search happened or root is terminal
        if (children.isEmpty()) {
            System.err.println("Warning: Root node has no children in bestAction(). Falling back to random legal.");
            return getRandomLegalActionFallback(this.informationSetState);
        }

        boolean foundVisitedLegalAction = false;
        // Iterate through all actions that were explored (have child nodes)
        for (Map.Entry<AbstractAction, ISMCTSTreeNode> entry : children.entrySet()) {
            AbstractAction action = entry.getKey();
            ISMCTSTreeNode node = entry.getValue();

            // Check if child was expanded AND is legal in the CURRENT info set state
            if (node != null && finalLegalSet.contains(action)) {
                foundVisitedLegalAction = true;
                // --- Selection criterion: Most visits ---
                double childValue = node.nVisits;
                // --- Alternative: Highest average value ---
                // double childValue = node.nVisits > 0 ? node.totValue / node.nVisits : -Double.MAX_VALUE;

                // Add noise for tie-breaking before comparison
                childValue = noise(childValue, params.epsilon, rnd.nextDouble());

                if (childValue > bestValue) {
                    bestValue = childValue;
                    bestAction = action;
                }
            }
        }

        // Fallback if no *visited* actions are legal now (can happen with small budgets or changing game state)
        if (bestAction == null) {
            System.err.println("Warning: No visited children are legal now. Checking unvisited/falling back.");
            // Option 1: Check unvisited children that are legal now
            for (AbstractAction action : children.keySet()) {
                if (children.get(action) == null && finalLegalSet.contains(action)) {
                    // Give unvisited legal actions a tiny chance if no visited ones work
                    double unvisitedValue = noise(params.epsilon, params.epsilon, rnd.nextDouble());
                    if (bestAction == null || unvisitedValue > bestValue) {
                        bestValue = unvisitedValue;
                        bestAction = action;
                    }
                }
            }
            // Option 2: If still no action, resort to random legal action
            if (bestAction == null) {
                return getRandomLegalActionFallback(this.informationSetState);
            }
        }
        return bestAction;
    }

    /** Fallback method to get a random legal action from a given state. */
    private AbstractAction getRandomLegalActionFallback(AbstractGameState state) {
        List<AbstractAction> currentLegal = player.getForwardModel().computeAvailableActions(state, params.actionSpace);
        if (currentLegal == null || currentLegal.isEmpty()) {
            System.err.println("FATAL: No legal actions available in fallback from state.");
            return null; // Should indicate game end or error
        }
        // Return a random action from the list
        return currentLegal.get(rnd.nextInt(currentLegal.size()));
    }

    //Getter for info set state
    public SGGameState getInformationSetState() {
        return informationSetState;
    }
} // End of ISMCTSTreeNode class