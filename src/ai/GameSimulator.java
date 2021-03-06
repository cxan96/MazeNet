package ai;

import generated.*;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.Stack;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;

public class GameSimulator {

    public static void main(String args[]) {
	// create the ais
	Map<Integer, AI> players = new HashMap<>();
	players.put(1, new AlphaMazeLevel1(1, new ManhattanEvaluator(), 0.1));
	//players.put(2, new AlphaMazeLevel1(1, new ManhattanEvaluator(), 0.1));
	players.put(2, new AlphaMazeLevel2(2, new ManhattanEvaluator(), new ManhattanEvaluator(),
					   20, 500, 15, 0.1));

	GameSimulator simulator = new GameSimulator();
	// simulate n games
	System.out.println(simulator.simulate(players, Integer.MAX_VALUE));
    }

    /**
     * Simulate N games in parallel.
     */
    public Map<Integer, Integer> simulateN(int n, Map<Integer, AI> players, int maxMoves) {
	// first, give each player a random treasure to look for
	Map<Integer, TreasureType> currentTreasures = new HashMap<>();
	// a list of all the treasures
	List<TreasureType> allTreasures = GameSimulator.getAllTreasures();
	Collections.shuffle(allTreasures);
	for (int id: players.keySet()) {
	    // get the first (random due to shuffle) treasure type and add it to the map
	    currentTreasures.put(id, allTreasures.get(0));
	    // remove the treasure from the available list
	    allTreasures.remove(0);
	}
	// each player still has to find the same amount of treasures
	Map<Integer, Integer> treasuresToGo = new HashMap<>();
	for (int id: players.keySet()) {
	    treasuresToGo.put(id, 24/players.size() + 1);
	}
	// simulate the game
	return simulateN(n, players, new Board(), currentTreasures, new ArrayList<>(), treasuresToGo, 1, maxMoves, 1);
    }

    /**
     * Simulate N games, the simulation is run parallel.
     */
    public Map<Integer, Integer> simulateN(int n, Map<Integer, AI> players, Board board, 
					  Map<Integer, TreasureType> currentTreasures, 
					  List<TreasureType> foundTreasures,
					   Map<Integer, Integer> treasuresToGo, int nextMove, int maxMoves, int playerID) {
	// a simulator will be run on every core
	int cores = Runtime.getRuntime().availableProcessors();

	// initialize the threadpool
	ExecutorService threadPool = Executors.newFixedThreadPool(cores);
	
	// this is a class for simulating n games
	class Simulator implements Callable<Map<Integer, Integer>> {
	    
	    private int numberOfGames;
	    private Map<Integer, AI> players;
	    private Board board;
	    private Map<Integer, TreasureType> currentTreasures;
	    private List<TreasureType> foundTreasures;
	    private Map<Integer, Integer> treasuresToGo;
	    private int nextMove;
	    private int maxMoves;

	    public Simulator (int numberOfGames, Map<Integer, AI> players, Board board, 
			      Map<Integer, TreasureType> currentTreasures, 
			      List<TreasureType> foundTreasures,
			      Map<Integer, Integer> treasuresToGo, int nextMove, int maxMoves) {
		this.numberOfGames = numberOfGames;
		this.players = players;
		this.board = board;
		this.currentTreasures = currentTreasures;
		this.foundTreasures = foundTreasures;
		this.treasuresToGo = treasuresToGo;
		this.nextMove = nextMove;
		this.maxMoves = maxMoves;
	    }
	    
	    /**
	     * Run numberOfGames games and put the results in the map.
	     *
	     * @return PlayerID -> Games won
	     */
	    @Override
	    public Map<Integer, Integer> call() {
		Map<Integer, Integer> res = new HashMap<>();
		// initialize the result
		res.put(0, 0);
		for (int id: players.keySet()) {
		    res.put(id, 0);
		}
		// run the simulations
		for (int i = 0; i<numberOfGames; i++) {
		    // initialize the parameters of the simulator as they will be changed inside
		    Board tmpBoard = new Board(this.board);
		    Map<Integer, TreasureType> tmpCurrentTreasures = new HashMap<>();
		    tmpCurrentTreasures.putAll(this.currentTreasures);
		    List<TreasureType> tmpFoundTreasures = new ArrayList<>();
		    tmpFoundTreasures.addAll(this.foundTreasures);
		    Map<Integer, Integer> tmpTreasuresToGo = new HashMap<>();
		    tmpTreasuresToGo.putAll(this.treasuresToGo);
		    
		    // simulate a game
		    int winnerID = GameSimulator.this.simulate(players, tmpBoard, tmpCurrentTreasures,
							       tmpFoundTreasures, tmpTreasuresToGo, nextMove, maxMoves, playerID);
		    
		    // update the results
		    res.put(winnerID, res.get(winnerID)+1);
		    
		}
		return res;
	    }
	}
	
	// how many games games for each thread?
	int gamesPerThread = n/cores;

	// now run the simulations in parallel
	List<Future<Map<Integer, Integer>>> simulationResults = new ArrayList<>(cores);
	for (int i = 0; i<cores; i++) {
	    simulationResults.add(threadPool.submit(new Simulator(gamesPerThread, players, board, 
								  currentTreasures, foundTreasures,
								  treasuresToGo, nextMove, maxMoves)));
	}

	// now get the results
	Map<Integer, Integer> res = new HashMap<>();
	res.put(0, 0);
	for (int id: players.keySet()) {
	    res.put(id, 0);
	}
	for (Future<Map<Integer, Integer>> f: simulationResults) {
	    try {
		for(Map.Entry<Integer, Integer> curRes: f.get().entrySet()) {
		    res.put(curRes.getKey(), res.get(curRes.getKey()) + curRes.getValue());
		}
	    } catch (InterruptedException | ExecutionException e) {
		// do nothing
	    }
	}

	// shut down the thread pool
	threadPool.shutdown();

	// return the results
	return res;
    }

    /**
     * This method simulates a game on a random board.
     *
     * @param players  Each AI mapped to its playerID
     * @param maxMoves The maximum number of moves to simulate
     *
     * @return The ID of the winning player
     */
    public int simulate(Map<Integer, AI> players, int maxMoves) {
	// first, give each player a random treasure to look for
	Map<Integer, TreasureType> currentTreasures = new HashMap<>();
	// a list of all the treasures
	List<TreasureType> allTreasures = GameSimulator.getAllTreasures();
	Collections.shuffle(allTreasures);
	for (int id: players.keySet()) {
	    // get the first (ramdom due to shuffle) treasure type and add it to the map
	    currentTreasures.put(id, allTreasures.get(0));
	    // remove the treasure from the available list
	    allTreasures.remove(0);
	}
	// each player still has to find the same amount of treasures
	Map<Integer, Integer> treasuresToGo = new HashMap<>();
	for (int id: players.keySet()) {
	    treasuresToGo.put(id, 24/players.size() + 1);
	}
	// simulate the game
	return simulate(players, new Board(), currentTreasures, new ArrayList<>(), treasuresToGo, 1, maxMoves, 1);
    }

    /**
     * This method simulates a game with specified parameters.
     *
     * @param players          Each AI mapped to its playerID
     * @param board            The starting board for the simulation
     * @param currentTreasures The current treasure for each playerID
     * @param foundTreasures   The treasures that have already been found
     * @param nextMove         The ID of the player who's turn is next
     * @param treasuresToGo    How many treasures does each player still have to find?
     * @param maxMoves         The maximum number of moves that is to be simulated
     * @param playerID         The ID of the current player (due to the non-random treasures fix)
     *
     * @return The ID of the winning player
     */
    public int simulate(Map<Integer, AI> players, Board board, Map<Integer, 
			TreasureType> currentTreasures, List<TreasureType> foundTreasures, 
			Map<Integer, Integer> treasuresToGo, int nextMove, int maxMoves, int playerID) {
	// first get all available treasures
	List<TreasureType> availableTreasures = getAllTreasures();
	availableTreasures.removeAll(foundTreasures);
	availableTreasures.removeAll(currentTreasures.values());

	// now create a stack for each player representing the treasures he still has to find
	Map<Integer, Stack<TreasureType>> playerStacks = new HashMap<>();
	
	// now fill the stack for each player with random treasures
	Collections.shuffle(availableTreasures);
	for(int id: players.keySet()) {
	    playerStacks.put(id, new Stack<>());
	    // at the bottom of the stack, place the starting position
	    playerStacks.get(id).push(TreasureType.fromValue("Start0"+id));
	    // now fill the stack
	    for (int i = 0; i<treasuresToGo.get(id)-2; i++) {
		playerStacks.get(id).push(availableTreasures.get(0));
		availableTreasures.remove(0);
	    }

	    if (treasuresToGo.get(id) > 1) {
		// add the current treasure at the top
		playerStacks.get(id).push(currentTreasures.get(id));
	    }
	}

	/**
	 * The following is a very dirty fix to prevent that each player is assigned the same starting
	 * treasure for every simulation.
	 * To fix this, all stacks are shuffeled, then the starting position is placed back at the bottom.
	 * The treasure that is at the top is the new current treasure of the player.
	 * 
	 * Do this procedure for every player except for playerID - as this treasure is known with absolute
	 * certainty.
	 */

	for (int id: players.keySet()) {
	    if (id != playerID) {
		// first remove the starting position (the last element down the stack)
		TreasureType curStart = playerStacks.get(id).get(0);
		playerStacks.get(id).removeElement(0);
		// now shuffle the stack
		Collections.shuffle(playerStacks.get(id));
		// put the starting position back at the bottom
		playerStacks.get(id).add(0, curStart);
		// set the treasure at the top to be the new treasure
		currentTreasures.put(id, playerStacks.get(id).peek());
	    }
	}

	// the game loop
	while (maxMoves > 0) {
	    // let each player make a move
	    for (int i=0; i<players.size() && maxMoves > 0; i++) {
		int currentID = nextMove + i;
		if (currentID > players.size()) {
		    currentID -= players.size();
		}
		
		// update the current treasure on the board
		board.setTreasure(playerStacks.get(currentID).peek());
		
		// create the AwaitMoveMessageType, use copies for safety reasons
		AwaitMoveMessageType msg = new AwaitMoveMessageType();
		msg.setBoard(new Board(board));
		List<TreasuresToGoType> tmpTGT = new ArrayList<>();
		for (int id: players.keySet()) {
		    TreasuresToGoType tmp = new TreasuresToGoType();
		    tmp.setPlayer(id);
		    tmp.setTreasures(playerStacks.get(id).size());
		    tmpTGT.add(tmp);
		}
		msg.getTreasuresToGo().addAll(tmpTGT);
		msg.getFoundTreasures().addAll(foundTreasures);
		msg.setTreasure(playerStacks.get(currentID).peek());

		// get the move from the current player
		MoveMessageType move = players.get(currentID).move(msg);

		// simulate the move on the board and test if the player has found his treasure
		if (board.proceedTurn(move, currentID)) {
		    // add the treasure to the found treasures
		    foundTreasures.add(playerStacks.get(currentID).pop());
		    // test if the player has won
		    if (playerStacks.get(currentID).empty()) {
			// we have a winner
			return currentID;
		    }
		}
		//System.out.println("Stack size of player "+currentID+": "+playerStacks.get(currentID).size());
		//System.out.println(board);
		
		// decrement the number of moves
		maxMoves--;
	    }
	}
	// no winner was found within the maxMoves moves simulated
	// return the id corresponding to the player with the smallest stack
	int winnerID = 0;
	int smallestSize = Integer.MAX_VALUE;
	// set to true if more than one player has the smallest stack
	boolean draw = false;
	for (int id: playerStacks.keySet()) {
	    if (playerStacks.get(id).size() <= smallestSize) {
		if (!draw && playerStacks.get(id).size() == smallestSize) {
		    draw = true;
		    winnerID = 0;
		} else if (draw && playerStacks.get(id).size() < smallestSize) {
		    winnerID = id;
		    smallestSize = playerStacks.get(id).size();
		    draw = false;
		} else if (!draw && playerStacks.get(id).size() < smallestSize){
		    winnerID = id;
		    smallestSize = playerStacks.get(id).size();
		}
	    }
	}
	return winnerID;
    }

    public static List<TreasureType> getAllTreasures() {
	List<TreasureType> allTreasures = new ArrayList<TreasureType>();
	allTreasures.add(TreasureType.SYM_01);
        allTreasures.add(TreasureType.SYM_02);
        allTreasures.add(TreasureType.SYM_03);
        allTreasures.add(TreasureType.SYM_04);
        allTreasures.add(TreasureType.SYM_05);
        allTreasures.add(TreasureType.SYM_06);
        allTreasures.add(TreasureType.SYM_07);
        allTreasures.add(TreasureType.SYM_08);
        allTreasures.add(TreasureType.SYM_09);
        allTreasures.add(TreasureType.SYM_10);
        allTreasures.add(TreasureType.SYM_11);
        allTreasures.add(TreasureType.SYM_12);
        allTreasures.add(TreasureType.SYM_13);
        allTreasures.add(TreasureType.SYM_14);
        allTreasures.add(TreasureType.SYM_15);
        allTreasures.add(TreasureType.SYM_16);
        allTreasures.add(TreasureType.SYM_17);
        allTreasures.add(TreasureType.SYM_18);
        allTreasures.add(TreasureType.SYM_19);
        allTreasures.add(TreasureType.SYM_20);
        allTreasures.add(TreasureType.SYM_21);
        allTreasures.add(TreasureType.SYM_22);
        allTreasures.add(TreasureType.SYM_23);
        allTreasures.add(TreasureType.SYM_24);
	return allTreasures;
    }
}
