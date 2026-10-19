"""Build bundled offline OCR APK using Gradle 8.9 + JDK17 + Android SDK35.
Example: python build.py --jdk C:/jdk17 --sdk C:/Android/sdk --key C:/private/localtest.jks --output C:/out/app.apk
Use the original localtest signing key to preserve upgrade compatibility.
"""
import argparse, pathlib, subprocess, os, shutil, tempfile
p=argparse.ArgumentParser()
for name in ('jdk','sdk','key','output'): p.add_argument('--'+name,required=True)
p.add_argument('--gradle',help='Gradle executable; defaults to checked-in wrapper')
a=p.parse_args();root=pathlib.Path(__file__).resolve().parent
jdk,sdk,key,out=[pathlib.Path(getattr(a,n)).resolve() for n in ('jdk','sdk','key','output')]
if not key.is_file():raise SystemExit('Original signing key must already exist; no replacement key will be generated.')
env=dict(os.environ,JAVA_HOME=str(jdk),ANDROID_HOME=str(sdk),LEDGER_SIGNING_KEY=str(key));env['PATH']=str(jdk/'bin')+os.pathsep+env.get('PATH','')
exe='.exe' if os.name=='nt' else ''
def run(*cmd):subprocess.run([str(x) for x in cmd],check=True,env=env)
with tempfile.TemporaryDirectory(prefix='ledger-rule-tests-') as tmp:
    java=root/'src/cn/local/notifyledger'
    run(jdk/('bin/javac'+exe),'-encoding','UTF-8','-d',tmp,java/'PaymentParser.java',java/'RecordPrivacy.java',java/'ScreenshotParser.java',root/'tests/ParserTest.java',root/'tests/RecordPrivacyTest.java',root/'tests/ScreenshotParserTest.java')
    for test in ('ParserTest','RecordPrivacyTest','ScreenshotParserTest'):run(jdk/('bin/java'+exe),'-cp',tmp,test)
gradle=pathlib.Path(a.gradle).resolve() if a.gradle else root/('gradlew.bat' if os.name=='nt' else 'gradlew')
run(gradle,'-p',root,'testReleaseUnitTest','assembleRelease','--console=plain')
artifact=root/'build/outputs/apk/release/NotifyLedger-release.apk'
out.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(artifact,out)
tools=sdk/'build-tools/35.0.0'
run(jdk/('bin/java'+exe),'-jar',tools/'lib/apksigner.jar','verify','--verbose',out)
run(tools/('aapt2'+exe),'dump','badging',out)
print('APK:',out)
