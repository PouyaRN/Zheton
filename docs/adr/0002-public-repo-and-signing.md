# ADR-0002: مهاجرت به مخزن عمومی مستقل، امضای دائمی APK و غیرفعال‌سازی پشتیبان ابری

<div dir="rtl">

- **وضعیت:** پذیرفته‌شده (Accepted)
- **تاریخ:** ۱۴۰۵/۰۸/۱۹ (`2026-11-10`)

## ۱. زمینه و مسئله
1. مخزن اولیهٔ `PouyaRN/Zheton` یک Fork خصوصی از `securityvisions/Andis` بود و گیت‌هاب تغییر وضعیت مستقیم Fork خصوصی به Public را با خطای `422 Validation Failed` مسدود می‌کند.
2. ورک‌فلو `release.yml` در نبود Secret، در هر بیلد یک کلید موقت جدید می‌ساخت که مانع ارتقای نسخه‌ها روی دستگاه کاربران می‌شد.
3. ویژگی `android:allowBackup="true"` در `AndroidManifest.xml` باعث ارسال پایگاه دادهٔ مالی (`durable.db`) به فضای ابری گوگل می‌شد که با وعدهٔ «۱۰۰٪ آفلاین و فقط روی گوشی» در تضاد بود.

## ۲. تصمیم
1. **مخزن عمومی مستقل (`Standalone Public Repo`):** مخزن خصوصی قبلی به `PouyaRN/Zheton-fork-backup` تغییر نام داده و آرشیو شد و مخزن عمومی مستقل `PouyaRN/Zheton` با حفظ کامل تاریخچهٔ کامیت‌ها و درج کامل کپی‌رایت سازندگان اصلی (`securityvisions` و `Sohail Lajevardi / doxigo`) در `LICENSE` و `NOTICE.md` ایجاد گردید.
2. **کلید امضای دائمی `RSA-4096`:** کلید دائمی به صورت آفلاین تولید شد، اثرانگشت گواهی (`73:8A:13:45:B9:90:B0:11:BB:48:FD:5F:A7:05:DD:A7:C5:2D:3C:9E:6E:37:AD:7F:4A:89:39:AC:26:EA:FF:F0`) در مستندات ثبت گردید و تولید کلید موقت از `release.yml` حذف شد.
3. **غیرفعال‌سازی کامل پشتیبان ابری (`allowBackup="false"`):** مقدار `android:allowBackup="false"` در `AndroidManifest.xml` اعمال شد و تمامی تگ‌های `<include>` از `backup_rules.xml` و `data_extraction_rules.xml` حذف شدند.

</div>

