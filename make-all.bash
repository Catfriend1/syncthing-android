#!/bin/bash -e

# Build the syncthing library

./make-go.bash arm
./make-syncthing.bash arm

./make-go.bash 386
./make-syncthing.bash 386

./make-go.bash arm64
./make-syncthing.bash arm64
