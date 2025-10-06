User [SerVB](https://github.com/SerVB) made a script to care about moving files into the Syncthing folder until N movie episodes are present.

See [syncthing#1279](https://github.com/syncthing/syncthing-android/issues/1279) for more details on the idea.

Thanks for your contribution!

# sequentialMover.py
Source: [https://gist.github.com/SerVB/26fd23d9b4e0b8aa57a6169ab1508812](https://gist.github.com/SerVB/26fd23d9b4e0b8aa57a6169ab1508812)

Copy of the script from the source linked above by 2019-02-24_16:19

```python
# "Sequential Mover":
# The script for moving a few episodes from the source dir to the syncthing "dist" dir.
#
# This script uses a json config file. Here is the example:
# {
#     "source": "D:/series/",
#     "dist": "D:/_syncthing/_few_episodes/",
#     "count": 2,
#     "exceptions": [
#         "Chaos - A mathematical adventure",
#         "Miraculous Ladybug Webisodes"
#     ]
# }
#
# The script assumes that subdirs of the "source" dir are different TV-series.
# Then it treats all the files in "TV-series" dirs as episodes.
# Example:
# D\
#  +-series\
#          +-series1\
#          |        +-series1-e1.mp4
#          |        +-series1-e2.mp4
#          +-series2\
#                   +-season1\
#                   |        +-series2-s1e1.mp4
#                   +-season2\
#                            +-series2-s2e1.mp4
#                            +-series2-s2e2.mp4
#
# The script searches for the same series in the "dist" dist dir and determines how many episodes to move.
# Assume "count" = 2. After script run, the "dist" dir will look like this:
# D\
#  +-_syncthing\
#              +-_few_episodes\
#                             +-series1\
#                             |        +-series1-e1.mp4
#                             |        +-series1-e2.mp4
#                             +-series2\
#                                      +-series2-s1e1.mp4
#                                      +-series2-s2e1.mp4
#
# If you rerun the script again, it will do nothing, because there are already two episodes moved.
# If you remove episodes from the "dist" dir, the script will again move episodes filling up to "count".
#
# Also, the script removes empty dirs from both "source" and "dist" dirs. So the "source" dir will look like this:
# D\
#  +-series\
#          +-series2\
#                   +-season2\
#                            +-series2-s2e2.mp4
#
# Here are some tips I use to run the script every hour quietly:
# https://stackoverflow.com/a/2725908/6639500
# https://www.howtogeek.com/tips/how-to-run-a-scheduled-task-without-a-command-window-appearing/
#
# The pre-history of the script is here:
# https://github.com/syncthing/syncthing-android/issues/1279

import json
import os

params = json.load(open("sequentialMoverParams.json", "r"))

SOURCE_DIR = str(params["source"])
DIST_DIR = str(params["dist"])
COUNT = int(params["count"])
EXCEPTIONS = set(map(str, params["exceptions"]))

del params

seriesDirs = []
for (dirPath, dirNames, fileNames) in os.walk(SOURCE_DIR):
    seriesDirs.extend(filter(lambda dirName: dirName not in EXCEPTIONS, map(str, dirNames)))
    break

presentedEpisodes = dict()

for seriesDir in seriesDirs:
    distSeriesDir = os.path.join(DIST_DIR, seriesDir)

    if os.path.isdir(distSeriesDir):
        for (dirPath, dirNames, fileNames) in os.walk(distSeriesDir):
            presentedEpisodes[seriesDir] = len(fileNames)
            break
    else:
        presentedEpisodes[seriesDir] = 0

neededEpisodes = dict()

for (seriesDir, episodes) in presentedEpisodes.iteritems():
    if episodes < COUNT:
        neededEpisodes[seriesDir] = COUNT - episodes

for (seriesDir, episodes) in neededEpisodes.iteritems():
    srcSeriesDir = os.path.join(SOURCE_DIR, seriesDir)
    distSeriesDir = os.path.join(DIST_DIR, seriesDir)

    if not os.path.isdir(distSeriesDir):
        os.makedirs(distSeriesDir)

    for (dirPath, dirNames, fileNames) in os.walk(srcSeriesDir):
        for fileName in fileNames:
            if episodes <= 0:
                break

            episodes -= 1

            srcEpisodePath = os.path.join(dirPath, fileName)
            distEpisodePath = os.path.join(distSeriesDir, fileName)

            os.rename(srcEpisodePath, distEpisodePath)


# https://gist.github.com/jacobtomlinson/9031697
def removeEmptyFolders(path, removeRoot=True):
    if not os.path.isdir(path) or ".stfolder" in path:
        return

    # remove empty subfolders
    files = os.listdir(path)
    if len(files):
        for f in files:
            fullpath = os.path.join(path, f)
            if os.path.isdir(fullpath):
                removeEmptyFolders(fullpath)

    # if folder empty, delete it
    files = os.listdir(path)
    if len(files) == 0 and removeRoot:
        print("Removing empty folder: %s" % path)
        os.rmdir(path)


removeEmptyFolders(SOURCE_DIR, False)
removeEmptyFolders(DIST_DIR, False)
```
