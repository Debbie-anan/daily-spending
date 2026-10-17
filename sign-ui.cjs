// Usage: node sign-ui.cjs PRIVATE_KEY_PEM OUTPUT_DIRECTORY VERSION
// Only signed public interface files go into OUTPUT_DIRECTORY. Never upload the key.
const fs=require('fs'),path=require('path'),crypto=require('crypto');
const [keyFile,outDir,versionText]=process.argv.slice(2);
const version=Number(versionText);
if(!keyFile||!outDir||!Number.isSafeInteger(version)||version<2)throw Error('Expected key path, output directory and integer version >= 2');
const html=fs.readFileSync(path.join(__dirname,'assets/index.html'));
const sha256=crypto.createHash('sha256').update(html).digest('hex');
const minShell=7;
const signature=crypto.sign('RSA-SHA256',Buffer.from(`${version}\n${minShell}\n${sha256}`),fs.readFileSync(keyFile)).toString('base64');
fs.mkdirSync(outDir,{recursive:true});
fs.writeFileSync(path.join(outDir,`ui-${version}.txt`),html);
fs.writeFileSync(path.join(outDir,'interface.json'),JSON.stringify({version,minShell,sha256,signature,html:`ui-${version}.txt`},null,2));
console.log(`Signed interface v${version}; private key not copied`);
