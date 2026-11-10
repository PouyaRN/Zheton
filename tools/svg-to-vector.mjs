#!/usr/bin/env node
/**
 * Generates offline Android VectorDrawable XML resources (`ic_asset_*.xml` and `flag_*.xml`)
 * in `app/src/main/res/drawable/` so Android never relies on system emoji fonts.
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const DRAWABLE_DIR = resolve(__dirname, '..', 'app', 'src', 'main', 'res', 'drawable');

const VECTORS = {
  'ic_asset_cash.xml': `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#10B981"
        android:pathData="M3,6C3,4.9 3.9,4 5,4H19C20.1,4 21,4.9 21,6V18C21,19.1 20.1,20 19,20H5C3.9,20 3,19.1 3,18V6ZM5,6V18H19V6H5ZM12,9C13.66,9 15,10.34 15,12C15,13.66 13.66,15 12,15C10.34,15 9,13.66 9,12C9,10.34 10.34,9 12,9Z" />
</vector>
`,
  'ic_asset_bank.xml': `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#2563EB"
        android:pathData="M12,2L2,7V9H22V7L12,2ZM4,11V18H7V11H4ZM10.5,11V18H13.5V11H10.5ZM17,11V18H20V11H17ZM2,20V22H22V20H2Z" />
</vector>
`,
  'ic_asset_usd.xml': `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#15803D"
        android:pathData="M12,2C6.48,2 2,6.48 2,12C2,17.52 6.48,22 12,22C17.52,22 22,17.52 22,12C22,6.48 17.52,2 12,2ZM13,17.93V19H11V17.93C9.06,17.56 7.6,16.17 7.5,14.25H9.6C9.7,15.3 10.5,16.1 12,16.1C13.6,16.1 14.3,15.3 14.3,14.4C14.3,13.5 13.7,12.95 11.7,12.45C9.3,11.85 7.8,10.9 7.8,9.1C7.8,7.45 9.1,6.2 11,5.85V4.75H13V5.85C14.8,6.2 16,7.4 16.1,9.1H14C13.9,8.15 13.2,7.5 12,7.5C10.7,7.5 9.9,8.1 9.9,9C9.9,9.85 10.6,10.35 12.5,10.85C15,11.5 16.4,12.45 16.4,14.35C16.4,16.15 15,17.55 13,17.93Z" />
</vector>
`,
  'ic_asset_eur.xml': `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#1D4ED8"
        android:pathData="M15,18.5C12.49,18.5 10.32,17.08 9.24,15H15V13H8.58C8.53,12.67 8.5,12.34 8.5,12C8.5,11.66 8.53,11.33 8.58,11H15V9H9.24C10.32,6.92 12.5,5.5 15,5.5C16.61,5.5 18.09,6.09 19.23,7.07L21,5.3C19.41,3.87 17.3,3 15,3C11.08,3 7.76,5.51 6.52,9H3V11H6.06C6.02,11.33 6,11.66 6,12C6,12.34 6.02,12.67 6.06,13H3V15H6.52C7.76,18.49 11.08,21 15,21C17.31,21 19.41,20.13 21,18.7L19.22,16.93C18.09,17.91 16.62,18.5 15,18.5Z" />
</vector>
`,
  'ic_asset_gbp.xml': `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#7C3AED"
        android:pathData="M14,6C12.34,6 11,7.34 11,9V12H15V14H11V17H17V19H7V17H9V14H7V12H9V9C9,6.24 11.24,4 14,4C15.95,4 17.63,5.12 18.44,6.76L16.65,7.65C16.17,6.67 15.17,6 14,6Z" />
</vector>
`,
  'ic_asset_gold.xml': `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#D97706"
        android:pathData="M6,4H18L21,9L12,21L3,9L6,4ZM7.2,6L5.4,9H18.6L16.8,6H7.2ZM12,17.6L17.7,10H6.3L12,17.6Z" />
</vector>
`,
  'ic_asset_silver.xml': `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#64748B"
        android:pathData="M4,15L6.5,8H17.5L20,15H4ZM6.8,13H17.2L16.1,10H7.9L6.8,13ZM2,17H22V19H2V17Z" />
</vector>
`,
  'ic_asset_coin.xml': `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#EAB308"
        android:pathData="M12,2C6.48,2 2,6.48 2,12C2,17.52 6.48,22 12,22C17.52,22 22,17.52 22,12C22,6.48 17.52,2 12,2ZM12,19C8.13,19 5,15.87 5,12C5,8.13 8.13,5 12,5C15.87,5 19,8.13 19,12C19,15.87 15.87,19 12,19ZM12,7L13.5,10.5L17,12L13.5,13.5L12,17L10.5,13.5L7,12L10.5,10.5L12,7Z" />
</vector>
`,
  'ic_asset_stock.xml': `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#0284C7"
        android:pathData="M16,6L18.29,8.29L13.41,13.17L9.41,9.17L2,16.59L3.41,18L9.41,12L13.41,16L19.71,9.71L22,12V6H16Z" />
</vector>
`,
  'ic_asset_crypto.xml': `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#F59E0B"
        android:pathData="M12,2L20.66,7V17L12,22L3.34,17V7L12,2ZM12,4.31L5.34,8.15V15.85L12,19.69L18.66,15.85V8.15L12,4.31ZM11,7H13V8.2C14.4,8.5 15.2,9.4 15.2,10.6C15.2,11.4 14.7,12.1 13.9,12.4C14.9,12.7 15.5,13.5 15.5,14.5C15.5,15.9 14.4,16.8 13,17V18H11V17H9V15.3H12.7C13.3,15.3 13.7,14.9 13.7,14.3C13.7,13.7 13.3,13.3 12.7,13.3H10V11.7H12.5C13.1,11.7 13.5,11.3 13.5,10.8C13.5,10.2 13.1,9.8 12.5,9.8H9V8.2H11V7Z" />
</vector>
`,
  'flag_no.xml': `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path android:fillColor="#BA0C2F" android:pathData="M2,4H22V20H2V4Z" />
    <path android:fillColor="#FFFFFF" android:pathData="M7,4H11V20H7V4ZM2,10H22V14H2V10Z" />
    <path android:fillColor="#00205B" android:pathData="M8,4H10V20H8V4ZM2,11H22V13H2V11Z" />
</vector>
`,
  'flag_tr.xml': `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path android:fillColor="#E30A17" android:pathData="M2,4H22V20H2V4Z" />
    <path android:fillColor="#FFFFFF" android:pathData="M10.5,8C8.29,8 6.5,9.79 6.5,12C6.5,14.21 8.29,16 10.5,16C11.6,16 12.6,15.55 13.32,14.83C12.74,15.19 12.05,15.4 11.3,15.4C9.42,15.4 7.9,13.88 7.9,12C7.9,10.12 9.42,8.6 11.3,8.6C12.05,8.6 12.74,8.81 13.32,9.17C12.6,8.45 11.6,8 10.5,8ZM15.5,10.2L16.2,11.6L17.7,11.8L16.6,12.9L16.9,14.4L15.5,13.7L14.1,14.4L14.4,12.9L13.3,11.8L14.8,11.6L15.5,10.2Z" />
</vector>
`,
  'flag_ae.xml': `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path android:fillColor="#00732F" android:pathData="M8,4H22V9.33H8V4Z" />
    <path android:fillColor="#FFFFFF" android:pathData="M8,9.33H22V14.67H8V9.33Z" />
    <path android:fillColor="#000000" android:pathData="M8,14.67H22V20H8V14.67Z" />
    <path android:fillColor="#FF0000" android:pathData="M2,4H8V20H2V4Z" />
</vector>
`,
  'flag_ca.xml': `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path android:fillColor="#FF0000" android:pathData="M2,4H7V20H2V4ZM17,4H22V20H17V4Z" />
    <path android:fillColor="#FFFFFF" android:pathData="M7,4H17V20H7V4Z" />
    <path android:fillColor="#FF0000" android:pathData="M12,7L13,9.5L15.5,9L14,11.5L16,13L13,13.5V16H11V13.5L8,13L10,11.5L8.5,9L11,9.5L12,7Z" />
</vector>
`,
};

mkdirSync(DRAWABLE_DIR, { recursive: true });
for (const [filename, xml] of Object.entries(VECTORS)) {
  writeFileSync(join(DRAWABLE_DIR, filename), xml, 'utf8');
}
console.log(`Generated ${Object.keys(VECTORS).length} VectorDrawable XML files in ${DRAWABLE_DIR}`);

