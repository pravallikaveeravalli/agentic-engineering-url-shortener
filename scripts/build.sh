#!/usr/bin/env bash
# Build entry point. Pins ADR-001's Java 21 and points Testcontainers at the real Docker socket.
#
# Task T008 (JDK pin) and T016 (Testcontainers socket discovery).
#
# --- Why the JDK is pinned ---
# ADR-001 (Accepted) fixes Java 21 LTS. This machine's default `java` is 24, with Temurin 21.0.8
# also installed. Every build goes through here so the toolchain is the one the ADR chose, and
# `maven.compiler.release` in pom.xml pins the language level as a second, independent guard.
#
# --- Why the Docker socket is discovered ---
# Testcontainers looks for /var/run/docker.sock and the Docker Desktop path. Neither exists when
# Docker is provided by Colima, Rancher Desktop or Lima, and the failure mode is an unhelpful
# "Could not find a valid Docker environment" *even though the `docker` CLI works fine*. This
# machine runs Colima. Rather than hard-code one person's path, the active socket is read from
# `docker context inspect`, so the integration tier runs for a reviewer on any of these runtimes.
# TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE is separate and also required: it tells the Ryuk resource
# reaper which socket path to bind *inside* its own container.
set -euo pipefail

JDK21="/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home"

if [ ! -x "${JDK21}/bin/javac" ]; then
  echo "FAIL: ADR-001 requires Java 21 and no JDK 21 was found at:" >&2
  echo "  ${JDK21}" >&2
  echo "Install a JDK 21 (Temurin) or adjust JDK21 in this script." >&2
  exit 1
fi

export JAVA_HOME="${JDK21}"
cd "$(dirname "$0")/.."

# Discover the Docker endpoint the CLI is actually using, if Docker is present at all.
# Absence is not an error here: the fast tier must run container-free (T017), so a developer
# without Docker can still run `scripts/build.sh -q test`.
if command -v docker >/dev/null 2>&1; then
  DOCKER_ENDPOINT="$(docker context inspect --format '{{.Endpoints.docker.Host}}' 2>/dev/null || true)"
  if [ -n "${DOCKER_ENDPOINT}" ]; then
    export DOCKER_HOST="${DOCKER_ENDPOINT}"
    # The override names the socket path *inside* a helper container, not on the host. With a
    # Linux-VM runtime (Colima, Lima, Rancher Desktop) the host path does not exist in the VM, so
    # binding it makes Ryuk fail with "error while creating mount source path ... operation not
    # supported". Inside those VMs the socket is at /var/run/docker.sock.
    case "${DOCKER_ENDPOINT}" in
      *colima*|*lima*|*.rd/*)
        export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock"
        ;;
      unix://*)
        export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="${DOCKER_ENDPOINT#unix://}"
        ;;
    esac
    echo "TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=${TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE:-unset}"
    echo "DOCKER_HOST=${DOCKER_HOST}"
  fi
fi

echo "JAVA_HOME=${JAVA_HOME}"
"${JAVA_HOME}/bin/java" --version | head -1
exec ./mvnw "$@"
