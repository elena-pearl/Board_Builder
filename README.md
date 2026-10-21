BoardBuilder:
BoardBuilder is a Java desktop application for creating and editing grid-based boards and levels. It allows you to change individual cells, navigate around the board, and save your work as a JSON file.
I created this project to make it easier to build tile-based layouts or platformer game levels without having to manually write all of the board data.

Features:
- Block editing: Choose between values 0-9 to edit blocks on the board.
- Board navigation: Move around using the arrow keys or W, A, S, and D.
- Expandable workspace: The board can expand to the right and upward as you work.
- Save and load: Export boards as JSON files and load them again later.
- Undo and redo: Undo previous edits or redo changes.
- Board preview: Preview your board while working on it.
- Additional tools: Includes a map, legend, statistics, instructions, tutorial, and file list.

Requirements:
- Java Development Kit (JDK) 8 or newer
- No external libraries required

How to Run:
1. Clone or download this repository.
2. Open a terminal in the folder containing BoardBuilder.java.
3. Compile the program:
        javac BoardBuilder.java
4. Run the application:
         java BoardBuilder
Make sure Java is installed and that java and javac are available in your terminal.

Controls:
  Key              Action
  0–9              Select a cell value or editing mode
  Arrow keys       Navigate around the board
  W, A, S, D       Navigate around the board
  E                Export the board
  P                Preview the board
  Ctrl + Z         Undo
  Ctrl + Y         Redo

Getting Started:
1. Run the application.
2. Select a block value by pressing a number from 0 to 9.
3. Use the editing controls to make changes to the board.
4. Navigate around the workspace as needed.
5. Export your board to save your progress.
6. Load a saved JSON file whenever you want to continue working on a board.
The meaning of each block value depends on how the board will be used in your project.

Project Structure:
BoardBuilder/
|-- BoardBuilder.java
|-- README.md
|-- .gitignore
|-- data/
|   |__ .gitkeep
|__ screenshots/
    |__ .gitkeep

- BoardBuilder.java — Main application source code.
- data/ — Stores saved boards and example JSON files.
- screenshots/ — Stores screenshots of the application.

Screenshots:
Screenshots will be added here to show the application and its features.

Future Improvements:
Possible improvements include adding more editing options, improving board customization, and making the interface easier to use.

License:
No license has been selected for this project yet.
