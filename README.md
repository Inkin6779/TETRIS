# Functional Tetris

The original NES Tetris written in a style that adheres 
to haskell's paradigms as much as possible.

Featuring clear separation of data and program logic, 
all-out immutability, some Monads and a platform independent
thread-safe core logic. All bundled with a simple ScalaJS GUI
for playing in the browser. 

I wrote this as an exercise to get more used to functional 
programming with the cats framework in particular and
with the whole style in general. 

### Usage

Just download the html and compiled JS files from one of the 
release folders and open with your browser. There are no ads
and I do not collect any data. In fact, you can just play offline.
The highscore is stored using cookies. 

*Local file cookies do not work in Google Chrome.* 

[You can play the newest version from this repo right here on GitHub.](https://xdracam.github.io/functional-tetris/)

### Building yourself

Clone the project and treat as a usual ScalaJS project:

    cd path/to/FunctionalTetris
    sbt "~fastOptJS" # recompiles on every source change
    sbt fullOptJS    # for a compressed release version
    
### Can I include this in my webpage?

Sure. Just include one of the JS files found inside the 
release folders inside of a `<script>` tag. Then you 
can use the JS 'API':

    FTetris.startGame(
        canv: html.Canvas,
        onpointchange: Int => Void,
        onlevelchange: Int => Void,
        onlineclear: Int => Void,
        ongameend: () => Void,
        touchRootNode: [OPT] dom.Node,
        onpausestart: [OPT] () => Void,
        onpauseend: [OPT] () => Void
    ); 
    
Where `canv` is a reference to the canvas you want the game 
to render in, e.g. `document.getElementByID('myCanvas')`.
The game fits itself into the canvas dimensions, but it 
assumes that the equation `height = 2 * width` holds, for
aesthetic reasons.

`touchRootNode` enables the user to specify the dom node 
which receives all touch event handlers. If not specified,
the canvas is used. This should encompass the whole area
which allows touch input, but not the <body> tag itself.

The others are callbacks that either take an `int` - the
updated value - or no arguments and they all return nothing.
The pause callbacks are optional since the canvas has
a built-in pause effect.


### License

Use at your own risk. Tetris is a licensed product owned
by the [Tetris Company](https://tetris.com/).

This project is completely non-commercial and only intended
for educational purposes.

If you plan on using this code somewhere, just include 
a reference to this project or my GitHub account.
   
