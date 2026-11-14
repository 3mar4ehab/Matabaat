# مصادر بيانات Matabaat — مصر

## مصدر البيانات الأولي
يستخدم Matabaat بيانات OpenStreetMap (OSM) فقط للبيانات المفتوحة التي تسمح شروطها بإعادة الاستخدام:
- كاميرات السرعة الثابتة: `highway=speed_camera`
- عناصر تهدئة المرور والمطبات: `traffic_calming=*`

الاستعلام الموجود في `tools/osm-egypt-hazards.overpassql` يستخرج هذه العناصر داخل حدود مصر من Overpass API. النتائج ليست شاملة بالضرورة، وقد تحتوي على بيانات ناقصة أو قديمة؛ يجب إظهارها كمصدر OSM وليس كبلاغ مؤكد من مستخدم.

## الترخيص والإسناد
بيانات OSM مرخصة بموجب Open Database License (ODbL). يجب إظهار:
`© OpenStreetMap contributors`
مع رابط https://www.openstreetmap.org/copyright داخل التطبيق، والاحتفاظ بمصدر كل سجل ومعرّف عنصر OSM وتاريخ الاستيراد. راجع https://opendatacommons.org/licenses/odbl/ و https://osmfoundation.org/wiki/Attribution_Guidelines.

عند دمج بيانات OSM مع بيانات المستخدمين، يجب مراجعة التزامات ODbL الخاصة بقواعد البيانات المشتقة/المجمعة قبل النشر التجاري. لا تنسخ بيانات Radarbot أو أي قاعدة مغلقة.

## Firebase — ما يلزم قبل تفعيل المزامنة
لا يمكن توصيل التطبيق بمشروع Firebase حقيقي دون إنشاء مشروع مملوك لك. المطلوب:
1. إنشاء مشروع Firebase من https://console.firebase.google.com/
2. إضافة Android app بالمعرّف `com.roadalert.egypt`.
3. تنزيل ملف `google-services.json` ورفعه إلى جذر مجلد `app/` في المستودع، أو إرساله هنا كمرفق حتى نجهز التكامل.
4. تفعيل Cloud Firestore.

لا تضع Service Account JSON أو مفاتيح Admin داخل التطبيق أو في المستودع. استيراد بيانات OSM إلى Firestore يجب أن يتم من بيئة موثوقة/خدمة خلفية بصلاحيات محدودة، وليس من APK مباشرة.

## نموذج بيانات مقترح
كل hazard يحتوي على:
- `type`: SPEED_CAMERA / SPEED_BUMP / POTHOLE
- `latitude`, `longitude`
- `source`: OSM أو USER
- `sourceId`: معرّف عنصر OSM عند توفره
- `createdAt`, `updatedAt`
- `confidence`, `confirmations`, `reports`

بلاغات المستخدمين تحتاج Firebase Authentication أو آلية موثوقة لتقليل السبام، مع قواعد Firestore تمنع القراءة/الكتابة غير المصرح بها. لا تفتح قواعد Firestore للعامة دون قيود.

## حدود النسخة الحالية
قائمة البلاغات في APK الحالية لا تزال محلية. تم تجهيز زر الإبلاغ السريع، لكن المزامنة والاستيراد لن يشتغلا حتى إعداد مشروع Firebase وملف التطبيق الخاص به.
