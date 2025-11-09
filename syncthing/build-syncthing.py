from __future__ import print_function
import os
import os.path
import shutil
import subprocess
import sys
import platform

PLATFORM_DIRS = {
    'Windows': 'windows-x86_64',
    'Linux': 'linux-x86_64',
    'Darwin': 'darwin-x86_64',
}

# Leave empty to auto-detect version by 'git describe'.
FORCE_DISPLAY_SYNCTHING_VERSION = ''
FILENAME_SYNCTHING_BINARY = 'libsyncthingnative.so'

BUILD_TARGETS = [
    {
        'arch': 'arm',
        'goarch': 'arm',
        'jni_dir': 'armeabi-v7a',
        'cc': 'armv7a-linux-androideabi{}-clang',
    },
    {
        'arch': 'arm64',
        'goarch': 'arm64',
        'jni_dir': 'arm64-v8a',
        'cc': 'aarch64-linux-android{}-clang',
    },
    {
        'arch': 'x86',
        'goarch': '386',
        'jni_dir': 'x86',
        'cc': 'i686-linux-android{}-clang',
    },
    {
        'arch': 'x86_64',
        'goarch': 'amd64',
        'jni_dir': 'x86_64',
        'cc': 'x86_64-linux-android{}-clang',
    }
]

def fail(message, *args, **kwargs):
    print((message % args).format(**kwargs))
    sys.exit(1)


def get_min_sdk(project_dir):
    with open(os.path.join(project_dir, 'gradle', 'libs.versions.toml')) as file_handle:
        for line in file_handle:
            tokens = list(filter(None, line.split('"')))
            if len(tokens) == 3 and tokens[0] == 'min-sdk = ':
                return int(tokens[1])

    fail('Failed to find min-sdk')

def which_raw(program):
    import os
    def is_exe(fpath):
        return os.path.isfile(fpath) and os.access(fpath, os.X_OK)

    fpath, fname = os.path.split(program)
    if fpath:
        if is_exe(program):
            return program
    else:
        for path in os.environ["PATH"].split(os.pathsep):
            exe_file = os.path.join(path, program)
            if is_exe(exe_file):
                return exe_file

    return None

def which(program):
    if (sys.platform == 'win32'):
        which_result = which_raw(program + ".bat")
        if not which_result:
            which_result = which_raw(program + ".cmd")
            if not which_result:
                which_result = which_raw(program + ".exe")
        return which_result
    else:
        return which_raw(program)

def change_permissions_recursive(path, mode):
    import os
    for root, dirs, files in os.walk(path, topdown=False):
        for dir in [os.path.join(root,d) for d in dirs]:
            os.chmod(dir, mode)
        for file in [os.path.join(root, f) for f in files]:
            os.chmod(file, mode)

def get_expected_go_version():
    import os

    # Read pinned version from gradle/libs.versions.toml
    toml_path = os.path.join(
        os.path.dirname(os.path.dirname(os.path.realpath(__file__))),
        'gradle',
        'libs.versions.toml'
    )
    
    if not os.path.exists(toml_path):
        raise RuntimeError(f"Could not find {toml_path}")
    
    with open(toml_path, 'r') as f:
        for line in f:
            if 'go_version' in line and '=' in line:
                # Extract version from line like: go_version = "1.25.4"
                parts = line.split('=', 1)
                if len(parts) == 2:
                    version = parts[1].strip().strip('"').strip("'")
                    if version:
                        return version
    
    raise RuntimeError("Could not find go_version in gradle/libs.versions.toml")

def get_go_version(go_binary):
    """Get the version of a Go binary"""
    try:
        result = subprocess.check_output([go_binary, 'version'], stderr=subprocess.STDOUT, timeout=10)
        # Parse "go version go1.25.0 linux/amd64" to "1.25.0"
        version_line = result.decode().strip()
        parts = version_line.split()
        if len(parts) >= 3 and parts[2].startswith('go'):
            return parts[2][2:]  # Remove "go" prefix
        return None
    except:
        return None

def install_go():
    import os
    import tarfile
    import shutil
    import subprocess

    if sys.version_info[0] >= 3:
        from urllib.request import urlretrieve
    else:
        from urllib import urlretrieve

    if not os.path.isdir(prerequisite_tools_dir):
        os.makedirs(prerequisite_tools_dir)

    expected_version = get_expected_go_version()
    print('Required Go version:', expected_version)

    # Find system Go for bootstrap (assume it exists as per fdroid environment)
    system_go = which("go")
    if not system_go:
        fail('Error: No system Go found for bootstrap. golang-go package should be installed in fdroid environment.')
    
    system_version = get_go_version(system_go)
    print('Found system Go version:', system_version, 'at:', system_go)

    if system_version == expected_version:
        print('System Go version matches required version. No build needed.')
        return
    
    print('System Go version differs from required. Building Go', expected_version, 'from source using system Go as bootstrap...')

    # Download Go source code from GitHub
    go_source_url = 'https://github.com/golang/go/archive/go' + expected_version + '.tar.gz'
    go_source_tar = os.path.join(prerequisite_tools_dir, 'go-source-' + expected_version + '.tar.gz')
    
    if not os.path.isfile(go_source_tar):
        print('Downloading Go source to:', go_source_tar)
        urlretrieve(go_source_url, go_source_tar)
    print('Downloaded Go source to:', go_source_tar)

    # Extract Go source
    go_extract_dir = os.path.join(prerequisite_tools_dir, 'go-go' + expected_version)
    if not os.path.isdir(go_extract_dir):
        print("Extracting Go source ...")
        with tarfile.open(go_source_tar, 'r:gz') as tar:
            try:
                tar.extractall(prerequisite_tools_dir, filter="data")
            except TypeError:
                tar.extractall(prerequisite_tools_dir)

    # Prepare the Go build directory
    go_build_dir = os.path.join(prerequisite_tools_dir, 'go')
    if os.path.isdir(go_build_dir):
        shutil.rmtree(go_build_dir)
    
    # Copy source to build directory
    shutil.copytree(go_extract_dir, go_build_dir)

    # Build Go from source using system Go as bootstrap
    print('Building Go from source using system Go as bootstrap...')
    build_env = os.environ.copy()
    build_env['GOROOT_BOOTSTRAP'] = os.path.dirname(os.path.dirname(system_go))
    if not os.path.isdir(os.path.join(build_env['GOROOT_BOOTSTRAP'], 'src', 'encoding')):
        build_env['GOROOT_BOOTSTRAP'] = '/usr/lib/go'
        print('Override build_env:GOROOT_BOOTSTRAP using ', build_env['GOROOT_BOOTSTRAP'])

    if sys.platform == 'win32':
        build_script = os.path.join(go_build_dir, 'src', 'make.bat')
        subprocess.check_call([build_script], env=build_env, cwd=os.path.join(go_build_dir, 'src'))
    else:
        build_script = os.path.join(go_build_dir, 'src', 'make.bash')
        # Make sure the script is executable
        os.chmod(build_script, 0o755)
        subprocess.check_call(['bash', build_script], env=build_env, cwd=os.path.join(go_build_dir, 'src'))

    # Check if we already have a built Go with correct version
    go_bin_path = os.path.join(go_build_dir, 'bin')
    built_go = os.path.join(go_bin_path, 'go')
    if sys.platform == 'win32':
         built_go += '.exe'

    # Verify the build
    if not os.path.isfile(built_go):
        fail('Go build failed: go binary not found at ' + built_go)
    
    # Test the built Go and verify version
    try:
        result = subprocess.check_output([built_go, 'version'], stderr=subprocess.STDOUT)
        print('Successfully built Go:', result.decode().strip())
        
        built_version = get_go_version(built_go)
        if built_version != expected_version:
            fail('Built Go version ' + str(built_version) + ' does not match expected ' + expected_version)
        
    except Exception as e:
        fail('Built Go is not working: ' + str(e))

    # Add built Go to PATH and set GOROOT
    print('Adding built Go to PATH:', go_bin_path)
    print('Setting GOROOT to:', go_build_dir)
    os.environ["PATH"] = go_bin_path + os.pathsep + os.environ["PATH"]
    os.environ["GOROOT"] = go_build_dir


def write_file(fullfn, text):
    with open(fullfn, 'w') as hFile:
        hFile.write(text + '\n')


def get_ndk_ready():
    if os.environ.get('ANDROID_NDK_HOME', ''):
        return
    ndk_env_vars_defined = True
    if not os.environ.get('NDK_VERSION', ''):
        print('NDK_VERSION is NOT defined.')
        ndk_env_vars_defined = False
    if not os.environ.get('ANDROID_HOME', ''):
        print('ANDROID_HOME is NOT defined.')
        ndk_env_vars_defined = False
    if not ndk_env_vars_defined:
        fail('Error: ANDROID_NDK_HOME or NDK_VERSION and ANDROID_HOME environment variable must be defined.')
        return
    os.environ["ANDROID_NDK_HOME"] = os.path.join(os.environ['ANDROID_HOME'], 'ndk', os.environ['NDK_VERSION'])
    return


#
# BUILD SCRIPT MAIN.
#
if platform.system() not in PLATFORM_DIRS:
    fail('Unsupported python platform %s. Supported platforms: %s', platform.system(),
         ', '.join(PLATFORM_DIRS.keys()))

module_dir = os.path.dirname(os.path.realpath(__file__))
project_dir = os.path.realpath(os.path.join(module_dir, '..'))
syncthing_dir = os.path.join(module_dir, 'src', 'github.com', 'syncthing', 'syncthing')
prerequisite_tools_dir = os.path.dirname(os.path.realpath(__file__)) + os.path.sep + ".." + os.path.sep + ".." + os.path.sep + "syncthing-android-prereq"
min_sdk = get_min_sdk(project_dir)

# print ('Info: min_sdk = ' + str(min_sdk))

# Check if git is available.
git_bin = which("git");
if not git_bin:
    fail('Error: git is not available on the PATH.')

print('git_bin=\'' + git_bin + '\'')

# Check for prebuilt libraries from cache
def check_and_copy_prebuilt_libraries():
    """Check if prebuilt libraries exist for current syncthing commit and copy them if found"""
    try:
        # Get syncthing commit hash
        syncthing_commit = subprocess.check_output([
            git_bin, '-C', syncthing_dir, 'rev-parse', 'HEAD'
        ]).decode().strip()
        
        # Use platform-specific path for prebuilt libraries
        if platform.system() == 'Windows':
            # On Windows, use relative path
            prebuilt_base_dir = os.path.join(prerequisite_tools_dir, 'prebuilt-jnilibs')
        else:
            # On Linux/other platforms, use absolute path to match cache location
            prebuilt_base_dir = '/opt/syncthing-android-prereq/prebuilt-jnilibs'
        
        prebuilt_dir = os.path.join(prebuilt_base_dir, syncthing_commit)
        
        if not os.path.isdir(prebuilt_dir):
            print('No prebuilt libraries found for syncthing commit', syncthing_commit, '- will build from scratch')
            return False
        
        print('Found prebuilt libraries directory for syncthing commit', syncthing_commit)
        
        # Check if all required architectures from BUILD_TARGETS are available
        missing_archs = []
        available_archs = []
        
        for target in BUILD_TARGETS:
            jni_dir = target['jni_dir']
            arch_dir = os.path.join(prebuilt_dir, jni_dir)
            lib_file = os.path.join(arch_dir, FILENAME_SYNCTHING_BINARY)
            
            if os.path.isfile(lib_file):
                available_archs.append(jni_dir)
            else:
                missing_archs.append(jni_dir)
        
        # If any architecture is missing, return False to rebuild all
        if missing_archs:
            print('Missing architectures:', ', '.join(missing_archs))
            print('Will build all architectures from scratch to ensure consistency')
            return False
        
        # Create target directory
        target_libs_dir = os.path.join(project_dir, 'app', 'src', 'main', 'jniLibs')
        if not os.path.isdir(target_libs_dir):
            os.makedirs(target_libs_dir)
        
        # Copy prebuilt libraries for each architecture from BUILD_TARGETS
        for target in BUILD_TARGETS:
            jni_dir = target['jni_dir']
            src_arch_dir = os.path.join(prebuilt_dir, jni_dir)
            dst_arch_dir = os.path.join(target_libs_dir, jni_dir)
            
            # Create target architecture directory
            if not os.path.isdir(dst_arch_dir):
                os.makedirs(dst_arch_dir)
            
            # Copy the library file
            src_lib_file = os.path.join(src_arch_dir, FILENAME_SYNCTHING_BINARY)
            dst_lib_file = os.path.join(dst_arch_dir, FILENAME_SYNCTHING_BINARY)
            
            shutil.copy2(src_lib_file, dst_lib_file)
            print('Copied prebuilt library for', jni_dir)
        
        print('Copied prebuilt libraries from cache')
        return True
            
    except Exception as e:
        print('Error checking for prebuilt libraries:', str(e))
        print('Will build from scratch')
        return False

# Try to use prebuilt libraries first
if check_and_copy_prebuilt_libraries():
    print('Using prebuilt libraries, skipping native build')
    sys.exit(0)

# Check if go is available.
go_bin = which("go");
if not go_bin:
    fail('Error: go is not available on the PATH. Please install go to build go itself.')

# Check if the available Go has the correct version
# If not, use present go to build the correct go version.
install_go()

# Update go_bin to point to the newly built Go
go_bin = which("go")
if not go_bin:
    fail('Error: go is not available on the PATH after installation.')

print('go_bin=\'' + go_bin + '\'')

# Check if "ANDROID_NDK_HOME" env var is set. If not, try to discover and set it.
get_ndk_ready()
if not os.environ.get('ANDROID_NDK_HOME', ''):
    fail('Error: ANDROID_NDK_HOME environment variable not defined')
print('ANDROID_NDK_HOME=\'' + os.environ.get('ANDROID_NDK_HOME', '') + '\'')

# Make sure all tags are available for git describe
print('Invoking git fetch ...')
subprocess.check_call([
    'git',
    '-C',
    syncthing_dir,
    'fetch',
    '--tags'
])

subprocess.check_call([go_bin, 'env', '-w', 'GOFLAGS=-buildvcs=false'], cwd=syncthing_dir)

if FORCE_DISPLAY_SYNCTHING_VERSION:
    syncthingVersion = FORCE_DISPLAY_SYNCTHING_VERSION.replace("rc", "preview");
else:
    print('Invoking git describe ...')
    syncthingVersion = subprocess.check_output([
        git_bin,
        '-C',
        syncthing_dir,
        'describe',
        '--always'
    ]).strip();
    syncthingVersion = syncthingVersion.decode().replace("rc", "preview");

print('Building syncthing version', syncthingVersion);
print('SOURCE_DATE_EPOCH=[' + os.environ['SOURCE_DATE_EPOCH'] + ']');
for target in BUILD_TARGETS:
    print('')
    print('*** Building for', target['arch'])

    cc = os.path.join(
        os.environ['ANDROID_NDK_HOME'],
        'toolchains',
        'llvm',
        'prebuilt',
        PLATFORM_DIRS[platform.system()],
        'bin',
        target['cc'].format(min_sdk),
    )

    # See why "-checklinkname=0" is required: https://github.com/wlynxg/anet?tab=readme-ov-file#how-to-build-with-go-1230-or-later 
    environ = os.environ.copy()
    environ.update({
        'GOPATH': module_dir,
        'GO111MODULE': 'on',
        'CGO_ENABLED': '1',
        'EXTRA_LDFLAGS': '-checklinkname=0',
    })

    subprocess.check_call([go_bin, 'mod', 'download'], cwd=syncthing_dir)
    subprocess.check_call(
                              [go_bin, 'version'],
                              env=environ, cwd=syncthing_dir)
    subprocess.check_call([
                              go_bin, 'run', 'build.go', '-goos', 'android',
                              '-goarch', target['goarch'],
                              '-cc', cc,
                              '-version', syncthingVersion
                          ] + ['-no-upgrade', 'build'], env=environ, cwd=syncthing_dir)

    # Determine path of source artifact
    source_artifact = os.path.join(syncthing_dir, 'syncthing')

    # Copy compiled binary to jniLibs folder
    target_dir = os.path.join(project_dir, 'app', 'src', 'main', 'jniLibs', target['jni_dir'])
    if not os.path.isdir(target_dir):
        os.makedirs(target_dir)
    target_artifact = os.path.join(target_dir, FILENAME_SYNCTHING_BINARY)
    if os.path.exists(target_artifact):
        os.unlink(target_artifact)
    os.rename(os.path.join(syncthing_dir, 'syncthing'), target_artifact)

    print('*** Finished build for', target['arch'])

print('All builds finished')
