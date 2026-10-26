"""Optional workspace-local JDK/SDK/Gradle bootstrap. HTTPS with normal TLS validation."""
from pathlib import Path
import hashlib, json, urllib.request, zipfile, os, subprocess, xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / '.tools'
TOOLS.mkdir(exist_ok=True)
def download(url, name, expected=None):
    dest = TOOLS / name
    if not dest.exists():
        print('Downloading', name, flush=True)
        with urllib.request.urlopen(url, timeout=120) as src, dest.open('wb') as out:
            while chunk := src.read(1024*1024): out.write(chunk)
    if expected and hashlib.sha256(dest.read_bytes()).hexdigest() != expected:
        raise RuntimeError('Checksum mismatch: ' + name)
    return dest

if os.name != 'nt': raise SystemExit('Use Android Studio SDK Manager and JDK 17 on this platform.')
jdks = list(TOOLS.glob('jdk-17*'))
if not jdks:
    z = download('https://aka.ms/download-jdk/microsoft-jdk-17.0.17-windows-x64.zip', 'jdk.zip')
    with zipfile.ZipFile(z) as arc: arc.extractall(TOOLS)
    jdks = list(TOOLS.glob('jdk-17*'))
java = jdks[0]
sdk = TOOLS / 'android-sdk'
if not (sdk / 'cmdline-tools/latest/bin/sdkmanager.bat').exists():
    z = download('https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip', 'android-tools.zip')
    with zipfile.ZipFile(z) as arc: arc.extractall(TOOLS / 'sdk-unpack')
    (sdk / 'cmdline-tools').mkdir(parents=True, exist_ok=True)
    (TOOLS / 'sdk-unpack/cmdline-tools').rename(sdk / 'cmdline-tools/latest')
env = dict(os.environ, JAVA_HOME=str(java), ANDROID_HOME=str(sdk), GRADLE_USER_HOME=str(TOOLS / 'gradle-home'))
env['PATH'] = str(java / 'bin') + os.pathsep + env['PATH']
licenses = sdk / 'licenses'
licenses.mkdir(exist_ok=True)
# Standard Android SDK license hashes. Running this script accepts the Android SDK license.
(licenses / 'android-sdk-license').write_text('\n8933bad161af4178b1185d1a37fbf41ea5269c55\nd56f5187479451eabf01fb78af6dfcb131a6481e\n24333f8a63b6825ea9c5514f83c2829b004d1fee\n')
# Fetch the same official packages with Python: some managed Windows environments
# permit Python TLS but cannot connect using sdkmanager's Java network stack.
manifest = ET.fromstring(urllib.request.urlopen('https://dl.google.com/android/repository/repository2-3.xml').read())
for package, target in [('platform-tools', 'platform-tools'), ('platforms;android-36', 'platforms/android-36'), ('build-tools;35.0.0', 'build-tools/35.0.0')]:
    if (sdk / target / 'source.properties').exists(): continue
    node = next(n for n in manifest.findall('remotePackage') if n.attrib['path'] == package)
    archive = next(a for a in node.findall('.//archive') if a.findtext('host-os') in [None, 'windows'])
    relative = archive.findtext('complete/url')
    z = download('https://dl.google.com/android/repository/'+relative, relative)
    checksum = archive.find('complete/checksum')
    algorithm = checksum.attrib.get('type', 'sha1')
    if hashlib.new(algorithm, z.read_bytes()).hexdigest() != checksum.text: raise RuntimeError('SDK checksum mismatch')
    unpack = TOOLS / ('unpack-' + target.replace('/', '-'))
    with zipfile.ZipFile(z) as arc: arc.extractall(unpack)
    folder = next(p for p in unpack.iterdir() if p.is_dir())
    (sdk / target).parent.mkdir(parents=True, exist_ok=True)
    folder.rename(sdk / target)
gradle_url = 'https://services.gradle.org/distributions/gradle-8.13-bin.zip'
sha = urllib.request.urlopen(gradle_url+'.sha256').read().decode().strip()
z = download(gradle_url, 'gradle.zip', sha)
if not (TOOLS / 'gradle-8.13').exists():
    with zipfile.ZipFile(z) as arc: arc.extractall(TOOLS)
(ROOT / 'local.properties').write_text('sdk.dir='+str(sdk).replace('\\','/').replace(':','\\:')+'\n', newline='\n')
print('Build tools ready. Run scripts/build.ps1.', flush=True)
