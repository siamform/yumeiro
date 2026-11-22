const LOG=console.log.bind(console);
// F-05-N: run with Node 22 next to crypto-js 4.2.0 (npm i crypto-js@4.2.0, test only, never shipped). Node's crypto stands in for ScriptCrypto. Commit-time output: cryptojs-shim-check.txt
const crypto=require('crypto'); const CJ=require('crypto-js');
const H=s=>Buffer.from(s,'hex');
global.sendMessage=(ch,a)=>{a=JSON.parse(a);try{
 if(ch==='crypto_hash'){const m={MD5:'md5',SHA1:'sha1',SHA256:'sha256',SHA512:'sha512',SHA224:'sha224',SHA384:'sha384'}[a[0]];return crypto.createHash(m).update(H(a[1])).digest('hex');}
 if(ch==='crypto_hmac'){const m={MD5:'md5',SHA1:'sha1',SHA256:'sha256',SHA512:'sha512'}[a[0]];return crypto.createHmac(m,H(a[1])).update(H(a[2])).digest('hex');}
 if(ch==='crypto_cipher'){let [d,c,mo,p,k,iv,data]=a;k=H(k);let alg=c==='AES'?`aes-${k.length*8}-${mo.toLowerCase()}`:`des-ede3-${mo.toLowerCase()}`;if(c!=='AES'&&k.length==16)k=Buffer.concat([k,k.slice(0,8)]);
  const x=(d==='enc'?crypto.createCipheriv:crypto.createDecipheriv)(alg,k,mo==='ECB'?null:H(iv));x.setAutoPadding(p==='Pkcs7');return Buffer.concat([x.update(H(data)),x.final()]).toString('hex');}
 if(ch==='crypto_evpkdf'){let [p,s,kb,ib]=a;let need=+kb+ +ib,out=Buffer.alloc(0),prev=Buffer.alloc(0);while(out.length<need){prev=crypto.createHash('md5').update(Buffer.concat([prev,H(p),H(s)])).digest();out=Buffer.concat([out,prev]);}return out.slice(0,need).toString('hex');}
 return '';}catch(e){return ''}};
global.btoa=s=>Buffer.from(s,'latin1').toString('base64'); global.atob=s=>Buffer.from(s,'base64').toString('latin1');
const src=require('fs').readFileSync('/home/user/workspace/yumeiro/app/src/main/assets/js/provider_runtime.js','utf8');
global.__result=()=>{};global.__error=()=>{};
new Function(src)();
const Y=globalThis.require('crypto-js'); let fails=0;
const eq=(n,a,b)=>{if(a!==b){fails++;LOG('FAIL',n,a,b)}else LOG('ok',n)};
eq('md5',Y.MD5('hello').toString(),CJ.MD5('hello').toString());
eq('sha256 utf8',Y.SHA256('héllo ✓').toString(),CJ.SHA256('héllo ✓').toString());
eq('hmac256',Y.HmacSHA256('msg','key').toString(),CJ.HmacSHA256('msg','key').toString());
eq('hmacmd5 b64',Y.HmacMD5('msg','key').toString(Y.enc.Base64),CJ.HmacMD5('msg','key').toString(CJ.enc.Base64));
const k=CJ.enc.Hex.parse('000102030405060708090a0b0c0d0e0f'),iv=CJ.enc.Hex.parse('0f0e0d0c0b0a09080706050403020100');
const ct=CJ.AES.encrypt('secret stream url',k,{iv,mode:CJ.mode.CBC,padding:CJ.pad.Pkcs7}).toString();
eq('aes cbc key dec',Y.AES.decrypt(ct,Y.enc.Hex.parse('000102030405060708090a0b0c0d0e0f'),{iv:Y.enc.Hex.parse('0f0e0d0c0b0a09080706050403020100'),mode:Y.mode.CBC,padding:Y.pad.Pkcs7}).toString(Y.enc.Utf8),'secret stream url');
const ct2=CJ.AES.encrypt('passphrase text','pw').toString();
eq('aes passphrase dec',Y.AES.decrypt(ct2,'pw').toString(Y.enc.Utf8),'passphrase text');
eq('aes passphrase roundtrip',CJ.AES.decrypt(Y.AES.encrypt('round','pw').toString(),'pw').toString(CJ.enc.Utf8),'round');
eq('aes ecb enc',Y.AES.encrypt('abc',Y.enc.Utf8.parse('1234567890123456'),{mode:Y.mode.ECB}).toString(),CJ.AES.encrypt('abc',CJ.enc.Utf8.parse('1234567890123456'),{mode:CJ.mode.ECB}).toString());
const dk=CJ.enc.Utf8.parse('123456789012345678901234'); const d3=CJ.TripleDES.encrypt('triple',dk,{mode:CJ.mode.ECB}).toString();
eq('3des',Y.TripleDES.decrypt(d3,Y.enc.Utf8.parse('123456789012345678901234'),{mode:Y.mode.ECB}).toString(Y.enc.Utf8),'triple');
eq('b64 roundtrip',Y.enc.Base64.stringify(Y.enc.Utf8.parse('ü1')),CJ.enc.Base64.stringify(CJ.enc.Utf8.parse('ü1')));
eq('b64 parse words',JSON.stringify(Y.enc.Base64.parse('AAECAwQ=').words.slice(0,2)),JSON.stringify(CJ.enc.Base64.parse('AAECAwQ=').words.slice(0,2)));
eq('wordarray create',Y.lib.WordArray.create([0x01020304],3).toString(),CJ.lib.WordArray.create([0x01020304],3).toString());
eq('cipherparams',Y.AES.decrypt(Y.lib.CipherParams.create({ciphertext:Y.enc.Base64.parse(ct)}),Y.enc.Hex.parse('000102030405060708090a0b0c0d0e0f'),{iv:Y.enc.Hex.parse('0f0e0d0c0b0a09080706050403020100')}).toString(Y.enc.Utf8),'secret stream url');
eq('default interop',Y.default.AES===Y.AES,true);
eq('zero pad',Y.AES.decrypt(CJ.AES.encrypt('zz',k,{iv,padding:CJ.pad.ZeroPadding}).toString(),Y.enc.Hex.parse('000102030405060708090a0b0c0d0e0f'),{iv:Y.enc.Hex.parse('0f0e0d0c0b0a09080706050403020100'),padding:Y.pad.ZeroPadding}).toString(Y.enc.Utf8),'zz');
eq('concat',Y.enc.Hex.parse('0102').concat(Y.enc.Hex.parse('03')).toString(),'010203');
LOG('fails',fails);
