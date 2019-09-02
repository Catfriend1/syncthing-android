@echo off
REM
REM Syntax:
REM 	git_fetch_branch.cmd [BRANCH_NAME_TO_FETCH]
REM
git fetch --all
git fetch origin "%1":"%1"
git checkout "%1"
