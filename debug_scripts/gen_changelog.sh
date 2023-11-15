#!/bin/sh
git log --pretty=format:%s $(git describe --tags --abbrev=0)..HEAD | egrep -v -i "bump|import|README.md|whatsnew" > app/src/main/play/en-GB/whatsnew
