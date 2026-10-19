# تفعيل الحصص الصوتية الفعلية — بدون شراء دومين

تحتاج حسابين خارجيين: Cloudflare Worker بعنوان `workers.dev` مجاني مبدئيًا، ومشروع LiveKit Cloud بحدود خطته الحالية. **خدمة الغرفة تعمل بالصوت فقط داخل APK، ولا تفتح تطبيق Google Meet أو تطلب الكاميرا.** لا تنشر مفاتيح API أو بيانات تسجيل الطلاب في GitHub؛ لا تضع مفاتيح على الهاتف.

## 1. إنشاء LiveKit Cloud
سجّل في https://cloud.livekit.io/ وأنشئ Project. من إعداداته احفظ عندك:
- Project URL بنمط `wss://...livekit.cloud`
- API Key وAPI Secret (سري)
ستستخدمهم في أسرار Cloudflare فقط، ولن تُضمِّنهم داخل Android.

## 2. تجهيز Worker وقاعدة البيانات
من مجلد `voice-backend` على جهازك، يلزم Node.js متوافق مع Wrangler واتصال بحساب Cloudflare:
```sh
npm install
npx wrangler login
npx wrangler d1 create ahlquran_voice
```
انسخ `database_id` الذي سيظهر من النتيجة، واستبدل النص `REPLACE-WITH-D1-DATABASE-ID` في `wrangler.toml` به فقط.

أنشئ الجداول على قاعدة Cloudflare البعيدة:
```sh
npx wrangler d1 execute ahlquran_voice --remote --file=./schema.sql
```

## 3. الأسرار (اكتب القيم في الطرفية ولا تضعها في الملفات المرفوعة)
```sh
npx wrangler secret put LIVEKIT_URL
npx wrangler secret put LIVEKIT_API_KEY
npx wrangler secret put LIVEKIT_API_SECRET
npx wrangler secret put BOOTSTRAP_SECRET
```
اختر `BOOTSTRAP_SECRET` قيمة قوية مستقلة 32 حرفًا عشوائيًا أو أكثر، واحفظها بشكل آمن. `APP_ORIGIN` محدد إلى `https://appassets.androidplatform.net`؛ هو أصل ملفات Android المعبأة، وليس دومينًا مطلوبًا منك شراؤه.

## 4. النشر
```sh
npx wrangler deploy
```
سيظهر لك رابط الخدمة مثل `https://ahlquran-voice.YOUR-SUBDOMAIN.workers.dev`. افتح `https://.../api/health` للتحقق من الخدمة، ولا تنشر أسرارها.

## 5. إنشاء حساب المدير مرة واحدة من PowerShell على Windows
بدّل `YOUR-WORKER` بعنوان Worker الذي حصلت عليه، وأدخل القيم السرية عند جهازك فقط:
```powershell
$worker = 'https://YOUR-WORKER.workers.dev'
$bootstrap = Read-Host 'Bootstrap Secret'
$adminEmail = Read-Host 'Admin login email'
$adminPassword = Read-Host 'Admin password (16+ chars)' -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($adminPassword)
try { $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
$body = @{name='مدير الأكاديمية';email=$adminEmail;password=$plain} | ConvertTo-Json
$plain = $null
Invoke-RestMethod -Uri "$worker/api/bootstrap" -Method Post -Headers @{'X-Bootstrap-Secret'=$bootstrap} -ContentType 'application/json' -Body ([Text.Encoding]::UTF8.GetBytes($body))
```
هذا المسار يرفض إعادة تأسيس مستخدم إداري إذا كان هناك مستخدم سابق في D1. اسم البريد المستخدَم للدخول لا ينشئ صندوق بريد جديدًا.

## 6. ربط التطبيق وبناء APK
على الكمبيوتر عدّل **القيمة العامة فقط** في `app/src/main/assets/voice-config.js`:
```js
window.AHL_VOICE_API = 'https://YOUR-WORKER.workers.dev';
```
ارفع الملفات المعدلة لمستودع GitHub وشغّل Actions، ثم نزّل APK الجديد من Releases. لا تضع المفاتيح السرية في `voice-config.js`.

## 7. تجربة بين هاتفين
- افتح APK على هاتف المدير → الغرف الصوتية → سجّل بالبريد والباسورد السحابي → أنشئ حساب المعلم والطالب مع كلمات مرور قوية. سلّمهما بياناتهما بطريقة خاصة.
- على هاتف المعلم: افتح التطبيق ثم الغرف الصوتية، سجّل بحسابه السحابي → إنشاء غرفة → اختيار الطالب.
- على هاتف الطالب: سجّل بحسابه السحابي → تظهر الغرفة المسموح بها → دخول الصوت وهو **مستمع والميكروفون مغلق**.
- على هاتف المعلم: «السماح بالتسميع» للطالب. على هاتف الطالب: إن كان متصلًا ولم يتغير إذنه، يغادر ويعيد الدخول ثم يضغط «فتح ميكروفوني». المعلم يستطيع إلغاء الحق وإنهاء الغرفة للجميع.
- جرّب الصوت على شبكة Wi-Fi والبيانات، وجرب رفض حساب غير مدعو؛ اختبر الأذونات على إصدارات Android المستهدفة قبل استقبال طلاب حقيقيين.

## ملاحظات إنتاجية
المشروع **مرحلة اختبار تكامل**: ينقصه مراجعة أمان مستقلة، إدارة استعادة كلمات المرور، حماية شاملة من الإساءة، إرشادات حماية الأطفال وسياسات الخصوصية، ونسخ احتياطية لقاعدة D1. جلسات خدمة الصوت عمرها 24 ساعة؛ JWT الاتصال بالغرفة ينتهي بعد 30 دقيقة من الإصدار (انتهاء صلاحية البدء وليس قطع مكالمة متصلة تلقائيًا). كود الخادم لا يسجّل التلاوات، ولا يطلب التطبيق إذن الكاميرا أو التخزين المرئي. حدود الخطط المجانية والخدمة وتكاليفها خاضعة للمزوّد.
