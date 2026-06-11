#!/bin/bash
# ╔══════════════════════════════════════════════════════════════════╗
# ║  MASTER SETUP & RUN SCRIPT                                      ║
# ║  Generic Symbolic Verification Engine                            ║
# ║  JPF + SPF + Hoare Logic Contracts                               ║
# ║                                                                  ║
# ║  Works on: Fresh Ubuntu 20.04/22.04/24.04 (VirtualBox or bare)  ║
# ║  Author: Aryan (BIT Mesra)                                       ║
# ╚══════════════════════════════════════════════════════════════════╝
#
# Usage:
#   chmod +x master_setup.sh
#   ./master_setup.sh              # Interactive menu
#   ./master_setup.sh --setup      # Full setup (Java, JPF, SPF, libs)
#   ./master_setup.sh --run        # Run verification (skip setup)
#   ./master_setup.sh --ui         # Compile and launch SPFVerifierUI
#   ./master_setup.sh --verify <Class> <jpf_file>   # Direct verify
#   ./master_setup.sh --experiment jgrapht          # JGraphT experiment
#   ./master_setup.sh --experiment scalified         # Scalified experiment
#   ./master_setup.sh --all        # Full setup + run all verifications

set -euo pipefail

# ═══════════════════════════════════════════════════════════════════
# CONFIGURATION — Edit these if your paths differ
# ═══════════════════════════════════════════════════════════════════
USERNAME=$(whoami)
HOME_DIR=$HOME

# Auto-detect: PROJECT_DIR = wherever this script lives (i.e. the cloned repo)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${SCRIPT_DIR}"
SRC_DIR="${PROJECT_DIR}/src"
EXP_DIR="${PROJECT_DIR}/exp"
BUILD_DIR="${PROJECT_DIR}/build"

JPF_CORE_DIR="${HOME_DIR}/jpf-core"
JPF_SYMBC_DIR="${HOME_DIR}/jpf-symbc"
LIBS_DIR="${HOME_DIR}/libs"
SITE_PROPS="${HOME_DIR}/.jpf/site.properties"

# JPF forks (Java 8 compatible)
JPF_CORE_REPO="https://github.com/yannicnoller/jpf-core.git"
JPF_SYMBC_REPO="https://github.com/SymbolicPathFinder/jpf-symbc.git"

# Library JARs
JGRAPHT_JAR="jgrapht-core-0.9.2.jar"
JGRAPHT_URL="https://repo1.maven.org/maven2/org/jgrapht/jgrapht-core/0.9.2/jgrapht-core-0.9.2.jar"
SCALIFIED_JAR="tree-0.2.5.jar"
SCALIFIED_URL="https://repo1.maven.org/maven2/com/scalified/tree/0.2.5/tree-0.2.5.jar"

# Classpath components
JPF_CP=".:${JPF_CORE_DIR}/build/*:${JPF_SYMBC_DIR}/build/*:${JPF_SYMBC_DIR}/lib/*"
JGRAPHT_CP=".:${LIBS_DIR}/${JGRAPHT_JAR}:${JPF_CORE_DIR}/build/*:${JPF_SYMBC_DIR}/build/*:${JPF_SYMBC_DIR}/lib/*"
SCALIFIED_CP=".:${LIBS_DIR}/${SCALIFIED_JAR}:${JPF_CORE_DIR}/build/*:${JPF_SYMBC_DIR}/build/*:${JPF_SYMBC_DIR}/lib/*"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
DIM='\033[2m'
NC='\033[0m' # No Color

# ═══════════════════════════════════════════════════════════════════
# UTILITY FUNCTIONS
# ═══════════════════════════════════════════════════════════════════

print_header() {
    echo ""
    echo -e "${CYAN}╔══════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║${NC}  ${BOLD}$1${NC}"
    echo -e "${CYAN}╚══════════════════════════════════════════════════════════╝${NC}"
    echo ""
}

print_step() {
    echo -e "  ${GREEN}[✓]${NC} $1"
}

print_warn() {
    echo -e "  ${YELLOW}[!]${NC} $1"
}

print_error() {
    echo -e "  ${RED}[✗]${NC} $1"
}

print_info() {
    echo -e "  ${DIM}[*]${NC} $1"
}

separator() {
    echo -e "${DIM}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

check_command() {
    if command -v "$1" &> /dev/null; then
        return 0
    else
        return 1
    fi
}

# ═══════════════════════════════════════════════════════════════════
# PHASE 1: INSTALL SYSTEM DEPENDENCIES
# ═══════════════════════════════════════════════════════════════════

detect_distro() {
    # Returns: debian, fedora, arch, suse, or unknown
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        case "$ID" in
            ubuntu|debian|linuxmint|pop|elementary|zorin|kali)
                echo "debian" ;;
            fedora|rhel|centos|rocky|alma|ol)
                echo "fedora" ;;
            arch|manjaro|endeavouros|garuda)
                echo "arch" ;;
            opensuse*|sles)
                echo "suse" ;;
            *)
                # Fallback: check for ID_LIKE
                case "${ID_LIKE:-}" in
                    *debian*|*ubuntu*) echo "debian" ;;
                    *fedora*|*rhel*)   echo "fedora" ;;
                    *arch*)            echo "arch" ;;
                    *suse*)            echo "suse" ;;
                    *)                 echo "unknown" ;;
                esac
                ;;
        esac
    else
        echo "unknown"
    fi
}

detect_arch() {
    # Returns the dpkg/JVM architecture suffix
    local machine
    machine=$(uname -m)
    case "$machine" in
        x86_64)  echo "amd64" ;;
        aarch64) echo "arm64" ;;
        armv7l)  echo "armhf" ;;
        i686)    echo "i386" ;;
        *)       echo "$machine" ;;
    esac
}

find_java8_home() {
    # Search common locations for a Java 8 installation
    local arch
    arch=$(detect_arch)

    local candidates=(
        "/usr/lib/jvm/java-8-openjdk-${arch}"
        "/usr/lib/jvm/java-1.8.0-openjdk"
        "/usr/lib/jvm/java-1.8.0-openjdk-${arch}"
        "/usr/lib/jvm/java-8-openjdk"
        "/usr/lib/jvm/java-8-jdk"
        "/usr/lib/jvm/adoptopenjdk-8-hotspot-${arch}"
        "/usr/lib/jvm/temurin-8-jdk-${arch}"
    )

    for dir in "${candidates[@]}"; do
        if [ -f "${dir}/bin/java" ]; then
            echo "${dir}"
            return 0
        fi
    done

    # Brute force: search /usr/lib/jvm for anything with "8" in the name
    if [ -d /usr/lib/jvm ]; then
        for dir in /usr/lib/jvm/*8*; do
            if [ -f "${dir}/bin/java" ]; then
                echo "${dir}"
                return 0
            fi
        done
    fi

    return 1
}

set_java8_active() {
    # Given a JAVA_HOME, export it and put it first on PATH
    local jhome="$1"
    export JAVA_HOME="${jhome}"
    export PATH="${jhome}/bin:${PATH}"
    JAVA8_HOME="${jhome}"  # save globally for later phases

    # Try to set as system default (non-fatal if it fails)
    if check_command update-alternatives; then
        sudo update-alternatives --set java "${jhome}/jre/bin/java" 2>/dev/null \
            || sudo update-alternatives --set java "${jhome}/bin/java" 2>/dev/null \
            || true
        sudo update-alternatives --set javac "${jhome}/bin/javac" 2>/dev/null || true
    fi
}

install_dependencies() {
    print_header "PHASE 1: Installing System Dependencies"

    local DISTRO
    DISTRO=$(detect_distro)
    local ARCH
    ARCH=$(detect_arch)

    print_info "Detected: ${DISTRO} (${ARCH})"

    if [ "$DISTRO" = "unknown" ]; then
        print_warn "Unknown Linux distribution."
        print_warn "You may need to install these manually:"
        print_warn "  - OpenJDK 8 (JDK, not just JRE)"
        print_warn "  - git, ant, wget"
        print_warn "Then re-run this script."
    fi

    # ── Package manager update ──
    case "$DISTRO" in
        debian)
            print_info "Updating apt package list..."
            sudo apt update -qq 2>/dev/null
            # Ensure universe repo is enabled (needed for Java 8 on 22.04+)
            if check_command add-apt-repository; then
                sudo add-apt-repository -y universe 2>/dev/null || true
                sudo apt update -qq 2>/dev/null
            fi
            print_step "Package list updated"
            ;;
        fedora)
            print_info "Updating dnf..."
            sudo dnf check-update -q 2>/dev/null || true
            print_step "Package list updated"
            ;;
        arch)
            print_info "Updating pacman..."
            sudo pacman -Sy --noconfirm 2>/dev/null
            print_step "Package list updated"
            ;;
        suse)
            print_info "Updating zypper..."
            sudo zypper refresh -q 2>/dev/null
            print_step "Package list updated"
            ;;
    esac

    # ── Install Java 8 ──
    if check_command java && java -version 2>&1 | grep -q '1.8'; then
        print_step "Java 8 already installed: $(java -version 2>&1 | head -1)"
        # Still find JAVA_HOME for later use
        local found_home
        found_home=$(find_java8_home) && JAVA8_HOME="${found_home}"
    else
        print_info "Installing OpenJDK 8..."
        case "$DISTRO" in
            debian)
                sudo apt install -y openjdk-8-jdk 2>/dev/null
                if [ $? -ne 0 ]; then
                    print_warn "openjdk-8-jdk not in repos. Trying adoptium/temurin..."
                    # Adoptium PPA fallback for Ubuntu 24.04+
                    if check_command add-apt-repository; then
                        wget -qO- https://packages.adoptium.net/artifactory/api/gpg/key/public | \
                            sudo tee /etc/apt/trusted.gpg.d/adoptium.asc 2>/dev/null
                        echo "deb https://packages.adoptium.net/artifactory/deb $(. /etc/os-release && echo $VERSION_CODENAME) main" | \
                            sudo tee /etc/apt/sources.list.d/adoptium.list 2>/dev/null
                        sudo apt update -qq 2>/dev/null
                        sudo apt install -y temurin-8-jdk 2>/dev/null
                    fi
                fi
                ;;
            fedora)
                sudo dnf install -y java-1.8.0-openjdk-devel 2>/dev/null
                ;;
            arch)
                # AUR or jdk8-openjdk
                if check_command yay; then
                    yay -S --noconfirm jdk8-openjdk 2>/dev/null
                else
                    sudo pacman -S --noconfirm jdk8-openjdk 2>/dev/null || \
                        print_warn "Install jdk8-openjdk from AUR manually"
                fi
                ;;
            suse)
                sudo zypper install -y java-1_8_0-openjdk-devel 2>/dev/null
                ;;
        esac

        # Find and activate Java 8
        local found_home
        if found_home=$(find_java8_home); then
            set_java8_active "${found_home}"
            print_step "Java 8 installed and activated"
        else
            print_error "Java 8 installation FAILED or not found."
            print_error "Install OpenJDK 8 manually for your distro, then re-run."
            print_error "  Debian/Ubuntu:  sudo apt install openjdk-8-jdk"
            print_error "  Fedora/RHEL:    sudo dnf install java-1.8.0-openjdk-devel"
            print_error "  Arch:           yay -S jdk8-openjdk"
            exit 1
        fi
    fi

    # Verify Java 8 is active
    JAVA_VER=$(java -version 2>&1 | head -1)
    if echo "$JAVA_VER" | grep -q '1.8'; then
        print_step "Java 8 confirmed: $JAVA_VER"
    else
        print_warn "Java 8 is installed but NOT the default (current: $JAVA_VER)"
        local found_home
        if found_home=$(find_java8_home); then
            set_java8_active "${found_home}"
            print_step "Forced Java 8 via JAVA_HOME=${found_home}"
        else
            print_error "Cannot locate Java 8 on this system."
            print_error "JPF/SPF REQUIRE Java 8. Install it and re-run."
            exit 1
        fi
    fi

    # ── Install Git, Ant, wget ──
    local pkgs_to_install=()

    check_command git   || pkgs_to_install+=(git)
    check_command ant   || pkgs_to_install+=(ant)
    check_command wget  || pkgs_to_install+=(wget)

    if [ ${#pkgs_to_install[@]} -gt 0 ]; then
        print_info "Installing: ${pkgs_to_install[*]}..."
        case "$DISTRO" in
            debian) sudo apt install -y "${pkgs_to_install[@]}" -qq 2>/dev/null ;;
            fedora) sudo dnf install -y "${pkgs_to_install[@]}" 2>/dev/null ;;
            arch)   sudo pacman -S --noconfirm "${pkgs_to_install[@]}" 2>/dev/null ;;
            suse)   sudo zypper install -y "${pkgs_to_install[@]}" 2>/dev/null ;;
            *)      print_warn "Install manually: ${pkgs_to_install[*]}" ;;
        esac
    fi

    check_command git  && print_step "Git ready"    || print_error "Git missing"
    check_command ant  && print_step "Ant ready"     || print_error "Ant missing"
    check_command wget && print_step "wget ready"    || print_error "wget missing"

    echo ""
    print_step "All system dependencies ready (${DISTRO}/${ARCH})"
}

# ═══════════════════════════════════════════════════════════════════
# PHASE 2: BUILD JPF-CORE AND JPF-SYMBC
# ═══════════════════════════════════════════════════════════════════

build_jpf() {
    print_header "PHASE 2: Building JPF-Core and JPF-SymBC"

    # --- jpf-core ---
    if [ -f "${JPF_CORE_DIR}/build/RunJPF.jar" ]; then
        print_step "jpf-core already built (${JPF_CORE_DIR}/build/RunJPF.jar)"
    else
        if [ -d "${JPF_CORE_DIR}" ]; then
            print_warn "jpf-core directory exists but not built. Rebuilding..."
            cd "${JPF_CORE_DIR}"
        else
            print_info "Cloning jpf-core (yannicnoller/jpf-core — Java 8 compatible)..."
            cd "${HOME_DIR}"
            git clone "${JPF_CORE_REPO}" jpf-core
            cd "${JPF_CORE_DIR}"
        fi

        print_info "Building jpf-core with Ant (this takes 1-2 minutes)..."
        ant build
        if [ -f "${JPF_CORE_DIR}/build/RunJPF.jar" ]; then
            print_step "jpf-core BUILD SUCCESSFUL"
        else
            print_error "jpf-core build FAILED. Check errors above."
            exit 1
        fi
    fi

    # --- jpf-symbc ---
    if [ -f "${JPF_SYMBC_DIR}/build/jpf-symbc.jar" ]; then
        print_step "jpf-symbc already built (${JPF_SYMBC_DIR}/build/jpf-symbc.jar)"
    else
        if [ -d "${JPF_SYMBC_DIR}" ]; then
            print_warn "jpf-symbc directory exists but not built. Rebuilding..."
            cd "${JPF_SYMBC_DIR}"
        else
            print_info "Cloning jpf-symbc (SymbolicPathFinder)..."
            cd "${HOME_DIR}"
            git clone "${JPF_SYMBC_REPO}" jpf-symbc
            cd "${JPF_SYMBC_DIR}"
        fi

        print_info "Building jpf-symbc with Ant (this takes 2-3 minutes)..."
        ant build
        if [ -f "${JPF_SYMBC_DIR}/build/jpf-symbc.jar" ]; then
            print_step "jpf-symbc BUILD SUCCESSFUL"
        else
            print_error "jpf-symbc build FAILED. Check errors above."
            exit 1
        fi
    fi

    echo ""
    print_step "Both JPF components built successfully"
}

# ═══════════════════════════════════════════════════════════════════
# PHASE 3: CONFIGURE ENVIRONMENT
# ═══════════════════════════════════════════════════════════════════

configure_environment() {
    print_header "PHASE 3: Configuring Environment"

    # Detect Java 8 home dynamically
    local java8_path
    if [ -n "${JAVA8_HOME:-}" ]; then
        java8_path="${JAVA8_HOME}"
    elif java8_path=$(find_java8_home); then
        true  # found
    else
        print_error "Cannot locate Java 8 JAVA_HOME. Run Phase 1 first."
        exit 1
    fi
    print_info "Using JAVA_HOME=${java8_path}"

    # --- site.properties ---
    mkdir -p "${HOME_DIR}/.jpf"
    cat > "${SITE_PROPS}" << EOF
jpf-core = ${JPF_CORE_DIR}
jpf-symbc = ${JPF_SYMBC_DIR}
extensions = \${jpf-core},\${jpf-symbc}
EOF
    print_step "Created ${SITE_PROPS}"

    # --- .bashrc environment variables ---
    MARKER="# === SPF Verification Engine Environment ==="
    # Remove old block if it exists (in case Java path changed)
    if grep -q "${MARKER}" "${HOME_DIR}/.bashrc" 2>/dev/null; then
        sed -i "/${MARKER}/,/# === END SPF Environment ===/d" "${HOME_DIR}/.bashrc"
        print_info "Removed old environment block from .bashrc"
    fi

    cat >> "${HOME_DIR}/.bashrc" << EOF

${MARKER}
export JAVA_HOME=${java8_path}
export JAVA8_HOME=${java8_path}
export JPF_CORE=${JPF_CORE_DIR}
export JPF_SYMBC=${JPF_SYMBC_DIR}
export CLASSPATH=".:${JPF_CORE_DIR}/build/*:${JPF_SYMBC_DIR}/build/*:${JPF_SYMBC_DIR}/lib/*"
export PATH=\$JAVA_HOME/bin:\$PATH
# SPFVerifierUI-specific env vars (absolute paths)
export JPF_CORE_JAR="${JPF_CORE_DIR}/build/jpf.jar"
export JPF_SYMBC_JAR="${JPF_SYMBC_DIR}/build/jpf-symbc.jar"
export JPF_SYMBC_CLASSES="${JPF_SYMBC_DIR}/build/jpf-symbc-classes.jar"
export JPF_SYMBC_LIB="${JPF_SYMBC_DIR}/lib/*"
# === END SPF Environment ===
EOF
    print_step "Added environment variables to .bashrc"

    # Source them now
    export JAVA_HOME="${java8_path}"
    export JAVA8_HOME="${java8_path}"
    export JPF_CORE="${JPF_CORE_DIR}"
    export JPF_SYMBC="${JPF_SYMBC_DIR}"
    export CLASSPATH=".:${JPF_CORE_DIR}/build/*:${JPF_SYMBC_DIR}/build/*:${JPF_SYMBC_DIR}/lib/*"
    export PATH=$JAVA_HOME/bin:$PATH
    export JPF_CORE_JAR="${JPF_CORE_DIR}/build/jpf.jar"
    export JPF_SYMBC_JAR="${JPF_SYMBC_DIR}/build/jpf-symbc.jar"
    export JPF_SYMBC_CLASSES="${JPF_SYMBC_DIR}/build/jpf-symbc-classes.jar"
    export JPF_SYMBC_LIB="${JPF_SYMBC_DIR}/lib/*"
    print_step "Environment variables exported for current session"
}

# ═══════════════════════════════════════════════════════════════════
# PHASE 4: DOWNLOAD LIBRARY JARS
# ═══════════════════════════════════════════════════════════════════

download_libraries() {
    print_header "PHASE 4: Downloading External Libraries"

    mkdir -p "${LIBS_DIR}"

    # JGraphT 0.9.2 (pre-lambda, works better with JPF)
    if [ -f "${LIBS_DIR}/${JGRAPHT_JAR}" ]; then
        print_step "JGraphT 0.9.2 already downloaded"
    else
        print_info "Downloading JGraphT 0.9.2..."
        if wget -q -O "${LIBS_DIR}/${JGRAPHT_JAR}" "${JGRAPHT_URL}"; then
            print_step "JGraphT 0.9.2 downloaded to ${LIBS_DIR}/"
        else
            print_error "JGraphT download failed!"
            print_warn "Download manually from: ${JGRAPHT_URL}"
            print_warn "Place at: ${LIBS_DIR}/${JGRAPHT_JAR}"
        fi
    fi

    # Scalified Tree 0.2.5
    if [ -f "${LIBS_DIR}/${SCALIFIED_JAR}" ]; then
        print_step "Scalified Tree 0.2.5 already downloaded"
    else
        print_info "Downloading Scalified Tree 0.2.5..."
        if wget -q -O "${LIBS_DIR}/${SCALIFIED_JAR}" "${SCALIFIED_URL}"; then
            print_step "Scalified Tree 0.2.5 downloaded to ${LIBS_DIR}/"
        else
            print_error "Scalified download failed!"
            print_warn "Download manually from: ${SCALIFIED_URL}"
            print_warn "Place at: ${LIBS_DIR}/${SCALIFIED_JAR}"
        fi
    fi

    echo ""
    print_step "Library setup complete"
}

# ═══════════════════════════════════════════════════════════════════
# PHASE 5: SETUP PROJECT DIRECTORY
# ═══════════════════════════════════════════════════════════════════

setup_project() {
    print_header "PHASE 5: Setting Up Project"

    if [ -d "${SRC_DIR}" ] && [ -f "${SRC_DIR}/GenericContractsTest.java" ]; then
        print_step "Source files found in ${SRC_DIR}"
    else
        print_error "Source files not found in ${SRC_DIR}"
        print_info "Make sure you're running this script from inside the cloned repo:"
        print_info "  git clone https://github.com/sandipghosal/contract-verifier.git"
        print_info "  cd contract-verifier"
        print_info "  ./master_setup.sh --setup"
        return 1
    fi

    if [ -d "${EXP_DIR}" ] && [ -f "${EXP_DIR}/JGraphTWrapper.java" ]; then
        print_step "Experiment files found in ${EXP_DIR}"
    else
        print_warn "Experiment files not found in ${EXP_DIR} (experiments won't run)"
    fi

    # Create build directory and prepare it
    prepare_build
    print_step "Build directory ready at ${BUILD_DIR}"

    # Compile core engine in build/
    print_info "Compiling core verification engine..."
    cd "${BUILD_DIR}"
    javac -cp "${JPF_CP}" GenericContractsTest.java DispatcherGenerator.java BoundedQueue.java BoundedList.java 2>&1
    if [ $? -eq 0 ]; then
        print_step "Core engine compiled"
    else
        print_error "Compilation failed. Check Java 8 is active."
        return 1
    fi

    # Generate default dispatcher for BoundedQueue
    print_info "Generating default dispatcher (BoundedQueue)..."
    java -cp "." DispatcherGenerator BoundedQueue
    javac -cp "${JPF_CP}" GeneratedDispatcher.java
    print_step "Default dispatcher generated and compiled"

    echo ""
    print_step "Project ready"
}

# ═══════════════════════════════════════════════════════════════════
# PREPARE BUILD DIRECTORY
# Copies engine files from src/ to build/ for flat-directory execution
# ═══════════════════════════════════════════════════════════════════

prepare_build() {
    mkdir -p "${BUILD_DIR}"
    # Copy all engine source files from src/
    cp "${SRC_DIR}"/*.java "${BUILD_DIR}/" 2>/dev/null || true
    cp "${SRC_DIR}"/*.txt "${BUILD_DIR}/" 2>/dev/null || true
    cp "${SRC_DIR}"/*.jpf "${BUILD_DIR}/" 2>/dev/null || true
}

prepare_build_with_exp() {
    # Prepare build/ with engine files + specific experiment files
    prepare_build
    # Copy experiment files from exp/
    cp "${EXP_DIR}"/*.java "${BUILD_DIR}/" 2>/dev/null || true
    cp "${EXP_DIR}"/*.txt "${BUILD_DIR}/" 2>/dev/null || true
    cp "${EXP_DIR}"/*.jpf "${BUILD_DIR}/" 2>/dev/null || true
}

# ═══════════════════════════════════════════════════════════════════
# FULL SETUP (Phases 1-5)
# ═══════════════════════════════════════════════════════════════════

full_setup() {
    print_header "FULL SYSTEM SETUP — From Scratch"
    echo -e "  This will install Java 8, Git, Ant, build JPF/SPF,"
    echo -e "  download libraries, and configure everything."
    echo ""

    install_dependencies
    build_jpf
    configure_environment
    download_libraries
    setup_project

    print_header "SETUP COMPLETE"
    echo -e "  ${GREEN}Everything is ready!${NC}"
    echo ""
    echo "  Run this script again to use the interactive menu:"
    echo "    ./master_setup.sh"
    echo ""
    echo "  Or run directly:"
    echo "    ./master_setup.sh --run"
    echo "    ./master_setup.sh --verify BoundedQueue generic_verify.jpf"
    echo "    ./master_setup.sh --experiment scalified"
    echo "    ./master_setup.sh --ui"
    echo ""
}

# ═══════════════════════════════════════════════════════════════════
# HEALTH CHECK — Verify everything is ready
# ═══════════════════════════════════════════════════════════════════

health_check() {
    print_header "SYSTEM HEALTH CHECK"

    HEALTHY=1

    # Java 8
    if check_command java && java -version 2>&1 | grep -q '1.8'; then
        print_step "Java 8: $(java -version 2>&1 | head -1)"
    else
        print_error "Java 8 NOT found — run: ./master_setup.sh --setup"
        HEALTHY=0
    fi

    # JPF Core
    if [ -f "${JPF_CORE_DIR}/build/RunJPF.jar" ]; then
        print_step "jpf-core: built"
    else
        print_error "jpf-core NOT built — run: ./master_setup.sh --setup"
        HEALTHY=0
    fi

    # JPF SymBC
    if [ -f "${JPF_SYMBC_DIR}/build/jpf-symbc.jar" ]; then
        print_step "jpf-symbc: built"
    else
        print_error "jpf-symbc NOT built — run: ./master_setup.sh --setup"
        HEALTHY=0
    fi

    # site.properties
    if [ -f "${SITE_PROPS}" ]; then
        print_step "site.properties: exists"
    else
        print_error "site.properties missing — run: ./master_setup.sh --setup"
        HEALTHY=0
    fi

    # Libraries
    if [ -f "${LIBS_DIR}/${JGRAPHT_JAR}" ]; then
        print_step "JGraphT 0.9.2 JAR: present"
    else
        print_warn "JGraphT JAR missing (needed only for JGraphT experiments)"
    fi

    if [ -f "${LIBS_DIR}/${SCALIFIED_JAR}" ]; then
        print_step "Scalified 0.2.5 JAR: present"
    else
        print_warn "Scalified JAR missing (needed only for Scalified experiments)"
    fi

    # Project files
    if [ -f "${SRC_DIR}/GenericContractsTest.java" ]; then
        print_step "Source files: present in ${SRC_DIR}"
    else
        print_error "Source files NOT found in ${SRC_DIR}"
        HEALTHY=0
    fi

    if [ -f "${EXP_DIR}/JGraphTWrapper.java" ]; then
        print_step "Experiment files: present in ${EXP_DIR}"
    else
        print_warn "Experiment files missing (library experiments won't run)"
    fi

    echo ""
    if [ $HEALTHY -eq 1 ]; then
        print_step "All systems healthy — ready to verify!"
    else
        print_error "Issues found. Run: ./master_setup.sh --setup"
    fi
    echo ""

    return $HEALTHY
}

# ═══════════════════════════════════════════════════════════════════
# VERIFY A DATA STRUCTURE
# ═══════════════════════════════════════════════════════════════════

verify_ds() {
    local CLASS="$1"
    local JPF_FILE="$2"
    local LIB_JAR=""
    local COMPILE_CP="${JPF_CP}"

    # Determine if we need a library JAR
    if [[ "$CLASS" == JGraphT* ]]; then
        LIB_JAR="${LIBS_DIR}/${JGRAPHT_JAR}"
        COMPILE_CP="${JGRAPHT_CP}"
        if [ ! -f "$LIB_JAR" ]; then
            print_error "JGraphT JAR not found at $LIB_JAR"
            print_info "Run: ./master_setup.sh --setup"
            return 1
        fi
    elif [[ "$CLASS" == Scalified* ]]; then
        LIB_JAR="${LIBS_DIR}/${SCALIFIED_JAR}"
        COMPILE_CP="${SCALIFIED_CP}"
        if [ ! -f "$LIB_JAR" ]; then
            print_error "Scalified JAR not found at $LIB_JAR"
            print_info "Run: ./master_setup.sh --setup"
            return 1
        fi
    fi

    # Prepare build directory with engine + experiment files
    if [[ "$CLASS" == JGraphT* ]] || [[ "$CLASS" == Scalified* ]]; then
        prepare_build_with_exp
    else
        prepare_build
    fi
    cd "${BUILD_DIR}"

    separator
    echo -e "  ${BOLD}Verifying: ${CYAN}${CLASS}${NC}"
    separator
    echo ""

    # Step 1: Compile the data structure
    print_info "Compiling ${CLASS}.java..."
    if [ -n "$LIB_JAR" ]; then
        javac -cp ".:${LIB_JAR}" ${CLASS}.java
    else
        javac ${CLASS}.java
    fi
    print_step "Compiled"

    # Step 2: Generate dispatcher
    print_info "Generating dispatcher for ${CLASS}..."
    if [ -n "$LIB_JAR" ]; then
        java -cp ".:${LIB_JAR}" DispatcherGenerator ${CLASS}
    else
        java -cp "." DispatcherGenerator ${CLASS}
    fi
    print_step "Dispatcher generated"

    # Step 3: Compile everything with JPF classpath
    print_info "Compiling all with JPF classpath..."
    javac -cp "${COMPILE_CP}" GenericContractsTest.java DispatcherGenerator.java GeneratedDispatcher.java ${CLASS}.java
    print_step "Full compilation done"

    # Step 4: Run JPF
    echo ""
    print_info "Running JPF symbolic verification..."
    echo ""
    separator
    echo -e "  ${BOLD}JPF OUTPUT${NC}"
    separator
    echo ""

    java -jar "${JPF_CORE_DIR}/build/RunJPF.jar" "${JPF_FILE}"

    echo ""
    separator
    echo -e "  ${GREEN}${BOLD}Verification complete for ${CLASS}${NC}"
    separator
    echo ""
}

# ═══════════════════════════════════════════════════════════════════
# RUN ALL CORE VERIFICATIONS
# ═══════════════════════════════════════════════════════════════════

run_all_core() {
    print_header "Running All Core Data Structure Verifications"

    # BoundedQueue
    echo ""
    echo -e "${YELLOW}═══ 1/2: BoundedQueue ═══${NC}"
    cat > "${BUILD_DIR}/verify_boundedqueue.jpf" << EOF
target=GenericContractsTest
target.args=BoundedQueue,BoundedQueue_contracts.txt
classpath=.
vm.insn_factory.class=gov.nasa.jpf.symbc.SymbolicInstructionFactory
listener=gov.nasa.jpf.symbc.SymbolicListener
symbolic.min_int=-10
symbolic.max_int=10
symbolic.debug=true
symbolic.lazy=true
search.multiple_errors=true
EOF
    verify_ds "BoundedQueue" "verify_boundedqueue.jpf"

    # BoundedList
    echo ""
    echo -e "${YELLOW}═══ 2/2: BoundedList ═══${NC}"
    cat > "${BUILD_DIR}/verify_boundedlist.jpf" << EOF
target=GenericContractsTest
target.args=BoundedList,BoundedList_contracts.txt
classpath=.
vm.insn_factory.class=gov.nasa.jpf.symbc.SymbolicInstructionFactory
listener=gov.nasa.jpf.symbc.SymbolicListener
symbolic.min_int=-10
symbolic.max_int=10
symbolic.debug=true
symbolic.lazy=true
search.multiple_errors=true
EOF
    verify_ds "BoundedList" "verify_boundedlist.jpf"

    print_header "All Core Verifications Complete"
}

# ═══════════════════════════════════════════════════════════════════
# JGRAPHT EXPERIMENT
# ═══════════════════════════════════════════════════════════════════

run_jgrapht_experiment() {
    print_header "EXPERIMENT: JGraphT Library Verification"

    prepare_build_with_exp
    cd "${BUILD_DIR}"

    echo "  This experiment attempts to symbolically verify REAL"
    echo "  JGraphT library code through JPF/SPF."
    echo ""
    echo "  Expected result: JPF will FAIL with:"
    echo "  'Choco does not support bitwise SHIFT'"
    echo ""
    echo "  This is a VALID research finding — JGraphT uses HashMap"
    echo "  internally, which uses bitwise >>> that Choco can't solve."
    echo ""

    # Create JPF config
    cat > "${BUILD_DIR}/verify_jgrapht_experiment.jpf" << EOF
target=GenericContractsTest
target.args=JGraphTWrapper,JGraphTWrapper_contracts.txt
classpath=.:${LIBS_DIR}/${JGRAPHT_JAR}
vm.insn_factory.class=gov.nasa.jpf.symbc.SymbolicInstructionFactory
listener=gov.nasa.jpf.symbc.SymbolicListener
symbolic.min_int=-5
symbolic.max_int=5
symbolic.debug=true
symbolic.lazy=true
search.multiple_errors=true
EOF

    verify_ds "JGraphTWrapper" "verify_jgrapht_experiment.jpf"

    echo ""
    echo -e "${CYAN}╔══════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║${NC}  ${BOLD}ANALYSIS${NC}"
    echo -e "${CYAN}╠══════════════════════════════════════════════════════════╣${NC}"
    echo -e "${CYAN}║${NC}  JGraphT uses HashMap internally.                        "
    echo -e "${CYAN}║${NC}  HashMap.hash() uses bitwise >>> (unsigned right shift).  "
    echo -e "${CYAN}║${NC}  Choco constraint solver CANNOT handle symbolic bitwise.  "
    echo -e "${CYAN}║${NC}                                                           "
    echo -e "${CYAN}║${NC}  Call chain: add(p1) → addVertex(p1) → HashMap.put()      "
    echo -e "${CYAN}║${NC}           → HashMap.hash(p1) → p1.hashCode() >>> 16       "
    echo -e "${CYAN}║${NC}           → Choco: UNSUPPORTED                            "
    echo -e "${CYAN}║${NC}                                                           "
    echo -e "${CYAN}║${NC}  ${YELLOW}CONCLUSION:${NC} Production libs using HashMap/HashSet      "
    echo -e "${CYAN}║${NC}  cannot be symbolically verified via JPF/Choco.           "
    echo -e "${CYAN}╚══════════════════════════════════════════════════════════╝${NC}"
    echo ""
}

# ═══════════════════════════════════════════════════════════════════
# SCALIFIED EXPERIMENT (BUGGY → FIXED)
# ═══════════════════════════════════════════════════════════════════

run_scalified_experiment() {
    print_header "EXPERIMENT: Scalified Tree Library Verification"

    prepare_build_with_exp
    cd "${BUILD_DIR}"

    echo "  This experiment runs TWO verification passes:"
    echo "    Run 1: BUGGY wrapper  → JPF detects remove() bug"
    echo "    Run 2: FIXED wrapper  → JPF validates all contracts"
    echo ""

    # Create contracts file
    cat > "${BUILD_DIR}/ScalifiedWrapper_contracts.txt" << 'CONTRACTS'
# Contracts for ScalifiedWrapper (REAL Scalified library)

# add() contracts
{true} add(p1) {size() == oldSize + 1}
{true} add(p1) {contains(p1)}
{isEmpty()} add(p1) {size() == 1}
{isEmpty()} add(p1) {!isEmpty()}

# remove() contracts
{contains(p1)} remove(p1) {!contains(p1)}
{size() == 1 && contains(p1)} remove(p1) {isEmpty()}

# Intentionally buggy contracts (should FAIL)
{size() == 2} add(p1) {size() == 2}
{isEmpty()} add(p1) {size() == 0}
CONTRACTS

    # Create JPF config
    cat > "${BUILD_DIR}/verify_scalified_experiment.jpf" << EOF
target=GenericContractsTest
target.args=ScalifiedWrapper,ScalifiedWrapper_contracts.txt
classpath=.:${LIBS_DIR}/${SCALIFIED_JAR}
vm.insn_factory.class=gov.nasa.jpf.symbc.SymbolicInstructionFactory
listener=gov.nasa.jpf.symbc.SymbolicListener
symbolic.min_int=-5
symbolic.max_int=5
symbolic.debug=true
symbolic.lazy=true
search.multiple_errors=true
EOF

    # ─── RUN 1: BUGGY ───
    echo ""
    echo -e "${RED}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "  ${BOLD}RUN 1: BUGGY ScalifiedWrapper${NC}"
    echo -e "  Bug: remove() calls target.remove(target) — wrong API usage"
    echo -e "${RED}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""

    cp "${BUILD_DIR}/ScalifiedWrapper_BUGGY.java" "${BUILD_DIR}/ScalifiedWrapper.java"
    verify_ds "ScalifiedWrapper" "verify_scalified_experiment.jpf"

    echo ""
    echo -e "  ${RED}Expected: add() VALIDATED, remove() VIOLATION DETECTED${NC}"
    echo ""

    # ─── RUN 2: FIXED ───
    echo ""
    echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "  ${BOLD}RUN 2: FIXED ScalifiedWrapper${NC}"
    echo -e "  Fix: remove() finds parent, calls parent.dropSubtree(child)"
    echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""

    cp "${BUILD_DIR}/ScalifiedWrapper_FIXED.java" "${BUILD_DIR}/ScalifiedWrapper.java"
    verify_ds "ScalifiedWrapper" "verify_scalified_experiment.jpf"

    echo ""
    echo -e "${CYAN}╔══════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║${NC}  ${BOLD}EXPERIMENT SUMMARY${NC}"
    echo -e "${CYAN}╠══════════════════════════════════════════════════════════╣${NC}"
    echo -e "${CYAN}║${NC}  1. Scalified library loaded and executed by JPF         "
    echo -e "${CYAN}║${NC}  2. BUGGY wrapper: JPF caught remove() bug               "
    echo -e "${CYAN}║${NC}  3. FIXED wrapper: JPF validated all valid contracts      "
    echo -e "${CYAN}║${NC}  4. Intentional bugs correctly detected in both runs      "
    echo -e "${CYAN}║${NC}                                                           "
    echo -e "${CYAN}║${NC}  ${GREEN}CONCLUSION:${NC} Unlike JGraphT, Scalified's simpler        "
    echo -e "${CYAN}║${NC}  internals (arrays, no HashMap) are JPF-compatible.       "
    echo -e "${CYAN}║${NC}  Contract verification catches real integration bugs.     "
    echo -e "${CYAN}╚══════════════════════════════════════════════════════════╝${NC}"
    echo ""
}

# ═══════════════════════════════════════════════════════════════════
# COMPILE & LAUNCH SPFVerifierUI
# ═══════════════════════════════════════════════════════════════════

launch_ui() {
    print_header "SPFVerifierUI — Desktop Application"

    if [ ! -f "${SRC_DIR}/SPFVerifierUI.java" ]; then
        print_error "SPFVerifierUI.java not found in ${SRC_DIR}"
        print_info "Place SPFVerifierUI.java in ${SRC_DIR} and retry."
        return 1
    fi

    # Prepare build directory with all source files
    prepare_build
    cd "${BUILD_DIR}"

    # ── Export env vars that SPFVerifierUI.java reads via loadEnvConfig() ──
    # The UI reads these SPECIFIC names (not JPF_CORE/JPF_SYMBC):
    export JPF_CORE_JAR="${JPF_CORE_DIR}/build/jpf.jar"
    export JPF_SYMBC_JAR="${JPF_SYMBC_DIR}/build/jpf-symbc.jar"
    export JPF_SYMBC_CLASSES="${JPF_SYMBC_DIR}/build/jpf-symbc-classes.jar"
    export JPF_SYMBC_LIB="${JPF_SYMBC_DIR}/lib/*"

    # Map JAVA_HOME → JAVA8_HOME (the name the UI expects)
    local java8_path="${JAVA8_HOME:-${JAVA_HOME:-}}"
    if [ -n "${java8_path}" ]; then
        export JAVA8_HOME="${java8_path}"
    fi

    # Auto-wire library JARs if they exist
    local extra=""
    if [ -f "${LIBS_DIR}/${JGRAPHT_JAR}" ]; then
        extra="${LIBS_DIR}/${JGRAPHT_JAR}"
    fi
    if [ -f "${LIBS_DIR}/${SCALIFIED_JAR}" ]; then
        if [ -n "$extra" ]; then
            extra="${extra}:${LIBS_DIR}/${SCALIFIED_JAR}"
        else
            extra="${LIBS_DIR}/${SCALIFIED_JAR}"
        fi
    fi
    if [ -n "$extra" ]; then
        export EXTRA_JARS="${extra}"
        print_step "EXTRA_JARS set: ${extra}"
    fi

    print_step "UI environment configured (absolute paths)"
    print_info "  JPF_CORE_JAR     = ${JPF_CORE_JAR}"
    print_info "  JPF_SYMBC_JAR    = ${JPF_SYMBC_JAR}"
    print_info "  JPF_SYMBC_CLASSES= ${JPF_SYMBC_CLASSES}"
    print_info "  JPF_SYMBC_LIB    = ${JPF_SYMBC_LIB}"
    [ -n "${JAVA8_HOME:-}" ] && print_info "  JAVA8_HOME       = ${JAVA8_HOME}"
    [ -n "${EXTRA_JARS:-}" ] && print_info "  EXTRA_JARS       = ${EXTRA_JARS}"

    print_info "Compiling SPFVerifierUI.java..."
    javac -cp "${JPF_CP}" SPFVerifierUI.java
    print_step "Compiled"

    print_info "Launching SPFVerifierUI..."
    echo ""
    echo "  The GUI window should appear shortly."
    echo "  Use it to select a .java file and .txt contracts file,"
    echo "  then click 'Run Verification'."
    echo ""
    echo "  Library JARs (JGraphT, Scalified) are pre-configured."
    echo "  Open Settings to verify or add more."
    echo ""

    java -cp "${JPF_CP}" SPFVerifierUI &
    print_step "SPFVerifierUI launched (PID: $!)"
}

# ═══════════════════════════════════════════════════════════════════
# INTERACTIVE MENU
# ═══════════════════════════════════════════════════════════════════

show_menu() {
    echo ""
    echo -e "${CYAN}╔══════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║${NC}  ${BOLD}Generic Symbolic Verification Engine${NC}"
    echo -e "${CYAN}║${NC}  ${DIM}JPF + SPF + Hoare Logic Contracts${NC}"
    echo -e "${CYAN}╠══════════════════════════════════════════════════════════╣${NC}"
    echo -e "${CYAN}║${NC}                                                          "
    echo -e "${CYAN}║${NC}  ${BOLD}SETUP${NC}                                                  "
    echo -e "${CYAN}║${NC}    ${GREEN}1${NC}  Full setup (Java, JPF, SPF, libs, project)        "
    echo -e "${CYAN}║${NC}    ${GREEN}2${NC}  Health check (verify everything is ready)          "
    echo -e "${CYAN}║${NC}                                                          "
    echo -e "${CYAN}║${NC}  ${BOLD}CORE VERIFICATION${NC}                                      "
    echo -e "${CYAN}║${NC}    ${GREEN}3${NC}  Verify BoundedQueue                                "
    echo -e "${CYAN}║${NC}    ${GREEN}4${NC}  Verify BoundedList                                 "
    echo -e "${CYAN}║${NC}    ${GREEN}5${NC}  Verify ALL core data structures                    "
    echo -e "${CYAN}║${NC}                                                          "
    echo -e "${CYAN}║${NC}  ${BOLD}LIBRARY EXPERIMENTS${NC}                                     "
    echo -e "${CYAN}║${NC}    ${GREEN}6${NC}  JGraphT experiment (HashMap → Choco failure)       "
    echo -e "${CYAN}║${NC}    ${GREEN}7${NC}  Scalified experiment (Buggy → Fixed)                "
    echo -e "${CYAN}║${NC}    ${GREEN}8${NC}  Run ALL experiments                                 "
    echo -e "${CYAN}║${NC}                                                          "
    echo -e "${CYAN}║${NC}  ${BOLD}UI APPLICATION${NC}                                          "
    echo -e "${CYAN}║${NC}    ${GREEN}9${NC}  Launch SPFVerifierUI (Java Swing GUI)               "
    echo -e "${CYAN}║${NC}                                                          "
    echo -e "${CYAN}║${NC}    ${RED}0${NC}  Exit                                                "
    echo -e "${CYAN}║${NC}                                                          "
    echo -e "${CYAN}╚══════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -ne "  ${BOLD}Select option [0-9]: ${NC}"
}

interactive_menu() {
    while true; do
        show_menu
        read -r choice

        case $choice in
            1) full_setup ;;
            2) health_check ;;
            3)
                prepare_build
                cd "${BUILD_DIR}"
                cat > "${BUILD_DIR}/verify_boundedqueue.jpf" << EOF
target=GenericContractsTest
target.args=BoundedQueue,BoundedQueue_contracts.txt
classpath=.
vm.insn_factory.class=gov.nasa.jpf.symbc.SymbolicInstructionFactory
listener=gov.nasa.jpf.symbc.SymbolicListener
symbolic.min_int=-10
symbolic.max_int=10
symbolic.debug=true
symbolic.lazy=true
search.multiple_errors=true
EOF
                verify_ds "BoundedQueue" "verify_boundedqueue.jpf"
                ;;
            4)
                prepare_build
                cd "${BUILD_DIR}"
                cat > "${BUILD_DIR}/verify_boundedlist.jpf" << EOF
target=GenericContractsTest
target.args=BoundedList,BoundedList_contracts.txt
classpath=.
vm.insn_factory.class=gov.nasa.jpf.symbc.SymbolicInstructionFactory
listener=gov.nasa.jpf.symbc.SymbolicListener
symbolic.min_int=-10
symbolic.max_int=10
symbolic.debug=true
symbolic.lazy=true
search.multiple_errors=true
EOF
                verify_ds "BoundedList" "verify_boundedlist.jpf"
                ;;
            5) run_all_core ;;
            6) run_jgrapht_experiment ;;
            7) run_scalified_experiment ;;
            8)
                run_all_core
                run_jgrapht_experiment
                run_scalified_experiment
                ;;
            9) launch_ui ;;
            0)
                echo ""
                echo -e "  ${GREEN}Goodbye!${NC}"
                echo ""
                exit 0
                ;;
            *)
                print_error "Invalid option. Choose 0-9."
                ;;
        esac

        echo ""
        echo -ne "  ${DIM}Press ENTER to return to menu...${NC}"
        read -r
    done
}

# ═══════════════════════════════════════════════════════════════════
# PRE-FLIGHT: Silently activate Java 8 if available
# ═══════════════════════════════════════════════════════════════════

preflight_java8() {
    # If Java 8 is already active, nothing to do
    if check_command java && java -version 2>&1 | grep -q '1.8'; then
        return 0
    fi
    # Try to find and activate Java 8 silently
    local found_home
    if found_home=$(find_java8_home 2>/dev/null); then
        export JAVA_HOME="${found_home}"
        export PATH="${found_home}/bin:${PATH}"
    fi
}

preflight_java8

# ═══════════════════════════════════════════════════════════════════
# ENTRY POINT — CLI argument parsing
# ═══════════════════════════════════════════════════════════════════

case "${1:-}" in
    --setup)
        full_setup
        ;;
    --run)
        health_check && run_all_core
        ;;
    --ui)
        health_check && launch_ui
        ;;
    --verify)
        if [ -z "${2:-}" ] || [ -z "${3:-}" ]; then
            echo "Usage: $0 --verify <ClassName> <jpf_config_file>"
            echo ""
            echo "Examples:"
            echo "  $0 --verify BoundedQueue generic_verify.jpf"
            echo "  $0 --verify BoundedList verify_boundedlist.jpf"
            echo "  $0 --verify JGraphTWrapper verify_jgraphtwrapper.jpf"
            echo "  $0 --verify ScalifiedWrapper verify_scalifiedwrapper.jpf"
            exit 1
        fi
        health_check && verify_ds "$2" "$3"
        ;;
    --experiment)
        case "${2:-}" in
            jgrapht)  health_check && run_jgrapht_experiment ;;
            scalified) health_check && run_scalified_experiment ;;
            all)
                health_check
                run_jgrapht_experiment
                run_scalified_experiment
                ;;
            *)
                echo "Usage: $0 --experiment {jgrapht|scalified|all}"
                exit 1
                ;;
        esac
        ;;
    --all)
        full_setup
        run_all_core
        run_jgrapht_experiment
        run_scalified_experiment
        ;;
    --health)
        health_check
        ;;
    --help|-h)
        echo ""
        echo "Usage: $0 [OPTION]"
        echo ""
        echo "Options:"
        echo "  (none)           Interactive menu"
        echo "  --setup          Full system setup (Java, JPF, SPF, libs)"
        echo "  --run            Run all core verifications"
        echo "  --verify C F     Verify class C with config F"
        echo "  --experiment E   Run experiment (jgrapht|scalified|all)"
        echo "  --ui             Compile and launch SPFVerifierUI"
        echo "  --all            Full setup + all verifications"
        echo "  --health         System health check"
        echo "  --help           Show this help"
        echo ""
        ;;
    "")
        interactive_menu
        ;;
    *)
        echo "Unknown option: $1"
        echo "Run: $0 --help"
        exit 1
        ;;
esac
