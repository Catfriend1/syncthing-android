User [SerVB](https://github.com/SerVB) made a script to care about moving files into the Syncthing folder until N movie episodes are present.

See [syncthing#1279](https://github.com/syncthing/syncthing-android/issues/1279) for more details on the idea.

Thanks for your contribution!

# sequentialMover.py
Source: [https://gist.github.com/SerVB/26fd23d9b4e0b8aa57a6169ab1508812](https://gist.github.com/SerVB/26fd23d9b4e0b8aa57a6169ab1508812)

Copy of the script from the source linked above by 2019-02-24_12:48

```
# https://stackoverflow.com/a/2725908/6639500
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
