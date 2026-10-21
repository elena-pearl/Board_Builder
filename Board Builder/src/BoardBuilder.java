import javax.swing.JFrame; //windows
import javax.swing.JPanel; //drawing areas
import javax.swing.SwingUtilities;
import javax.swing.Timer; //draw loop
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor; //copy and paste in textboxes
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage; //used to measure text for the textbox cursor
import java.io.File; //checking if a file exists and making the data folder
import java.nio.file.Files; //reading and writing files
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack; //undo and redo stacks

/**
 * BoardBuilder - a board (level) editor made with only the Java libraries (Swing and AWT).
 *
 * WHAT IT DOES
 *   The board is a grid of blocks. Every block has a mode (0-9) and every mode has a color.
 *   Press a number key to pick a mode, then click and drag on the board to paint blocks.
 *   The board has no limit to the right and up, new blocks are added as you move.
 *   Boards can be saved to and loaded from json files in the data folder.
 *
 * HOW THE CODE IS ORGANIZED
 *   BoardBuilder (this class) holds the data everything shares (blocks, sizes, view position,
 *     undo stacks, settings) and the static functions that work on it (save, load, resize, undo...).
 *   Screen (abstract) is the parent of every window:
 *     Board (main window), Menu, Legend, Map, Stats, Instructions, Tutorial, and FileList.
 *   Button is the parent of the things drawn inside a window: Switch and Textbox inherit from it.
 *     Every Screen keeps a list of Buttons and treats them all the same way (polymorphism),
 *     each kind just overrides how it is drawn, clicked, and typed in.
 *   Block is one square of the board, Change is one painted block (for undo and redo),
 *   and JSONReader reads the saved files back in.
 *
 * THE DRAW LOOP
 *   A Swing Timer calls draw() about 60 times a second. draw() lets every window update itself
 *   (painting, moving, map math) and then asks every open window to repaint.
 *
 * COORDINATES
 *   Every block lives in a column and a row. Column 0, row 0 is the bottom left block and the board
 *   grows to the right and up from there (numCols and numRows count them). A block's pixel position
 *   is column * block width and row * block height, so changing the block size only changes how big
 *   the blocks are drawn, never which blocks have which modes.
 *   The view (x and y) is how far the canvas is moved: x is 0 or less (moving right makes it more
 *   negative) and y is 0 or more (moving up makes it bigger). x = 0 and y = 0 is the start, where the
 *   bottom left of the canvas lines up with the bottom left of the bottom left block.
 *   The block size does not have to divide evenly into the canvas size, the extra blocks just go off
 *   the right and top edges (the canvas only shows what is inside it).
 *
 * CONTROLS
 *   0-9 pick a mode, arrow keys or wasd move one block (hold to keep going), e exports,
 *   p turns preview on and off, ctrl+z undo, ctrl+y redo. The bar at the bottom of the board has
 *   the rest (resize, files, windows, switches). The Help window lists everything.
 *
 * FILE FORMAT (data/name.json), a json array in this order:
 *   [ {"width": w}, {"height": h}, {"block width": bW}, {"block height": bH},
 *     {"rect mode": "corner" or "center"},
 *     [ {"mode 1 name": "..."}, ... {"mode 9 name": "..."} ],
 *     [ {"mode": m, "col": c, "row": r, "x": x, "y": y}, ... ] ]   (only blocks with a mode above 0 are saved)
 *   col and row say which block it is (col 0, row 0 is the bottom left block). x and y are the same spot
 *   in pixels measured from the bottom left corner of the board with y going up: the bottom left corner
 *   of the block (rect mode corner) or the center of the block (rect mode center).
 *   Older files (mode names at index 3, no block height or rect mode, or no col and row) can still be
 *   loaded, their x and y (counted from the top left) are turned into columns and rows.
 */
public class BoardBuilder {

    static int w = 600, h = 600, bW = 20, bH = 20; //width, height, block width, and block height
    static int displayWidth, displayHeight; //users screen width and height
    static int barH = 206; //height of the space at the bottom of the board window (this space does not translate)
    static String fileName = ""; //json file name of the board that is currently open ("" if it has not been saved yet)
    static String status = ""; //message shown at the bottom of the board

    static int x = 0, y = 0; //x and y vals
    static ArrayList<Block> blocks = new ArrayList<Block>(); //block array list

    static int numCols = 0, numRows = 0; //number of columns and rows of blocks (the board starts at the bottom left and grows to the right and up)
    static boolean moveR = false, moveU = false, moveL = false, moveD = false; //move booleans
    static int holdFrames = 0; //how long a move key has been held
    static int mode = 0; //current mode selected
    static int frameCount = 0; //how many times draw has run

    static boolean gridVisible = true; //grid settings
    static float gridThickness = 1;
    static Color gridColor = new Color(0, 0, 0);
    static boolean shiftDown = false; //true while shift is held (textboxes use it to select text)
    static boolean preview = false; //preview mode (no gridlines and no painting)
    static boolean centerExport = false; //false = rectMode corner, true = rectMode center
    static boolean legendOnMap = false; //show legend on the map

    static Stack<ArrayList<Change>> undoStack = new Stack<ArrayList<Change>>(); //undo and redo stacks
    static Stack<ArrayList<Change>> redoStack = new Stack<ArrayList<Change>>();
    static ArrayList<Change> currentChange = new ArrayList<Change>(); //everything changed while the mouse is held down

    static boolean mapClicked = false; //map clicked boolean
    static boolean mapDirty = false; //true when map needs new data
    static boolean boardStarted = false; //true once user has made or loaded a board

    static Color[] colors = new Color[10]; //mode colors array
    static Color buttonFill = new Color(70, 70, 70); //default button color
    static Font uiFont = new Font("SansSerif", Font.PLAIN, 12);
    static Font titleFont = new Font("SansSerif", Font.BOLD, 16);

    static final int LEFT = 0, CENTER = 1, RIGHT = 2; //text alignment

    static Board board; //all the windows
    static Menu menu;
    static Legend legend;
    static Map map;
    static Stats stats;
    static Instructions instructions;
    static Tutorial tutorial;
    static FileList fileList;
    static ArrayList<Screen> screens = new ArrayList<Screen>();

    public static void main(String[] args) {
        settings(); //get display size
        SwingUtilities.invokeLater(() -> setup()); //open the windows
    }

    /**
     * Gets the size of the users screen. Runs before any window is made.
     */
    static void settings() {
        //getting display size
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds(); //the screen without the task bar
        displayWidth = usable.width;
        displayHeight = usable.height;
    }

    /**
     * Runs once on the Swing thread. Sets the mode colors, creates every window, shows the menu,
     * and starts the draw loop. The board, legend, map, and the other windows stay closed until
     * the user makes or loads a board (and then opens them with the buttons on the bar).
     */
    static void setup() {
        //setting mode colors
        colors[0] = new Color(255, 255, 255);
        colors[1] = new Color(227, 52, 47);
        colors[2] = new Color(246, 153, 63);
        colors[3] = new Color(255, 237, 74);
        colors[4] = new Color(56, 193, 114);
        colors[5] = new Color(77, 200, 200);
        colors[6] = new Color(52, 144, 220);
        colors[7] = new Color(101, 116, 205);
        colors[8] = new Color(149, 97, 226);
        colors[9] = new Color(246, 109, 155);

        //creating the windows
        board = new Board();
        menu = new Menu();
        legend = new Legend();
        map = new Map();
        stats = new Stats();
        instructions = new Instructions();
        tutorial = new Tutorial();
        fileList = new FileList();

        screens.add(board);
        screens.add(menu);
        screens.add(legend);
        screens.add(map);
        screens.add(stats);
        screens.add(instructions);
        screens.add(tutorial);
        screens.add(fileList);

        //building all the windows (they are not shown yet)
        for (Screen s : screens) {
            s.setup();
        }

        board.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); //closing the board closes the program

        menu.show(); //menu opens first so user can make or load a board

        //draw loop, runs about 60 times a second
        new Timer(16, e -> draw()).start();
    }

    /**
     * The draw loop, runs about 60 times a second.
     * Handles a click on the map, sends the map new data if something changed, then updates
     * and repaints every window that is open.
     */
    static void draw() {
        frameCount++;

        if (boardStarted) {
            //checks if map was clicked
            if (mapClicked) {
                int i=map.getInd(); //gets index number of the block clicked on map

                if (i < blocks.size()) {
                    Block clicked = blocks.get(i);

                    x = -clicked.getX(); //left edge of the screen goes to the left edge of that block (negative because of translate)
                    y = -(clicked.getY() + clicked.h); //bottom edge of the screen goes to the bottom edge of that block

                    //keeps the screen on blocks that already exist and lined up with the blocks
                    int maxX = Math.max(0, (numCols*bW - w) / bW * bW);
                    int maxY = Math.max(0, (numRows*bH - h) / bH * bH);
                    x = Math.max(x, -maxX);
                    y = Math.max(0, Math.min(y, maxY));
                }

                mapDirty = true;
                mapClicked = false; //set map clicked to false
            }

            //sends the map new data (only once per frame no matter how many things changed)
            if (mapDirty) {
                loadMapData();
                mapDirty = false;
            }
        }

        //updating and redrawing every window that is open
        for (Screen s : screens) {
            s.update();
            if (s.isVisible()) s.panel.repaint();
        }
    }

    /**
     * Checks that a canvas size and block size are allowed.
     * Width and height have to fit on the screen (and be at least 400), blocks have to be 10 to 50,
     * (the block size does not have to divide evenly, the extra blocks just go off the edge).
     *
     * @param nw  canvas width
     * @param nh  canvas height
     * @param nbW block width
     * @param nbH block height
     * @return a message that says what is wrong, or null if everything is fine
     */
    static String checkBoardValues(int nw, int nh, int nbW, int nbH) {
        if (nw < 400) return "error: width has to be at least 400";
        if (nw > maxBoardW()) return "error: width can be at most " + maxBoardW() + " on this screen";
        if (nh < 400) return "error: height has to be at least 400";
        if (nh > maxBoardH()) return "error: height can be at most " + maxBoardH() + " on this screen";
        if (nbW < 10 || nbW > 50 || nbH < 10 || nbH > 50) return "error: block width and height have to be 10 to 50";
        return null;
    }

    /**
     * @return the widest canvas that fits on the users screen (room is left for the window edges)
     */
    static int maxBoardW() {
        return Math.max(400, displayWidth - 20);
    }

    /**
     * @return the tallest canvas that fits on the users screen (room is left for the bar and the title bar)
     */
    static int maxBoardH() {
        return Math.max(400, displayHeight - barH - 50);
    }



    /**
     * The Apply button, reads the boxes at the bottom of the board and resizes the board if they are ok.
     */
    static void applySize() {
        int nw = board.wBox.getInt(), nh = board.hBox.getInt(), nbW = board.bWBox.getInt(), nbH = board.bHBox.getInt();

        String err = checkBoardValues(nw, nh, nbW, nbH);
        if (err != null) {
            status = err;
            return;
        }

        if (nw == w && nh == h && nbW == bW && nbH == bH) {
            status = "nothing changed";
            return;
        }

        resizeBoard(nw, nh, nbW, nbH);
        status = "board resized";
    }

    /**
     * The block width and height boxes. Runs every time they change, so the board changes while the
     * user types. Half typed numbers (like the 1 on the way to 15) are skipped.
     */
    static void applyBlockSize() {
        int nbW = board.bWBox.getInt(), nbH = board.bHBox.getInt();

        if (nbW < 10 || nbW > 50 || nbH < 10 || nbH > 50 || (nbW == bW && nbH == bH)) return;

        resizeBoard(w, h, nbW, nbH);
        status = "block size is now " + bW + " x " + bH;
    }

    /**
     * Makes a brand new blank board (writes over the one that is open). The board starts at the bottom left.
     *
     * @param nw  canvas width
     * @param nh  canvas height
     * @param nbW block width
     * @param nbH block height
     */
    static void newBoard(int nw, int nh, int nbW, int nbH) {
        w = nw;
        h = nh;
        bW = nbW;
        bH = nbH;

        fileName = ""; //not saved yet
        setFileBoxes(fileName);
        legend.clearNames();
        x = 0;
        y = 0;
        clearHistory();

        loadBlocks(); //load blocks function

        finishBoard();
        status = "new board created";
    }

    /**
     * The Restart button. Clears everything (the painting, the mode names, the file that is open, and how
     * far the board has grown) and starts over with the same sizes.
     */
    static void restartBoard() {
        newBoard(w, h, bW, bH);
        status = "board cleared";
    }

    /**
     * Forgets everything that could be undone or redone (used when the board is replaced).
     */
    static void clearHistory() {
        undoStack.clear();
        redoStack.clear();
        currentChange = new ArrayList<Change>();
    }

    /**
     * Changes the canvas size and block size of the board that is open. The blocks keep their column, row,
     * and mode, only their size and position change, so the painting is never changed by resizing (and
     * undo and redo do not notice it). The screen stays on the same column and row of blocks, and more
     * blocks are added if the canvas or the blocks got smaller and there are not enough to fill the screen.
     *
     * @param nw  new canvas width
     * @param nh  new canvas height
     * @param nbW new block width
     * @param nbH new block height
     */
    static void resizeBoard(int nw, int nh, int nbW, int nbH) {
        //the screen stays on the same column and row
        int viewCol = -x / bW;
        int viewRow = y / bH;

        w = nw;
        h = nh;
        bW = nbW;
        bH = nbH;

        for (Block b : blocks) {
            b.place(); //every block keeps its column, row, and mode, only its size and position change
        }

        x = -viewCol * bW;
        y = viewRow * bH;

        addX(); //more blocks are needed if the canvas got bigger or the blocks got smaller
        addY();

        finishBoard();
    }

    /**
     * Everything that has to happen after a board is made, loaded, or resized: resizes the board window
     * (only if the canvas size changed), makes or moves the bar, and sends the map data.
     */
    static void finishBoard() {
        moveR = false;
        moveL = false;
        moveU = false;
        moveD = false;

        currentChange = new ArrayList<Change>();
        board.painting = false;

        //the window only changes size (and moves to the middle of the screen) if the canvas size changed
        boolean sizeChanged = (board.winW != w || board.winH != h+barH);
        if (sizeChanged) board.resize(w, h+barH); //board window is the canvas plus the bar

        if (!board.barBuilt) board.buildBar(); //the bar is built once, after that it is moved and updated
        else board.syncBar();

        if (sizeChanged || !boardStarted) {
            board.frame.setLocation(Math.max(0, (displayWidth/2)-(w/2)), Math.max(0, (displayHeight/2)-((h+barH)/2))); //set location of main program window
        }
        board.show();

        boardStarted = true;

        updateTitle();
        loadMapData(); //send map data
    }

    /**
     * Turns two ints (a column and row, or an x and y) into one number so they can be a hash map key.
     *
     * @param a first int
     * @param b second int
     */
    static long key(int a, int b) {
        return ((long)a << 32) | (b & 0xffffffffL);
    }

    /**
     * Adds one column of blocks on the right (lined up with the other blocks).
     */
    static void addColumn() {
        for (int r=0; r<numRows; r++) {
            blocks.add(new Block(numCols, r));
        }
        numCols++;
    }

    /**
     * Adds one row of blocks on the top (lined up with the other blocks).
     */
    static void addRow() {
        for (int c=0; c<numCols; c++) {
            blocks.add(new Block(c, numRows));
        }
        numRows++;
    }

    /**
     * Adds columns of blocks on the right until the blocks reach the right edge of the screen.
     */
    static void addX() {
        while (numCols*bW < -x+w) {
            addColumn();
        }
    }

    /**
     * Adds rows of blocks on the top until the blocks reach the top edge of the screen.
     */
    static void addY() {
        while (numRows*bH < y+h) {
            addRow();
        }
    }

    /**
     * Moves the view one block in whatever direction is true (x and y are opposite because of translate).
     * The view cannot go left of or below the start of the board.
     */
    static void moveStep() {
        //if moveL boolean is true and x is less than 0 (constrain x so it does not go past 0)
        if (moveL && x < 0) {
            x = Math.min(0, x + bW); //add block width to x to make it move left
        }

        if (moveR) {
            x -= bW; //subtract block width from x to make it move right
            addX(); //add x blocks
        }

        if (moveU) {
            y += bH; //add block height to y to make it move up
            addY(); //add y blocks
        }

        //if moveD boolean is true and y is more than 0 (constrain y so it does not go past 0)
        if (moveD && y > 0) {
            y = Math.max(0, y - bH); //subtract block height from y to make it move down
        }

        mapDirty = true;
    }

    /**
     * Sets the move booleans. A new direction moves one block right away, holding the key keeps moving
     * after a short delay (see Board.update). If nothing changed it is just the key repeating, so it is ignored.
     *
     * @param rV right
     * @param lV left
     * @param uV up
     * @param dV down
     */
    static void setMove(boolean rV, boolean lV, boolean uV, boolean dV) {
        //if nothing changed then it is just the key repeating, so ignore it
        if (rV == moveR && lV == moveL && uV == moveU && dV == moveD) return;

        moveR = rV;
        moveL = lV;
        moveU = uV;
        moveD = dV;
        holdFrames = 0;

        if (rV || lV || uV || dV) moveStep(); //one key press = one block
        mapDirty = true;
    }

    /**
     * Fills the canvas with blank blocks, starting at the bottom left. If the block size does not divide
     * evenly into the canvas size the extra blocks go off the right and top edges.
     */
    static void loadBlocks() {
        blocks.clear();

        numCols = (int)Math.ceil(w / (double)bW);
        numRows = (int)Math.ceil(h / (double)bH);

        for (int r=0; r<numRows; r++) {
            for (int c=0; c<numCols; c++) {
                blocks.add(new Block(c, r));
            }
        }
    }

    /**
     * Puts a finished step on the undo stack (only the last 200 steps are remembered).
     * A new step means redo is not possible anymore.
     *
     * @param step everything that changed in this step (blocks that were painted or a mode name)
     */
    static void pushChange(ArrayList<Change> step) {
        undoStack.push(step);
        if (undoStack.size() > 200) undoStack.remove(0);
        redoStack.clear();
    }

    /**
     * Takes the last step off the undo stack, puts everything back the way it was, and moves it to the redo
     * stack. Only painting and mode names are undone, resizing the board is not a step.
     */
    static void undo() {
        if (undoStack.isEmpty()) {
            status = "nothing to undo";
            return;
        }

        ArrayList<Change> c = undoStack.pop();
        for (int i=c.size()-1; i>=0; i--) {
            c.get(i).revert();
        }
        redoStack.push(c);
        mapDirty = true;
    }

    /**
     * Takes the last step off the redo stack, does it again, and moves it back to the undo stack.
     */
    static void redo() {
        if (redoStack.isEmpty()) {
            status = "nothing to redo";
            return;
        }

        ArrayList<Change> c = redoStack.pop();
        for (int i=0; i<c.size(); i++) {
            c.get(i).apply();
        }
        undoStack.push(c);
        mapDirty = true;
    }

    /**
     * Turns preview on or off. Preview hides the gridlines (board and map) and turns painting off.
     */
    static void togglePreview() {
        preview = !preview;
        board.painting = false;
        status = preview ? "preview on (painting is off)" : "preview off";
    }

    /**
     * Turns a string into a json string (adds the quotes and handles special characters).
     *
     * @param s the text, null counts as empty
     * @return the text as it should be written in a json file
     */
    static String jsonString(String s) {
        if (s == null) s = "";
        s = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t");
        return "\"" + s + "\"";
    }

    /**
     * The Export button (and the e key). Saves a new file with the name in the file box.
     * Will not write over a file that is already there, that is what Update is for.
     */
    static void exportBoard() {
        String name = board.fileBox.text.trim(); //file name from the box at the bottom of the board

        if (name.length() == 0) {
            status = "error: type a file name first";
            return;
        }

        //does not write over a file that is already there, that is what update is for
        if (new File("data/" + name + ".json").exists()) {
            status = "error: " + name + " already exists, use Update to overwrite";
            return;
        }

        saveBoard(name);
    }

    /**
     * The Update button. Writes over the file that is currently open.
     */
    static void updateBoard() {
        if (fileName.length() == 0) {
            status = "error: no file open yet, use Export first";
            return;
        }

        saveBoard(fileName);
    }

    /**
     * Writes the board to data/name.json (see the file format at the top of the class).
     * If the Export center switch is on, x and y are saved at the center of each block instead of its corner.
     *
     * @param name file name without the .json
     */
    static void saveBoard(String name) {
        StringBuilder export = new StringBuilder(); //text that will be saved

        int offX = centerExport ? bW/2 : 0; //if rectMode is center, x and y are the center of the block
        int offY = centerExport ? bH/2 : 0;

        //getting block values (only blocks that have a mode)
        ArrayList<Block> modeBlocks = new ArrayList<Block>();
        for (Block b : blocks) {
            if (b.getM() > 0) {
                modeBlocks.add(b);
            }
        }

        //width, height, block width, block height, and rect mode
        export.append("[\n");
        export.append("  {\"width\": " + w + "},\n");
        export.append("  {\"height\": " + h + "},\n");
        export.append("  {\"block width\": " + bW + "},\n");
        export.append("  {\"block height\": " + bH + "},\n");
        export.append("  {\"rect mode\": " + (centerExport ? "\"center\"" : "\"corner\"") + "},\n");

        //mode names for legend
        export.append("  [\n");
        for (int i=0; i<9; i++) {
            String modeName = "mode " + (i+1) + " name"; //name in json

            export.append("    {" + jsonString(modeName) + ": " + jsonString(legend.getModeName(i)) + "}");
            if (i < 8) export.append(",");
            export.append("\n");
        }
        export.append("  ],\n");

        //block values, col and row say which block, x and y are the same spot in pixels from the bottom left corner (y goes up)
        export.append("  [\n");
        for (int i=0; i<modeBlocks.size(); i++) {
            Block b = modeBlocks.get(i);

            export.append("    {\"mode\": " + b.getM() + ", \"col\": " + b.col + ", \"row\": " + b.row + ", \"x\": " + (b.col*bW+offX) + ", \"y\": " + (b.row*bH+offY) + "}");
            if (i < modeBlocks.size()-1) export.append(",");
            export.append("\n");
        }
        export.append("  ]\n");
        export.append("]\n");

        //saving file
        try {
            new File("data").mkdirs(); //make data folder if it doesnt exist
            Files.write(Paths.get("data/" + name + ".json"), export.toString().getBytes("UTF-8"));

            fileName = name;
            setFileBoxes(name);
            updateTitle();
            status = "saved data/" + name + ".json";
        }
        catch (Exception e) {
            status = "error: could not save " + name;
        }
    }

    /**
     * Loads a board from the data folder. If it works the menu and file list close and the board shows a
     * message. If it does not work the board that is open is left alone, and the error is returned so the
     * window that asked can show it (the message does not go to the other windows).
     *
     * @param name file name without the .json
     * @return null if it worked, otherwise the error message
     */
    static String loadBoard(String name) {
        if (name.length() == 0) return "error: type a file name first";

        try {
            loadBoardData(name); //loading the board data taking name as input
            status = "loaded data/" + name + ".json";

            menu.hide(); //the menu and file list are not needed once a board is loaded
            fileList.hide();
            return null;
        }
        catch (Exception e) {
            return "error: could not load " + name;
        }
    }

    /**
     * Gets a whole number out of a json object.
     *
     * @param obj  json object
     * @param name key of the number
     */
    static int getInt(HashMap<String, Object> obj, String name) {
        return ((Number) obj.get(name)).intValue();
    }

    /**
     * Reads a json file and replaces the board with it. Everything is read before anything is changed,
     * so a broken file does not ruin the board that is open. Handles files where the block positions were
     * saved as centers, and older files (no block height, no rect mode, or no col and row).
     *
     * @param name file name without the .json
     * @throws Exception if the file is missing or does not make sense
     */
    @SuppressWarnings("unchecked")
    static void loadBoardData(String name) throws Exception {
        String text = new String(Files.readAllBytes(Paths.get("data/" + name + ".json")), "UTF-8"); //reading the file
        ArrayList<Object> json = (ArrayList<Object>) new JSONReader(text).read(); //turning the text into lists and objects

        //setting new values to their corresponding values in the json
        HashMap<String, Object> cW = (HashMap<String, Object>) json.get(0); //index 0 (canvas width)
        HashMap<String, Object> cH = (HashMap<String, Object>) json.get(1); //index 1 (canvas height)
        HashMap<String, Object> cBw = (HashMap<String, Object>) json.get(2); //index 2 (block width)
        ArrayList<Object> modeNames; //mode names array
        ArrayList<Object> blockVals; //block vals array
        HashMap<String, Object> cBh = null; //block height
        HashMap<String, Object> cRm = null; //rect mode

        if (json.get(3) instanceof ArrayList) { //older files have the mode names at index 3
            modeNames = (ArrayList<Object>) json.get(3);
            blockVals = (ArrayList<Object>) json.get(4);
            if (json.size() > 5) cBh = (HashMap<String, Object>) json.get(5);
            if (json.size() > 6) cRm = (HashMap<String, Object>) json.get(6);
        } else { //newer files: index 3 (block height), index 4 (rect mode), index 5 (mode names array), index 6 (block vals array)
            cBh = (HashMap<String, Object>) json.get(3);
            cRm = (HashMap<String, Object>) json.get(4);
            modeNames = (ArrayList<Object>) json.get(5);
            blockVals = (ArrayList<Object>) json.get(6);
        }

        int nw = getInt(cW, "width");
        int nh = getInt(cH, "height");
        int nbW = cBw.containsKey("block width") ? getInt(cBw, "block width") : getInt(cBw, "block size"); //old files only have block size
        int nbH = nbW;
        boolean centered = false;

        //block height and rect mode do not exist in the oldest files
        if (cBh != null) nbH = getInt(cBh, "block height");
        if (cRm != null) centered = "center".equals(cRm.get("rect mode"));

        if (nw <= 0 || nh <= 0 || nbW <= 0 || nbH <= 0) {
            throw new Exception("bad board values");
        }

        int offX = centered ? nbW/2 : 0; //if rectMode was center, x and y have to be moved back to the corner
        int offY = centered ? nbH/2 : 0;

        int oldBottomRow = (int)Math.ceil(nh/(double)nbH) - 1; //older files counted rows from the top left, this is their last row

        HashMap<Long, Integer> painted = new HashMap<Long, Integer>(); //mode of every painted block by column and row
        int maxCol = (int)Math.ceil(nw/(double)nbW) - 1; //the board is at least big enough to fill the canvas
        int maxRow = (int)Math.ceil(nh/(double)nbH) - 1;

        //getting block vals
        for (int i = 0; i < blockVals.size(); i++) {
            HashMap<String, Object> block = (HashMap<String, Object>) blockVals.get(i); //object for block

            int m = Math.max(0, Math.min(9, getInt(block, "mode")));
            int col, row;

            if (block.containsKey("col")) { //newer files say which column and row the block is in
                col = getInt(block, "col");
                row = getInt(block, "row");
            } else { //older files only have x and y counted from the top left (blocks above the start have a negative y)
                col = (getInt(block, "x") - offX) / nbW;
                row = oldBottomRow - Math.floorDiv(getInt(block, "y") - offY, nbH);
            }

            if (col < 0 || row < 0) continue; //not on the board

            painted.put(key(col, row), m);

            //making sure the board is big enough to hold the block
            if (col > maxCol) maxCol = col;
            if (row > maxRow) maxRow = row;
        }

        //getting mode names
        String[] names = new String[Math.min(9, modeNames.size())];
        for (int i=0; i<names.length; i++) {
            HashMap<String, Object> modeName = (HashMap<String, Object>) modeNames.get(i);
            names[i] = (String) modeName.get("mode " + (i+1) + " name");
        }

        //everything loaded fine, so now the real values can be changed
        w = nw;
        h = nh;
        bW = nbW;
        bH = nbH;
        numCols = maxCol + 1;
        numRows = maxRow + 1;

        //loading blocks, every column and row from the bottom left
        blocks = new ArrayList<Block>();
        for (int r=0; r<numRows; r++) {
            for (int c=0; c<numCols; c++) {
                Block b = new Block(c, r);

                //checking if this block is set to a mode
                Integer m = painted.get(key(c, r));
                if (m != null) b.setMode(m);

                blocks.add(b);
            }
        }

        legend.clearNames();
        for (int i=0; i<names.length; i++) {
            legend.setModeName(i, names[i]); //setting mode name to legend in index i
        }

        x = 0; //start at the bottom left
        y = 0;
        fileName = name;
        setFileBoxes(name);
        clearHistory();

        finishBoard();
    }

    /**
     * Makes the file boxes (bottom of the board and menu) show the same name.
     *
     * @param name file name to show
     */
    static void setFileBoxes(String name) {
        if (board.fileBox != null) board.fileBox.text = name;
        if (menu.mFile != null) menu.mFile.text = name;
    }

    /** Puts the name of the open file in the title of the board window. */
    static void updateTitle() {
        board.frame.setTitle(fileName.length() == 0 ? "board builder" : "board builder - " + fileName);
    }

    /**
     * Gathers the position, mode, and size of every block and sends it to the map window.
     * y vals are shifted so the top row is 0 (the board goes up from the bottom left, so y is negative).
     */
    static void loadMapData() {
        int topY = -numRows*bH; //top edge of the blocks

        //new array lists for block vals
        ArrayList<Integer> xVals = new ArrayList<Integer>();
        ArrayList<Integer> yVals = new ArrayList<Integer>();
        ArrayList<Integer> modes = new ArrayList<Integer>();

        for (Block b : blocks) {
            //adding values to corresponding array lists
            xVals.add(b.getX());
            yVals.add(b.getY()-topY); //minus the top edge in order to shift everything up to 0 so the top row is now 0
            modes.add(b.getM());
        }

        int bXmax = (numCols-1)*bW; //greatest block x val
        int bYmax = numRows*bH; //height of all the blocks

        //send map data, the screen is where it is in the board (the top of the screen is -y-h, then shifted by the top edge)
        map.sendMapData(bXmax, bYmax, -x, -y-h-topY, w, h, bW, bH, xVals, yVals, modes);
    }

    /**
     * Takes a value from one range and puts it in the same spot of another range.
     *
     * @param value  the value
     * @param start1 start of the range it is in
     * @param stop1  end of the range it is in
     * @param start2 start of the new range
     * @param stop2  end of the new range
     */
    static float mapRange(float value, float start1, float stop1, float start2, float stop2) {
        return start2 + (stop2 - start2) * ((value - start1) / (stop1 - start1));
    }

    /**
     * Draws text so it is vertically centered on y.
     *
     * @param g     what to draw on
     * @param s     the text (null draws nothing)
     * @param x     x val (start of the text for LEFT, middle for CENTER, end for RIGHT)
     * @param y     y val of the middle of the text
     * @param align LEFT, CENTER, or RIGHT
     */
    static void drawText(Graphics2D g, String s, int x, int y, int align) {
        if (s == null) return;

        FontMetrics fm = g.getFontMetrics();
        int tx = x;
        if (align == CENTER) tx = x - fm.stringWidth(s)/2;
        else if (align == RIGHT) tx = x - fm.stringWidth(s);
        int ty = y + (fm.getAscent() - fm.getDescent())/2;

        g.drawString(s, tx, ty);
    }



    /**
     * Cuts text short (ending in ...) so it fits in a width.
     *
     * @param g    what the text will be drawn on (its font is used to measure)
     * @param t    the text
     * @param maxW most pixels wide it can be
     */
    static String fitText(Graphics2D g, String t, int maxW) {
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(t) <= maxW) return t;

        while (t.length() > 0 && fm.stringWidth(t + "...") > maxW) {
            t = t.substring(0, t.length()-1);
        }
        return t + "...";
    }

    /**
     * Gives a button, switch, or textbox a tip (shown at the bottom of the board while the mouse is over it).
     *
     * @param b the button (any kind)
     * @param t the tip
     * @return the same button, so it can be made and given a tip in one line
     */
    static <T extends Button> T tip(T b, String t) {
        b.tip = t;
        return b;
    }



    /**
     * One square of the board. It lives in a column and a row (0, 0 is the bottom left) and has a mode (0-9)
     * that decides its color. Its pixel position and size come from the column, row, and block size,
     * so when the block size changes place() puts it in its new spot and the mode is not touched.
     */
    static class Block {
        //attributes
        int col, row; //which column and row the block is in
        int x, y, w, h, r = 0, mode; //position of the top left corner and size in pixels, r is the corner roundness
        Color f;

        /**
         * @param col column (0 is the left most)
         * @param row row (0 is the bottom)
         */
        Block(int col, int row) {
            this.col = col;
            this.row = row;
            place();
        }

        /**
         * Works out the pixel position and size from the column, row, and the current block size.
         * y is 0 at the bottom of the canvas (when the screen is at the start) and negative above it.
         */
        void place() {
            x = col*bW;
            y = -(row+1)*bH;
            w = bW;
            h = bH;
        }

        /**
         * Draws the block.
         *
         * @param g    what to draw on
         * @param grid true to also draw the gridline around the block
         */
        void handle(Graphics2D g, boolean grid) {
            f = colors[mode]; //setting fill to mode color

            //drawing rect
            g.setColor(f);
            g.fillRoundRect(x, y, w, h, r, r);

            //drawing gridline
            if (grid) {
                g.setColor(gridColor);
                g.drawRoundRect(x, y, w, h, r, r);
            }
        }

        /**
         * Sets the mode (and so the color) of the block.
         */
        void setMode(int m) {
            mode=m;
        }

        /**
         * True if the point is inside the block (the left and top edges count, the right and bottom do not,
         * so a point on the line between two blocks is only in one of them).
         */
        boolean checkCollisions(int pX, int pY) {
            return ((pX >= x) && (pX < x+w) && (pY >= y) && (pY < y+h));
        }

        /**
         * @return x val of the left edge
         */
        int getX() {
            return x;
        }

        /**
         * @return y val of the top edge
         */
        int getY() {
            return y;
        }

        /**
         * @return the mode
         */
        int getM() {
            return(mode);
        }
    }



    /**
     * One thing that changed (used for undo and redo): either a block that was painted (which block, its
     * old mode, and its new mode) or a mode name that was edited (which name, the old name, and the new name).
     * Everything done with one click and drag, or one edited name, is a list of these and is one step on the
     * undo and redo stacks. Resizing the board is not a change.
     */
    static class Change {
        boolean isName; //true = a mode name changed, false = a block was painted
        int index; //index of the block in the blocks list, or index 0-8 of the mode name
        int oldMode, newMode; //for a block
        String oldName, newName; //for a mode name

        /**
         * A block that was painted.
         *
         * @param index   index of the block in the blocks list
         * @param oldMode mode before
         * @param newMode mode after
         */
        Change(int index, int oldMode, int newMode) {
            this.index = index;
            this.oldMode = oldMode;
            this.newMode = newMode;
        }

        /**
         * A mode name that was edited.
         *
         * @param index   0-8 (mode 1-9)
         * @param oldName name before
         * @param newName name after
         */
        Change(int index, String oldName, String newName) {
            this.isName = true;
            this.index = index;
            this.oldName = oldName;
            this.newName = newName;
        }

        /** Puts it back the way it was. */
        void revert() {
            if (isName) legend.setModeName(index, oldName);
            else blocks.get(index).setMode(oldMode);
        }

        /** Does it again. */
        void apply() {
            if (isName) legend.setModeName(index, newName);
            else blocks.get(index).setMode(newMode);
        }
    }







    /**
     * A clickable rectangle with a label that runs an action. It is the parent of Switch and Textbox.
     * Screens keep all of their buttons, switches, and textboxes in one list and call the same methods
     * on all of them (handle, checkCollisions, click, keyTyped...), each kind does it its own way.
     */
    static class Button {
        //attributes
        int x, y, w, h;
        String label;
        Runnable action; //what happens when it is clicked
        String tip = ""; //tells the user what it does (shown at the bottom of the board while the mouse is over it)
        Color fill = buttonFill, textColor = Color.WHITE;

        /**
         * @param label  text on the button
         * @param action what runs when it is clicked (can be null)
         */
        Button(String label, int x, int y, int w, int h, Runnable action) {
            this.label = label;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.action = action;
        }

        /**
         * Draws the button.
         */
        void handle(Graphics2D g) {
            g.setColor(fill);
            g.fillRect(x, y, w, h);
            g.setStroke(new BasicStroke(1));
            g.setColor(new Color(150, 150, 150));
            g.drawRect(x, y, w, h);

            g.setFont(uiFont);
            g.setColor(textColor);
            drawText(g, label, x+w/2, y+h/2, CENTER);
        }

        /**
         * True if the point is on the button.
         */
        boolean checkCollisions(int pX, int pY) {
            return ((pX >= x) && (pX < x+w) && (pY >= y) && (pY < y+h));
        }

        /**
         * Called when the button is clicked, runs the action.
         */
        void click(int pX, int pY) {
            if (action != null) action.run();
        }

        /**
         * Called when a key is pressed in the window.
         *
         * @return true if the button used the key (only textboxes do), so nothing else should
         */
        boolean keyTyped(char key, int keyCode, boolean ctrl) {
            return false;
        }

        /**
         * Called on every button when the user clicks somewhere (textboxes stop typing).
         */
        void unfocus() {
        }

        /** Called while the mouse is dragged over the window (textboxes use it to select text). */
        void drag(int pX, int pY) {
        }

        /** Called when the mouse button is let go. */
        void release() {
        }
    }



    /**
     * An on/off switch with a label to its right. Inherits from Button, so the action runs after the
     * switch is flipped and can read the new value from the on boolean.
     */
    static class Switch extends Button {
        boolean on;

        /**
         * @param label text drawn next to the switch
         * @param on    starting value
         */
        Switch(String label, int x, int y, boolean on) {
            super(label, x, y, 40, 20, null);
            this.on = on;
        }

        /**
         * Draws the track, the knob, and the label (overrides Button).
         */
        void handle(Graphics2D g) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g.setColor(on ? new Color(56, 193, 114) : new Color(100, 100, 100));
            g.fillRoundRect(x, y, w, h, h, h);
            g.setColor(Color.WHITE);
            g.fillOval(on ? x+w-h+2 : x+2, y+2, h-4, h-4);

            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

            g.setFont(uiFont);
            g.setColor(textColor);
            drawText(g, label, x+w+10, y+h/2, LEFT);
        }

        /**
         * True if the point is on the switch or its label (overrides Button).
         */
        boolean checkCollisions(int pX, int pY) {
            int hitW = w + 10 + Textbox.metrics.stringWidth(label); //switch plus the label next to it
            return ((pX >= x) && (pX < x+hitW) && (pY >= y) && (pY < y+h));
        }

        /**
         * Flips the switch and then runs the action (overrides Button).
         */
        void click(int pX, int pY) {
            on = !on;
            super.click(pX, pY);
        }
    }



    /**
     * A box the user can type in. It inherits from Button so it is drawn and clicked like any other
     * button, but it overrides handle, click, drag, and keyTyped to add text editing.
     *
     * Editing works like a normal textbox: click or use the arrow keys to move the cursor, drag or use
     * shift + arrows to select, double click or ctrl + a to select everything, ctrl + c / x / v to
     * copy, cut, and paste, and typing replaces whatever is selected.
     */
    static class Textbox extends Button {
        String text;
        int cursor = 0; //where the cursor is in the text
        int anchor = 0; //the other end of the selection (same as cursor when nothing is selected)
        int scroll = 0; //first letter that is shown (for when the text is too long for the box)
        boolean focused = false; //true when user is typing in it
        boolean dragging = false; //true while the mouse is held down to select text
        long lastClick = 0; //time of the last click (used for double click)
        boolean live; //true = action runs every time the text changes, false = action runs when enter is pressed
        String allowed; //characters that can be typed (null = anything)
        int maxLen; //most letters the box can hold
        String textAtFocus = ""; //what the text was when the user started typing
        Runnable onCommit; //runs when the user is done typing (clicks away or presses enter) and the text is different
        static FontMetrics metrics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics().getFontMetrics(uiFont); //for measuring text when clicking

        /**
         * @param label   text drawn to the left of the box
         * @param text    starting text
         * @param action  what runs when enter is pressed (or every change if live is true), can be null
         * @param live    true = run the action on every change
         * @param allowed letters that can be typed, null for anything
         * @param maxLen  most letters that can be typed
         */
        Textbox(String label, String text, int x, int y, int w, int h, Runnable action, boolean live, String allowed, int maxLen) {
            super(label, x, y, w, h, action);
            this.text = text;
            this.cursor = text.length();
            this.anchor = cursor;
            this.live = live;
            this.allowed = allowed;
            this.maxLen = maxLen;
        }

        /** Keeps the cursor and anchor inside the text (the text can be changed from outside the box). */
        void clamp() {
            cursor = Math.max(0, Math.min(cursor, text.length()));
            anchor = Math.max(0, Math.min(anchor, text.length()));
        }

        /** True if some of the text is selected. */
        boolean hasSelection() {
            return anchor != cursor;
        }

        /** First letter of the selection. */
        int selStart() {
            return Math.min(anchor, cursor);
        }

        /** Spot after the last letter of the selection. */
        int selEnd() {
            return Math.max(anchor, cursor);
        }

        /** True if the letter can be typed in this box. */
        boolean allowedChar(char c) {
            return !Character.isISOControl(c) && c != KeyEvent.CHAR_UNDEFINED && "/\\".indexOf(c) < 0 && (allowed == null || allowed.indexOf(c) >= 0);
        }

        /** Removes the selected text and puts the cursor where it was. */
        void deleteSelection() {
            int start = selStart();
            text = text.substring(0, start) + text.substring(selEnd());
            cursor = start;
            anchor = start;
        }

        /** Runs the action if this is a live textbox (one that reacts to every change). */
        void runLive() {
            if (live && action != null) action.run();
        }

        /** Puts text at the cursor (replacing the selection), skipping letters that are not allowed. */
        void insert(String s) {
            StringBuilder ok = new StringBuilder();
            for (char c : s.toCharArray()) {
                if (allowedChar(c)) ok.append(c);
            }

            int room = maxLen - (text.length() - (selEnd() - selStart())); //how many letters can still fit
            String add = ok.substring(0, Math.max(0, Math.min(room, ok.length())));
            if (add.length() == 0) return;

            if (hasSelection()) deleteSelection();
            text = text.substring(0, cursor) + add + text.substring(cursor);
            cursor += add.length();
            anchor = cursor;
            runLive();
        }

        /** Copies the selected text to the clipboard. */
        void copy() {
            if (!hasSelection()) return;

            try {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text.substring(selStart(), selEnd())), null);
            }
            catch (Exception e) {
            }
        }

        /** Pastes whatever text is on the clipboard at the cursor. */
        void paste() {
            try {
                String s = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
                insert(s.replace("\n", "").replace("\r", ""));
            }
            catch (Exception e) {
            }
        }

        //handle function (overrides button)
        void handle(Graphics2D g) {
            g.setFont(uiFont);
            FontMetrics fm = g.getFontMetrics();

            clamp();

            //label on the left
            g.setColor(textColor);
            drawText(g, label, x-6, y+h/2, RIGHT);

            //box
            g.setColor(focused ? new Color(255, 255, 210) : Color.WHITE);
            g.fillRect(x, y, w, h);
            g.setStroke(new BasicStroke(1));
            g.setColor(focused ? new Color(52, 144, 220) : new Color(150, 150, 150));
            g.drawRect(x, y, w, h);

            //if the text is too long only part of it is shown, this keeps the cursor in view
            if (fm.stringWidth(text) <= w-12) scroll = 0;
            scroll = Math.min(scroll, cursor);
            while (scroll < cursor && fm.stringWidth(text.substring(scroll, cursor)) > w-12) {
                scroll++;
            }

            //how much of the text fits in the box
            int end = scroll;
            while (end < text.length() && fm.stringWidth(text.substring(scroll, end+1)) <= w-10) {
                end++;
            }

            //highlighting the selected text
            if (focused && hasSelection()) {
                int s = Math.max(selStart(), scroll);
                int e = Math.min(selEnd(), end);

                if (s < e) {
                    int sx = x+5+fm.stringWidth(text.substring(scroll, s));
                    int ex = x+5+fm.stringWidth(text.substring(scroll, e));

                    g.setColor(new Color(52, 144, 220, 130));
                    g.fillRect(sx, y+3, ex-sx, h-6);
                }
            }

            g.setColor(Color.BLACK);
            drawText(g, text.substring(scroll, end), x+5, y+h/2, LEFT);

            //blinking cursor
            if (focused && (System.currentTimeMillis()/500)%2 == 0) {
                int cx = x+5+fm.stringWidth(text.substring(scroll, cursor));
                g.drawLine(cx, y+4, cx, y+h-4);
            }
        }

        /**
         * Gets the spot in the text that is closest to an x val (used when clicking in the box).
         *
         * @param pX x val of the mouse
         * @return index in the text where the cursor should go
         */
        int indexAt(int pX) {
            int best = scroll;
            int bestDist = Integer.MAX_VALUE;

            for (int i=scroll; i<=text.length(); i++) {
                int dist = Math.abs(metrics.stringWidth(text.substring(scroll, i)) - (pX-(x+5)));
                if (dist < bestDist) {
                    bestDist = dist;
                    best = i;
                }
            }
            return best;
        }

        //click function (overrides button, starts typing and moves the cursor to where it was clicked)
        void click(int pX, int pY) {
            long now = System.currentTimeMillis();

            if (!focused) textAtFocus = text; //remembering what it was before the user changes it
            focused = true;
            scroll = Math.max(0, Math.min(scroll, text.length()));

            if (now - lastClick < 400) { //double click selects everything
                anchor = 0;
                cursor = text.length();
                dragging = false;
            } else {
                cursor = indexAt(pX);
                anchor = cursor;
                dragging = true;
            }
            lastClick = now;
        }

        //drag function (overrides button, dragging the mouse selects text)
        void drag(int pX, int pY) {
            if (focused && dragging) cursor = indexAt(pX);
        }

        //release function (overrides button)
        void release() {
            dragging = false;
        }

        //unfocus function (overrides button)
        void unfocus() {
            boolean wasFocused = focused;
            focused = false;
            dragging = false;
            if (wasFocused) commit();
        }

        /**
         * Runs onCommit if the text is different from when the user started typing.
         */
        void commit() {
            if (onCommit != null && !text.equals(textAtFocus)) onCommit.run();
            textAtFocus = text;
        }

        //key function (overrides button)
        boolean keyTyped(char key, int keyCode, boolean ctrl) {
            if (!focused) return false;
            clamp();

            //ctrl shortcuts, select all, copy, cut, and paste (anything else with ctrl is ignored)
            if (ctrl) {
                if (keyCode == KeyEvent.VK_A) {
                    anchor = 0;
                    cursor = text.length();
                } else if (keyCode == KeyEvent.VK_C) {
                    copy();
                } else if (keyCode == KeyEvent.VK_X) {
                    copy();
                    if (hasSelection()) {
                        deleteSelection();
                        runLive();
                    }
                } else if (keyCode == KeyEvent.VK_V) {
                    paste();
                }
                return true;
            }

            if (keyCode == KeyEvent.VK_ENTER) {
                focused = false;
                commit();
                if (!live && action != null) action.run();
            } else if (keyCode == KeyEvent.VK_ESCAPE) {
                focused = false;
                commit();
            } else if (keyCode == KeyEvent.VK_LEFT) { //move cursor left (shift keeps the selection going)
                if (!shiftDown && hasSelection()) cursor = selStart();
                else cursor = Math.max(0, cursor-1);
                if (!shiftDown) anchor = cursor;
            } else if (keyCode == KeyEvent.VK_RIGHT) { //move cursor right
                if (!shiftDown && hasSelection()) cursor = selEnd();
                else cursor = Math.min(text.length(), cursor+1);
                if (!shiftDown) anchor = cursor;
            } else if (keyCode == KeyEvent.VK_HOME) {
                cursor = 0;
                if (!shiftDown) anchor = cursor;
            } else if (keyCode == KeyEvent.VK_END) {
                cursor = text.length();
                if (!shiftDown) anchor = cursor;
            } else if (keyCode == KeyEvent.VK_BACK_SPACE) { //delete the selection or the letter before the cursor
                if (hasSelection()) {
                    deleteSelection();
                    runLive();
                } else if (cursor > 0) {
                    text = text.substring(0, cursor-1) + text.substring(cursor);
                    cursor--;
                    anchor = cursor;
                    runLive();
                }
            } else if (keyCode == KeyEvent.VK_DELETE) { //delete the selection or the letter after the cursor
                if (hasSelection()) {
                    deleteSelection();
                    runLive();
                } else if (cursor < text.length()) {
                    text = text.substring(0, cursor) + text.substring(cursor+1);
                    runLive();
                }
            } else if (allowedChar(key)) { //typing a letter
                insert("" + key);
            }

            return true;
        }

        //get int function, returns 0 if what is typed is not a number
        int getInt() {
            try {
                return Integer.parseInt(text.trim());
            }
            catch(NumberFormatException e) {
                return 0;
            }
        }
    }



    /**
     * A window. Every window of the program (Board, Menu, Legend, Map, Stats, Instructions, Tutorial,
     * FileList) inherits from this. It makes the JFrame and drawing area, sends mouse and key events to
     * the buttons in its ui list, and lets each window decide what to draw in draw().
     * Closing a window only hides it, so it can be opened again.
     */
    static abstract class Screen {
        //attributes
        String title;
        int winW, winH, locX, locY; //window size and location
        JFrame frame; //window
        JPanel panel; //drawing area
        ArrayList<Button> ui = new ArrayList<Button>(); //buttons, switches, and textboxes (polymorphism, they are all drawn and clicked the same way)
        int mouseX = 0, mouseY = 0; //mouse x and y in this window

        /**
         * @param title text at the top of the window
         * @param winW  width of the drawing area
         * @param winH  height of the drawing area
         * @param locX  x of the window on the screen
         * @param locY  y of the window on the screen
         */
        Screen(String title, int winW, int winH, int locX, int locY) {
            this.title = title;
            this.winW = winW;
            this.winH = winH;
            this.locX = locX;
            this.locY = locY;
        }

        /**
         * Builds the window and its listeners (does not show it).
         */
        void setup() {
            frame = new JFrame(title); //setting title of window

            panel = new JPanel() {
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                    g2.setColor(Color.BLACK); //setting background to black
                    g2.fillRect(0, 0, getWidth(), getHeight());

                    Screen.this.draw(g2); //whatever this screen draws
                    for (Button b : Screen.this.ui) { //then buttons, switches, and textboxes
                        b.handle(g2);
                    }
                }
            };
            panel.setPreferredSize(new Dimension(winW, winH)); //setting size of window
            panel.setFocusable(true);

            MouseAdapter mouse = new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    mouseX = e.getX();
                    mouseY = e.getY();
                    panel.requestFocusInWindow();
                    Screen.this.mousePressed();
                }

                public void mouseReleased(MouseEvent e) {
                    mouseX = e.getX();
                    mouseY = e.getY();
                    Screen.this.mouseReleased();
                }

                public void mouseMoved(MouseEvent e) {
                    mouseX = e.getX();
                    mouseY = e.getY();
                }

                public void mouseDragged(MouseEvent e) {
                    mouseX = e.getX();
                    mouseY = e.getY();
                    Screen.this.mouseDragged();
                }
            };
            panel.addMouseListener(mouse);
            panel.addMouseMotionListener(mouse);

            panel.addKeyListener(new KeyAdapter() {
                                     public void keyPressed(KeyEvent e) {
                                         shiftDown = e.isShiftDown();
                                         Screen.this.keyPressed(e.getKeyChar(), e.getKeyCode(), e.isControlDown() || e.isMetaDown());
                                     }

                                     public void keyReleased(KeyEvent e) {
                                         shiftDown = e.isShiftDown();
                                         Screen.this.keyReleased(e.getKeyCode());
                                     }
                                 }
            );

            //when the window is clicked away from, anything being typed in is finished
            frame.addWindowFocusListener(new WindowAdapter() {
                                             public void windowLostFocus(WindowEvent e) {
                                                 for (Button b : Screen.this.ui) {
                                                     b.unfocus();
                                                 }
                                             }
                                         }
            );

            frame.add(panel);
            frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE); //closing a window only hides it, it can be opened again
            frame.setResizable(false);
            frame.pack();
            frame.setLocation(locX, locY); //setting location of window on screen
        }

        /**
         * Each window draws its own stuff here (the buttons are drawn right after this runs).
         *
         * @param g what to draw on, 0,0 is the top left of the window
         */
        abstract void draw(Graphics2D g);

        /**
         * Runs every frame, windows override this when they need to do something over time.
         */
        void update() {
        }

        /**
         * Runs right before the window is shown, windows override this to refresh what they show.
         */
        void onShow() {
        }

        /**
         * Opens the window and brings it to the front.
         */
        void show() {
            onShow();
            frame.setVisible(true);
            frame.toFront();
            panel.requestFocusInWindow();
        }

        /**
         * Closes the window (it can be opened again with show).
         */
        void hide() {
            frame.setVisible(false);
        }

        /**
         * Opens the window if it is closed and closes it if it is open.
         */
        void toggle() {
            if (isVisible()) hide();
            else show();
        }

        /**
         * True if the window is open.
         */
        boolean isVisible() {
            return frame != null && frame.isVisible();
        }

        /**
         * Changes the size of the drawing area (and the window with it).
         *
         * @param newW new width
         * @param newH new height
         */
        void resize(int newW, int newH) {
            winW = newW;
            winH = newH;
            panel.setPreferredSize(new Dimension(winW, winH));

            //some systems ignore size changes on a window that can not be resized, so it is allowed for a moment
            frame.setResizable(true);
            frame.pack();
            frame.setResizable(false);

            panel.revalidate();
            panel.repaint();
        }

        /**
         * Finds the button, switch, or textbox that was clicked (mouseX and mouseY are where the mouse is)
         * and clicks it. Everything else stops being typed in.
         */
        void mousePressed() {
            for (Button b : ui) {
                b.unfocus();
            }

            Button hit = null;
            for (Button b : ui) {
                if (b.checkCollisions(mouseX, mouseY)) {
                    hit = b;
                    break;
                }
            }

            if (hit != null) hit.click(mouseX, mouseY); //clicked after the loop because a click can rebuild the list
        }

        /**
         * The mouse button was let go.
         */
        void mouseReleased() {
            for (Button b : ui) {
                b.release();
            }
        }

        /**
         * The mouse moved while the button was held down.
         */
        void mouseDragged() {
            for (Button b : ui) {
                b.drag(mouseX, mouseY);
            }
        }

        /**
         * Sends a key to the buttons, only a textbox that is being typed in will use it.
         *
         * @return true if one of them used the key
         */
        boolean uiKey(char key, int keyCode, boolean ctrl) {
            for (Button b : ui) {
                if (b.keyTyped(key, keyCode, ctrl)) return true;
            }
            return false;
        }

        /**
         * A key was pressed.
         *
         * @param key     the letter
         * @param keyCode what key it was (KeyEvent.VK_...)
         * @param ctrl    true if ctrl (or command on a mac) is held
         */
        void keyPressed(char key, int keyCode, boolean ctrl) {
            uiKey(key, keyCode, ctrl);
        }

        /**
         * A key was let go.
         */
        void keyReleased(int keyCode) {
        }
    }



    /**
     * The main window. The top part (w by h) is the canvas that shows the blocks and moves around,
     * the bar under it (barH tall) never moves and has the boxes, buttons, and switches.
     * Clicking the canvas paints, clicking the bar goes to the buttons.
     */
    static class Board extends Screen {
        Textbox wBox, hBox, bWBox, bHBox, fileBox; //boxes at the bottom
        Button previewBtn;
        Button restartBtn;
        long restartArmed = 0; //time Restart was clicked the first time (0 = it has not been clicked)
        boolean barBuilt = false; //true once the bar has been made
        int barTop = 0; //y of the top of the bar (the canvas height)
        boolean painting = false; //true while the mouse is held down on the canvas
        int heldCode = -1; //key that is being held

        /**
         * Makes the board window (the real size is set once a board is made or loaded).
         */
        Board() {
            super("board builder", 400, 400+barH, 0, 0);
        }

        /**
         * Makes all the buttons, switches, and textboxes in the bar. They are only made once, after that
         * syncBar() moves them when the canvas height changes (so a textbox keeps being typed in while the
         * board changes). Hover the mouse over any of them to see what it does at the bottom of the bar.
         */
        void buildBar() {
            ui.clear();
            barTop = h;
            barBuilt = true;

            int r1 = h+6, r2 = h+32, r3 = h+58, r4 = h+84, r5 = h+110, r6 = h+138, r7 = h+162, bh = 22; //y vals of each row

            //row 1, canvas size (enter or Apply)
            wBox = tip(new Textbox("Width", "" + w, 58, r1, 48, bh, () -> applySize(), false, "0123456789", 4), "canvas width in pixels (press enter or Apply)");
            hBox = tip(new Textbox("Height", "" + h, 166, r1, 48, bh, () -> applySize(), false, "0123456789", 4), "canvas height in pixels (press enter or Apply)");
            ui.add(wBox);
            ui.add(hBox);
            ui.add(tip(new Button("Apply", 226, r1, 60, bh, () -> applySize()), "use the canvas width and height in the boxes"));

            //row 2, block size (changes as you type)
            bWBox = tip(new Textbox("Block width", "" + bW, 76, r2, 38, bh, () -> applyBlockSize(), true, "0123456789", 2), "block width, the board changes as you type");
            bHBox = tip(new Textbox("Block height", "" + bH, 196, r2, 38, bh, () -> applyBlockSize(), true, "0123456789", 2), "block height, the board changes as you type");
            ui.add(bWBox);
            ui.add(bHBox);

            //row 3, files
            fileBox = tip(new Textbox("File", fileName, 36, r3, 100, bh, null, false, null, 24), "file name (files are in the data folder)");
            ui.add(fileBox);
            ui.add(tip(new Button("Export", 142, r3, 50, bh, () -> exportBoard()), "save a new file with this name"));
            ui.add(tip(new Button("Update", 196, r3, 50, bh, () -> updateBoard()), "save over the file that is open"));
            ui.add(tip(new Button("Load", 250, r3, 50, bh, () -> {
                String err = loadBoard(fileBox.text.trim());
                if (err != null) status = err;
            }
            ), "open the file with this name"));

            //row 4, tools
            previewBtn = tip(new Button("Preview", 134, r4, 58, bh, () -> togglePreview()), "hide the gridlines and stop painting (p)");
            restartBtn = tip(new Button("Restart", 258, r4, 58, bh, () -> restartClicked()), "clear the whole board (click twice)");
            ui.add(tip(new Button("Undo", 10, r4, 58, bh, () -> undo()), "go back one change (ctrl+z)"));
            ui.add(tip(new Button("Redo", 72, r4, 58, bh, () -> redo()), "bring back the change you undid (ctrl+y)"));
            ui.add(previewBtn);
            ui.add(tip(new Button("Menu", 196, r4, 58, bh, () -> menu.show()), "make a new board, load one, or browse files"));
            ui.add(restartBtn);

            //row 5, windows
            ui.add(tip(new Button("Legend", 10, r5, 58, bh, () -> legend.toggle()), "name your modes"));
            ui.add(tip(new Button("Map", 72, r5, 58, bh, () -> map.toggle()), "see the whole board and jump around"));
            ui.add(tip(new Button("Stats", 134, r5, 58, bh, () -> stats.toggle()), "sizes and how many blocks of each mode"));
            ui.add(tip(new Button("Help", 196, r5, 58, bh, () -> instructions.toggle()), "all the controls"));
            ui.add(tip(new Button("Tutorial", 258, r5, 58, bh, () -> tutorial.toggle()), "step by step guide"));

            //row 6 and 7, switches
            Switch gridSwitch = tip(new Switch("Grid lines", 10, r6, gridVisible), "show or hide the gridlines on the board and map");
            gridSwitch.action = () -> gridVisible = gridSwitch.on;
            ui.add(gridSwitch);

            Switch legendSwitch = tip(new Switch("Legend on map", 130, r6, legendOnMap), "show the mode names on the map");
            legendSwitch.action = () -> legendOnMap = legendSwitch.on;
            ui.add(legendSwitch);

            Switch exportSwitch = tip(new Switch(centerExport ? "Export at block center" : "Export at block corner", 10, r7, centerExport), "x and y are saved at each block's center or bottom left corner");
            exportSwitch.action = () -> {
                centerExport = exportSwitch.on;
                exportSwitch.label = centerExport ? "Export at block center" : "Export at block corner";
            };
            ui.add(exportSwitch);
        }

        /**
         * Moves the bar when the canvas height changed and puts the new sizes in the boxes
         * (a box that is being typed in is left alone).
         */
        void syncBar() {
            int dy = h - barTop; //how far the bar has to move
            for (Button b : ui) {
                b.y += dy;
            }
            barTop = h;

            if (!wBox.focused) wBox.text = "" + w;
            if (!hBox.focused) hBox.text = "" + h;
            if (!bWBox.focused) bWBox.text = "" + bW;
            if (!bHBox.focused) bHBox.text = "" + bH;
        }

        /**
         * Restart has to be clicked twice (so one stray click does not wipe the board). The first click
         * turns the button red for 3 seconds, the second click clears everything.
         */
        void restartClicked() {
            if (restartArmed > 0) {
                restartArmed = 0;
                restartBoard();
            } else {
                restartArmed = System.currentTimeMillis();
                status = "click Restart again to clear everything";
            }
        }

        /**
         * Draws the blocks that are on screen (moved by the view, the start is the bottom left), then the bar:
         * the current mode (square top right with its name under it), the file name, and a tip or the status.
         */
        void draw(Graphics2D g) {
            //canvas, only drawn inside the canvas space (not the bar)
            Graphics2D gb = (Graphics2D) g.create();
            gb.setClip(0, 0, w, h);
            gb.translate(x, y+h); //move canvas (the start is the bottom left corner of the canvas)
            gb.setStroke(new BasicStroke(gridThickness));

            boolean grid = gridVisible && !preview;

            //handling blocks, blocks that are off screen are skipped
            for (int i = 0; i < blocks.size(); i++) {
                Block b = blocks.get(i);

                if (b.x+b.w < -x || b.x > -x+w || b.y+b.h < -y-h || b.y > -y) continue;
                b.handle(gb, grid);
            }
            gb.dispose();

            //bar
            g.setColor(new Color(30, 30, 30));
            g.fillRect(0, h, winW, barH);
            g.setColor(new Color(120, 120, 120));
            g.drawLine(0, h, winW, h);

            //current mode, its color and number (top right of the bar)
            g.setColor(colors[mode]);
            g.fillRect(winW-34, h+6, 24, 24);
            g.setColor(new Color(150, 150, 150));
            g.drawRect(winW-34, h+6, 24, 24);
            g.setFont(uiFont);
            g.setColor(Color.BLACK);
            drawText(g, "" + mode, winW-22, h+18, CENTER);

            //name of the current mode, right under the square (mode 0 does not have a name)
            String modeName = (mode > 0) ? legend.getModeName(mode-1) : "";
            if (modeName.length() > 0) {
                g.setColor(Color.WHITE);
                drawText(g, fitText(g, modeName, 84), winW-10, h+42, RIGHT);
            } else {
                g.setColor(new Color(130, 130, 130));
                drawText(g, mode == 0 ? "blank" : "no name", winW-10, h+42, RIGHT);
            }

            //name of the file that is open
            g.setColor(new Color(170, 170, 170));
            drawText(g, fitText(g, "file: " + (fileName.length() == 0 ? "(not saved)" : fileName), winW-215), winW-10, h+172, RIGHT);

            //tip for whatever the mouse is over
            String tipText = "";
            if (mouseY >= h) {
                for (Button b : ui) {
                    if (b.tip.length() > 0 && b.checkCollisions(mouseX, mouseY)) {
                        tipText = b.tip;
                        break;
                    }
                }
            }

            //tip, or the status (errors are red)
            if (tipText.length() > 0) {
                g.setColor(new Color(140, 190, 255));
                drawText(g, tipText, 10, h+barH-12, LEFT);
            } else {
                g.setColor(status.startsWith("error") ? new Color(255, 120, 120) : new Color(200, 200, 200));
                drawText(g, status, 10, h+barH-12, LEFT);
            }
        }

        /**
         * Runs every frame. Paints the block under the mouse while the mouse is held down (and saves each
         * change for undo), keeps the view moving while a move key is held, and takes Restart back to normal
         * if it was not clicked a second time.
         */
        void update() {
            if (!boardStarted) return; //nothing to update until a board is made

            previewBtn.fill = preview ? new Color(56, 193, 114) : buttonFill;

            //restart has to be clicked twice, if the second click does not come the button goes back
            if (restartArmed > 0 && System.currentTimeMillis() - restartArmed > 3000) {
                restartArmed = 0;
                if (status.startsWith("click Restart")) status = "";
            }
            restartBtn.label = (restartArmed > 0) ? "Sure?" : "Restart";
            restartBtn.fill = (restartArmed > 0) ? new Color(200, 60, 60) : buttonFill;

            //checks to see which block mouse is colliding with, and checks to see if mouse is within the main program's canvas, and then sets block mode
            //(the mouse in the board is -x+mouseX across and -y-h+mouseY down because of translate and because the start is the bottom left)
            if (painting) {
                for (int i = 0; i < blocks.size(); i++) {
                    Block b = blocks.get(i);

                    if (b.getM() != mode && b.checkCollisions(-x+mouseX, -y-h+mouseY) && (mouseX>0) && (mouseX<w) && (mouseY>0) && (mouseY<h)) {
                        currentChange.add(new Change(i, b.getM(), mode)); //remembering the change for undo
                        b.setMode(mode);
                    }
                }
            }

            //holding a move key, waits a bit and then keeps moving (restrict motion)
            if (moveL || moveR || moveU || moveD) {
                holdFrames++;
                if (holdFrames > 12 && holdFrames % 3 == 0) {
                    moveStep();
                }
            }
        }

        /**
         * A click in the bar goes to the buttons, a click on the canvas starts painting (unless preview is on).
         */
        void mousePressed() {
            if (mouseY >= h) { //clicked in the bar
                super.mousePressed();
                return;
            }

            for (Button b : ui) {
                b.unfocus();
            }

            if (!preview) {
                painting = true;
                currentChange = new ArrayList<Change>();
            }
        }

        /**
         * Ends painting. Everything painted since the mouse was pressed becomes one step on the undo stack.
         */
        void mouseReleased() {
            super.mouseReleased(); //lets textboxes stop selecting

            if (painting) {
                painting = false;

                if (!currentChange.isEmpty()) {
                    pushChange(currentChange);
                }
                currentChange = new ArrayList<Change>();
            }
            mapDirty = true;
        }

        /**
         * Keys: number = mode, e = export, p = preview, arrows or wasd = move, ctrl+z = undo, ctrl+y = redo.
         * If a textbox is being typed in it gets the key instead.
         */
        void keyPressed(char key, int keyCode, boolean ctrl) {
            if (uiKey(key, keyCode, ctrl)) return; //user is typing in a textbox

            boolean repeat = (keyCode == heldCode); //true if the key is just repeating because it is held
            heldCode = keyCode;

            //undo and redo
            if (ctrl) {
                if (keyCode == KeyEvent.VK_Z) undo();
                else if (keyCode == KeyEvent.VK_Y) redo();
                return;
            }

            //checks to see if the key pressed is a digit and not a character
            if (Character.isDigit(key)) {
                mode = Character.getNumericValue(key); // sets mode to numeric value of key pressed
            }

            //if e key is pressed, board exports
            if (key == 'e' && !repeat) {
                exportBoard();
            }

            //if p key is pressed, preview turns on or off
            if (key == 'p' && !repeat) {
                togglePreview();
            }

            //setting move booleans
            if (keyCode == KeyEvent.VK_LEFT || key == 'a') {
                setMove(false, true, false, false);
            } else if (keyCode == KeyEvent.VK_RIGHT || key == 'd') {
                setMove(true, false, false, false);
            } else if (keyCode == KeyEvent.VK_UP || key == 'w') {
                setMove(false, false, true, false);
            } else if (keyCode == KeyEvent.VK_DOWN || key == 's') {
                setMove(false, false, false, true);
            }
        }

        /**
         * Letting go of a key stops the movement.
         */
        void keyReleased(int keyCode) {
            heldCode = -1;
            setMove(false, false, false, false);
        }
    }



    /**
     * The first window. Make a new board (width, height, block width, block height), load one from a file,
     * browse the saved files, or open the tutorial. Opening it again from the bar lets you write over the
     * board that is open. It has its own message line, so messages meant for the board do not show up here.
     */
    static class Menu extends Screen {
        Textbox mW, mH, mBW, mBH, mFile;
        String message = ""; //error message for this window

        /**
         * Makes the boxes and buttons.
         */
        Menu() {
            super("menu", 420, 330, (displayWidth/2)-210, (displayHeight/2)-165);

            mW = new Textbox("Width", "600", 100, 62, 70, 22, null, false, "0123456789", 4);
            mH = new Textbox("Height", "600", 280, 62, 70, 22, null, false, "0123456789", 4);
            mBW = new Textbox("Block width", "20", 100, 92, 70, 22, null, false, "0123456789", 2);
            mBH = new Textbox("Block height", "20", 280, 92, 70, 22, null, false, "0123456789", 2);
            ui.add(mW);
            ui.add(mH);
            ui.add(mBW);
            ui.add(mBH);

            //new board button
            ui.add(new Button("New Board", 20, 150, 380, 28, () -> {
                String err = checkBoardValues(mW.getInt(), mH.getInt(), mBW.getInt(), mBH.getInt());

                if (err != null) {
                    message = err;
                    return;
                }

                newBoard(mW.getInt(), mH.getInt(), mBW.getInt(), mBH.getInt());
                hide();
            }
            ));

            //load board
            mFile = new Textbox("File", "", 60, 212, 230, 22, null, false, null, 24);
            ui.add(mFile);
            ui.add(new Button("Load Board", 300, 212, 100, 22, () -> {
                String err = loadBoard(mFile.text.trim());

                if (err != null) message = err; //if it worked the menu closes by itself
            }
            ));

            //files and tutorial
            ui.add(new Button("Browse Files", 20, 250, 185, 26, () -> fileList.show()));
            ui.add(new Button("Open Tutorial", 215, 250, 185, 26, () -> tutorial.show()));
        }

        /**
         * Builds the window. Closing the menu before a board exists closes the program.
         */
        void setup() {
            super.setup();
            frame.addWindowListener(new WindowAdapter() {
                                        public void windowClosing(WindowEvent e) {
                                            if (!boardStarted) System.exit(0);
                                        }
                                    }
            );
        }

        /**
         * Fills the boxes with the current board values when the menu is opened again.
         */
        void onShow() {
            message = "";

            if (boardStarted) {
                mW.text = "" + w;
                mH.text = "" + h;
                mBW.text = "" + bW;
                mBH.text = "" + bH;
                mFile.text = fileName;
            }
        }

        /**
         * Draws the title, the biggest sizes your screen allows, and the message.
         */
        void draw(Graphics2D g) {
            g.setColor(Color.WHITE);
            g.setFont(titleFont);
            drawText(g, "board builder", 20, 26, LEFT);

            g.setFont(uiFont);
            drawText(g, "new board (this writes over the board you have open)", 20, 50, LEFT);

            g.setColor(new Color(170, 170, 170));
            drawText(g, "biggest allowed on your screen: width " + maxBoardW() + ", height " + maxBoardH(), 20, 124, LEFT);
            drawText(g, "smallest is 400 for width and height, blocks are 10 to 50", 20, 138, LEFT);

            g.setColor(Color.WHITE);
            drawText(g, "load board (files are in the data folder)", 20, 198, LEFT);

            g.setColor(new Color(255, 120, 120));
            drawText(g, message, 20, 300, LEFT);
        }
    }



    /**
     * Shows each mode's color and number with a textbox to name it. Names are saved with the board.
     */
    static class Legend extends Screen {
        Textbox[] names = new Textbox[9]; //mode name boxes, click one and type to change the name

        /**
         * Makes a textbox for each mode that can be named (modes 1-9).
         */
        Legend() {
            super("legend", 300, 250, 3*(displayWidth/4)-150, (displayHeight/2)-125);

            for (int i=0; i<9; i++) {
                final int index = i;
                names[i] = new Textbox("", "", 120, 20+22*i, 160, 20, null, false, null, 14);

                //when a name is finished it becomes one step that can be undone
                names[i].onCommit = () -> {
                    ArrayList<Change> step = new ArrayList<Change>();
                    step.add(new Change(index, names[index].textAtFocus, names[index].text));
                    pushChange(step);
                };
                ui.add(names[i]);
            }
        }

        /**
         * Draws the color squares and mode numbers (mode 0 is last).
         */
        void draw(Graphics2D g) {
            g.setFont(uiFont);

            //drawing mode blocks
            for (int i=0; i<10; i++) {
                int m = (i != 9) ? i+1 : 0; //mode 0 is last
                int rowY = 20+22*i;

                g.setColor(colors[m]);
                g.fillRect(20, rowY, 20, 20);
                g.setColor(new Color(150, 150, 150));
                g.drawRect(20, rowY, 20, 20);

                //text to show what mode number the color block is
                g.setColor(Color.WHITE);
                drawText(g, "Mode: " + m, 50, rowY+10, LEFT);
            }
        }

        /**
         * @param m index 0-8 (mode 1-9)
         * @return the name that is typed in for that mode
         */
        String getModeName(int m) {
            return names[m].text;
        }

        /**
         * Sets a name (used when a board is loaded).
         *
         * @param m index 0-8 (mode 1-9)
         * @param n the name, null counts as empty
         */
        void setModeName(int m, String n) {
            names[m].text = (n == null) ? "" : n;
        }

        /**
         * Empties every name.
         */
        void clearNames() {
            for (int i=0; i<9; i++) {
                names[i].text = "";
            }
        }
    }



    /**
     * A small picture of the whole board. The red rectangle is the part you are looking at, and
     * clicking a spot moves the board there. Uses the same gridlines as the board (preview hides them),
     * and can show the legend in the corner. Data comes from BoardBuilder.loadMapData() and the map only
     * recalculates when it gets new data.
     */
    static class Map extends Screen {
        //attributes
        int bXmax=0, bYmax=0, mapW=0, mapH=0; //block furthest x and y vals, map width, map height
        int blockW=1, blockH=1; //actual block width and height
        int cX, cY, cW, cH; //current view x, y, width, and height (in pixels)

        //array lists for block mode x and y vals
        ArrayList<Integer> xVals = new ArrayList<Integer>();
        ArrayList<Integer> yVals = new ArrayList<Integer>();
        ArrayList<Integer> modes = new ArrayList<Integer>();

        //array lists for mapped x and y vals
        ArrayList<Float> mXvals = new ArrayList<Float>();
        ArrayList<Float> mYvals = new ArrayList<Float>();

        int maxW; //max width
        int maxH; //max height
        float bW=0; //mapped block width
        float bH=0; //mapped block height

        int ind=0; //index
        boolean redraw = false; //true when map has new data

        /**
         * Works out the biggest the map window is allowed to be from the screen size.
         */
        Map() {
            super("map", 300, 300, (displayWidth/4)-150, (displayHeight/2)-150);

            //setting max width and height based on users display size
            maxW = (int)(displayWidth/2.4);
            maxH = (int)(displayHeight/1.5);
        }

        /**
         * Recalculates when the map is opened.
         */
        void onShow() {
            redraw = true;
        }

        /**
         * Runs when there is new data. Works out the size of the window, then where every block goes on
         * the map (each block x and y is squeezed from the size of the board to the size of the map).
         */
        void update() {
            if (!redraw) return;
            redraw = false;

            windowSize(); //getting vals for window size
            if (winW != mapW || winH != mapH) resize(mapW, mapH); //resizing window

            //clearing map x and y val ArrayLists
            mXvals.clear();
            mYvals.clear();

            float totalW = bXmax + blockW; //width of all the blocks

            //mapping all block x vals to map
            for (int i=0; i<xVals.size(); i++) {
                mXvals.add(mapRange(xVals.get(i), 0, totalW, 0, mapW));
            }

            //mapping all block y vals to map
            for (int i=0; i<yVals.size(); i++) {
                mYvals.add(mapRange(yVals.get(i), 0, bYmax, 0, mapH));
            }

            //getting mapped block width and heights
            bW = blockW * mapW / totalW;
            bH = blockH * mapH / (float)bYmax;
        }

        /**
         * Draws every block (edges are rounded to whole pixels so they fit together with no gaps), the
         * gridlines, the red rectangle, and the legend if that switch is on.
         */
        void draw(Graphics2D g) {
            int n = Math.min(mXvals.size(), Math.min(mYvals.size(), modes.size()));

            //the map has the same grid as the board, preview hides it (and it is skipped if the blocks are too small to see it)
            boolean grid = gridVisible && !preview && bW >= 4 && bH >= 4;
            g.setStroke(new BasicStroke(1));

            //drawing rects, edges are rounded to whole pixels so the blocks fit together with no gaps
            for (int i=0; i<n; i++) {
                int px0 = Math.round(mXvals.get(i));
                int px1 = Math.round(mXvals.get(i) + bW);
                int py0 = Math.round(mYvals.get(i));
                int py1 = Math.round(mYvals.get(i) + bH);

                g.setColor(colors[modes.get(i)]);
                g.fillRect(px0, py0, Math.max(1, px1-px0), Math.max(1, py1-py0));

                //drawing gridline
                if (grid) {
                    g.setColor(gridColor);
                    g.drawRect(px0, py0, Math.max(1, px1-px0), Math.max(1, py1-py0));
                }
            }

            //transparent red rectangle to show user where they are on map
            float totalW = bXmax + blockW;
            if (totalW > 0 && bYmax > 0) {
                float rX = mapRange(cX, 0, totalW, 0, mapW);
                float rY = mapRange(cY, 0, bYmax, 0, mapH);
                float rW = cW * mapW / totalW;
                float rH = cH * mapH / (float)bYmax;

                g.setColor(new Color(255, 0, 0, 35));
                g.fillRect(Math.round(rX), Math.round(rY), Math.round(rW), Math.round(rH));
            }

            //legend on the map
            if (legendOnMap) {
                g.setColor(new Color(0, 0, 0, 170));
                g.fillRect(6, 6, 130, 9*16+8);

                g.setFont(uiFont);
                for (int i=0; i<9; i++) {
                    String name = legend.getModeName(i);

                    g.setColor(colors[i+1]);
                    g.fillRect(12, 12+i*16, 10, 10);
                    g.setColor(Color.WHITE);
                    drawText(g, name.length() == 0 ? "Mode " + (i+1) : name, 28, 17+i*16, LEFT);
                }
            }
        }

        /**
         * Finds which block was clicked and tells the board to move there (the board does the moving).
         */
        void mousePressed() {
            //getting index of the block that was clicked
            for (int i=0; i<mXvals.size(); i++) {
                if (mXvals.get(i)+bW >= mouseX && mXvals.get(i) <= mouseX && mYvals.get(i)+bH >= mouseY && mYvals.get(i) <= mouseY) {
                    ind=i;
                }
            }

            //setting map clicked to true to move user to spot that was clicked on
            mapClicked=true;
        }

        /**
         * Works out the map window size: as big as the board but never bigger than the max size.
         */
        void windowSize() {
            //if the max x val is less than the max width for map, map width is set to max x val
            if (bXmax+blockW < maxW) mapW=bXmax+blockW;
                //if the max x val is bigger, than map width is set to max width
            else mapW=maxW;

            //if the max y val is less than the max height for map, map height is set to max y val
            if (bYmax < maxH) mapH=bYmax;
                //if the max y val is bigger, than map height is set to max height
            else mapH=maxH;
        }

        /**
         * @return the index of the block that was clicked on the map
         */
        int getInd() {
            return ind;
        }

        /**
         * Gives the map everything it needs to draw (see BoardBuilder.loadMapData).
         *
         * @param bXmax  biggest block x
         * @param bYmax  height of the whole board
         * @param cX     x of the view in the board (pixels)
         * @param cY     y of the view in the board (pixels)
         * @param cW     width of the view
         * @param cH     height of the view
         * @param blockW block width
         * @param blockH block height
         * @param xVals  x of every block
         * @param yVals  y of every block (shifted so the top row is 0)
         * @param modes  mode of every block
         */
        void sendMapData(int bXmax, int bYmax, int cX, int cY, int cW, int cH, int blockW, int blockH, ArrayList<Integer> xVals, ArrayList<Integer> yVals, ArrayList<Integer> modes) {
            this.bXmax = bXmax;
            this.bYmax = bYmax;
            this.cX = cX;
            this.cY = cY;
            this.cW = cW;
            this.cH = cH;
            this.blockW = blockW;
            this.blockH = blockH;
            this.xVals = xVals;
            this.yVals = yVals;
            this.modes = modes;

            redraw = true; //tells the map to redraw
        }
    }



    /**
     * Shows facts about the board: canvas size, block size, number of blocks, and how many blocks there
     * are of each mode.
     */
    static class Stats extends Screen {
        int[] counts = new int[10]; //number of blocks of each mode

        /**
         * Makes the window.
         */
        Stats() {
            super("stats", 300, 360, displayWidth-340, 60);
        }

        /**
         * Counts the modes a few times a second (only while the window is open).
         */
        void update() {
            if (!isVisible() || frameCount % 15 != 0) return;

            counts = new int[10];
            for (Block b : blocks) {
                counts[b.getM()]++;
            }
        }

        /**
         * Draws the numbers.
         */
        void draw(Graphics2D g) {
            g.setFont(uiFont);
            g.setColor(Color.WHITE);

            drawText(g, "canvas size: " + w + " x " + h, 20, 25, LEFT);
            drawText(g, "block size: " + bW + " x " + bH, 20, 45, LEFT);
            drawText(g, "blocks per screen: " + (w/bW) + " x " + (h/bH), 20, 65, LEFT);
            drawText(g, "number of blocks: " + blocks.size(), 20, 85, LEFT);
            drawText(g, "board size so far: " + (numCols*bW) + " x " + (numRows*bH), 20, 105, LEFT);
            drawText(g, "blocks of each mode:", 20, 135, LEFT);

            for (int i=0; i<10; i++) {
                int m = (i != 9) ? i+1 : 0;
                int rowY = 150+i*18;
                String name = (m > 0) ? legend.getModeName(m-1) : "";

                g.setColor(colors[m]);
                g.fillRect(20, rowY, 12, 12);
                g.setColor(Color.WHITE);
                drawText(g, "Mode " + m + (name.length() > 0 ? " (" + name + ")" : "") + ": " + counts[m], 40, rowY+6, LEFT);
            }
        }
    }



    /**
     * The Help window, lists the controls. Open and close it with the Help button.
     */
    static class Instructions extends Screen {
        String[] lines = {
                "click and drag on the board to paint blocks",
                "0-9: pick a mode (color), shown in the bottom right",
                "arrow keys or wasd: move one block (hold to keep moving)",
                "click on the map to jump to that spot",
                "type in the legend boxes to name each mode",
                "e: export    p: preview    ctrl+z: undo    ctrl+y: redo",
                "",
                "the bar at the bottom of the board:",
                "  Width, Height: canvas size, then Apply. Block sizes change live",
                "  (hover over anything in the bar to see what it does)",
                "  Export: saves a new file with the name in the File box",
                "  Update: writes over the file you have open",
                "  Load: opens the file in the File box",
                "  Undo / Redo: painting and mode names (not resizing)",
                "  Preview: hides gridlines on the board and map (painting is off)",
                "  Grid lines switch: shows or hides the gridlines on the board and map",
                "  Export switch: save x and y at block centers or top left corners",
                "  Legend on map switch: shows the mode names on the map",
                "  Menu: new board, load, or browse files (writes over this board)",
                "  Legend / Map / Stats / Help / Tutorial: open and close windows",
                "  Restart: clears the whole board (click it twice)",
                "  in a textbox: arrows or clicking move the cursor, drag or shift+arrows"
        };

        /**
         * Makes the window.
         */
        Instructions() {
            super("instructions", 440, 510, 60, 60);
        }

        /**
         * Draws the lines of text.
         */
        void draw(Graphics2D g) {
            g.setColor(Color.WHITE);
            g.setFont(titleFont);
            drawText(g, "instructions", 20, 26, LEFT);

            g.setFont(uiFont);
            for (int i=0; i<lines.length; i++) {
                drawText(g, lines[i], 20, 55+i*19, LEFT);
            }
        }
    }



    /**
     * A step by step walk through of the program. Back and Next change the step (steps holds the text,
     * the first line of each step is its title).
     */
    static class Tutorial extends Screen {
        int step = 0; //current step
        Button next;
        String[][] steps = {
                {"welcome!", "this tutorial goes through the board builder step by step.", "click Next to continue."},
                {"1. making a board", "open the Menu to pick a width, height, and block size.", "width and height are 400 up to your screen size and the", "block sizes are 10 to 50 (extra blocks go off the edge).", "then click New Board."},
                {"2. painting", "press a number key (0-9) to pick a mode (color).", "then click and drag on the board to paint blocks.", "the square in the bottom right shows your current mode."},
                {"3. moving around", "arrow keys or wasd move one block at a time.", "hold a key to keep going, the board grows as you go.", "click the Map button to open the map, then click on", "the map to jump to a spot."},
                {"4. the legend", "click the Legend button to open the legend window.", "type in its boxes to name each mode.", "turn on the Legend on map switch at the bottom to see it on the map."},
                {"5. undo, redo, and preview", "ctrl+z undoes and ctrl+y redoes (or use the buttons).", "press p or the Preview button to hide the gridlines", "on the board and the map."},
                {"6. changing the size", "type new values in the Width, Height, Block width, and", "Block height boxes at the bottom of the board.", "block sizes change as you type, Width and Height need Apply."},
                {"7. saving and loading", "type a file name and click Export to make a new file.", "Update writes over the file you have open.", "In the Menu, Browse Files shows every saved file.", "the Export switch picks if x and y are saved at the block center or corner."},
                {"that's it!", "open Help any time to see all the controls."}
        };

        /**
         * Makes the Back and Next buttons.
         */
        Tutorial() {
            super("tutorial", 460, 300, 100, 100);

            ui.add(new Button("Back", 20, 255, 80, 26, () -> {
                if (step > 0) step--;
            }
            ));

            next = new Button("Next", 360, 255, 80, 26, () -> {
                if (step < steps.length-1) step++;
                else {
                    step = 0;
                    hide();
                }
            }
            );
            ui.add(next);
        }

        /**
         * Draws the current step.
         */
        void draw(Graphics2D g) {
            next.label = (step == steps.length-1) ? "Close" : "Next";

            g.setColor(new Color(170, 170, 170));
            g.setFont(uiFont);
            drawText(g, "step " + (step+1) + " of " + steps.length, 20, 20, LEFT);

            g.setColor(Color.WHITE);
            g.setFont(titleFont);
            drawText(g, steps[step][0], 20, 52, LEFT);

            g.setFont(uiFont);
            for (int i=1; i<steps[step].length; i++) {
                drawText(g, steps[step][i], 20, 70+i*22, LEFT);
            }
        }
    }



    /**
     * Lists the .json files in the data folder, 10 to a page. Clicking one puts its name in the file boxes,
     * Load opens it. Green is the file that was clicked and blue is the file that is open.
     */
    static class FileList extends Screen {
        ArrayList<String> names = new ArrayList<String>(); //file names in the data folder (no .json)
        ArrayList<Button> rows = new ArrayList<Button>(); //one button for each file on this page
        String selected = ""; //file that was clicked
        String message = ""; //error message for this window
        int page = 0, perPage = 10;

        /**
         * Makes the window and looks for files.
         */
        FileList() {
            super("files", 300, 370, 80, 120);
            refresh();
        }

        /**
         * Looks in the data folder again when the window is opened (files may have been added).
         */
        void onShow() {
            message = "";
            refresh();
        }

        /**
         * Looks in the data folder for .json files and sorts them.
         */
        void refresh() {
            names.clear();

            File[] list = new File("data").listFiles();
            if (list != null) {
                for (File f : list) {
                    String n = f.getName();
                    if (f.isFile() && n.endsWith(".json")) {
                        names.add(n.substring(0, n.length()-5));
                    }
                }
            }
            names.sort(String.CASE_INSENSITIVE_ORDER);

            if (page*perPage >= names.size()) page = 0;
            buildList();
        }

        /**
         * Makes a button for each file on the current page and the Prev, Next, Refresh, and Load buttons.
         */
        void buildList() {
            ui.clear();
            rows.clear();

            for (int i=0; i<perPage; i++) {
                int index = page*perPage + i;
                if (index >= names.size()) break;

                final String name = names.get(index);
                Button b = new Button(name, 20, 45+i*26, 260, 22, () -> {
                    selected = name;
                    setFileBoxes(name); //puts the name in the file boxes
                }
                );
                rows.add(b);
                ui.add(b);
            }

            ui.add(new Button("Prev", 20, 322, 50, 24, () -> {
                if (page > 0) page--;
                buildList();
            }
            ));
            ui.add(new Button("Next", 75, 322, 50, 24, () -> {
                if ((page+1)*perPage < names.size()) page++;
                buildList();
            }
            ));
            ui.add(new Button("Refresh", 130, 322, 70, 24, () -> refresh()));
            ui.add(new Button("Load", 210, 322, 70, 24, () -> {
                if (selected.length() == 0) {
                    message = "error: click a file first";
                } else {
                    String err = loadBoard(selected); //if it worked this window closes by itself
                    message = (err == null) ? "" : err;
                }
            }
            ));
        }

        /**
         * Draws the title, page number, and status, and colors the file buttons.
         */
        void draw(Graphics2D g) {
            g.setColor(Color.WHITE);
            g.setFont(titleFont);
            drawText(g, "files", 20, 24, LEFT);

            g.setFont(uiFont);
            g.setColor(new Color(170, 170, 170));
            int pages = Math.max(1, (names.size()+perPage-1)/perPage);
            drawText(g, names.size() + " files, page " + (page+1) + " of " + pages, 80, 24, LEFT);

            if (names.size() == 0) {
                g.setColor(Color.WHITE);
                drawText(g, "no files yet, use Export on the board", 20, 60, LEFT);
            }

            //green = clicked, blue = the file you have open
            for (Button b : rows) {
                if (b.label.equals(selected)) b.fill = new Color(56, 193, 114);
                else if (b.label.equals(fileName)) b.fill = new Color(52, 144, 220);
                else b.fill = buttonFill;
            }

            g.setColor(new Color(255, 120, 120));
            drawText(g, message, 20, 358, LEFT);
        }
    }



    /**
     * A small json reader (Java does not have one built in). read() turns the text into
     * ArrayLists (for [ ]), HashMaps (for { }), Strings, Integers, and Doubles.
     * It only needs to read the files this program writes.
     */
    static class JSONReader {
        String s; //the json text
        int pos = 0; //where we are in the text

        /**
         * @param s the json text
         */
        JSONReader(String s) {
            this.s = s;
        }

        /**
         * Moves past spaces, tabs, and new lines.
         */
        void skipSpace() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        /**
         * Reads whatever value comes next (array, object, string, null, or number).
         *
         * @return the value
         */
        Object read() {
            skipSpace();
            char c = s.charAt(pos);

            if (c == '[') { //array
                pos++;
                ArrayList<Object> list = new ArrayList<Object>();
                skipSpace();

                if (s.charAt(pos) == ']') {
                    pos++;
                    return list;
                }

                while (true) {
                    list.add(read());
                    skipSpace();
                    char n = s.charAt(pos++); //is either , or ]
                    if (n == ']') break;
                }
                return list;
            } else if (c == '{') { //object
                pos++;
                HashMap<String, Object> obj = new HashMap<String, Object>();
                skipSpace();

                if (s.charAt(pos) == '}') {
                    pos++;
                    return obj;
                }

                while (true) {
                    skipSpace();
                    String k = (String) read(); //key
                    skipSpace();
                    pos++; //skip the :
                    obj.put(k, read()); //value
                    skipSpace();
                    char n = s.charAt(pos++); //is either , or }
                    if (n == '}') break;
                }
                return obj;
            } else if (c == '"') { //string
                pos++;
                StringBuilder sb = new StringBuilder();

                while (s.charAt(pos) != '"') {
                    char ch = s.charAt(pos++);

                    if (ch == '\\') { //special character
                        char e = s.charAt(pos++);
                        if (e == 'n') sb.append('\n');
                        else if (e == 't') sb.append('\t');
                        else if (e == 'u') {
                            sb.append((char) Integer.parseInt(s.substring(pos, pos+4), 16));
                            pos += 4;
                        } else sb.append(e);
                    } else {
                        sb.append(ch);
                    }
                }
                pos++; //skip closing quote
                return sb.toString();
            } else if (c == 'n') { //null
                pos += 4;
                return null;
            } else { //number
                int start = pos;
                while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) pos++;
                String num = s.substring(start, pos);

                if (num.contains(".") || num.contains("e") || num.contains("E")) return Double.parseDouble(num);
                return Integer.parseInt(num);
            }
        }
    }
}