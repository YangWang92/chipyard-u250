#!/usr/bin/env bash

# exit script if any command fails
set -e
set -o pipefail

RDIR=$(git rev-parse --show-toplevel)

# Ignore toolchain submodules
cd "$RDIR"
for name in toolchains/*/*/ ; do
    git config submodule."${name%/}".update none
done
# Disable updates to the FireSim submodule until explicitly requested
git config submodule.sims/firesim.update none
# Disable updates to the hammer-cad-plugins repo
git config submodule.vlsi/hammer-cad-plugins.update none
git submodule update --init --recursive #--jobs 8
# Un-ignore toolchain submodules
for name in toolchains/*/*/ ; do
    git config --unset submodule."${name%/}".update
done
git config --unset submodule.vlsi/hammer-cad-plugins.update

# Renable firesim and init only the required submodules to provide
# all required scala deps, without doing a full build-setup
git config --unset submodule.sims/firesim.update
git submodule update --init sims/firesim
git -C sims/firesim submodule update --init sim/midas
git config submodule.sims/firesim.update none
