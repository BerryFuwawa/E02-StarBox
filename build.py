"""Build without Gradle. Requires Python 3, JDK 17, Android SDK 28/build-tools 34."""
from pathlib import Path
import argparse, os, subprocess, zipfile

ROOT = Path(__file__).resolve().parent

def java_bin(home):
    path = Path(home)/'bin'
    if not home or not path.is_dir():
        raise SystemExit('Set JAVA_HOME or pass --java-home (JDK 17).')
    return path

def executable(directory, name):
    return Path(directory)/(name + ('.exe' if os.name == 'nt' else ''))

def run(*args):
    subprocess.run([str(a) for a in args], check=True, cwd=ROOT)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME', ''))
    parser.add_argument('--android-jar', help='Path to platforms/android-28/android.jar')
    parser.add_argument('--build-tools', help='Path to build-tools/34.0.0')
    parser.add_argument('--output', help='APK output path; defaults to dist/E02-Starbox-v1.7.0.apk')
    parser.add_argument('--signing-key', help='Existing release keystore; never created automatically')
    parser.add_argument('--signing-alias', help='Alias in the existing release keystore')
    parser.add_argument('--signing-password-env', help='Environment variable containing the release keystore password')
    args = parser.parse_args()
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if not args.android_jar and not sdk:
        parser.error('Set ANDROID_HOME or pass --android-jar and --build-tools.')
    android = Path(args.android_jar or str(Path(sdk)/'platforms/android-28/android.jar'))
    tools = Path(args.build_tools or str(Path(sdk)/'build-tools/34.0.0'))
    java = java_bin(args.java_home)
    if not android.is_file() or not (tools/'lib/d8.jar').is_file():
        parser.error('Android platform/build-tools paths are invalid.')
    if args.signing_key:
        key = Path(args.signing_key)
        if not key.is_file() or not args.signing_alias or not args.signing_password_env:
            parser.error('Release signing requires an existing key, alias, and password environment variable name.')
        if not os.environ.get(args.signing_password_env):
            parser.error('The release signing password environment variable is missing or empty.')
        alias = args.signing_alias
        password_arg = 'env:' + args.signing_password_env
    elif args.signing_alias or args.signing_password_env:
        parser.error('Pass --signing-key with the other release signing options.')
    src = ROOT/'src'
    build = ROOT/'build'
    classes, dex = build/'classes', build/'dex'
    # Isolate each compilation to avoid stale classes without deleting old user files.
    import tempfile
    with tempfile.TemporaryDirectory(prefix='compile-', dir=build if build.exists() else ROOT) as temp:
        classes, dex = Path(temp)/'classes', Path(temp)/'dex'
        classes.mkdir(); dex.mkdir()
        run(executable(java,'javac'), '-encoding','UTF-8','--release','8','-classpath',str(android)+os.pathsep+str(ROOT/'third_party/zxing/core-3.5.3.jar'),'-d',classes,*sorted(src.glob('*.java')))
        run(executable(java,'java'),'-cp',tools/'lib/d8.jar','com.android.tools.r8.D8','--lib',android,'--min-api','28','--output',dex,*sorted(classes.rglob('*.class')),ROOT/'third_party/zxing/core-3.5.3.jar')
        build.mkdir(exist_ok=True)
        unsigned, aligned = build/'unsigned.apk', build/'aligned.apk'
        run(executable(tools,'aapt'),'package','-f','-M',src/'AndroidManifest.xml','-S',src/'res','-A',src/'assets','-I',android,'-F',unsigned)
        with zipfile.ZipFile(unsigned,'a',zipfile.ZIP_DEFLATED) as z:
            z.write(dex/'classes.dex','classes.dex')
        run(executable(tools,'zipalign'),'-f','4',unsigned,aligned)
    private = ROOT/'.local'
    private.mkdir(exist_ok=True)
    if not args.signing_key:
        key = private/'development.p12'
        # This password protects a local development key, not a distributed credential.
        password = 'galaxy-local-development'
        alias, password_arg = 'galaxy', 'pass:' + password
        if not key.exists():
            run(executable(java,'keytool'),'-genkeypair','-keystore',key,'-storepass',password,'-alias',alias,'-keyalg','RSA','-keysize','2048','-validity','3650','-dname','CN=Galaxy E02 Tools Development','-storetype','PKCS12')
    dist = ROOT/'dist'
    dist.mkdir(exist_ok=True)
    apk = Path(args.output).resolve() if args.output else dist/'E02-Starbox-v1.7.0.apk'
    apk.parent.mkdir(parents=True,exist_ok=True)
    run(executable(java,'java'),'-jar',tools/'lib/apksigner.jar','sign','--ks',key,'--ks-pass',password_arg,'--ks-key-alias',alias,'--min-sdk-version','28','--v4-signing-enabled','false','--out',apk,aligned)
    run(executable(java,'java'),'-jar',tools/'lib/apksigner.jar','verify','--verbose',apk)
    run(executable(tools,'zipalign'),'-c','4',apk)
    print('Built:', apk)

if __name__ == '__main__':
    main()
