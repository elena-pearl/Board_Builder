/**
 * BoardBuilder is a small Java desktop app for creating grid-based boards and levels.
 * Choose a color mode, paint blocks onto the canvas, and save your work so you can
 * come back to it later. The canvas can grow as you move around, so you're not limited
 * to the area you can see at first.
 *
 * The app uses Java Swing and AWT for its windows and drawing. A timer refreshes the
 * open windows, while the board, map, legend, and other tools share the same board data.
 *
 * A few details about how the project is put together:
 * - BoardBuilder manages shared settings, board data, saving/loading, and undo/redo.
 * - Screen is the base class for the app's windows, including Board, Menu, Map, Legend,
 *   Stats, Instructions, Tutorial, and FileList.
 * - Button is the base class for the controls. Switch and Textbox build on it.
 * - Block represents one grid cell, Change records edits for undo/redo, and JSONReader
 *   reads saved board files.
 *
 * Controls:
 * - Press 0-9 to choose a mode, then click or drag to paint.
 * - Use the arrow keys or WASD to move around the board.
 * - Press E to export, P to toggle preview, Ctrl+Z to undo, and Ctrl+Y to redo.
 * - Use the bar at the bottom of the board for sizing, files, and extra windows.
 *
 * Saved boards live in data/<name>.json. The file stores the canvas and block sizes,
 * mode names, and painted blocks. Older board files are also supported.
 */
