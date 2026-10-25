/**
 * The coins the calculators offer, and the standard weights/purities their melt value needs.
 *
 * Weights and purities are the standard struck-coin figures, not measurements of a particular
 * coin: Emami and Bahar are both 8.133 g at 900‰; Nim is half, Rob a quarter, and Gerami is the
 * one-gram coin. A worn or mounted coin weighs less, and a collector coin is worth more than
 * its metal — neither is modelled here.
 */

export interface CoinPreset {
  readonly id: string;
  readonly label: string;
  readonly weightGrams: number;
  readonly purityPermille: number;
  /** Key in the rates Worker's `toman` map (`worker/src/index.ts`, `BONBAST_MAP`). */
  readonly rateKey: string;
}

export const COIN_PRESETS: readonly CoinPreset[] = Object.freeze([
  {
    id: 'emami',
    label: 'سکهٔ تمام امامی',
    weightGrams: 8.133,
    purityPermille: 900,
    rateKey: 'coin_emami',
  },
  {
    id: 'bahar',
    label: 'سکهٔ تمام بهار آزادی',
    weightGrams: 8.133,
    purityPermille: 900,
    rateKey: 'coin_bahar',
  },
  {
    id: 'nim',
    label: 'نیم‌سکه',
    weightGrams: 4.0665,
    purityPermille: 900,
    rateKey: 'coin_nim',
  },
  {
    id: 'rob',
    label: 'ربع‌سکه',
    weightGrams: 2.0333,
    purityPermille: 900,
    rateKey: 'coin_rob',
  },
  {
    id: 'gerami',
    label: 'سکهٔ گرمی',
    weightGrams: 1,
    purityPermille: 900,
    rateKey: 'coin_gerami',
  },
]);

export function coinPreset(id: string): CoinPreset | undefined {
  return COIN_PRESETS.find((coin) => coin.id === id);
}
