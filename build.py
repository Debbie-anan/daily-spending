"""Build without Gradle: Python 3, JDK 17, Android build-tools 35, platform 35.
Usage: python build.py --jdk PATH --tools PATH --platform PATH --work PATH --key PATH --output PATH
The signing key stays outside the source directory. This is a local test build.
"""
import argparse, pathlib, subprocess, zipfile, os
p=argparse.ArgumentParser()
for name in ('jdk','tools','platform','work','key','output'):p.add_argument('--'+name,required=True)
a=p.parse_args()
src=pathlib.Path(__file__).resolve().parent
jdk,tools,platform,work,key,out=[pathlib.Path(getattr(a,n)).resolve() for n in ('jdk','tools','platform','work','key','output')]
work.mkdir(parents=True,exist_ok=True);out.parent.mkdir(parents=True,exist_ok=True)
classes=work/'classes';classes.mkdir(exist_ok=True)
env=dict(os.environ,JAVA_HOME=str(jdk));env['PATH']=str(jdk/'bin')+os.pathsep+env.get('PATH','')
def run(*cmd):subprocess.run([str(x) for x in cmd],check=True,env=env)
run(jdk/'bin/javac.exe','-encoding','UTF-8','-source','8','-target','8','-classpath',platform/'android.jar','-d',classes,*src.glob('src/**/*.java'))
tests=work/'tests';tests.mkdir(exist_ok=True)
run(jdk/'bin/javac.exe','-encoding','UTF-8','-d',tests,src/'src/cn/local/notifyledger/PaymentParser.java',src/'tests/ParserTest.java')
run(jdk/'bin/java.exe','-cp',tests,'ParserTest')
run(jdk/'bin/javac.exe','-encoding','UTF-8','-d',tests,src/'src/cn/local/notifyledger/UpdatePolicy.java',src/'tests/UpdatePolicyTest.java')
run(jdk/'bin/java.exe','-cp',tests,'UpdatePolicyTest')
jar=work/'classes.jar'
with zipfile.ZipFile(jar,'w') as z:
 for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes).as_posix())
run(jdk/'bin/java.exe','-cp',tools/'lib/d8.jar','com.android.tools.r8.D8','--lib',platform/'android.jar','--min-api','26','--output',work,jar)
unsigned=work/'unsigned.apk'
resources=work/'resources.zip'
run(tools/'aapt2.exe','compile','--dir',src/'res','-o',resources)
run(tools/'aapt2.exe','link','-I',platform/'android.jar','--manifest',src/'AndroidManifest.xml','-o',unsigned,'-A',src/'assets',resources)
with zipfile.ZipFile(unsigned,'a') as z:z.write(work/'classes.dex','classes.dex')
aligned=work/'aligned.apk';run(tools/'zipalign.exe','-f','4',unsigned,aligned)
if not key.exists():
 key.parent.mkdir(parents=True,exist_ok=True)
 run(jdk/'bin/keytool.exe','-genkeypair','-keystore',key,'-storepass','android','-keypass','android','-alias','localtest','-dname','CN=Local Notification Ledger Test','-keyalg','RSA','-keysize','2048','-validity','3650')
run(jdk/'bin/java.exe','-jar',tools/'lib/apksigner.jar','sign','--ks',key,'--ks-key-alias','localtest','--ks-pass','pass:android','--key-pass','pass:android','--out',out,aligned)
run(jdk/'bin/java.exe','-jar',tools/'lib/apksigner.jar','verify','--verbose',out)
run(tools/'aapt2.exe','dump','badging',out)
print('APK:',out)
