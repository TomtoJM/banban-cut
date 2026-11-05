# Check videos from Atomic ToDoList

Atomic ToDoList saves a short video each time a todo is ticked. banban-cut picks them up by
itself and puts them in the bottom spots. Both apps depend on these rules; change one, change both.

| What | Rule |
| --- | --- |
| Folder | MediaStore `RELATIVE_PATH` = `DCIM/Atomic ToDoList/` (exactly, with the trailing slash) |
| File | MP4, 1080×675 (8:5, exactly one banban-cut spot), about 2.4 s, the tick at 0.5 s |
| Order | `DATE_ADDED` ascending, then `_ID` = the order the todos were ticked |
| Name | The todo's name, e.g. `Research 5 companies`, written **inside the MP4** as its title (the `©nam` tag, what `ffmpeg -metadata title=` writes). Android's scan copies it into MediaStore `TITLE`. Fallback: file name `<todo>__<yyyyMMdd-HHmmss>.mp4`; banban-cut reads the part before `__` |

Tested on Android 15 (API 35): a `TITLE` put in the `ContentValues` on insert, or updated after
`IS_PENDING` is cleared, is replaced by the scan with the file name. Only a title inside the MP4,
or a name in the file name, reaches banban-cut.

## What banban-cut does with them

1. On open (and on coming back to the app) it looks for check videos it hasn't used yet and shows
   "체크 영상 n개 가져올까요?" with one button. Needs READ_MEDIA_VIDEO/IMAGES, asked once; without it
   the app works through the system photo picker and no check videos appear.
2. The button puts one clip in each bottom spot, in order: first slides with an empty bottom, then
   new slides (up to 20). Centred, zoom 1 (fills the spot), no words. The todo's name goes on the
   same slide's top spot (the real thing being done) with a little darkening, unless it has words.
3. Saving writes the slides to `DCIM/반반컷/`, then (switch on by default) sends every original the
   carousel used that is in the phone's library to the trash with one `MediaStore.createTrashRequest`,
   so there is one system confirmation. Trashed items stay 30 days and can be restored.
4. Check videos that went into a saved carousel are remembered by `DISPLAY_NAME|SIZE` (app prefs
   `checks/used`), so a restored one is not offered again. Keep file names unique per video (the time stamp does this).

Tested on an emulator by `app/src/androidTest/.../CheckClipsTest.kt`.
