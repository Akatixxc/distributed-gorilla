package fi.utu.tech.distributed.gorilla.logic;

import fi.utu.tech.distributed.gorilla.mesh.Mesh;
import fi.utu.tech.distributed.gorilla.mesh.MeshMessage;
import fi.utu.tech.distributed.gorilla.views.MainCanvas;
import fi.utu.tech.distributed.gorilla.views.Views;
import fi.utu.tech.oomkit.app.AppConfiguration;
import fi.utu.tech.oomkit.app.GraphicalAppLogic;
import fi.utu.tech.oomkit.canvas.Canvas;
import fi.utu.tech.oomkit.util.Console;
import fi.utu.tech.oomkit.windows.Window;
import javafx.application.Platform;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * TODO: Extend this for GorillaMultiplayerLogic and make Overrides there
 * Alternatively this class can be also modified
 */
public class GorillaMultiplayerLogic implements GraphicalAppLogic {
    private Console console;
    private final MainCanvas mainCanvas = new MainCanvas();
    public Views views;

    protected GameState gameState;
    private GameMode gameMode;

    protected String myName = "Mää";
    protected final int gameSeed = 1;
    protected final int maxPlayers = 2;

    private Mesh server;

    // in case the game runs too slow:

    // on Linux/Mac, first try to add the Java VM parameter -Dprism.order=sw
    // JavaFX may have some memory leaks that can crash the whole system

    // true = turns off background levels and fade in/out = faster, but not as pretty
    private final boolean lowendMachine = true;

    // duration between game ticks (in ms). larger number = computationally less demanding game
    private final int tickDuration = 20;

    // no comment
    private final boolean synkistely = false;

    // true = you can check from the text console if the computer is too slow to render all frames
    // the system will display 'Frame skipped!' if the tick() loop takes too long.
    private final boolean verboseMessages = false;

    // List of AI players
    private final List<Player> otherPlayers = new ArrayList<>();

    // Helpers for menu system. No need to modify
    private int c = 0;
    private int selectedMenuItem = 0;
    

    // we should return the one we actually use for drawing
    // the others are just proxies that end to drawing here
    // No need to modify
    @Override
    public Canvas getCanvas() {
        return mainCanvas;
    }

    // initializes the game logic
    // No need to modify
    @Override
    public AppConfiguration configuration() {
        return new AppConfiguration(tickDuration, "Gorilla", false, verboseMessages, true, true, true);
    }

    /**
     * Key handling for menu navigation functionality
     * @param k The key pressed
     */
    @Override
    public void handleKey(Key k) {
    // During the game, in order to make the menu work,
    // click the text output area on the right.
    // To enter commands, click the area again.
        switch (gameMode) {
            case Intro:
                setMode(GameMode.Menu);
                break;
            case Menu:
                if (k == Key.Backspace) {
                    if (selectedMenuItem > 0) selectedMenuItem--;
                    else selectedMenuItem = 2;
                    views.setSelectedMenuItem(selectedMenuItem);
                    return;
                }
                if (k == Key.Alt) {
                    if (selectedMenuItem < 2) selectedMenuItem++;
                    else selectedMenuItem = 0;
                    views.setSelectedMenuItem(selectedMenuItem);
                    return;
                }
                if (k == Key.Ctrl) {
                    switch (selectedMenuItem) {
                        case 0:
                            // quit active game
                            if (gameState != null) {
                                resetGame();
                                setMode(GameMode.Menu);
                            } else {
                                setMode(GameMode.Game);
                            }
                            break;
                        case 1:
                            handleMultiplayer();
                            break;
                        case 2:
                            Platform.exit();
                    }
                }
                break;
            case Game:
                // instead we read with 'handleConsoleInput'
                break;
        }
    }

    /**
     * Reads the commands given by user in GUI and passes them into
     * command parser (parseCommandLine())
     */
    private void handleConsoleInput() throws IOException, ClassNotFoundException {
        if (console != null && console.inputQueue().peek() != null) {
            parseCommandLine(console.inputQueue().poll());
        }
    }

    /**
     * Called after the OOMkit has initialized and a window is fully visible and usable.
     * This method is the first one to be called on this class
     * @param window Oomkit application window (no need to modify)
     *  Command line parameters given, can be used for defining port and server address to connect
     */
    @Override
    public void initialize(Window window) {
        // To --port=1234 
        // IDEA: Run -> Edit configurations -> Program arguments
        // Eclipse (Ran as Java Application): Run -> Run configuration... -> Java Application -> Main (varies) -> Arguments -> Program arguments

        // Start server on the port given as a command line parameter or 1234
        //startServer("1235");

        // Connect to address given as a command line parameter "server" (default: localhost) on port given (default: 1234)
        //connectToServer("localhost","1234");

        views = new Views(mainCanvas, lowendMachine, synkistely, configuration().tickDuration, new Random().nextLong());
        this.console = window.console();

        // Set Game into intro mode showing the level and title text
        setMode(GameMode.Intro);

        resetGame();

        // Populate menu
        views.setMenu("Gorillasota 2029", new String[]{
                "Aloita / lopeta peli",
                "Palvelinyhteys",
                "Lopeta"
        });

        updateMenuInfo();
    }

    /**
     * Called when the window is closed
     * Useful for terminating threads
     */
    //@Override
    public void terminate() {
        System.out.println("Closing the game!");
    }

    /**
     * Resets the single player game
     */
    public void resetGame() {
        otherPlayers.clear();
        gameState = null;
    }

    /**
     * Add AI player with provided name
     * @param name The name of the ai player to be created
     */
    public void joinGame(String name) {
        if (otherPlayers.size() + 1 < maxPlayers) {
            otherPlayers.add(new Player(name, new LinkedBlockingQueue<>(), false));
        }
    }

    /**
     * Called peridically by OOMkit, makes game to proceed
     * Very important function in terms of understanding the game structure
     * See the super method documentation for better understanding
     */
    @Override
    public void tick() {
        try {
            handleConsoleInput();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        toggleGameMode();
        views.redraw();
    }

    /**
     * Sets the game mode. Mainly affects on the current view on the scereen (Intro, menu, game...)
     * @param mode
     */
    public void setMode(GameMode mode) {
        // Start new game if not running
        if (mode == GameMode.Game && gameState == null)
            initGame();

        gameMode = mode;
        views.setMode(mode);
        updateMenuInfo();
    }

    /**
     * Start the mesh server on the specified port
     * @param port The port the mesh should listen to for new nodes
     */
    protected void startServer(String port) throws IOException {
        System.out.println("Starting server at port " + port);
        server = new Mesh(Integer.parseInt(port));
        server.start();

        // ...or at least somebody should be
    }

    /**
     * Connect the Mesh into an existing mesh
     * @param address The IP address of the mesh node to connect to
     * @param port The listening port of the mesh node to connect to
     */
    protected void connectToServer(String address, String port) throws IOException {
        System.out.printf("Connecting to server at %s", address, port);
        System.out.println("");
        server.connect(address, Integer.parseInt(port));
        // ...or at least somebody should be
    }

    /**
     * Starts a new single player game with max number of AI players
     */
    private void initGame() {
        double h = getCanvas().getHeight();

        // Create maxPlayers-1 AI players
        for (int i=1; i<maxPlayers; i++) {
            joinGame("Kingkong " + i);
        }

        List<String> names = new LinkedList<>();
        names.add(myName);
        for (Player player : otherPlayers) names.add(player.name);

        GameConfiguration configuration = new GameConfiguration(gameSeed, h, names);

        gameState = new GameState(configuration, myName, new LinkedBlockingQueue<>(), otherPlayers);
        views.setGameState(gameState);
    }

    /**
     * Add move to players move queue by using player name
     * @param player Player name
     * @param move The move to be added
     */
    private void addPlayerMove(String player, Move move) {
        for (Player p : otherPlayers)
            if (p.name.equals(player))
                p.moves.add(move);
    }

    /**
     * Handles message sending. Usually fired by "say" command
     * @param msg Chat message object containing the message and other information
     */
    protected void handleChatMessage(MeshMessage msg) {
        server.broadcast(msg);
    }

    /**
     * Handles starting a multiplayer game. This event is usually fired by selecting
     * Palvelinyhteys in game menu
     */
    protected void handleMultiplayer() {
        System.out.println("Not implemented on this logic");
    }

    /**
     * Handles banana throwing. This event is usually fired by angle and velocity commands
     * @param mtb
     */
    protected void handleThrowBanana(MoveThrowBanana mtb) {
        gameState.addLocalPlayerMove(mtb);
    }

    /**
     * Handles name change. Fired by "name" command
     * @param newName Your new name
     */
    protected void handleNameChange(String newName) {
        myName = newName;
        System.out.println("Nimesi on nyt " + newName);
    }

    /**
     * Parses the game command prompt and fires appropriate handlers
     * @param cmd Unparsed command to be parsed
     */
    private void parseCommandLine(String cmd) throws IOException, ClassNotFoundException {
        if (cmd.contains(" ")) {
            String rest = cmd.substring(cmd.split(" ")[0].length() + 1);
            switch (cmd.split(" ")[0]) {
                case "q":
                case "quit":
                case "exit":
                    Platform.exit();
                    break;
                case "name":
                    if (cmd.equals("name")){
                        System.out.println("Nimesi on " + myName);
                    }
                    handleNameChange(rest);
                    break;
                case "s":
                case "chat":
                case "say":
                    handleChatMessage(new MeshMessage(myName, rest));
                    break;
                case "a":
                case "k":
                case "angle":
                case "kulma":
                    if (gameMode != GameMode.Game) return;
                    try {
                        double angle = Double.parseDouble(rest);
                        MoveThrowBanana mtb = new MoveThrowBanana(angle, Double.NaN);
                        handleThrowBanana(mtb);
                        System.out.println("Asetettu kulma: " + angle);
                    } catch (NumberFormatException e) {
                        System.out.println("Virheellinen komento, oikea on: angle <liukuluku -45..225>");
                    } catch (IllegalArgumentException e) {
                        System.out.println(e.getMessage());
                    }
                    break;
                case "v":
                case "n":
                case "velocity":
                case "nopeus":
                    if (gameMode != GameMode.Game) return;
                    try {
                        double velocity = Double.parseDouble(rest);
                        MoveThrowBanana mtb = new MoveThrowBanana(Double.NaN, velocity);
                        handleThrowBanana(mtb);
                        System.out.println("Asetettu nopeus: " + velocity);
                    } catch (NumberFormatException e) {
                        System.out.println("Virheellinen komento, oikea on: velocity <liukuluku 0..150>");
                    } catch (IllegalArgumentException e) {
                        System.out.println(e.getMessage());
                    }
                    break;
                case "connect":
                    connectToServer(rest.split(":")[0], rest.split(":")[1]);
                    break;
                case "server":
                    startServer(rest);
                    break;
            }
        }
    }

    /**
     * Primitive AI - creates moves for AI players
     */
    private void moveAIplayers() {
        // currently a rather primitive random AI
        if (new Random().nextInt(50) < 4 && !otherPlayers.isEmpty()) {
            Move move = new MoveThrowBanana(
                    new Random().nextDouble() * 180,
                    35 + new Random().nextDouble() * 35);

            addPlayerMove("Kingkong " + (new Random().nextInt(otherPlayers.size()) + 1), move);
        }
    }

    /**
     * Updates the info on the bottom of the menu
     */
    protected void updateMenuInfo() {
        views.setMenuInfo(new String[]{"Pelaajia: " + (otherPlayers.size() + 1), String.format("Yhdistetty koneeseen <-> %s", "none"), "Peli aktiivinen: " + (gameState != null)});
    }

    /**
     * Calls different functions depending on the current game mode. Called periodically by the GorillaLogic tick() method
     */
    private void toggleGameMode() {
        switch (gameMode) {
            case Intro:
                // when the intro is done, jump to menu
                if (views.introDone())
                    setMode(GameMode.Menu);
                break;
            case Menu:
                c++;
                if (c > 50) {
                    c = 0;
                }
                if (selectedMenuItem == 1 && c == 0) {
                    updateMenuInfo();
                }
                break;
            case Game:
                moveAIplayers();
                // Advance the game state, the actual game
                gameState.tick();
                break;
        }
    }
}
