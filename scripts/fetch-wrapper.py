"""Restore the pinned official Gradle wrapper; verify the wrapper and distribution hashes."""
from pathlib import Path
import urllib.request, hashlib
root = Path(__file__).resolve().parents[1]
wrapper = root / 'gradle/wrapper'
wrapper.mkdir(parents=True, exist_ok=True)
base = 'https://raw.githubusercontent.com/gradle/gradle/v8.13.0/'
for relative in ['gradlew', 'gradlew.bat', 'gradle/wrapper/gradle-wrapper.jar']:
    data = urllib.request.urlopen(base + relative, timeout=30).read()
    if relative.endswith('.jar'):
        expected = urllib.request.urlopen('https://services.gradle.org/distributions/gradle-8.13-wrapper.jar.sha256').read().decode().strip()
        if hashlib.sha256(data).hexdigest() != expected: raise RuntimeError('Wrapper checksum mismatch')
    (root / relative).write_bytes(data)
checksum = urllib.request.urlopen('https://services.gradle.org/distributions/gradle-8.13-bin.zip.sha256').read().decode().strip()
(wrapper / 'gradle-wrapper.properties').write_text('distributionBase=GRADLE_USER_HOME\ndistributionPath=wrapper/dists\ndistributionUrl=https\\://services.gradle.org/distributions/gradle-8.13-bin.zip\ndistributionSha256Sum='+checksum+'\nnetworkTimeout=10000\nvalidateDistributionUrl=true\nzipStoreBase=GRADLE_USER_HOME\nzipStorePath=wrapper/dists\n')
print('Official Gradle 8.13 wrapper verified.')
