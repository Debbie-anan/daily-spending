"""Publish a locally built and signed APK. Requires authenticated GitHub CLI."""
import argparse, hashlib, json, pathlib, subprocess, tempfile, xml.etree.ElementTree as ET
p=argparse.ArgumentParser()
p.add_argument('--apk',required=True)
p.add_argument('--notes-file',required=True)
a=p.parse_args()
repo='Debbie-anan/daily-spending'
root=pathlib.Path(__file__).resolve().parent
manifest=ET.parse(root/'AndroidManifest.xml').getroot()
ns='{http://schemas.android.com/apk/res/android}'
code=int(manifest.attrib[ns+'versionCode']);version=manifest.attrib[ns+'versionName'];tag='v'+version
apk=pathlib.Path(a.apk).resolve();notes_path=pathlib.Path(a.notes_file).resolve()
if not apk.is_file():raise SystemExit('APK does not exist')
notes=notes_path.read_text(encoding='utf-8-sig')
def run(*cmd):subprocess.run(cmd,check=True)
with tempfile.TemporaryDirectory(prefix='daily-spending-release-') as tmp:
    d=pathlib.Path(tmp);asset=d/'app-release.apk';asset.write_bytes(apk.read_bytes())
    metadata={'versionCode':code,'versionName':version,'apkUrl':f'https://github.com/{repo}/releases/download/{tag}/app-release.apk','sha256':hashlib.sha256(asset.read_bytes()).hexdigest(),'notes':notes}
    update=d/'update.json';update.write_text(json.dumps(metadata,ensure_ascii=False,indent=2),encoding='utf-8')
    run('gh','release','create',tag,'--repo',repo,'--title','今日花费 '+version,'--notes-file',str(notes_path),'--draft',str(asset),str(update))
    run('gh','release','edit',tag,'--repo',repo,'--draft=false','--latest')
print(f'https://github.com/{repo}/releases/tag/{tag}')
