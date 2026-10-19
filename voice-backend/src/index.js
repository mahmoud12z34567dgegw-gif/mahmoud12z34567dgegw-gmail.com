// Cloudflare Workers + D1 authentication and LiveKit audio-only access. Never embed server keys in an APK.
const enc=new TextEncoder();const now=()=>Math.floor(Date.now()/1000);
const textHex=bytes=>Array.from(new Uint8Array(bytes),b=>b.toString(16).padStart(2,'0')).join('');
const buf64=bytes=>{let s='';for(let b of new Uint8Array(bytes))s+=String.fromCharCode(b);return btoa(s).replace(/=/g,'').replace(/\+/g,'-').replace(/\//g,'_')};
const hash=async x=>textHex(await crypto.subtle.digest('SHA-256',enc.encode(x)));
const salt=()=>buf64(crypto.getRandomValues(new Uint8Array(18)));
async function passwordHash(p,s){const key=await crypto.subtle.importKey('raw',enc.encode(p),'PBKDF2',false,['deriveBits']);return textHex(await crypto.subtle.deriveBits({name:'PBKDF2',salt:enc.encode(s),iterations:120000,hash:'SHA-256'},key,256))}
const same=(a,b)=>{if(typeof a!=='string'||typeof b!=='string'||a.length!==b.length)return false;let d=0;for(let i=0;i<a.length;i++)d|=a.charCodeAt(i)^b.charCodeAt(i);return d===0};
async function signedJwt(payload,key,secret){const h=buf64(enc.encode(JSON.stringify({alg:'HS256',typ:'JWT'}))),p=buf64(enc.encode(JSON.stringify(payload)));const k=await crypto.subtle.importKey('raw',enc.encode(secret),{name:'HMAC',hash:'SHA-256'},false,['sign']);let sig=await crypto.subtle.sign('HMAC',k,enc.encode(h+'.'+p));return h+'.'+p+'.'+buf64(sig)}
const reply=(obj,status=200,cors={})=>new Response(JSON.stringify(obj),{status,headers:{'content-type':'application/json; charset=utf-8','cache-control':'no-store','x-content-type-options':'nosniff',...cors}});
async function reqJson(req){let t=await req.text();if(t.length>12000)throw Error('طلب كبير');return JSON.parse(t||'{}')}
const cleanEmail=e=>String(e||'').trim().toLowerCase();const isEmail=e=>/^[^\s@]{1,60}@[^\s@]{1,100}\.[^\s@]{2,20}$/.test(e);
async function who(req,db){let header=req.headers.get('Authorization')||'';if(!header.startsWith('Bearer '))return null;const raw=header.slice(7);if(!/^[A-Za-z0-9_-]{32,128}$/.test(raw))return null;return db.prepare('SELECT u.id,u.name,u.email,u.role FROM sessions s JOIN users u ON s.user_id=u.id WHERE s.hash=? AND s.expires_at>?').bind(await hash(raw),now()).first()}
async function livekitToken(env,room,user,speaker){let t=now();return signedJwt({iss:env.LIVEKIT_API_KEY,sub:user.id,name:user.name,nbf:t-10,iat:t,exp:t+1800,jti:crypto.randomUUID(),video:{roomJoin:true,room,canSubscribe:true,canPublish:!!speaker,canPublishData:false,canPublishSources:speaker?['microphone']:[]}},env.LIVEKIT_API_KEY,env.LIVEKIT_API_SECRET)}
async function livekitAdmin(env,room,method,data){let t=now(),token=await signedJwt({iss:env.LIVEKIT_API_KEY,sub:'academy-server',iat:t,nbf:t-10,exp:t+60,video:{roomAdmin:true,room}},env.LIVEKIT_API_KEY,env.LIVEKIT_API_SECRET);let host=env.LIVEKIT_URL.replace(/^wss:/,'https:').replace(/^ws:/,'http:').replace(/\/$/,'');return fetch(host+'/twirp/livekit.RoomService/'+method,{method:'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},body:JSON.stringify(data)});}
export default {async fetch(req,env){const origin=req.headers.get('Origin'),allowed=[env.APP_ORIGIN||'https://appassets.androidplatform.net'];let cors=origin&&allowed.includes(origin)?{'Access-Control-Allow-Origin':origin,'Vary':'Origin','Access-Control-Allow-Headers':'Authorization,Content-Type','Access-Control-Allow-Methods':'GET,POST,OPTIONS'}:{};
 if(req.method==='OPTIONS')return new Response(null,{status:origin&&allowed.includes(origin)?204:403,headers:cors});if(origin&&!allowed.includes(origin))return reply({error:'مصدر غير مسموح'},403);
 const url=new URL(req.url),path=url.pathname;let body={};try{if(req.method==='POST')body=await reqJson(req);const db=env.DB;
 if(path==='/api/health'&&req.method==='GET')return reply({ok:true,service:'ahlquran-voice',mode:'audio-only',version:'1.5'},200,cors);
 if(path==='/api/bootstrap'&&req.method==='POST'){
  if(!env.BOOTSTRAP_SECRET||!same(req.headers.get('X-Bootstrap-Secret')||'',env.BOOTSTRAP_SECRET))return reply({error:'غير مسموح'},403,cors);
  const exists=await db.prepare('SELECT COUNT(*) n FROM users').first();if(exists.n)return reply({error:'تم التأسيس سابقًا'},409,cors);
  let email=cleanEmail(body.email),password=String(body.password||''),name=String(body.name||'مدير الأكاديمية').trim().slice(0,70);if(!isEmail(email)||password.length<16||password.length>128||!name)return reply({error:'بيانات المدير غير صالحة'},400,cors);
  let s=salt();await db.prepare('INSERT INTO users VALUES (?,?,?,?,?,?,?)').bind(crypto.randomUUID(),name,email,'admin',s,await passwordHash(password,s),now()).run();return reply({ok:true},201,cors);
 }
 if(path==='/api/login'&&req.method==='POST'){
  let email=cleanEmail(body.email),p=String(body.password||'');if(!isEmail(email)||p.length>128)return reply({error:'بيانات الدخول غير صحيحة'},401,cors);
  let ek=await hash(email),fail=await db.prepare('SELECT * FROM login_failures WHERE email_hash=?').bind(ek).first();if(fail&&fail.attempts>=5&&now()-fail.window_start<900)return reply({error:'تم إيقاف المحاولات مؤقتًا لهذا الحساب؛ أعد المحاولة لاحقًا'},429,cors);
  let user=await db.prepare('SELECT * FROM users WHERE email=?').bind(email).first();if(!user||!same(await passwordHash(p,user.salt),user.pw_hash)){
   await db.prepare('INSERT INTO login_failures (email_hash,attempts,window_start) VALUES(?,1,?) ON CONFLICT(email_hash) DO UPDATE SET attempts=CASE WHEN ? - window_start >= 900 THEN 1 ELSE attempts+1 END,window_start=CASE WHEN ? - window_start >= 900 THEN ? ELSE window_start END').bind(ek,now(),now(),now(),now()).run();return reply({error:'بيانات الدخول غير صحيحة'},401,cors);
  }
  await db.prepare('DELETE FROM login_failures WHERE email_hash=?').bind(ek).run();
  let token=buf64(crypto.getRandomValues(new Uint8Array(32)));await db.prepare('INSERT INTO sessions VALUES (?,?,?)').bind(await hash(token),user.id,now()+86400).run();return reply({token,user:{id:user.id,name:user.name,email:user.email,role:user.role},expires_in:86400},200,cors);
 }
 let user=await who(req,db);if(!user)return reply({error:'سجّل دخول حساب الغرف الصوتية أولًا'},401,cors);
 if(path==='/api/me'&&req.method==='GET')return reply({user},200,cors);
 if(path==='/api/logout'&&req.method==='POST'){await db.prepare('DELETE FROM sessions WHERE hash=?').bind(await hash((req.headers.get('Authorization')||'').slice(7))).run();return reply({ok:true},200,cors)}
 if(path==='/api/users'&&req.method==='GET'){
  if(user.role==='student')return reply({error:'غير مصرح'},403,cors);
  let rows=await db.prepare("SELECT id,name,email,role FROM users WHERE role IN ('student','teacher') ORDER BY name LIMIT 300").all();return reply({users:rows.results},200,cors);
 }
 if(path==='/api/users'&&req.method==='POST'){
  if(user.role!=='admin')return reply({error:'للمدير فقط'},403,cors);
  let email=cleanEmail(body.email),pass=String(body.password||''),name=String(body.name||'').trim(),role=body.role;
  if(!isEmail(email)||!['student','teacher'].includes(role)||name.length<2||name.length>70||pass.length<12||pass.length>128)return reply({error:'بيانات الحساب غير صالحة (كلمة مرور 12 حرفًا على الأقل)'},400,cors);
  let s=salt(),id=crypto.randomUUID();try{await db.prepare('INSERT INTO users VALUES (?,?,?,?,?,?,?)').bind(id,name,email,role,s,await passwordHash(pass,s),now()).run()}catch(e){return reply({error:'البريد مستخدم بالفعل'},409,cors)}return reply({user:{id,name,email,role}},201,cors);
 }
 if(path==='/api/rooms'&&req.method==='POST'){
  if(user.role!=='teacher')return reply({error:'المعلم فقط ينشئ غرفة'},403,cors);
  let title=String(body.title||'').trim(),ids=Array.isArray(body.students)?[...new Set(body.students)].filter(x=>typeof x==='string'):[];
  if(!title||title.length>100||!ids.length||ids.length>40)return reply({error:'اسم الغرفة وحدد طالبًا واحدًا على الأقل (حتى 40)'},400,cors);
  let placeholders=ids.map(()=>'?').join(',');let result=await db.prepare('SELECT id FROM users WHERE role=\'student\' AND id IN ('+placeholders+')').bind(...ids).all();if(result.results.length!==ids.length)return reply({error:'أحد حسابات الطلاب غير صالح'},400,cors);
  let id=crypto.randomUUID();await db.batch([db.prepare('INSERT INTO rooms VALUES (?,?,?,?,NULL)').bind(id,title,user.id,now()),...ids.map(x=>db.prepare('INSERT INTO room_members VALUES (?,?,0)').bind(id,x))]);return reply({room:{id,title,teacher_id:user.id}},201,cors);
 }
 if(path==='/api/rooms'&&req.method==='GET'){
  let query=user.role==='admin'?db.prepare('SELECT r.*,u.name teacher FROM rooms r JOIN users u ON r.teacher_id=u.id ORDER BY r.created_at DESC LIMIT 150'):user.role==='teacher'?db.prepare('SELECT r.*,u.name teacher FROM rooms r JOIN users u ON r.teacher_id=u.id WHERE r.teacher_id=? ORDER BY r.created_at DESC LIMIT 150').bind(user.id):db.prepare('SELECT r.*,u.name teacher FROM rooms r JOIN room_members m ON m.room_id=r.id JOIN users u ON r.teacher_id=u.id WHERE m.user_id=? ORDER BY r.created_at DESC LIMIT 150').bind(user.id);
  let rows=await query.all();return reply({rooms:rows.results},200,cors);
 }
 const match=path.match(/^\/api\/rooms\/([a-f0-9-]{36})(?:\/(join|members|speaker|end))?$/);if(match){let room=await db.prepare('SELECT * FROM rooms WHERE id=?').bind(match[1]).first();if(!room)return reply({error:'الغرفة غير موجودة'},404,cors);const own=room.teacher_id===user.id;const member=await db.prepare('SELECT * FROM room_members WHERE room_id=? AND user_id=?').bind(room.id,user.id).first();if(!(own||member||user.role==='admin'))return reply({error:'غير مدعو لهذه الغرفة'},403,cors);
  if(match[2]==='members'&&req.method==='GET'){
   let rows=await db.prepare('SELECT u.id,u.name,m.can_speak FROM room_members m JOIN users u ON m.user_id=u.id WHERE m.room_id=? ORDER BY u.name').bind(room.id).all();return reply({room,members:rows.results},200,cors);
  }
  if(match[2]==='join'&&req.method==='POST'){
   if(room.ended_at||(!own&&!member)||user.role==='admin')return reply({error:'الغرفة مغلقة أو لست مدعوًا'},403,cors);
   if(!env.LIVEKIT_URL||!env.LIVEKIT_API_KEY||!env.LIVEKIT_API_SECRET)return reply({error:'مزود الصوت غير مهيأ بعد'},503,cors);
   return reply({serverUrl:env.LIVEKIT_URL,participantToken:await livekitToken(env,room.id,user,own||member?.can_speak),room:{id:room.id,title:room.title},canSpeak:!!(own||member?.can_speak),user},200,cors);
  }
  if(match[2]==='speaker'&&req.method==='POST'){
   if(!own||room.ended_at)return reply({error:'المعلم المنشئ فقط'},403,cors);
   let studentId=String(body.studentId||''),allow=body.allow===true;let m=await db.prepare('SELECT * FROM room_members WHERE room_id=? AND user_id=?').bind(room.id,studentId).first();if(!m)return reply({error:'الطالب غير مدعو'},404,cors);
   await db.prepare('UPDATE room_members SET can_speak=? WHERE room_id=? AND user_id=?').bind(allow?1:0,room.id,studentId).run();
   // Live participant permission update may return participant_not_found if not yet connected; DB remains source of truth for next join.
   if(env.LIVEKIT_URL&&env.LIVEKIT_API_KEY&&env.LIVEKIT_API_SECRET){try{await livekitAdmin(env,room.id,'UpdateParticipant',{room:room.id,identity:studentId,permission:{canPublish:allow,canSubscribe:true,canPublishData:false,canPublishSources:allow?[2]:[]}})}catch(_){}}
   return reply({ok:true,canSpeak:allow},200,cors);
  }
  if(match[2]==='end'&&req.method==='POST'){
   if(!own)return reply({error:'المعلم فقط'},403,cors);await db.prepare('UPDATE rooms SET ended_at=? WHERE id=? AND ended_at IS NULL').bind(now(),room.id).run();if(env.LIVEKIT_URL&&env.LIVEKIT_API_KEY&&env.LIVEKIT_API_SECRET){try{await livekitAdmin(env,room.id,'DeleteRoom',{room:room.id})}catch(_){}}
   return reply({ok:true},200,cors);
  }
 }
 return reply({error:'المسار غير موجود'},404,cors);
 }catch(e){return reply({error:'تعذر تنفيذ الطلب؛ راجع البيانات وإعداد الخدمة'},500,cors)} }};
