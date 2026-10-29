# TejpPhone

An Android app for designing tape pictures on a fence. You draw strips of tape between the
edges of a fence grid, pick their width and colour, and can trace over a reference picture or let
the app generate strips from it. Drawings are saved as `.tjp` files, the same format as the
desktop version, and can be exported as JPG.

Requires Android 8.1 (API 27) or newer. There is also a build for Meta Quest headsets with a
[mixed reality](#mixed-reality-meta-quest) mode.

## Building

There are two builds (product flavors):

- `phone` – phones and tablets. This is the default.
- `quest` – Meta Quest. The same app plus mixed reality, built with the Meta Spatial SDK.
  Needs Horizon OS v69 or newer.

In Android Studio, pick the build in **Build Variants** (`phoneDebug` or `questDebug`) and run the
`app` configuration. From the command line:

```
./gradlew assemblePhoneDebug
./gradlew assembleQuestDebug
```

The APKs end up in `app/build/outputs/apk/phone/debug/` and `app/build/outputs/apk/quest/debug/`.

## The screen

- **Top bar** – the file name (with "Unsaved changes" when needed), a Save button, and a menu with
  the other file actions.
- **Tool options** – a row of buttons for the current tool, with a one-line hint below it.
- **Fence** – the drawing area.
- **Bottom bar** – the four tools (Draw, Select, Move, Picture) and Undo / Redo.

### Moving around

- Pinch with two fingers to zoom, drag with two fingers to pan.
- Drag with one finger outside the fence to pan.
- Double tap to fit the whole fence on screen (also in the menu as **Fit to screen**).
- With a mouse, the scroll wheel zooms.
- While your finger is on the fence a magnifier shows what is under it.

## Tools

### Draw

Put a finger on an edge of the grid and drag to another edge to draw a strip. The phone ticks
each time the strip snaps to a new edge. A tap without dragging adds nothing.

To change an existing strip, hold your finger on one of its ends until it buzzes, then drag to
move that end.

Options:
- **Width** and **Colour** open the style picker. Tap a width or a colour to use it. Tap **+** to
  mix your own colour; hold a mixed colour to remove it.
- **Invert colours** makes auto strips treat the light parts of the picture as dark and the
  dark parts as light (see [Auto strips](#auto-strips)).

### Select

- Tap a strip to add it to or remove it from the selection.
- Tap empty space to clear the selection.
- Drag to paint strips into the selection.
- Turn on **Box select** to drag a box instead; the box replaces the current selection.
- **Select all**, **Select none**, **Select last** (the most recently drawn strip),
  **Same width** and **Same colour** (as the current drawing width/colour).

When strips are selected, a bar appears at the bottom of the fence with:
colour and width, copy, mirror, to front, to back, delete, and clear selection.
Changing the width or colour in the style picker also applies it to the selected strips.

### Move

Drag on the fence to move the selected strips. **Select all** and **Select none** are available
here too, so you can move everything at once.

### Picture

Use a photo or image as a guide to trace over.

- **Load picture** – pick an image. It is placed on the fence and shown under the grid.
- Drag the picture to move it, drag a corner to resize it.
- **Keep aspect** – keep the picture's proportions while resizing.
- **Show** – show or hide the picture.
- **Opacity** – how strongly the picture shows through.
- **Remove** – take the picture off the fence.

#### Auto strips

Turn on **Auto strips** to let the app fill the fence with strips based on the picture.
Choose a direction (**Horizontal**, **Diagonal** or **Vertical**) and adjust:

- **Threshold** – controls the thick strips.
- **Contrast** – controls the thin strips.

The strips update as you move a slider. Use the **–** and **+** buttons next to a slider for
1% steps. A series of slider changes counts as one step for Undo.
Auto strips work on diagonal and vertical fences, and need a loaded picture.

## Menu

- **Save** – save to the current file, or ask where to save if it is new.
- **Save as…** – save to a new `.tjp` file. If you leave `.tjp` off the name, it is added.
- **Open…** – open a `.tjp` file.
- **Export JPG…** – save the drawing as an image.
- **Layers** – view the drawing layer by layer. Swipe sideways or use **Previous** / **Next**;
  **Show layers below** draws the layers underneath for context.
- **Fit to screen** – zoom to show the whole fence.
- **Mixed reality** / **Leave mixed reality** – Quest build only, see below.
- **New diagonal fence** / **New vertical fence** – start a new drawing.

If there are unsaved changes, the app asks before opening another file or starting a new fence.

## Mixed reality (Meta Quest)

In the Quest build the app starts as a normal window. **Mixed reality** in the menu switches to
passthrough: you see your room, and the drawing screen floats in it on a panel. The grey area
around the fence is see-through, so you can hold the grid and strips up against a real fence.

- Point at the panel and hold the grip button to move and turn it.
- Drag a corner of the panel to resize it. A bigger panel gives the screen more room; buttons and
  text keep their size.
- The panel starts 1.2 m straight ahead, at the size the normal window had (a 1024 x 640 dp
  window becomes 1.28 x 0.8 m). It cannot start where the normal window was, because the app is
  not told where that is.
- The thumbstick does not move you around, so the panel stays put in the room.
- Zoom with the **+** and **–** buttons at the right of the fence; there is no two-finger pinch
  with a controller. Double tap or **Fit to screen** still fits the whole fence.
- **Leave mixed reality** in the menu goes back to the normal window, on the same drawing.

## Autosave

The drawing (and its picture) is saved automatically when you leave the app, and comes back the
next time you open it. This does not replace saving to a `.tjp` file.

## Tests

```
./gradlew test
```
